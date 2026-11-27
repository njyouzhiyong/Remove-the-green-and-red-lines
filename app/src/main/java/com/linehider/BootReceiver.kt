package com.linehider

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * 开机完成后，若用户开启了「开机自启」且遮罩总开关为开，启动 OverlayService。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = AppPreferences(context)
        if (prefs.bootAutoStart && prefs.overlayEnabled) {
            OverlayService.start(context)
        }
    }
}
