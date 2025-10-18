package com.example.autopilot

import android.content.Context
import com.example.autopilot.data.Prefs
import com.example.autopilot.service.AutoPilotService
import org.json.JSONArray
import org.json.JSONObject

object ScenarioBridge {
    private const val KEY_STEPS = "steps"

    // In-memory mirror for service; in production use a repository/binder
    private var cached: JSONArray = JSONArray()

    fun export(ctx: Context): String {
        val pkg = Prefs.getTargetPackage(ctx)
        val obj = JSONObject().apply { put(KEY_STEPS, cached) }
        Prefs.saveScenario(ctx, pkg, obj.toString())
        return obj.toString()
    }

    fun import(ctx: Context, json: String) {
        val obj = JSONObject(json)
        cached = obj.optJSONArray(KEY_STEPS) ?: JSONArray()
        val pkg = Prefs.getTargetPackage(ctx)
        Prefs.saveScenario(ctx, pkg, obj.toString())
    }

    fun addSleep(ctx: Context, ms: Long) {
        cached.put(JSONObject().apply { put("type", "sleep"); put("ms", ms) })
        export(ctx)
    }

    fun addWaitText(ctx: Context, text: String, timeoutMs: Long) {
        cached.put(JSONObject().apply {
            put("type", "wait_text"); put("text", text); put("timeoutMs", timeoutMs)
        })
        export(ctx)
    }

    fun addInputText(ctx: Context, text: String) {
        cached.put(JSONObject().apply { put("type", "input_text"); put("text", text) })
        export(ctx)
    }

    fun addSwipe(ctx: Context, x1: Int, y1: Int, x2: Int, y2: Int, dur: Long) {
        cached.put(JSONObject().apply {
            put("type","swipe");
            put("x1", x1); put("y1", y1); put("x2", x2); put("y2", y2); put("dur", dur)
        })
        export(ctx)
    }
}
