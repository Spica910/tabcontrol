package com.example.autopilot

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.CheckBox
import android.widget.TextView
import android.app.Activity
import com.example.autopilot.picker.AppPickerActivity
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import android.content.ComponentName
import android.content.Context
import com.example.autopilot.service.AutoPilotService
import android.content.ClipData
import android.content.ClipboardManager

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
        val btnPickApp = findViewById<Button>(R.id.btnPickApp)
        val btnRecord = findViewById<Button>(R.id.btnRecord)
        val btnPlay = findViewById<Button>(R.id.btnPlay)
        val btnStep = findViewById<Button>(R.id.btnStep)
        val btnClear = findViewById<Button>(R.id.btnClear)
        val btnExport = findViewById<Button>(R.id.btnExport)
        val btnImport = findViewById<Button>(R.id.btnImport)
        val chkAutoPlay = findViewById<CheckBox>(R.id.chkAutoPlay)
        val chkRepeat = findViewById<CheckBox>(R.id.chkRepeat)
        val edtRepeatCount = findViewById<EditText>(R.id.edtRepeatCount)
        val edtRepeatDelay = findViewById<EditText>(R.id.edtRepeatDelay)
        val edtSleepMs = findViewById<EditText>(R.id.edtSleepMs)
        val btnAddSleep = findViewById<Button>(R.id.btnAddSleep)
        val edtWaitText = findViewById<EditText>(R.id.edtWaitText)
        val edtWaitTimeout = findViewById<EditText>(R.id.edtWaitTimeout)
        val btnAddWait = findViewById<Button>(R.id.btnAddWait)

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

        btnPickApp.setOnClickListener {
            startActivityForResult(Intent(this, AppPickerActivity::class.java), 1001)
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

        btnExport.setOnClickListener {
            val cm = getSystemService(ClipboardManager::class.java)
            val json = ScenarioBridge.export(this)
            cm.setPrimaryClip(ClipData.newPlainText("scenario", json))
            txtStatus.text = "상태: 시나리오 클립보드로 내보냄"
        }
        btnImport.setOnClickListener {
            val cm = getSystemService(ClipboardManager::class.java)
            val text = cm.primaryClip?.getItemAt(0)?.text?.toString()
            if (!text.isNullOrEmpty()) {
                ScenarioBridge.import(this, text)
                txtStatus.text = "상태: 시나리오 가져오기 완료"
            }
        }

        btnAddSleep.setOnClickListener {
            val ms = edtSleepMs.text.toString().toLongOrNull() ?: return@setOnClickListener
            ScenarioBridge.addSleep(this, ms)
            txtStatus.text = "상태: 대기 스텝 추가"
        }
        btnAddWait.setOnClickListener {
            val t = edtWaitText.text.toString()
            val timeout = edtWaitTimeout.text.toString().toLongOrNull() ?: 5000L
            if (t.isNotEmpty()) {
                ScenarioBridge.addWaitText(this, t, timeout)
                txtStatus.text = "상태: 텍스트 대기 스텝 추가"
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001 && resultCode == Activity.RESULT_OK) {
            val pkg = data?.getStringExtra("package") ?: return
            data.Prefs.setTargetPackage(this, pkg)
            findViewById<EditText>(R.id.edtPackage).setText(pkg)
        }
    }
}
