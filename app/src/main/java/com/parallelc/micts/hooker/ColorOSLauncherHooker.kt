package com.parallelc.micts.hooker

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import com.parallelc.micts.config.XposedConfig.CONFIG_NAME
import com.parallelc.micts.config.XposedConfig.DEFAULT_CONFIG
import com.parallelc.micts.config.XposedConfig.KEY_GESTURE_TRIGGER
import com.parallelc.micts.config.XposedConfig.KEY_VIBRATE
import com.parallelc.micts.module
import com.parallelc.micts.ui.activity.triggerCircleToSearch
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

/** ColorOS launcher-side fallback for ROM variants that handle the gesture in Quickstep. */
object ColorOSLauncherHooker {
    private const val TAG = "MiCTS-ColorOS"
    private const val TRIGGER_DELAY_MS = 250L
    private const val DEBOUNCE_MS = 500L

    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var lastTriggerTime = 0L

    fun hook(param: PackageReadyParam) {
        val classLoader = param.classLoader

        runCatching {
            val featureClass = classLoader.loadClass("com.android.common.config.FeatureOption")
            listOf("isSupportCircleToSearch", "isSupportCircleToSearchNavbarHidden")
                .forEach { name ->
                    module!!.hook(featureClass.getDeclaredMethod(name))
                        .intercept(FeatureEnabledHooker())
                }
        }.onFailure { log("ColorOS launcher feature gates were not found", it) }

        runCatching {
            val companionClass = classLoader.loadClass(
                "com.android.quickstep.inputconsumers.OplusCuiInputConsumer\$Companion"
            )
            module!!.hook(companionClass.getDeclaredMethod("isCUISupport"))
                .intercept(FeatureEnabledHooker())
        }.onFailure { log("ColorOS CUI feature gate was not found", it) }

        val detectorClass = classLoader.loadClass(
            "com.android.quickstep.inputconsumers.OplusCuiInputConsumer\$mGestureDetector\$1"
        )
        module!!.hook(detectorClass.getDeclaredMethod("onLongPress", MotionEvent::class.java))
            .intercept(LongPressHooker())
    }

    private fun isEnabled(): Boolean {
        val prefs = module!!.getRemotePreferences(CONFIG_NAME)
        return prefs.getBoolean(
            KEY_GESTURE_TRIGGER,
            DEFAULT_CONFIG[KEY_GESTURE_TRIGGER] as Boolean
        )
    }

    private fun trigger() {
        val prefs = module!!.getRemotePreferences(CONFIG_NAME)
        val vibrate = prefs.getBoolean(KEY_VIBRATE, DEFAULT_CONFIG[KEY_VIBRATE] as Boolean)
        val context = currentApplication()
        if (!triggerCircleToSearch(1, context, vibrate)) {
            module!!.log(Log.ERROR, TAG, "ColorOS launcher gesture detected, but CTS invocation failed")
        }
    }

    private fun currentApplication(): Context? = runCatching {
        Class.forName("android.app.ActivityThread")
            .getDeclaredMethod("currentApplication")
            .invoke(null) as? Context
    }.getOrNull()

    private fun activateInputConsumer(receiver: Any?, event: MotionEvent?) {
        if (receiver == null || event == null) return
        runCatching {
            val inputConsumer = receiver.javaClass.declaredFields
                .firstOrNull { it.name == "this\$0" }
                ?.apply { isAccessible = true }
                ?.get(receiver) ?: return
            inputConsumer.javaClass.getMethod("setActive", MotionEvent::class.java)
                .invoke(inputConsumer, event)
        }.onFailure { log("Unable to consume the ColorOS launcher gesture", it) }
    }

    private fun log(message: String, error: Throwable) {
        module!!.log(Log.WARN, TAG, message, error)
    }

    class FeatureEnabledHooker : Hooker {
        override fun intercept(chain: Chain): Any? {
            return if (isEnabled()) true else chain.proceed()
        }
    }

    class LongPressHooker : Hooker {
        override fun intercept(chain: Chain): Any? {
            if (!isEnabled()) return chain.proceed()

            val now = SystemClock.elapsedRealtime()
            if (now - lastTriggerTime < DEBOUNCE_MS) return null
            lastTriggerTime = now

            activateInputConsumer(chain.thisObject, chain.args.firstOrNull() as? MotionEvent)
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({ trigger() }, TRIGGER_DELAY_MS)
            return null
        }
    }
}
