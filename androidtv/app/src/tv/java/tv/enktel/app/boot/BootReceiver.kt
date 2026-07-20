package tv.enktel.app.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import tv.enktel.app.EnktelApp
import tv.enktel.app.MainActivity

/**
 * TV-only auto-start: when the device finishes booting (or the app is
 * re-installed / upgraded, so the receiver can re-register), we honour the
 * user's "Start on boot" preference and launch MainActivity into the leanback
 * launcher so EnkTel is what appears on the TV. The setting defaults to OFF —
 * we never grab the screen without the user opting in.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON" &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return

        val app = context.applicationContext as? EnktelApp ?: return
        // goAsync gives us up to ~10s to check the preference off the main thread.
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (app.graph.settings.startOnBootNow()) {
                    val launch = Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    context.startActivity(launch)
                }
            } catch (_: Exception) {
                // Nothing we can usefully do here — the OS will surface any startActivity failure.
            } finally {
                pending.finish()
            }
        }
    }
}
