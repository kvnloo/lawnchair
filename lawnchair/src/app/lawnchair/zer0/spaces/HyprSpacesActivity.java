package app.lawnchair.zer0.spaces;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.os.Bundle;
import android.os.Process;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import com.android.launcher3.R;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Opt-in, downstream-only window experiment. Android/Samsung own real window placement.
 * This activity owns labels and app-pair requests; it does not impersonate a system compositor.
 */
public final class HyprSpacesActivity extends Activity {
    public static final String EXTRA_ACTIVATE = "app.lawnchair.zer0.spaces.ACTIVATE";
    public static final String PREFS_NAME = "zer0_hyprspaces";
    public static final String KEY_NAMES = "names";
    public static final String KEY_ACTIVE = "active";

    private SharedPreferences prefs;
    private SpaceModel model;
    private List<LauncherActivityInfo> apps = new ArrayList<>();
    private Spinner primary;
    private Spinner secondary;
    private String scratchPrimary;
    private String scratchSecondary;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        model = SpaceModel.restore(prefs.getString(KEY_NAMES, null),
                prefs.getString(KEY_ACTIVE, null));
        String requested = getIntent().getStringExtra(EXTRA_ACTIVATE);
        if (requested != null) {
            try {
                model = model.activate(requested);
                persist();
            } catch (IllegalArgumentException ignored) {
                Toast.makeText(this, R.string.zer0_space_unknown, Toast.LENGTH_SHORT).show();
            }
        }
        apps = loadPersonalApps();
        render();
    }

    private List<LauncherActivityInfo> loadPersonalApps() {
        ArrayList<LauncherActivityInfo> result = new ArrayList<>();
        LauncherApps launcherApps = getSystemService(LauncherApps.class);
        if (launcherApps != null) {
            try {
                List<LauncherActivityInfo> installed =
                        launcherApps.getActivityList(null, Process.myUserHandle());
                if (installed != null) result.addAll(installed);
            } catch (RuntimeException ignored) {
                // No permission escalation, work-profile enumeration, or guessed alternatives.
            }
        }
        result.sort(Comparator.comparing(
                (LauncherActivityInfo app) -> app.getLabel().toString(),
                String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private void persist() {
        prefs.edit().putString(KEY_NAMES, model.encodeNames())
                .putString(KEY_ACTIVE, model.savedActiveName()).apply();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private TextView text(String value, int sp) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextSize(sp);
        label.setPadding(dp(4), dp(8), dp(4), dp(8));
        return label;
    }

    private Button button(String value, Runnable click) {
        Button control = new Button(this);
        control.setText(value);
        control.setAllCaps(false);
        control.setOnClickListener(view -> click.run());
        return control;
    }

    private LinearLayout row() {
        LinearLayout line = new LinearLayout(this);
        line.setOrientation(LinearLayout.HORIZONTAL);
        return line;
    }

    private void render() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(28));

        content.addView(text(getString(R.string.zer0_spaces_title), 26));
        content.addView(text(getString(R.string.zer0_space_active, model.activeName()), 16));
        content.addView(text(getString(R.string.zer0_spaces_disclaimer), 13));

        for (String space : model.names()) {
            LinearLayout line = row();
            line.addView(button((model.activeName().equals(space) ? "● " : "  ") + space,
                    () -> activate(space)), new LinearLayout.LayoutParams(0, dp(55), 1));
            line.addView(button(getString(R.string.zer0_space_rename),
                    () -> askForName(space)), new LinearLayout.LayoutParams(dp(84), dp(55)));
            line.addView(button("×", () -> confirmRemove(space)),
                    new LinearLayout.LayoutParams(dp(50), dp(55)));
            content.addView(line);
        }
        content.addView(button(getString(R.string.zer0_space_create),
                () -> askForName(null)));
        content.addView(button(getString(R.string.zer0_space_scratch),
                () -> activate(SpaceModel.SCRATCH)));

        content.addView(text(getString(R.string.zer0_spaces_pair_title), 20));
        content.addView(text(getString(R.string.zer0_spaces_pair_note), 13));

        ArrayList<String> labels = new ArrayList<>();
        for (LauncherActivityInfo info : apps) {
            labels.add(info.getLabel() + " · " + info.getComponentName().getPackageName());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        primary = new Spinner(this);
        primary.setContentDescription(getString(R.string.zer0_spaces_primary));
        primary.setAdapter(adapter);
        secondary = new Spinner(this);
        secondary.setContentDescription(getString(R.string.zer0_spaces_secondary));
        secondary.setAdapter(adapter);
        content.addView(text(getString(R.string.zer0_spaces_primary), 14));
        content.addView(primary);
        content.addView(text(getString(R.string.zer0_spaces_secondary), 14));
        content.addView(secondary);

        selectSavedPair();
        Button save = button(getString(R.string.zer0_spaces_save_pair), this::savePair);
        save.setEnabled(apps.size() > 1);
        content.addView(save);

        Button launch = button(getString(R.string.zer0_spaces_launch_pair), this::requestSplitPair);
        launch.setEnabled(apps.size() > 1);
        content.addView(launch);
        content.addView(text(getString(R.string.zer0_spaces_limitations), 13));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content);
        setContentView(scroll);
    }

    private void activate(String space) {
        try {
            model = model.activate(space);
            persist();
            render();
        } catch (IllegalArgumentException ignored) {
            Toast.makeText(this, R.string.zer0_space_unknown, Toast.LENGTH_SHORT).show();
        }
    }

    private void askForName(String oldName) {
        EditText editor = new EditText(this);
        editor.setSingleLine(true);
        if (oldName != null) editor.setText(oldName);
        new AlertDialog.Builder(this)
                .setTitle(oldName == null
                        ? R.string.zer0_space_create : R.string.zer0_space_rename)
                .setView(editor)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    try {
                        String proposed = editor.getText().toString();
                        SpaceModel updated = oldName == null
                                ? model.add(proposed) : model.rename(oldName, proposed);
                        if (oldName != null) migratePair(oldName, updated.names(), proposed);
                        model = updated;
                        persist();
                        render();
                    } catch (IllegalArgumentException error) {
                        Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }).show();
    }

    private void confirmRemove(String name) {
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.zer0_space_delete_confirm, name))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    try {
                        SpaceModel updated = model.remove(name);
                        prefs.edit().remove(pairKey(name, 0)).remove(pairKey(name, 1)).apply();
                        model = updated;
                        persist();
                        render();
                    } catch (IllegalArgumentException error) {
                        Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }).show();
    }

    private static String pairKey(String name, int slot) {
        return "pair_" + slot + "_" + name.toLowerCase(Locale.ROOT);
    }

    private void migratePair(String oldName, List<String> updatedNames, String requested) {
        // Rename only changes labels. Preserve the old space's explicit app pair.
        String newName = null;
        for (String name : updatedNames) {
            if (name.equalsIgnoreCase(requested.trim())) newName = name;
        }
        if (newName == null) return;
        SharedPreferences.Editor editor = prefs.edit();
        for (int slot = 0; slot < 2; slot++) {
            String previous = prefs.getString(pairKey(oldName, slot), null);
            if (previous != null) editor.putString(pairKey(newName, slot), previous);
            if (!pairKey(oldName, slot).equals(pairKey(newName, slot))) {
                editor.remove(pairKey(oldName, slot));
            }
        }
        editor.apply();
    }

    private String getSaved(int slot) {
        if (model.isScratchActive()) return slot == 0 ? scratchPrimary : scratchSecondary;
        return prefs.getString(pairKey(model.activeName(), slot), null);
    }

    private void selectSavedPair() {
        for (int slot = 0; slot < 2; slot++) {
            String component = getSaved(slot);
            if (component == null) continue;
            for (int i = 0; i < apps.size(); i++) {
                if (component.equals(apps.get(i).getComponentName().flattenToString())) {
                    (slot == 0 ? primary : secondary).setSelection(i);
                    break;
                }
            }
        }
    }

    private boolean validSelection() {
        if (apps.size() < 2) return false;
        return primary.getSelectedItemPosition() >= 0
                && secondary.getSelectedItemPosition() >= 0
                && !apps.get(primary.getSelectedItemPosition()).getComponentName().equals(
                        apps.get(secondary.getSelectedItemPosition()).getComponentName());
    }

    private void savePair() {
        if (!validSelection()) {
            Toast.makeText(this, R.string.zer0_spaces_pick_distinct, Toast.LENGTH_SHORT).show();
            return;
        }
        String first = apps.get(primary.getSelectedItemPosition())
                .getComponentName().flattenToString();
        String second = apps.get(secondary.getSelectedItemPosition())
                .getComponentName().flattenToString();
        if (model.isScratchActive()) {
            scratchPrimary = first;
            scratchSecondary = second;
        } else {
            prefs.edit().putString(pairKey(model.activeName(), 0), first)
                    .putString(pairKey(model.activeName(), 1), second).apply();
        }
        Toast.makeText(this, R.string.zer0_spaces_pair_saved, Toast.LENGTH_SHORT).show();
    }

    private void requestSplitPair() {
        if (!validSelection()) {
            Toast.makeText(this, R.string.zer0_spaces_pick_distinct, Toast.LENGTH_SHORT).show();
            return;
        }
        // One UI chooses whether/how to honor these public Android launch hints.
        // There is no claim that a split was verified; no retry after a partial launch.
        ComponentName first = apps.get(primary.getSelectedItemPosition()).getComponentName();
        ComponentName second = apps.get(secondary.getSelectedItemPosition()).getComponentName();
        try {
            startActivity(mainIntent(first, false));
        } catch (RuntimeException error) {
            Toast.makeText(this, R.string.zer0_spaces_pair_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(mainIntent(second, true));
            Toast.makeText(this, R.string.zer0_spaces_pair_requested, Toast.LENGTH_LONG).show();
        } catch (RuntimeException error) {
            Toast.makeText(this, R.string.zer0_spaces_second_failed, Toast.LENGTH_LONG).show();
        }
    }

    private static Intent mainIntent(ComponentName component, boolean adjacent) {
        Intent intent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (adjacent) intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT);
        return intent;
    }
}
