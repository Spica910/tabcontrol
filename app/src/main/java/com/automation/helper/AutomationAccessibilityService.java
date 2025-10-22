package com.automation.helper;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.media.Image;
import android.media.ImageReader;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;

import java.util.ArrayList;
import java.util.List;

public class AutomationAccessibilityService extends AccessibilityService {
    private static final String TAG = "AutomationService";
    private static AutomationAccessibilityService instance;
    private Handler handler = new Handler(Looper.getMainLooper());
    private List<AutomationTask> taskQueue = new ArrayList<>();

    public static AutomationAccessibilityService getInstance() {
        return instance;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Handle accessibility events if needed
        Log.d(TAG, "Accessibility event: " + event.toString());
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "Service interrupted");
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.d(TAG, "Accessibility service connected");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        Log.d(TAG, "Service destroyed");
    }

    // Launch an app by package name
    public void launchApp(String packageName) {
        try {
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(launchIntent);
                Log.d(TAG, "Launched app: " + packageName);
            } else {
                Log.e(TAG, "App not found: " + packageName);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error launching app: " + e.getMessage());
        }
    }

    // Click at specific coordinates
    public void clickAt(int x, int y) {
        clickAt(x, y, 0);
    }

    public void clickAt(int x, int y, long delayMs) {
        handler.postDelayed(() -> {
            Path clickPath = new Path();
            clickPath.moveTo(x, y);

            GestureDescription.StrokeDescription clickStroke =
                    new GestureDescription.StrokeDescription(clickPath, 0, 100);

            GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
            gestureBuilder.addStroke(clickStroke);

            dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
                @Override
                public void onCompleted(GestureDescription gestureDescription) {
                    super.onCompleted(gestureDescription);
                    Log.d(TAG, "Click completed at (" + x + ", " + y + ")");
                }

                @Override
                public void onCancelled(GestureDescription gestureDescription) {
                    super.onCancelled(gestureDescription);
                    Log.e(TAG, "Click cancelled");
                }
            }, null);
        }, delayMs);
    }

    // Execute a sequence of automation tasks
    public void executeTaskSequence(List<AutomationTask> tasks) {
        taskQueue.clear();
        taskQueue.addAll(tasks);
        executeNextTask();
    }

    private void executeNextTask() {
        if (taskQueue.isEmpty()) {
            Log.d(TAG, "All tasks completed");
            return;
        }

        AutomationTask task = taskQueue.remove(0);
        executeTask(task);
    }

    private void executeTask(AutomationTask task) {
        switch (task.type) {
            case LAUNCH_APP:
                launchApp(task.packageName);
                handler.postDelayed(this::executeNextTask, task.delayAfter);
                break;

            case CLICK:
                clickAt(task.x, task.y);
                handler.postDelayed(this::executeNextTask, task.delayAfter);
                break;

            case WAIT:
                handler.postDelayed(this::executeNextTask, task.delayAfter);
                break;

            default:
                executeNextTask();
                break;
        }
    }

    // Task definition
    public static class AutomationTask {
        public enum TaskType {
            LAUNCH_APP,
            CLICK,
            WAIT
        }

        TaskType type;
        String packageName;
        int x, y;
        long delayAfter; // milliseconds

        public static AutomationTask launchApp(String packageName, long delayAfter) {
            AutomationTask task = new AutomationTask();
            task.type = TaskType.LAUNCH_APP;
            task.packageName = packageName;
            task.delayAfter = delayAfter;
            return task;
        }

        public static AutomationTask click(int x, int y, long delayAfter) {
            AutomationTask task = new AutomationTask();
            task.type = TaskType.CLICK;
            task.x = x;
            task.y = y;
            task.delayAfter = delayAfter;
            return task;
        }

        public static AutomationTask delay(long delayMs) {
            AutomationTask task = new AutomationTask();
            task.type = TaskType.WAIT;
            task.delayAfter = delayMs;
            return task;
        }
    }
}
