package com.automation.helper;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class ScheduledTaskReceiver extends BroadcastReceiver {
    private static final String TAG = "ScheduledTaskReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Scheduled task triggered");

        // Get task configuration from intent
        String packageName = intent.getStringExtra("package_name");
        int clickX = intent.getIntExtra("click_x", -1);
        int clickY = intent.getIntExtra("click_y", -1);

        // Execute the automation tasks
        AutomationAccessibilityService service = AutomationAccessibilityService.getInstance();
        if (service != null) {
            List<AutomationAccessibilityService.AutomationTask> tasks = new ArrayList<>();

            // Launch the app
            if (packageName != null && !packageName.isEmpty()) {
                tasks.add(AutomationAccessibilityService.AutomationTask.launchApp(packageName, 3000));
            }

            // Wait for app to load
            tasks.add(AutomationAccessibilityService.AutomationTask.delay(2000));

            // Perform click if coordinates are specified
            if (clickX >= 0 && clickY >= 0) {
                tasks.add(AutomationAccessibilityService.AutomationTask.click(clickX, clickY, 1000));
            }

            service.executeTaskSequence(tasks);
        } else {
            Log.e(TAG, "Accessibility service not available");
        }
    }

    // Schedule a task to run at specific time
    public static void scheduleTask(Context context, int hour, int minute,
                                   String packageName, int clickX, int clickY) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(context, ScheduledTaskReceiver.class);
        intent.putExtra("package_name", packageName);
        intent.putExtra("click_x", clickX);
        intent.putExtra("click_y", clickY);

        int requestCode = (hour * 60 + minute); // Unique code based on time
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // Set the time
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);

        // If the time has already passed today, schedule for tomorrow
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }

        // Schedule exact alarm
        if (alarmManager != null) {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    pendingIntent);

            Log.d(TAG, "Task scheduled for " + hour + ":" + minute);
        }
    }

    // Schedule repeating task
    public static void scheduleRepeatingTask(Context context, int hour, int minute,
                                            String packageName, int clickX, int clickY) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(context, ScheduledTaskReceiver.class);
        intent.putExtra("package_name", packageName);
        intent.putExtra("click_x", clickX);
        intent.putExtra("click_y", clickY);

        int requestCode = (hour * 60 + minute);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);

        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
        }

        if (alarmManager != null) {
            alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    AlarmManager.INTERVAL_DAY, // Repeat daily
                    pendingIntent);

            Log.d(TAG, "Repeating task scheduled for " + hour + ":" + minute);
        }
    }

    // Cancel scheduled task
    public static void cancelTask(Context context, int hour, int minute) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(context, ScheduledTaskReceiver.class);
        int requestCode = (hour * 60 + minute);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);

        if (pendingIntent != null && alarmManager != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
            Log.d(TAG, "Task cancelled for " + hour + ":" + minute);
        }
    }
}
