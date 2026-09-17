package com.parallelc.micts.trigger

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import android.os.SystemClock
import android.service.voice.VoiceInteractionSession
import android.widget.Toast
import com.parallelc.micts.R
import com.parallelc.micts.config.AppConfig
import com.parallelc.micts.ui.activity.triggerCircleToSearch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One short, user-initiated handoff; no service, permanent keep-alive, or Activity reference. */
object SidebarTrigger {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val gate = SidebarRequestGate()
    private var pending: Job? = null

    // Called from onCreate on the main thread. A newer tap supersedes any pending request.
    fun reserve(): Long {
        pending?.cancel()
        return gate.reserve()
    }

    // Called from onDestroy, also on the main thread, with APPLICATION context only.
    fun submit(context: Context, ticket: Long, requestedAt: Long, delayMs: Long, vibrate: Boolean) {
        if (!gate.claim(ticket)) return
        pending = scope.launch {
            TriggerDiagnostics.record(context, "launcher destroyed; waiting for underlying app")
            val power = context.getSystemService(PowerManager::class.java)
            val keyguard = context.getSystemService(KeyguardManager::class.java)
            val eligible = {
                gate.canRun(ticket, SystemClock.elapsedRealtime() - requestedAt,
                    power.isInteractive, keyguard.isKeyguardLocked)
            }
            val trigger: suspend () -> TriggerResult = { TriggerRequest.run(
                initialDelayMs = delayMs.coerceIn(250L, 2000L),
                isEligible = eligible,
                wait = { delay(it) },
                primary = {
                    // Same source as Activity.showAssist, but no calling Activity token:
                    // VIMS selects visible activities AFTER our launcher has gone away.
                    val accepted = triggerCircleToSearch(1, context, vibrate,
                        VoiceInteractionSession.SHOW_SOURCE_APPLICATION)
                    TriggerDiagnostics.record(context, "visible-screen application-source accepted=$accepted")
                    accepted
                },
                fallback = {
                    val accepted = triggerCircleToSearch(1, context, vibrate)
                    TriggerDiagnostics.record(context, "visible-screen gesture-source accepted=$accepted")
                    accepted
                }
            ) }
            val prepareGoogle = context.getSharedPreferences(AppConfig.CONFIG_NAME, Context.MODE_PRIVATE)
                .getBoolean(AppConfig.KEY_PREPARE_GOOGLE,
                    AppConfig.DEFAULT_CONFIG[AppConfig.KEY_PREPARE_GOOGLE] as Boolean)
            val result = if (prepareGoogle) {
                ConnectionWarmup.run(GoogleSearchConnection(context), eligible, { delay(it) },
                    { TriggerDiagnostics.record(context, it) }, trigger)
            } else {
                TriggerDiagnostics.record(context, "Google preparation disabled")
                trigger()
            }
            when (result) {
                TriggerResult.REJECTED -> Toast.makeText(context,
                    R.string.preview_trigger_failed, Toast.LENGTH_LONG).show()
                TriggerResult.CANCELLED -> TriggerDiagnostics.record(context,
                    "cancelled: superseded, screen locked/off, or request expired")
                TriggerResult.ACCEPTED -> Unit
            }
        }
    }
}
