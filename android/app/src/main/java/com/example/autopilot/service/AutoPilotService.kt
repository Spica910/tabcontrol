package com.example.autopilot.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.content.Intent
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import com.example.autopilot.data.Prefs

class AutoPilotService : AccessibilityService() {

    data class Step(
        val ts: Long,
        val selector: String?,
        val rect: Rect?
    )

    private val steps = mutableListOf<Step>()
    private var recording = false
    private var running = false
    private var currentIndex = 0
    private val handler = Handler(Looper.getMainLooper())
    private var repeatRemaining: Int = 0 // 0 = infinite when repeat enabled

    override fun onServiceConnected() {
        super.onServiceConnected()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!recording) return
        val src = event.source ?: return
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val r = Rect()
            src.getBoundsInScreen(r)
            val selector = buildSelector(src)
            steps.add(Step(System.currentTimeMillis(), selector, Rect(r)))
        }
    }

    override fun onInterrupt() {}

    private fun buildSelector(node: AccessibilityNodeInfo): String? {
        val id = node.viewIdResourceName
        val text = node.text?.toString()
        return when {
            !id.isNullOrEmpty() -> "id:$id"
            !text.isNullOrEmpty() -> "text:$text"
            else -> null
        }
    }

    private fun findNodeBySelector(selector: String?): AccessibilityNodeInfo? {
        if (selector == null) return null
        val root = rootInActiveWindow ?: return null
        return when {
            selector.startsWith("id:") -> {
                val q = selector.removePrefix("id:")
                root.findAccessibilityNodeInfosByViewId(q).firstOrNull()
            }
            selector.startsWith("text:") -> {
                val q = selector.removePrefix("text:")
                root.findAccessibilityNodeInfosByText(q).firstOrNull()
            }
            else -> null
        }
    }

    private fun tapRect(rect: Rect) {
        val path = Path().apply { moveTo(rect.centerX().toFloat(), rect.centerY().toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    private fun playStep(step: Step): Boolean {
        val node = findNodeBySelector(step.selector)
        if (node != null) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return true
        }
        val r = step.rect
        if (r != null) {
            tapRect(r)
            return true
        }
        return false
    }

    private fun stepNext() {
        if (currentIndex >= steps.size) {
            // Scenario finished
            val repeatEnabled = Prefs.isRepeatEnabled(this)
            if (repeatEnabled) {
                val countPref = Prefs.getRepeatCount(this)
                val delayMs = Prefs.getRepeatDelay(this)
                if (countPref == 0 || repeatRemaining > 1) {
                    if (countPref > 0) repeatRemaining -= 1
                    currentIndex = 0
                    handler.postDelayed({ stepNext() }, delayMs)
                    return
                }
            }
            running = false
            return
        }
        playStep(steps[currentIndex])
        currentIndex += 1
        if (running) handler.postDelayed({ stepNext() }, 250)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_RECORD -> recording = !recording
            ACTION_PLAY -> {
                running = true; currentIndex = 0
                repeatRemaining = Prefs.getRepeatCount(this)
                stepNext()
            }
            ACTION_STEP -> { if (!running) { running = true; stepNext(); running = false } }
            ACTION_CLEAR -> { steps.clear() }
        }
        return START_STICKY
    }

    companion object {
        const val ACTION_TOGGLE_RECORD = "com.example.autopilot.TOGGLE_RECORD"
        const val ACTION_PLAY = "com.example.autopilot.PLAY"
        const val ACTION_STEP = "com.example.autopilot.STEP"
        const val ACTION_CLEAR = "com.example.autopilot.CLEAR"
    }
}
