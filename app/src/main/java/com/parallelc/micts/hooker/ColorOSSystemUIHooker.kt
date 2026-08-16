package com.parallelc.micts.hooker

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.parallelc.micts.config.XposedConfig.CONFIG_NAME
import com.parallelc.micts.config.XposedConfig.DEFAULT_CONFIG
import com.parallelc.micts.config.XposedConfig.KEY_GESTURE_TRIGGER
import com.parallelc.micts.config.XposedConfig.KEY_VIBRATE
import com.parallelc.micts.module
import com.parallelc.micts.ui.activity.triggerCircleToSearch
import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.Hooker
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

/**
 * ColorOS 16 gesture-bar integration.
 *
 * The class names and feature gates are based on ColorCTS (MIT), adapted to use
 * MiCTS remote preferences and trigger services.
 */
object ColorOSSystemUIHooker {
    private const val TAG = "MiCTS-ColorOS"
    private const val TRIGGER_DELAY_MS = 250L
    private const val DEBOUNCE_MS = 500L

    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var lastTriggerTime = 0L
    @Volatile private var cachedInputMonitor: Any? = null

    private var innerMonitorField: java.lang.reflect.Field? = null
    private var forcePilferMethod: java.lang.reflect.Method? = null
    private var pilferMethod: java.lang.reflect.Method? = null

    fun hook(param: PackageReadyParam) {
        val classLoader = param.classLoader

        runCatching {
            val featureClass = classLoader.loadClass(
                "com.oplusos.systemui.common.feature.CustomizeFeatureOption"
            )
            featureClass.declaredFields
                .firstOrNull { it.name == "sIsSupportCircleToSearchNavbarHidden" }
                ?.apply { isAccessible = true }
                ?.set(null, true)
        }.onFailure { log("ColorOS navbar feature flag was not found", it) }

        runCatching {
            val navBarUtils = classLoader.loadClass(
                "com.oplus.systemui.navigationbar.utils.OplusNavBarUtils"
            )
            module!!.hook(navBarUtils.getDeclaredMethod("isEnableOcrScreen"))
                .intercept(FeatureEnabledHooker())
        }.onFailure { log("ColorOS OCR/navigation gate hook failed", it) }

        val controllerClass = classLoader.loadClass(
            "com.oplus.systemui.navigationbar.gesture.otherbusiness.GestureHomeHandleEventController"
        )
        controllerClass.declaredMethods
            .firstOrNull { it.name == "onLongClick" && it.parameterCount == 0 }
            ?.let { module!!.hook(it).intercept(LongClickHooker()) }
            ?: error("GestureHomeHandleEventController.onLongClick was not found")

        runCatching {
            val compatClass = classLoader.loadClass(
                "com.android.systemui.shared.system.InputMonitorCompat"
            )
            innerMonitorField = compatClass.getDeclaredField("mInputMonitor").apply {
                isAccessible = true
            }
            forcePilferMethod = compatClass.getDeclaredMethod("forcePilferPointers")
            pilferMethod = classLoader.loadClass("android.view.InputMonitor")
                .getDeclaredMethod("pilferPointers")

            controllerClass.getDeclaredMethod("setInputMonitor", compatClass).let {
                module!!.hook(it).intercept(InputMonitorHooker())
            }
        }.onFailure { log("ColorOS input-monitor hook unavailable", it) }
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
            module!!.log(Log.ERROR, TAG, "ColorOS gesture detected, but CTS invocation failed")
        }
    }

    private fun currentApplication(): Context? = runCatching {
        Class.forName("android.app.ActivityThread")
            .getDeclaredMethod("currentApplication")
            .invoke(null) as? Context
    }.getOrNull()

    private fun pilferPointers() {
        runCatching {
            val monitor = cachedInputMonitor ?: return
            runCatching { forcePilferMethod?.invoke(monitor) }
            innerMonitorField?.get(monitor)?.let { pilferMethod?.invoke(it) }
        }.onFailure { log("Unable to consume ColorOS gesture input", it) }
    }

    private fun log(message: String, error: Throwable) {
        module!!.log(Log.WARN, TAG, message, error)
    }

    class FeatureEnabledHooker : Hooker {
        override fun intercept(chain: Chain): Any? {
            return if (isEnabled()) true else chain.proceed()
        }
    }

    class InputMonitorHooker : Hooker {
        override fun intercept(chain: Chain): Any? {
            cachedInputMonitor = chain.args.firstOrNull()
            return chain.proceed()
        }
    }

    class LongClickHooker : Hooker {
        override fun intercept(chain: Chain): Any? {
            if (!isEnabled()) return chain.proceed()

            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastTriggerTime < DEBOUNCE_MS) return true
            lastTriggerTime = now

            pilferPointers()
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({ trigger() }, TRIGGER_DELAY_MS)
            return true
        }
    }
}
