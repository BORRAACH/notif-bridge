package com.example.myapplication

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

/** Recebe todas as notificacoes do sistema e as encaminha ao receptor no Arch. */
class NotifyListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        // Ignora notificacoes persistentes (player, VPN...) e resumos de grupo.
        if (sbn.isOngoing) return
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val config = BridgeConfig(this)
        if (!config.isConfigured) return

        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isEmpty() && text.isEmpty()) return

        Sender.send(config, appLabel(sbn.packageName), title, text) { error ->
            if (error != null) Log.w(TAG, "Falha ao enviar notificacao: $error")
        }
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

    private companion object {
        const val TAG = "NotifBridge"
    }
}
