package com.automation.helper;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

public class ScenarioRecorderActivity extends Activity {
    public static final String EXTRA_SCENARIO = "scenario";

    private Scenario scenario;
    private List<Scenario.ClickAction> recordedActions;
    private boolean isRecording = false;
    private long appLaunchTime;

    private TextView titleText;
    private TextView statusText;
    private TextView actionsCountText;
    private Button startStopButton;
    private Button saveButton;
    private LinearLayout touchOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recorder);

        scenario = (Scenario) getIntent().getSerializableExtra(EXTRA_SCENARIO);
        recordedActions = new ArrayList<>();

        initViews();
        launchTargetApp();
    }

    private void initViews() {
        titleText = findViewById(R.id.text_title);
        statusText = findViewById(R.id.text_status);
        actionsCountText = findViewById(R.id.text_actions_count);
        startStopButton = findViewById(R.id.btn_start_stop);
        saveButton = findViewById(R.id.btn_save);
        touchOverlay = findViewById(R.id.touch_overlay);

        titleText.setText(scenario.getName());
        updateStatus();

        startStopButton.setOnClickListener(v -> toggleRecording());
        saveButton.setOnClickListener(v -> saveScenario());

        touchOverlay.setOnTouchListener((v, event) -> {
            if (isRecording && event.getAction() == MotionEvent.ACTION_DOWN) {
                recordTouch((int) event.getRawX(), (int) event.getRawY());
                return true;
            }
            return false;
        });
    }

    private void launchTargetApp() {
        AutomationAccessibilityService service = AutomationAccessibilityService.getInstance();
        if (service != null) {
            appLaunchTime = System.currentTimeMillis();
            service.launchApp(scenario.getPackageName());

            new Handler().postDelayed(() -> {
                statusText.setText("앱이 실행되었습니다. '녹화 시작'을 눌러주세요.");
            }, 2000);
        } else {
            Toast.makeText(this, "접근성 서비스가 활성화되지 않았습니다", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void toggleRecording() {
        isRecording = !isRecording;
        updateStatus();

        if (isRecording) {
            touchOverlay.setVisibility(View.VISIBLE);
            Toast.makeText(this, "녹화가 시작되었습니다. 화면을 터치하세요.", Toast.LENGTH_SHORT).show();
        } else {
            touchOverlay.setVisibility(View.GONE);
            Toast.makeText(this, "녹화가 중지되었습니다.", Toast.LENGTH_SHORT).show();
        }
    }

    private void recordTouch(int x, int y) {
        long currentTime = System.currentTimeMillis();
        int delayFromLaunch = (int) (currentTime - appLaunchTime);

        // Add delay before this click (based on time from app launch)
        recordedActions.add(new Scenario.ClickAction(x, y, delayFromLaunch));

        Toast.makeText(this,
            String.format("클릭 기록: (%d, %d)", x, y),
            Toast.LENGTH_SHORT).show();

        updateStatus();
    }

    private void updateStatus() {
        if (isRecording) {
            statusText.setText("🔴 녹화 중... 화면을 터치하세요");
            startStopButton.setText("녹화 중지");
        } else {
            statusText.setText("⏸ 녹화 대기 중");
            startStopButton.setText("녹화 시작");
        }

        actionsCountText.setText(String.format("기록된 터치: %d개", recordedActions.size()));
        saveButton.setEnabled(recordedActions.size() > 0);
    }

    private void saveScenario() {
        if (recordedActions.isEmpty()) {
            Toast.makeText(this, "기록된 터치가 없습니다", Toast.LENGTH_SHORT).show();
            return;
        }

        scenario.setActions(recordedActions);

        Intent result = new Intent();
        result.putExtra(EXTRA_SCENARIO, scenario);
        setResult(RESULT_OK, result);
        finish();
    }

    @Override
    public void onBackPressed() {
        if (recordedActions.size() > 0) {
            Toast.makeText(this, "저장하지 않고 종료합니다", Toast.LENGTH_SHORT).show();
        }
        super.onBackPressed();
    }
}
