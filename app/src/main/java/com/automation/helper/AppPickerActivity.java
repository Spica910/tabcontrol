package com.automation.helper;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AppPickerActivity extends Activity {
    public static final String EXTRA_APP_NAME = "app_name";
    public static final String EXTRA_PACKAGE_NAME = "package_name";

    private RecyclerView recyclerView;
    private EditText searchEdit;
    private AppAdapter adapter;
    private List<AppInfo> appList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_picker);

        recyclerView = findViewById(R.id.recycler_apps);
        searchEdit = findViewById(R.id.edit_search);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        loadApps();

        searchEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (adapter != null) {
                    adapter.getFilter().filter(s);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadApps() {
        new Thread(() -> {
            appList = new ArrayList<>();
            PackageManager pm = getPackageManager();
            List<ApplicationInfo> packages = pm.getInstalledApplications(PackageManager.GET_META_DATA);

            for (ApplicationInfo appInfo : packages) {
                // Only show launchable apps
                if (pm.getLaunchIntentForPackage(appInfo.packageName) != null) {
                    String appName = appInfo.loadLabel(pm).toString();
                    Drawable icon = appInfo.loadIcon(pm);
                    appList.add(new AppInfo(appName, appInfo.packageName, icon));
                }
            }

            Collections.sort(appList, (a, b) -> a.name.compareToIgnoreCase(b.name));

            runOnUiThread(() -> {
                adapter = new AppAdapter(appList, this::onAppSelected);
                recyclerView.setAdapter(adapter);
            });
        }).start();
    }

    private void onAppSelected(AppInfo app) {
        Intent result = new Intent();
        result.putExtra(EXTRA_APP_NAME, app.name);
        result.putExtra(EXTRA_PACKAGE_NAME, app.packageName);
        setResult(RESULT_OK, result);
        finish();
    }

    static class AppInfo {
        String name;
        String packageName;
        Drawable icon;

        AppInfo(String name, String packageName, Drawable icon) {
            this.name = name;
            this.packageName = packageName;
            this.icon = icon;
        }
    }

    static class AppAdapter extends RecyclerView.Adapter<AppAdapter.ViewHolder> implements Filterable {
        private List<AppInfo> originalList;
        private List<AppInfo> filteredList;
        private OnAppClickListener listener;

        interface OnAppClickListener {
            void onAppClick(AppInfo app);
        }

        AppAdapter(List<AppInfo> apps, OnAppClickListener listener) {
            this.originalList = apps;
            this.filteredList = new ArrayList<>(apps);
            this.listener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_app, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            AppInfo app = filteredList.get(position);
            holder.nameText.setText(app.name);
            holder.packageText.setText(app.packageName);
            holder.iconImage.setImageDrawable(app.icon);
            holder.itemView.setOnClickListener(v -> listener.onAppClick(app));
        }

        @Override
        public int getItemCount() {
            return filteredList.size();
        }

        @Override
        public Filter getFilter() {
            return new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    List<AppInfo> filtered = new ArrayList<>();

                    if (constraint == null || constraint.length() == 0) {
                        filtered.addAll(originalList);
                    } else {
                        String filterPattern = constraint.toString().toLowerCase().trim();
                        for (AppInfo app : originalList) {
                            if (app.name.toLowerCase().contains(filterPattern) ||
                                app.packageName.toLowerCase().contains(filterPattern)) {
                                filtered.add(app);
                            }
                        }
                    }

                    FilterResults results = new FilterResults();
                    results.values = filtered;
                    return results;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    filteredList.clear();
                    filteredList.addAll((List<AppInfo>) results.values);
                    notifyDataSetChanged();
                }
            };
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView iconImage;
            TextView nameText;
            TextView packageText;

            ViewHolder(View view) {
                super(view);
                iconImage = view.findViewById(R.id.img_app_icon);
                nameText = view.findViewById(R.id.text_app_name);
                packageText = view.findViewById(R.id.text_package_name);
            }
        }
    }
}
