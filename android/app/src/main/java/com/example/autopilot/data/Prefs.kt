package com.example.autopilot.data

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val FILE = "autopilot.prefs"
    private const val KEY_TARGET = "target_package"
    private const val KEY_AUTO_PLAY = "auto_play"
    private const val KEY_REPEAT_ENABLED = "repeat_enabled"
    private const val KEY_REPEAT_COUNT = "repeat_count" // 0 = infinite
    private const val KEY_REPEAT_DELAY = "repeat_delay_ms"

    private fun sp(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun setTargetPackage(ctx: Context, pkg: String) {
        sp(ctx).edit().putString(KEY_TARGET, pkg).apply()
    }
    fun getTargetPackage(ctx: Context): String = sp(ctx).getString(KEY_TARGET, "") ?: ""

    fun setAutoPlay(ctx: Context, v: Boolean) { sp(ctx).edit().putBoolean(KEY_AUTO_PLAY, v).apply() }
    fun isAutoPlay(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_AUTO_PLAY, false)

    fun setRepeatEnabled(ctx: Context, v: Boolean) { sp(ctx).edit().putBoolean(KEY_REPEAT_ENABLED, v).apply() }
    fun isRepeatEnabled(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_REPEAT_ENABLED, false)

    fun setRepeatCount(ctx: Context, c: Int) { sp(ctx).edit().putInt(KEY_REPEAT_COUNT, c).apply() }
    fun getRepeatCount(ctx: Context): Int = sp(ctx).getInt(KEY_REPEAT_COUNT, 0)

    fun setRepeatDelay(ctx: Context, ms: Long) { sp(ctx).edit().putLong(KEY_REPEAT_DELAY, ms).apply() }
    fun getRepeatDelay(ctx: Context): Long = sp(ctx).getLong(KEY_REPEAT_DELAY, 500)

    // Scenario JSON storage per package
    private fun scenarioKey(pkg: String) = "scenario:$pkg"

    fun saveScenario(ctx: Context, pkg: String, json: String) {
        sp(ctx).edit().putString(scenarioKey(pkg), json).apply()
    }
    fun loadScenario(ctx: Context, pkg: String): String? = sp(ctx).getString(scenarioKey(pkg), null)
}
