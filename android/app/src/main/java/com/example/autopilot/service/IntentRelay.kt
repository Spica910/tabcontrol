package com.example.autopilot.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

class IntentRelay : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // no-op placeholder
        stopSelf()
        return START_NOT_STICKY
    }
}
