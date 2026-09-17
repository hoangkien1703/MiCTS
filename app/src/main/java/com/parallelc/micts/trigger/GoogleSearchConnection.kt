package com.parallelc.micts.trigger

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.CompletableDeferred

/** Only binds Google's exported public search service. No UI, audio, or Binder commands. */
class GoogleSearchConnection(context: Context) : AssistantConnection {
    private val context = context.applicationContext
    private val connected = CompletableDeferred<Boolean>()
    private var attempted = false
    private var closed = false
    private val callback = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            connected.complete(!closed && service.isBinderAlive)
        }
        override fun onServiceDisconnected(name: ComponentName) {
            connected.complete(false)
        }
        override fun onNullBinding(name: ComponentName) {
            connected.complete(false)
        }
        override fun onBindingDied(name: ComponentName) {
            connected.complete(false)
        }
    }

    override fun bind(): Boolean {
        check(!attempted && !closed)
        val intent = Intent("com.google.android.apps.gsa.publicsearch.IPublicSearchService")
            .setPackage(GOOGLE)
        return try {
            val info = context.packageManager.resolveService(intent, 0)?.serviceInfo
            if (info == null || info.packageName != GOOGLE || !info.exported || !info.enabled) {
                record("Google public search service unavailable")
                return false
            }
            if (!info.permission.isNullOrEmpty() &&
                context.checkSelfPermission(info.permission) != PackageManager.PERMISSION_GRANTED) {
                record("Google public search service requires a permission this app does not have")
                return false
            }
            intent.component = ComponentName(info.packageName, info.name)
            attempted = true
            context.bindService(intent, callback, Context.BIND_AUTO_CREATE).also {
                record("Google preparation bind accepted=$it")
            }
        } catch (e: SecurityException) {
            record("Google preparation denied: ${e.javaClass.simpleName}")
            false
        } catch (e: RuntimeException) {
            record("Google preparation failed: ${e.javaClass.simpleName}")
            false
        }
    }

    override suspend fun awaitConnected(): Boolean = connected.await()

    override fun close() {
        if (closed) return
        closed = true
        connected.complete(false)
        // Android requires cleanup even if bindService returned false or onNullBinding fired.
        if (attempted) {
            runCatching { context.unbindService(callback) }
            record("Google preparation connection released")
        }
    }

    private fun record(event: String) = TriggerDiagnostics.record(context, event)

    private companion object {
        const val GOOGLE = "com.google.android.googlequicksearchbox"
    }
}
