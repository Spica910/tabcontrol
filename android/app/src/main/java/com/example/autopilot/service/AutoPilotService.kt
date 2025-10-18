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
import android.graphics.Bitmap
import android.util.Base64
import com.example.autopilot.capture.ScreenCaptureService

class AutoPilotService : AccessibilityService() {

    sealed class Step {
        data class Tap(val ts: Long, val selector: String?, val rect: Rect?): Step()
        data class Sleep(val ms: Long): Step()
        data class WaitText(val text: String, val timeoutMs: Long): Step()
        data class InputText(val text: String): Step()
        data class Swipe(val x1: Int, val y1: Int, val x2: Int, val y2: Int, val dur: Long): Step()
        data class ScrollUntilText(val text: String, val max: Int, val down: Boolean): Step()
        data class FindImageLabel(val label: String): Step()
        data class Template(val imgBase64: String, val th: Float): Step()
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
                val nodeNow = findNodeBySelector(step.selector)
                if (nodeNow != null) {
                    nodeNow.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    hideTip()
                    true
                } else {
                    // 자동 스크롤 탐색 후 클릭 시 진행
                    val maxDown = 7; val maxUp = 3
                    tryScrollToFind(step.selector, maxDown, true) { foundDown ->
                        if (foundDown != null) {
                            foundDown.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            currentIndex += 1
                            if (running) handler.postDelayed({ stepNext() }, 250)
                        } else {
                            tryScrollToFind(step.selector, maxUp, false) { foundUp ->
                                if (foundUp != null) {
                                    foundUp.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                    currentIndex += 1
                                    if (running) handler.postDelayed({ stepNext() }, 250)
                                } else {
                                    val r = step.rect
                                    if (r != null) { showTip("여기를 탭", r); tapRect(r) }
                                    currentIndex += 1
                                    if (running) handler.postDelayed({ stepNext() }, 250)
                                }
                            }
                        }
                    }
                    false
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
                } else {
                    val maxDown = 7; val maxUp = 3
                    tryScrollToFindText(step.label, maxDown, true) { foundDown ->
                        if (foundDown != null) {
                            foundDown.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                            currentIndex += 1
                            if (running) handler.postDelayed({ stepNext() }, 250)
                        } else {
                            tryScrollToFindText(step.label, maxUp, false) { foundUp ->
                                if (foundUp != null) {
                                    foundUp.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                    currentIndex += 1
                                    if (running) handler.postDelayed({ stepNext() }, 250)
                                } else {
                                    currentIndex += 1
                                    if (running) handler.postDelayed({ stepNext() }, 250)
                                }
                            }
                        }
                    }
                    false
                }
            }
            is Step.Template -> {
                showTip("이미지 템플릿", null)
                val screenBmp = ScreenCaptureService.captureLatest() ?: return false
                val templ = decodeBase64Png(step.imgBase64) ?: return false
                val found = findTemplate(screenBmp, templ, step.th)
                if (found != null) { tapRect(found); true } else false
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
        const val ACTION_IMPORT = "com.example.autopilot.IMPORT"
        const val ACTION_SET_PACKAGE = "com.example.autopilot.SET_PACKAGE"
        const val ACTION_LAUNCH = "com.example.autopilot.LAUNCH"
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
                    "template" -> steps.add(Step.Template(o.optString("img"), o.optDouble("th", 0.9).toFloat()))
                }
            }
        } catch (_: Throwable) {}
    }

    private fun decodeBase64Png(b64: String): Bitmap? = try { val bytes = Base64.decode(b64, Base64.DEFAULT); android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size) } catch (_:Throwable){ null }
    private fun findTemplate(screen: Bitmap, templ: Bitmap, th: Float): Rect? {
        // Simple grayscale NCC search with downscale for speed
        val scale = 0.5f
        val screenScaled = Bitmap.createScaledBitmap(screen, (screen.width * scale).toInt(), (screen.height * scale).toInt(), true)
        val templScaled = Bitmap.createScaledBitmap(templ, (templ.width * scale).toInt().coerceAtLeast(1), (templ.height * scale).toInt().coerceAtLeast(1), true)

        val sw = screenScaled.width; val sh = screenScaled.height
        val tw = templScaled.width; val thh = templScaled.height
        if (tw <= 1 || thh <= 1 || sw < tw || sh < thh) return null

        val sPix = IntArray(sw * sh); screenScaled.getPixels(sPix, 0, sw, 0, 0, sw, sh)
        val tPix = IntArray(tw * thh); templScaled.getPixels(tPix, 0, tw, 0, 0, tw, thh)

        fun gray(c:Int)= ((c shr 16 and 0xFF)*299 + (c shr 8 and 0xFF)*587 + (c and 0xFF)*114)/1000.0
        val tMean = tPix.asSequence().map { gray(it) }.average()
        val tVar = tPix.asSequence().map { val v=gray(it)-tMean; v*v }.sum()
        if (tVar == 0.0) return null

        var best = -1.0
        var bestX = 0; var bestY = 0
        for (y in 0 .. sh - thh) {
            for (x in 0 .. sw - tw) {
                var sMean = 0.0
                var n = 0
                var idxS = y * sw + x
                // mean
                for (j in 0 until thh) {
                    for (i in 0 until tw) { sMean += gray(sPix[idxS + i]); n += 1 }
                    idxS += sw
                }
                sMean /= n
                var sVar = 0.0
                var num = 0.0
                idxS = y * sw + x
                var idxT = 0
                for (j in 0 until thh) {
                    for (i in 0 until tw) {
                        val gs = gray(sPix[idxS + i]) - sMean
                        val gt = gray(tPix[idxT]) - tMean
                        num += gs * gt
                        sVar += gs * gs
                        idxT += 1
                    }
                    idxS += sw
                }
                val denom = Math.sqrt(sVar * tVar)
                if (denom > 0) {
                    val score = num / denom
                    if (score > best) { best = score; bestX = x; bestY = y }
                }
            }
        }
        if (best < th) return null
        val rx = (bestX / scale).toInt()
        val ry = (bestY / scale).toInt()
        val rw = (tw / scale).toInt()
        val rh = (thh / scale).toInt()
        return Rect(rx, ry, rx + rw, ry + rh)
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

    private fun tryScrollToFind(selector: String?, max: Int, down: Boolean, onDone: (AccessibilityNodeInfo?) -> Unit){
        if (selector == null) { onDone(null); return }
        var remaining = max
        fun check(): AccessibilityNodeInfo? = findNodeBySelector(selector)
        fun tick(){
            val node = check()
            if (node != null || remaining <= 0) { onDone(node); return }
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
                    handler.postDelayed({ tick() }, 250)
                }
            }, null)
        }
        tick()
    }

    private fun tryScrollToFindText(text: String, max: Int, down: Boolean, onDone: (AccessibilityNodeInfo?) -> Unit){
        var remaining = max
        fun check(): AccessibilityNodeInfo? = rootInActiveWindow?.findAccessibilityNodeInfosByText(text)?.firstOrNull()
        fun tick(){
            val node = check()
            if (node != null || remaining <= 0) { onDone(node); return }
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
                    handler.postDelayed({ tick() }, 250)
                }
            }, null)
        }
        tick()
    }
}
