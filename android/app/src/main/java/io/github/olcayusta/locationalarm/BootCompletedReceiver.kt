package io.github.olcayusta.locationalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val prefs = PrefsRepository(context)
        if (!prefs.isTracking) return

        val target = prefs.loadTarget() ?: return
        GeofenceManager(context).addGeofence(target, onSuccess = {}, onFailure = {})
    }
}
