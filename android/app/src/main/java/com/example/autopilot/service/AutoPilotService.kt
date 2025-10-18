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
import org.json.JSONArray
import org.json.JSONObject
import android.view.WindowManager
import android.view.Gravity
import android.widget.TextView
import android.graphics.Color
import android.view.View

class AutoPilotService : AccessibilityService() {

    sealed class Step {
        data class Tap(val ts: Long, val selector: String?, val rect: Rect?): Step()
        data class Sleep(val ms: Long): Step()
        data class WaitText(val text: String, val timeoutMs: Long): Step()
        data class InputText(val text: String): Step()
        data class Swipe(val x1: Int, val y1: Int, val x2: Int, val y2: Int, val dur: Long): Step()
        data class ScrollUntilText(val text: String, val max: Int, val down: Boolean): Step()
        data class FindImageLabel(val label: String): Step()
    }

    private val steps = mutableListOf<Step>()
    private var recording = false
    private var running = false
    private var currentIndex = 0
    private val handler = Handler(Looper.getMainLooper())
    private var repeatRemaining: Int = 0 // 0 = infinite when repeat enabled
    private var wm: WindowManager? = null
    private var tipView: TextView? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        loadScenarioFromPrefs()
        wm = getSystemService(WindowManager::class.java)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!recording) return
        val src = event.source ?: return
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val r = Rect()
            src.getBoundsInScreen(r)
            val selector = buildSelector(src)
            steps.add(Step.Tap(System.currentTimeMillis(), selector, Rect(r)))
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

    private fun showTip(text: String, rect: Rect?) {
        val wm = wm ?: return
        if (tipView == null) {
            tipView = TextView(this).apply {
                setBackgroundColor(0x88000000.toInt())
                setTextColor(Color.WHITE)
                setPadding(16, 8, 16, 8)
            }
            val p = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                android.graphics.PixelFormat.TRANSLUCENT
            )
            p.gravity = Gravity.TOP or Gravity.START
            wm.addView(tipView, p)
        }
        tipView?.text = text
        val lp = tipView?.layoutParams as? WindowManager.LayoutParams ?: return
        if (rect != null) {
            lp.x = rect.centerX()
            lp.y = rect.top - 80
        } else { lp.x = 20; lp.y = 20 }
        wm.updateViewLayout(tipView, lp)
    }

    private fun hideTip(){
        val wm = wm; val tv = tipView
        if (wm != null && tv != null) {
            wm.removeView(tv)
            tipView = null
        }
    }

    private fun playStep(step: Step): Boolean {
        return when(step) {
            is Step.Tap -> {
                val node = findNodeBySelector(step.selector)
                if (node != null) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    hideTip()
                    true
                } else {
                    val r = step.rect
                    if (r != null) { showTip("여기를 탭", r); tapRect(r); true } else false
                }
            }
            is Step.Sleep -> {
                showTip("대기 ${step.ms}ms", null)
                handler.postDelayed({ stepNext() }, step.ms)
                false
            }
            is Step.WaitText -> {
                showTip("텍스트 대기: ${step.text}", null)
                waitForText(step.text, step.timeoutMs) { hideTip(); stepNext() }
                false
            }
            is Step.InputText -> {
                showTip("텍스트 입력", null)
                val root = rootInActiveWindow
                val focus = root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                if (focus != null) {
                    val args = android.os.Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, step.text) }
                    focus.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                }
                true
            }
            is Step.Swipe -> {
                val path = Path().apply { moveTo(step.x1.toFloat(), step.y1.toFloat()); lineTo(step.x2.toFloat(), step.y2.toFloat()) }
                val stroke = GestureDescription.StrokeDescription(path, 0, step.dur)
                val gesture = GestureDescription.Builder().addStroke(stroke).build()
                dispatchGesture(gesture, null, null)
                true
            }
            is Step.ScrollUntilText -> {
                showTip("스크롤로 찾기: ${step.text}", null)
                scrollUntilText(step.text, step.max, step.down) { stepNext() }
                false
            }
            is Step.FindImageLabel -> {
                showTip("이미지 라벨 찾기: ${step.label}", null)
                val node = rootInActiveWindow?.findAccessibilityNodeInfosByText(step.label)?.firstOrNull()
                if (node != null) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK); true
                } else { false }
            }
        }
    }

    private fun waitForText(text: String, timeoutMs: Long, onDone: () -> Unit){
        val start = System.currentTimeMillis()
        fun tick(){
            val root = rootInActiveWindow
            val found = root?.findAccessibilityNodeInfosByText(text)?.isNotEmpty() == true
            if (found) { onDone(); return }
            if (System.currentTimeMillis() - start > timeoutMs) { onDone(); return }
            handler.postDelayed({ tick() }, 200)
        }
        tick()
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
        val advanced = playStep(steps[currentIndex])
        if (advanced) currentIndex += 1
        if (running && advanced) handler.postDelayed({ stepNext() }, 250)
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
            ACTION_CLEAR -> { steps.clear(); saveScenarioToPrefs() }
        }
        return START_STICKY
    }

    companion object {
        const val ACTION_TOGGLE_RECORD = "com.example.autopilot.TOGGLE_RECORD"
        const val ACTION_PLAY = "com.example.autopilot.PLAY"
        const val ACTION_STEP = "com.example.autopilot.STEP"
        const val ACTION_CLEAR = "com.example.autopilot.CLEAR"
    }

    private fun saveScenarioToPrefs(){
        val pkg = Prefs.getTargetPackage(this)
        val arr = JSONArray()
        steps.forEach { s ->
            when(s){
                is Step.Tap -> arr.put(JSONObject().apply {
                    put("type","tap"); put("ts", s.ts); put("selector", s.selector)
                    s.rect?.let { r -> put("rect", JSONObject().apply { put("x", r.left); put("y", r.top); put("w", r.width()); put("h", r.height()) }) }
                })
                is Step.Sleep -> arr.put(JSONObject().apply { put("type","sleep"); put("ms", s.ms) })
                is Step.WaitText -> arr.put(JSONObject().apply { put("type","wait_text"); put("text", s.text); put("timeoutMs", s.timeoutMs) })
            }
        }
        val root = JSONObject().apply { put("steps", arr) }
        Prefs.saveScenario(this, pkg, root.toString())
    }

    private fun loadScenarioFromPrefs(){
        val pkg = Prefs.getTargetPackage(this)
        val raw = Prefs.loadScenario(this, pkg)
        steps.clear()
        if (raw.isNullOrEmpty()) return
        try {
            val obj = JSONObject(raw)
            val arr = obj.optJSONArray("steps") ?: JSONArray()
            for (i in 0 until arr.length()){
                val o = arr.getJSONObject(i)
                when(o.optString("type")){
                    "tap" -> {
                        val rectObj = o.optJSONObject("rect")
                        val rect = if (rectObj != null) android.graphics.Rect(
                            rectObj.optInt("x"), rectObj.optInt("y"), rectObj.optInt("x") + rectObj.optInt("w"), rectObj.optInt("y") + rectObj.optInt("h")
                        ) else null
                        steps.add(Step.Tap(o.optLong("ts"), o.optString("selector", null), rect))
                    }
                    "sleep" -> steps.add(Step.Sleep(o.optLong("ms")))
                    "wait_text" -> steps.add(Step.WaitText(o.optString("text"), o.optLong("timeoutMs", 5000)))
                    "input_text" -> steps.add(Step.InputText(o.optString("text")))
                    "swipe" -> steps.add(Step.Swipe(o.optInt("x1"), o.optInt("y1"), o.optInt("x2"), o.optInt("y2"), o.optLong("dur", 300)))
                    "scroll_until_text" -> steps.add(Step.ScrollUntilText(o.optString("text"), o.optInt("max", 5), o.optBoolean("down", true)))
                    "find_image_label" -> steps.add(Step.FindImageLabel(o.optString("label")))
                }
            }
        } catch (_: Throwable) {}
    }

    private fun scrollUntilText(text: String, max: Int, down: Boolean, onDone: () -> Unit){
        var remaining = max
        fun tick(){
            val root = rootInActiveWindow
            val found = root?.findAccessibilityNodeInfosByText(text)?.isNotEmpty() == true
            if (found || remaining <= 0){ onDone(); return }
            remaining -= 1
            val displayH = resources.displayMetrics.heightPixels
            val x = resources.displayMetrics.widthPixels / 2f
            val y1 = if (down) displayH * 0.75f else displayH * 0.25f
            val y2 = if (down) displayH * 0.25f else displayH * 0.75f
            val path = Path().apply { moveTo(x, y1); lineTo(x, y2) }
            val stroke = GestureDescription.StrokeDescription(path, 0, 300)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            dispatchGesture(gesture, object: GestureResultCallback(){
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    handler.postDelayed({ tick() }, 300)
                }
            }, null)
        }
        tick()
    }
}
