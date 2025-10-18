package com.example.autopilot.picker

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.activity.ComponentActivity
import com.example.autopilot.R

class AppPickerActivity : ComponentActivity() {
    data class AppItem(val label: String, val packageName: String) {
        override fun toString(): String = "$label\n$packageName"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val listView = ListView(this)
        setContentView(listView)

        val pm = packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val infos = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        val items = infos.map { ri ->
            val label = ri.loadLabel(pm).toString()
            val pkg = ri.activityInfo.packageName
            AppItem(label, pkg)
        }.sortedBy { it.label.lowercase() }

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val item = items[position]
            val data = Intent().putExtra("package", item.packageName)
            setResult(RESULT_OK, data)
            finish()
        }
    }
}
