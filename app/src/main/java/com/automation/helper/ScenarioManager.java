package com.automation.helper;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class ScenarioManager {
    private static final String PREFS_NAME = "scenarios";
    private static final String KEY_SCENARIOS = "scenario_list";
    private static ScenarioManager instance;

    private SharedPreferences prefs;
    private Gson gson;
    private List<Scenario> scenarios;

    private ScenarioManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
        loadScenarios();
    }

    public static synchronized ScenarioManager getInstance(Context context) {
        if (instance == null) {
            instance = new ScenarioManager(context);
        }
        return instance;
    }

    private void loadScenarios() {
        String json = prefs.getString(KEY_SCENARIOS, null);
        if (json != null) {
            Type type = new TypeToken<List<Scenario>>(){}.getType();
            scenarios = gson.fromJson(json, type);
        } else {
            scenarios = new ArrayList<>();
        }
    }

    private void saveScenarios() {
        String json = gson.toJson(scenarios);
        prefs.edit().putString(KEY_SCENARIOS, json).apply();
    }

    public void addScenario(Scenario scenario) {
        scenarios.add(scenario);
        saveScenarios();
    }

    public void updateScenario(Scenario scenario) {
        for (int i = 0; i < scenarios.size(); i++) {
            if (scenarios.get(i).getId().equals(scenario.getId())) {
                scenarios.set(i, scenario);
                saveScenarios();
                return;
            }
        }
    }

    public void deleteScenario(String id) {
        scenarios.removeIf(s -> s.getId().equals(id));
        saveScenarios();
    }

    public Scenario getScenario(String id) {
        for (Scenario s : scenarios) {
            if (s.getId().equals(id)) {
                return s;
            }
        }
        return null;
    }

    public List<Scenario> getAllScenarios() {
        return new ArrayList<>(scenarios);
    }

    public List<Scenario> getScheduledScenarios() {
        List<Scenario> scheduled = new ArrayList<>();
        for (Scenario s : scenarios) {
            if (s.isScheduled() && s.isEnabled()) {
                scheduled.add(s);
            }
        }
        return scheduled;
    }
}
