package com.example.autopilot.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import androidx.activity.ComponentActivity
import com.example.autopilot.R
import com.example.autopilot.data.Prefs
import org.json.JSONArray
import org.json.JSONObject

class ScenarioEditorActivity: ComponentActivity() {
    private lateinit var list: ListView
    private lateinit var adapter: ArrayAdapter<String>
    private var steps = JSONArray()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.scenario_editor)
        list = findViewById(R.id.list)
        val btnUp = findViewById<Button>(R.id.btnUp)
        val btnDown = findViewById<Button>(R.id.btnDown)
        val btnDelete = findViewById<Button>(R.id.btnDelete)

        load()
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_activated_1, render())
        list.choiceMode = ListView.CHOICE_MODE_SINGLE
        list.adapter = adapter

        btnUp.setOnClickListener { move(-1) }
        btnDown.setOnClickListener { move(1) }
        btnDelete.setOnClickListener { delete() }
    }

    private fun load(){
        val raw = Prefs.loadScenario(this, Prefs.getTargetPackage(this))
        steps = if (raw.isNullOrEmpty()) JSONArray() else JSONObject(raw).optJSONArray("steps") ?: JSONArray()
    }

    private fun save(){
        val obj = JSONObject().apply { put("steps", steps) }
        Prefs.saveScenario(this, Prefs.getTargetPackage(this), obj.toString())
    }

    private fun render(): MutableList<String> {
        val items = mutableListOf<String>()
        for (i in 0 until steps.length()){
            val s = steps.getJSONObject(i)
            val t = s.optString("type")
            val summary = when(t){
                "tap" -> "탭: ${s.optString("selector","(좌표)")}"
                "sleep" -> "대기: ${s.optLong("ms")}ms"
                "wait_text" -> "텍스트 대기: ${s.optString("text")}" 
                "input_text" -> "입력: ${s.optString("text")}"
                "swipe" -> "스와이프: ${s.optInt("x1")},${s.optInt("y1")}→${s.optInt("x2")},${s.optInt("y2")} (${s.optLong("dur")}ms)"
                else -> t
            }
            items.add("${i+1}. $summary")
        }
        return items
    }

    private fun swap(i: Int, j: Int){
        if (i !in 0 until steps.length() || j !in 0 until steps.length()) return
        val a = steps.getJSONObject(i)
        val b = steps.getJSONObject(j)
        steps.put(i, b)
        steps.put(j, a)
        save()
        adapter.clear(); adapter.addAll(render()); adapter.notifyDataSetChanged()
        list.setItemChecked(j, true)
    }

    private fun move(delta: Int){
        val pos = list.checkedItemPosition
        if (pos == ListView.INVALID_POSITION) return
        val np = pos + delta
        if (np in 0 until steps.length()) swap(pos, np)
    }

    private fun delete(){
        val pos = list.checkedItemPosition
        if (pos == ListView.INVALID_POSITION) return
        val newArr = JSONArray()
        for (i in 0 until steps.length()) if (i != pos) newArr.put(steps.get(i))
        steps = newArr
        save()
        adapter.clear(); adapter.addAll(render()); adapter.notifyDataSetChanged()
        list.clearChoices()
    }
}
