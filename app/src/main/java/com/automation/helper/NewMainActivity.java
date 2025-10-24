package com.automation.helper;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.List;

public class NewMainActivity extends Activity {
    private static final int REQUEST_CODE_OVERLAY = 100;
    private static final int REQUEST_CODE_APP_PICKER = 101;
    private static final int REQUEST_CODE_RECORDER = 102;

    private RecyclerView recyclerView;
    private ScenarioAdapter adapter;
    private ScenarioManager scenarioManager;
    private Button btnEnableAccessibility;
    private Button btnEnableOverlay;
    private FloatingActionButton fabAdd;
    private TextView emptyText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_new);

        scenarioManager = ScenarioManager.getInstance(this);

        initViews();
        checkPermissions();
        loadScenarios();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recycler_scenarios);
        btnEnableAccessibility = findViewById(R.id.btn_enable_accessibility);
        btnEnableOverlay = findViewById(R.id.btn_enable_overlay);
        fabAdd = findViewById(R.id.fab_add);
        emptyText = findViewById(R.id.text_empty);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        btnEnableAccessibility.setOnClickListener(v -> openAccessibilitySettings());
        btnEnableOverlay.setOnClickListener(v -> requestOverlayPermission());
        fabAdd.setOnClickListener(v -> createNewScenario());
    }

    private void checkPermissions() {
        // Check overlay permission
        if (Settings.canDrawOverlays(this)) {
            btnEnableOverlay.setEnabled(false);
            btnEnableOverlay.setText("✓ 오버레이 권한 허용됨");
        }

        // Check accessibility service
        AutomationAccessibilityService service = AutomationAccessibilityService.getInstance();
        if (service != null) {
            btnEnableAccessibility.setEnabled(false);
            btnEnableAccessibility.setText("✓ 접근성 서비스 활성화됨");
        }
    }

    private void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
        Toast.makeText(this, "'Automation Helper' 서비스를 활성화하세요", Toast.LENGTH_LONG).show();
    }

    private void requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_CODE_OVERLAY);
        }
    }

    private void createNewScenario() {
        Intent intent = new Intent(this, AppPickerActivity.class);
        startActivityForResult(intent, REQUEST_CODE_APP_PICKER);
    }

    private void loadScenarios() {
        List<Scenario> scenarios = scenarioManager.getAllScenarios();

        if (scenarios.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyText.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
            adapter = new ScenarioAdapter(scenarios);
            recyclerView.setAdapter(adapter);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_OVERLAY) {
            checkPermissions();
        } else if (requestCode == REQUEST_CODE_APP_PICKER && resultCode == RESULT_OK) {
            String appName = data.getStringExtra(AppPickerActivity.EXTRA_APP_NAME);
            String packageName = data.getStringExtra(AppPickerActivity.EXTRA_PACKAGE_NAME);

            // Create new scenario
            Scenario scenario = new Scenario(appName, packageName, appName);

            // Go to recorder
            Intent recorderIntent = new Intent(this, ScenarioRecorderActivity.class);
            recorderIntent.putExtra(ScenarioRecorderActivity.EXTRA_SCENARIO, scenario);
            startActivityForResult(recorderIntent, REQUEST_CODE_RECORDER);

        } else if (requestCode == REQUEST_CODE_RECORDER && resultCode == RESULT_OK) {
            Scenario scenario = (Scenario) data.getSerializableExtra(ScenarioRecorderActivity.EXTRA_SCENARIO);
            scenarioManager.addScenario(scenario);
            loadScenarios();
            Toast.makeText(this, "시나리오가 저장되었습니다", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkPermissions();
        loadScenarios();
    }

    class ScenarioAdapter extends RecyclerView.Adapter<ScenarioAdapter.ViewHolder> {
        private List<Scenario> scenarios;

        ScenarioAdapter(List<Scenario> scenarios) {
            this.scenarios = scenarios;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_scenario, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Scenario scenario = scenarios.get(position);
            holder.bind(scenario);
        }

        @Override
        public int getItemCount() {
            return scenarios.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView nameText;
            TextView detailsText;
            Switch enabledSwitch;
            Button btnRun;
            Button btnSchedule;
            Button btnDelete;

            ViewHolder(View view) {
                super(view);
                nameText = view.findViewById(R.id.text_scenario_name);
                detailsText = view.findViewById(R.id.text_scenario_details);
                enabledSwitch = view.findViewById(R.id.switch_enabled);
                btnRun = view.findViewById(R.id.btn_run);
                btnSchedule = view.findViewById(R.id.btn_schedule);
                btnDelete = view.findViewById(R.id.btn_delete);
            }

            void bind(Scenario scenario) {
                nameText.setText(scenario.getName());
                detailsText.setText(String.format("%s\n터치: %d개",
                        scenario.getPackageName(),
                        scenario.getActions().size()));

                enabledSwitch.setChecked(scenario.isEnabled());
                enabledSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    scenario.setEnabled(isChecked);
                    scenarioManager.updateScenario(scenario);
                });

                btnRun.setOnClickListener(v -> runScenario(scenario));
                btnSchedule.setOnClickListener(v -> scheduleScenario(scenario));
                btnDelete.setOnClickListener(v -> deleteScenario(scenario));
            }
        }

        private void runScenario(Scenario scenario) {
            AutomationAccessibilityService service = AutomationAccessibilityService.getInstance();
            if (service == null) {
                Toast.makeText(NewMainActivity.this,
                        "접근성 서비스를 먼저 활성화하세요", Toast.LENGTH_LONG).show();
                return;
            }

            List<AutomationAccessibilityService.AutomationTask> tasks =
                    new java.util.ArrayList<>();

            // Launch app
            tasks.add(AutomationAccessibilityService.AutomationTask.launchApp(
                    scenario.getPackageName(), 2000));

            // Add all click actions
            for (Scenario.ClickAction action : scenario.getActions()) {
                tasks.add(AutomationAccessibilityService.AutomationTask.click(
                        action.getX(), action.getY(), action.getDelayAfter()));
            }

            service.executeTaskSequence(tasks);
            Toast.makeText(NewMainActivity.this, "시나리오 실행 중...", Toast.LENGTH_SHORT).show();
        }

        private void scheduleScenario(Scenario scenario) {
            Toast.makeText(NewMainActivity.this, "예약 기능은 곧 추가됩니다", Toast.LENGTH_SHORT).show();
        }

        private void deleteScenario(Scenario scenario) {
            scenarioManager.deleteScenario(scenario.getId());
            loadScenarios();
            Toast.makeText(NewMainActivity.this, "시나리오가 삭제되었습니다", Toast.LENGTH_SHORT).show();
        }
    }
}
