package com.automation.helper;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Scenario implements Serializable {
    private String id;
    private String name;
    private String packageName;
    private String appName;
    private List<ClickAction> actions;
    private int hour = -1;
    private int minute = -1;
    private boolean enabled = true;

    public Scenario() {
        this.id = String.valueOf(System.currentTimeMillis());
        this.actions = new ArrayList<>();
    }

    public Scenario(String name, String packageName, String appName) {
        this();
        this.name = name;
        this.packageName = packageName;
        this.appName = appName;
    }

    public void addClickAction(int x, int y, int delayAfter) {
        actions.add(new ClickAction(x, y, delayAfter));
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }

    public String getAppName() { return appName; }
    public void setAppName(String appName) { this.appName = appName; }

    public List<ClickAction> getActions() { return actions; }
    public void setActions(List<ClickAction> actions) { this.actions = actions; }

    public int getHour() { return hour; }
    public void setHour(int hour) { this.hour = hour; }

    public int getMinute() { return minute; }
    public void setMinute(int minute) { this.minute = minute; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isScheduled() {
        return hour >= 0 && minute >= 0;
    }

    public static class ClickAction implements Serializable {
        private int x;
        private int y;
        private int delayAfter; // milliseconds

        public ClickAction(int x, int y, int delayAfter) {
            this.x = x;
            this.y = y;
            this.delayAfter = delayAfter;
        }

        public int getX() { return x; }
        public int getY() { return y; }
        public int getDelayAfter() { return delayAfter; }
    }
}
