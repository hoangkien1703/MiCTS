package com.parallelc.micts.trigger

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.parallelc.micts.BuildConfig

/** One local report; no screen content, account data, identifiers, or network upload. */
object TriggerDiagnostics {
    private const val STORE = "trigger_diagnostics"
    private const val REPORT = "last_report"
    private const val GOOGLE = "com.google.android.googlequicksearchbox"

    fun begin(context: Context, mode: String) {
        val googleVersion = runCatching {
            context.packageManager.getPackageInfo(GOOGLE, 0).versionName
        }.getOrNull() ?: "not installed or unavailable"
        val assistant = runCatching {
            Settings.Secure.getString(context.contentResolver, "voice_interaction_service")
        }.getOrNull() ?: "unknown"
        val header = """
            MiCTS ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})
            Package: ${BuildConfig.APPLICATION_ID}
            Device: ${Build.MANUFACTURER} ${Build.MODEL}; Android ${Build.VERSION.RELEASE}
            Google: $googleVersion
            Voice service: $assistant
            Method: $mode
            Request accepted does not confirm that Circle to Search appeared.
        """.trimIndent()
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit().putString(REPORT, header).apply()
    }

    fun record(context: Context, event: String) {
        Log.i("MiCTSPreview", event)
        val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        prefs.edit().putString(REPORT, (prefs.getString(REPORT, "") + "\n" + event).takeLast(4000)).apply()
    }

    fun read(context: Context): String =
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getString(REPORT, null)
            ?: context.getString(com.parallelc.micts.R.string.no_diagnostics)
}
