package com.automation.helper;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TimePicker;
import android.widget.Toast;

public class MainActivity extends Activity {
    private EditText editPackageName;
    private EditText editClickX;
    private EditText editClickY;
    private TimePicker timePicker;
    private Button btnEnableAccessibility;
    private Button btnScheduleTask;
    private Button btnTestNow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        setupListeners();
    }

    private void initViews() {
        editPackageName = findViewById(R.id.edit_package_name);
        editClickX = findViewById(R.id.edit_click_x);
        editClickY = findViewById(R.id.edit_click_y);
        timePicker = findViewById(R.id.time_picker);
        btnEnableAccessibility = findViewById(R.id.btn_enable_accessibility);
        btnScheduleTask = findViewById(R.id.btn_schedule_task);
        btnTestNow = findViewById(R.id.btn_test_now);

        timePicker.setIs24HourView(true);
    }

    private void setupListeners() {
        btnEnableAccessibility.setOnClickListener(v -> openAccessibilitySettings());
        btnScheduleTask.setOnClickListener(v -> scheduleTask());
        btnTestNow.setOnClickListener(v -> testTaskNow());
    }

    private void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
        Toast.makeText(this, "접근성 서비스에서 'Automation Helper'를 활성화하세요", Toast.LENGTH_LONG).show();
    }

    private void scheduleTask() {
        String packageName = editPackageName.getText().toString().trim();
        String xStr = editClickX.getText().toString().trim();
        String yStr = editClickY.getText().toString().trim();

        if (packageName.isEmpty()) {
            Toast.makeText(this, "패키지 이름을 입력하세요", Toast.LENGTH_SHORT).show();
            return;
        }

        int clickX = xStr.isEmpty() ? -1 : Integer.parseInt(xStr);
        int clickY = yStr.isEmpty() ? -1 : Integer.parseInt(yStr);

        int hour = timePicker.getHour();
        int minute = timePicker.getMinute();

        ScheduledTaskReceiver.scheduleRepeatingTask(
                this, hour, minute, packageName, clickX, clickY);

        Toast.makeText(this,
                String.format("작업이 매일 %02d:%02d에 예약되었습니다", hour, minute),
                Toast.LENGTH_LONG).show();
    }

    private void testTaskNow() {
        String packageName = editPackageName.getText().toString().trim();
        String xStr = editClickX.getText().toString().trim();
        String yStr = editClickY.getText().toString().trim();

        if (packageName.isEmpty()) {
            Toast.makeText(this, "패키지 이름을 입력하세요", Toast.LENGTH_SHORT).show();
            return;
        }

        AutomationAccessibilityService service = AutomationAccessibilityService.getInstance();
        if (service == null) {
            Toast.makeText(this, "접근성 서비스가 활성화되지 않았습니다", Toast.LENGTH_LONG).show();
            openAccessibilitySettings();
            return;
        }

        int clickX = xStr.isEmpty() ? -1 : Integer.parseInt(xStr);
        int clickY = yStr.isEmpty() ? -1 : Integer.parseInt(yStr);

        java.util.List<AutomationAccessibilityService.AutomationTask> tasks = new java.util.ArrayList<>();

        // Launch app
        tasks.add(AutomationAccessibilityService.AutomationTask.launchApp(packageName, 3000));

        // Wait for app to load
        tasks.add(AutomationAccessibilityService.AutomationTask.delay(2000));

        // Click if coordinates specified
        if (clickX >= 0 && clickY >= 0) {
            tasks.add(AutomationAccessibilityService.AutomationTask.click(clickX, clickY, 1000));
        }

        service.executeTaskSequence(tasks);
        Toast.makeText(this, "작업 실행 중...", Toast.LENGTH_SHORT).show();
    }
}
