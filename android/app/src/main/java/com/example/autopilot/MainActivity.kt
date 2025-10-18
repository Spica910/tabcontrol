package com.example.autopilot

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.CheckBox
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import android.content.ComponentName
import android.content.Context
import com.example.autopilot.service.AutoPilotService

class MainViewModel: ViewModel() {
    var targetPackage: String = ""
}

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val txtStatus = findViewById<TextView>(R.id.txtStatus)
        val edtPackage = findViewById<EditText>(R.id.edtPackage)
        val btnOpenAccessibility = findViewById<Button>(R.id.btnOpenAccessibility)
        val btnLaunch = findViewById<Button>(R.id.btnLaunch)
        val btnRecord = findViewById<Button>(R.id.btnRecord)
        val btnPlay = findViewById<Button>(R.id.btnPlay)
        val btnStep = findViewById<Button>(R.id.btnStep)
        val btnClear = findViewById<Button>(R.id.btnClear)
        val chkAutoPlay = findViewById<CheckBox>(R.id.chkAutoPlay)
        val chkRepeat = findViewById<CheckBox>(R.id.chkRepeat)
        val edtRepeatCount = findViewById<EditText>(R.id.edtRepeatCount)
        val edtRepeatDelay = findViewById<EditText>(R.id.edtRepeatDelay)

        edtPackage.setText(vm.targetPackage)

        // Load prefs
        edtPackage.setText(data.Prefs.getTargetPackage(this))
        chkAutoPlay.isChecked = data.Prefs.isAutoPlay(this)
        chkRepeat.isChecked = data.Prefs.isRepeatEnabled(this)
        edtRepeatCount.setText(data.Prefs.getRepeatCount(this).toString())
        edtRepeatDelay.setText(data.Prefs.getRepeatDelay(this).toString())

        btnOpenAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnLaunch.setOnClickListener {
            vm.targetPackage = edtPackage.text.toString().trim()
            data.Prefs.setTargetPackage(this, vm.targetPackage)
            data.Prefs.setAutoPlay(this, chkAutoPlay.isChecked)
            data.Prefs.setRepeatEnabled(this, chkRepeat.isChecked)
            data.Prefs.setRepeatCount(this, edtRepeatCount.text.toString().toIntOrNull() ?: 0)
            data.Prefs.setRepeatDelay(this, edtRepeatDelay.text.toString().toLongOrNull() ?: 500)
            val launchIntent = packageManager.getLaunchIntentForPackage(vm.targetPackage)
            if (launchIntent != null) {
                startActivity(launchIntent)
                txtStatus.text = "상태: 대상 앱 실행됨"
                if (chkAutoPlay.isChecked) {
                    sendToService(service.AutoPilotService.ACTION_PLAY)
                }
            } else {
                txtStatus.text = "상태: 패키지 찾을 수 없음"
            }
        }

        fun sendToService(action: String){
            val intent = Intent(this, AutoPilotService::class.java).apply { this.action = action }
            startService(intent)
        }

        btnRecord.setOnClickListener { sendToService(AutoPilotService.ACTION_TOGGLE_RECORD) }
        btnPlay.setOnClickListener { sendToService(AutoPilotService.ACTION_PLAY) }
        btnStep.setOnClickListener { sendToService(AutoPilotService.ACTION_STEP) }
        btnClear.setOnClickListener { sendToService(AutoPilotService.ACTION_CLEAR) }
    }
}
