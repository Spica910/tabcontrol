package com.example.autopilot.tiles

import android.service.quicksettings.TileService
import android.content.Intent
import com.example.autopilot.data.Prefs
import com.example.autopilot.service.AutoPilotService

class QuickTileService : TileService() {
    override fun onClick() {
        super.onClick()
        val pkg = Prefs.getTargetPackage(this)
        if (pkg.isNotEmpty()) {
            val launch = packageManager.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                startActivityAndCollapse(launch)
                startService(Intent(this, AutoPilotService::class.java).apply { action = AutoPilotService.ACTION_PLAY })
            }
        }
    }
}
