package com.example.autopilot.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.autopilot.R
import com.example.autopilot.data.Prefs
import com.example.autopilot.service.AutoPilotService

class PlayWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetIds: IntArray, appWidgetManager: AppWidgetManager) {
        for (appWidgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_play)
            val intent = Intent(context, AutoPilotService::class.java).apply { action = AutoPilotService.ACTION_PLAY }
            val pi = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.btnWidgetPlay, pi)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
