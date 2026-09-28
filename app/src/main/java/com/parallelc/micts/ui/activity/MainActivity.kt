package com.parallelc.micts.ui.activity

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.media.AudioAttributes
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.parallelc.micts.config.AppConfig.KEY_FRESH_SESSION
import com.parallelc.micts.trigger.TriggerDiagnostics
import com.parallelc.micts.trigger.SidebarTrigger
import android.service.voice.VoiceInteractionSession
import kotlinx.coroutines.Job
import com.parallelc.micts.BuildConfig
import com.parallelc.micts.R
import com.parallelc.micts.config.AppConfig.CONFIG_NAME
import com.parallelc.micts.config.AppConfig.DEFAULT_CONFIG
import com.parallelc.micts.config.AppConfig.KEY_ASYNC_TRIGGER
import com.parallelc.micts.config.AppConfig.KEY_DEFAULT_DELAY
import com.parallelc.micts.config.AppConfig.KEY_TILE_DELAY
import com.parallelc.micts.config.AppConfig.KEY_VIBRATE
import com.parallelc.micts.module
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.lsposed.hiddenapibypass.HiddenApiBypass

const val LOG_TAG = BuildConfig.APP_NAME

@SuppressLint("PrivateApi")
fun triggerCircleToSearch(
    entryPoint: Int,
    context: Context?,
    vibrate: Boolean,
    sourceFlags: Int = VoiceInteractionSession.SHOW_SOURCE_ASSIST_GESTURE,
): Boolean {
    val result =  runCatching {
        val bundle = circleSearchArguments(entryPoint)
        val flags = VoiceInteractionSession.SHOW_WITH_ASSIST or
            VoiceInteractionSession.SHOW_WITH_SCREENSHOT or sourceFlags
        val iVimsClass = Class.forName("com.android.internal.app.IVoiceInteractionManagerService")
        val vis = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java).invoke(null, "voiceinteraction")
        val vims = Class.forName("com.android.internal.app.IVoiceInteractionManagerService\$Stub").getMethod("asInterface", IBinder::class.java).invoke(null, vis)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            HiddenApiBypass.invoke(iVimsClass, vims, "showSessionFromSession", null, bundle, flags,
                if (Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)) "hyperOS_home" else null) as Boolean
        } else {
            HiddenApiBypass.invoke(iVimsClass, vims, "showSessionFromSession", null, bundle, flags) as Boolean
        }
    }.onFailure { e ->
        val errMsg = "triggerCircleToSearch invoke omni failed: " + e.stackTraceToString()
        module?.log(Log.ERROR, LOG_TAG, errMsg) ?: Log.e(LOG_TAG, errMsg)
    }.getOrDefault(false)
    if (result) vibrateOnRequest(context, vibrate)
    return result
}

private fun vibrateOnRequest(context: Context?, vibrate: Boolean) {
    if (vibrate && context != null) {
        runCatching {
            (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).run {
                val attr = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setFlags(128)
                    .build()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK), attr)
                } else {
                    vibrate(longArrayOf(0, 1, 75, 76), -1, attr)
                }
            }
        }.onFailure { e ->
            val errMsg = "triggerCircleToSearch vibrate failed: " + e.stackTraceToString()
            module?.log(Log.ERROR, LOG_TAG, errMsg) ?: Log.e(LOG_TAG, errMsg)
        }
    }
}

/** Fresh arguments for every attempt; never reuse an old invocation timestamp. */
@SuppressLint("DiscouragedApi")
fun circleSearchArguments(entryPoint: Int): Bundle = Bundle().apply {
    if (BuildConfig.APP_NAME == "MiCTS") {
        putLong("invocation_time_ms", SystemClock.elapsedRealtime())
        putInt("omni.entry_point", entryPoint)
        putBoolean("micts_trigger", true)
        // Some OEM builds read their own contextual-search key before forwarding to Google.
        runCatching {
            val resources = Resources.getSystem()
            val id = resources.getIdentifier("config_defaultContextualSearchKey", "string", "android")
            if (id != 0) resources.getString(id).takeIf { it.isNotBlank() }
                ?.let { putInt(it, entryPoint) }
        }
    }
}

class MainActivity : ComponentActivity() {
    private var triggerJob: Job? = null
    private var started = false
    private var sidebarMode = false
    private var sidebarTicket: Long? = null
    private var requestedAt = 0L
    private var delayMs = 0L
    private var vibrate = false

    override fun onCreate(savedInstanceState: Bundle?) {
        val prefs = getSharedPreferences(CONFIG_NAME, MODE_PRIVATE)
        sidebarMode = BuildConfig.APP_NAME == "MiCTS" &&
            prefs.getBoolean(KEY_FRESH_SESSION, DEFAULT_CONFIG[KEY_FRESH_SESSION] as Boolean)
        // Do not let ColorOS turn this launcher into an empty floating sidebar window.
        // A NoDisplay activity MUST finish before onResume; it never calls showAssist().
        if (sidebarMode) setTheme(R.style.Theme_App_NoDisplay)
        super.onCreate(savedInstanceState)
        if (savedInstanceState?.getBoolean("request_started") == true) {
            finish()
            return
        }
        val key = if (intent.getBooleanExtra("from_tile", false)) KEY_TILE_DELAY else KEY_DEFAULT_DELAY
        delayMs = prefs.getLong(key, DEFAULT_CONFIG[key] as Long).coerceIn(0L, 2000L)
        vibrate = prefs.getBoolean(KEY_VIBRATE, DEFAULT_CONFIG[KEY_VIBRATE] as Boolean)
        started = true

        if (sidebarMode) {
            requestedAt = SystemClock.elapsedRealtime()
            sidebarTicket = SidebarTrigger.reserve()
            TriggerDiagnostics.begin(this, "sidebar: close launcher, then capture visible app")
            finish()
            // Submission happens only in onDestroy, after this activity releases its place.
            return
        }

        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        if (prefs.getBoolean(KEY_ASYNC_TRIGGER, DEFAULT_CONFIG[KEY_ASYNC_TRIGGER] as Boolean)) {
            triggerJob = lifecycleScope.launch { legacyTrigger() }
        } else {
            runBlocking { legacyTrigger() }
        }
    }

    private suspend fun legacyTrigger() {
        TriggerDiagnostics.begin(this, "legacy session")
        if (delayMs > 0) delay(delayMs)
        val accepted = triggerCircleToSearch(1, this, vibrate)
        TriggerDiagnostics.record(this, "legacy accepted=$accepted")
        if (!accepted) Toast.makeText(this, R.string.trigger_failed, Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("request_started", started)
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        super.onStop()
        triggerJob?.cancel()
        if (!isChangingConfigurations) finish()
    }

    override fun onDestroy() {
        val ticket = sidebarTicket
        sidebarTicket = null
        val submit = ticket != null && isFinishing && !isChangingConfigurations
        super.onDestroy()
        if (submit) {
            SidebarTrigger.submit(applicationContext, ticket!!, requestedAt, delayMs, vibrate)
        }
    }
}
