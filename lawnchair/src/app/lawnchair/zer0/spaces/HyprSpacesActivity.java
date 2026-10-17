package app.lawnchair.zer0.spaces;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import app.lawnchair.LawnchairApp;
import app.lawnchair.zer0.voice.VoiceAppVisibility;
import com.android.launcher3.R;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Opt-in, downstream-only window experiment. Android/Samsung own real window placement.
 * This activity owns labels and app-pair requests; it does not impersonate a system compositor.
 */
public final class HyprSpacesActivity extends Activity {
    public static final String EXTRA_ACTIVATE = "app.lawnchair.zer0.spaces.ACTIVATE";
    public static final String EXTRA_SOURCE_SCREEN_ID =
            "app.lawnchair.zer0.spaces.SOURCE_SCREEN_ID";
    public static final String PENDING_SCREEN_ID = "pending_screen_id";
    public static final String PENDING_SCREEN_REQUEST_TIME = "pending_screen_requested_at";
    public static final String PREFS_NAME = "zer0_hyprspaces";
    public static final String KEY_NAMES = "names";
    public static final String KEY_ACTIVE = "active";
    private static final String PROBE_ID = "probe_id";
    private static final String PROBE_MODE = "probe_mode";
    private static final String PROBE_STAGE = "probe_stage";
    private static final String PROBE_OBS = "probe_observation";
    private static final String PROBE_API = "probe_api";
    private static final String PROBE_FREEFORM = "probe_freeform";
    private static final String PROBE_IN_MULTIWINDOW = "probe_in_multiwindow";

    private SharedPreferences prefs;
    private SpaceModel model;
    private List<LauncherActivityInfo> apps = new ArrayList<>();
    private Spinner primary;
    private Spinner secondary;
    private String scratchPrimary;
    private String scratchSecondary;
    private List<String> scratchPins = new ArrayList<>();
    private boolean firstResume = true;
    private String searchQuery = "";
    private ScrollView activeScroll;
    private EditText activeSearch;
    private int launcherSourceScreenId = SpacePageBinding.NO_SCREEN;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        launcherSourceScreenId = getIntent().getIntExtra(
                EXTRA_SOURCE_SCREEN_ID, SpacePageBinding.NO_SCREEN);
        model = SpaceModel.restore(prefs.getString(KEY_NAMES, null),
                prefs.getString(KEY_ACTIVE, null));
        String requested = getIntent().getStringExtra(EXTRA_ACTIVATE);
        boolean activatedRequestedSpace = false;
        if (requested != null) {
            try {
                model = model.activate(requested);
                persist();
                activatedRequestedSpace = true;
            } catch (IllegalArgumentException ignored) {
                Toast.makeText(this, R.string.zer0_space_unknown, Toast.LENGTH_SHORT).show();
            }
        }
        // Unknown space requests must not navigate to the previously active page.
        // Schedule navigation only after successful activation of the requested space.
        if (activatedRequestedSpace && scheduleLinkedHomePageIfAny()) {
            finish();
            return;
        }
        apps = loadPersonalApps();
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (firstResume) {
            firstResume = false;
        } else {
            // Re-check installed-app and hidden-app preferences after switching away.
            // A previously visible app must not remain exposed in this picker.
            apps = loadPersonalApps();
            render();
        }
    }

    private List<LauncherActivityInfo> loadPersonalApps() {
        ArrayList<LauncherActivityInfo> result = new ArrayList<>();
        LauncherApps launcherApps = getSystemService(LauncherApps.class);
        if (launcherApps != null) {
            try {
                List<LauncherActivityInfo> installed =
                        launcherApps.getActivityList(null, Process.myUserHandle());
                if (installed != null) {
                    // Picker must not reveal hidden apps merely by listing their full labels.
                    // Empty query applies the shared drawer/search visibility policy.
                    for (LauncherActivityInfo info : installed) {
                        if (VoiceAppVisibility.isAllowed(this, info, "")) {
                            result.add(info);
                        }
                    }
                }
            } catch (RuntimeException ignored) {
                // No permission escalation, work-profile enumeration, or guessed alternatives.
            }
        }
        result.sort(Comparator.comparing(
                HyprSpacesActivity::displayLabel, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private static String displayLabel(LauncherActivityInfo activity) {
        CharSequence label = activity.getLabel();
        return label == null ? activity.getComponentName().flattenToShortString() : label.toString();
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
        final int previousScrollY = activeScroll == null ? 0 : activeScroll.getScrollY();
        final boolean searchWasFocused = activeSearch != null && activeSearch.hasFocus();
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(28));

        content.addView(text(getString(R.string.zer0_spaces_title), 26));
        content.addView(text(getString(R.string.zer0_space_active, model.activeName()), 16));
        content.addView(text(getString(R.string.zer0_spaces_disclaimer), 13));
        if (!model.isScratchActive()) {
            int linkedScreenId = prefs.getInt(pageKey(model.activeName()),
                    SpacePageBinding.NO_SCREEN);
            content.addView(text(getString(R.string.zer0_page_binding_status,
                    linkedScreenId < 0
                            ? getString(R.string.zer0_page_binding_none)
                            : String.valueOf(linkedScreenId)), 13));
            Button bindCurrent = button(getString(R.string.zer0_page_bind), () -> {
                if (launcherSourceScreenId < 0) return;
                prefs.edit().putInt(pageKey(model.activeName()), launcherSourceScreenId).apply();
                render();
            });
            bindCurrent.setEnabled(launcherSourceScreenId >= 0);
            content.addView(bindCurrent);
            if (linkedScreenId >= 0) {
                content.addView(button(getString(R.string.zer0_page_unbind), () -> {
                    prefs.edit().remove(pageKey(model.activeName())).apply();
                    render();
                }));
            }
        }

        for (String space : model.names()) {
            LinearLayout line = row();
            Button activate = button(
                    (model.activeName().equals(space) ? "● " : "  ") + space,
                    () -> activate(space));
            activate.setContentDescription(getString(R.string.zer0_accessible_activate, space));
            line.addView(activate, new LinearLayout.LayoutParams(0, dp(55), 1));
            line.addView(button(getString(R.string.zer0_space_rename),
                    () -> askForName(space)), new LinearLayout.LayoutParams(dp(84), dp(55)));
            Button remove = button("×", () -> confirmRemove(space));
            remove.setContentDescription(getString(R.string.zer0_accessible_delete_space, space));
            line.addView(remove, new LinearLayout.LayoutParams(dp(50), dp(55)));
            content.addView(line);
        }
        content.addView(button(getString(R.string.zer0_space_create),
                () -> askForName(null)));
        content.addView(button(getString(R.string.zer0_space_scratch),
                () -> activate(SpaceModel.SCRATCH)));

        content.addView(text(getString(R.string.zer0_search_title), 20));
        EditText search = new EditText(this);
        search.setSingleLine(true);
        activeSearch = search;
        search.setHint(R.string.zer0_search_hint);
        search.setContentDescription(getString(R.string.zer0_search_title));
        search.setImeOptions(EditorInfo.IME_ACTION_GO);
        search.setText(searchQuery);
        search.setSelection(searchQuery.length());
        LinearLayout results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        content.addView(search);
        content.addView(results);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) {}

            @Override public void onTextChanged(
                    CharSequence value, int start, int before, int count) {
                searchQuery = value.toString();
                updateSearchResults(results);
            }

            @Override public void afterTextChanged(Editable value) {}
        });
        search.setOnEditorActionListener((view, actionId, event) -> {
            boolean hardwareEnter = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId != EditorInfo.IME_ACTION_GO && !hardwareEnter) return false;
            SpaceAppSearch.Entry exact =
                    SpaceAppSearch.exactUnique(searchEntries(), searchQuery);
            if (exact == null) {
                Toast.makeText(this, R.string.zer0_search_select, Toast.LENGTH_SHORT).show();
            } else {
                launchPinned(exact.component);
            }
            return true;
        });
        updateSearchResults(results);

        content.addView(text(getString(R.string.zer0_pins_title), 20));
        content.addView(text(getString(R.string.zer0_pins_note), 13));
        List<String> pins = currentPins();
        int visiblePins = 0;
        for (String component : pins) {
            LauncherActivityInfo app = findVisibleApp(component, apps);
            if (app == null) continue; // No hidden/uninstalled name or component exposure.
            visiblePins++;
            LinearLayout shortcut = row();
            shortcut.addView(button(displayLabel(app), () -> launchPinned(component)),
                    new LinearLayout.LayoutParams(0, dp(55), 1));
            Button unpin = button("×", () -> unpin(component));
            unpin.setContentDescription(
                    getString(R.string.zer0_accessible_unpin, displayLabel(app)));
            shortcut.addView(unpin, new LinearLayout.LayoutParams(dp(50), dp(55)));
            content.addView(shortcut);
        }
        if (visiblePins == 0) {
            content.addView(text(getString(R.string.zer0_pins_empty), 13));
        }

        content.addView(text(getString(R.string.zer0_spaces_pair_title), 20));
        content.addView(text(getString(R.string.zer0_spaces_pair_note), 13));

        ArrayList<String> labels = new ArrayList<>();
        for (LauncherActivityInfo info : apps) {
            labels.add(displayLabel(info) + " · " + info.getComponentName().getPackageName());
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
        if (apps.size() > 1) secondary.setSelection(1);
        content.addView(text(getString(R.string.zer0_spaces_primary), 14));
        content.addView(primary);
        Button pinButton = button(getString(R.string.zer0_pins_add), this::pinSelected);
        pinButton.setEnabled(!apps.isEmpty());
        content.addView(pinButton);
        content.addView(text(getString(R.string.zer0_spaces_secondary), 14));
        content.addView(secondary);

        selectSavedPair();
        Button save = button(getString(R.string.zer0_spaces_save_pair), this::savePair);
        save.setEnabled(apps.size() > 1);
        content.addView(save);

        Button launch = button(getString(R.string.zer0_spaces_launch_pair), this::requestSplitPair);
        launch.setEnabled(apps.size() > 1);
        content.addView(launch);
        Button adjacent = button(getString(R.string.zer0_probe_adjacent),
                this::requestAdjacentToSpaces);
        adjacent.setEnabled(!apps.isEmpty());
        content.addView(adjacent);
        Button systemSplit = button(getString(R.string.zer0_probe_system_split),
                this::requestOptInSystemSplit);
        systemSplit.setEnabled(LawnchairApp.getInstance().isOptInSplitActionAvailable());
        content.addView(systemSplit);
        content.addView(text(getString(R.string.zer0_probe_system_split_note), 13));
        content.addView(text(getString(R.string.zer0_probe_capabilities,
                Build.VERSION.SDK_INT,
                getPackageManager().hasSystemFeature(
                        PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT),
                isInMultiWindowMode()), 13));
        content.addView(text(getString(R.string.zer0_spaces_limitations), 13));
        addProbeControls(content);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content);
        setContentView(scroll);
        activeScroll = scroll;
        scroll.post(() -> {
            scroll.scrollTo(0, previousScrollY);
            if (searchWasFocused) {
                search.requestFocus();
                search.setSelection(searchQuery.length());
            }
        });
    }

    private List<SpaceAppSearch.Entry> searchEntries() {
        ArrayList<SpaceAppSearch.Entry> candidates = new ArrayList<>(apps.size());
        for (LauncherActivityInfo app : apps) {
            candidates.add(new SpaceAppSearch.Entry(
                    app.getComponentName().flattenToString(), displayLabel(app)));
        }
        return candidates;
    }

    private void updateSearchResults(LinearLayout resultRows) {
        resultRows.removeAllViews();
        for (SpaceAppSearch.Entry item : SpaceAppSearch.filter(searchEntries(), searchQuery)) {
            LauncherActivityInfo visible = findVisibleApp(item.component, apps);
            if (visible == null) continue;
            String appLabel = displayLabel(visible);
            int sameLabels = 0;
            for (LauncherActivityInfo candidate : apps) {
                if (displayLabel(candidate).equalsIgnoreCase(appLabel)) sameLabels++;
            }
            String visibleTitle = sameLabels > 1
                    ? appLabel + " · " + visible.getComponentName().getPackageName()
                    : appLabel;
            LinearLayout line = row();
            Button open = button(visibleTitle, () -> launchPinned(item.component));
            open.setContentDescription(getString(R.string.zer0_search_open, visibleTitle));
            line.addView(open, new LinearLayout.LayoutParams(0, dp(52), 1));
            Button pin = button("+", () -> pinComponent(item.component));
            pin.setContentDescription(getString(R.string.zer0_search_pin, appLabel));
            line.addView(pin, new LinearLayout.LayoutParams(dp(52), dp(52)));
            resultRows.addView(line);
        }
    }

    private void activate(String space) {
        try {
            SpaceModel next = model.activate(space);
            if (model.isScratchActive() && !next.isScratchActive()) {
                // Scratch is ephemeral even within a long-lived Spaces activity.
                scratchPins.clear();
                scratchPrimary = null;
                scratchSecondary = null;
            }
            model = next;
            persist();
            if (scheduleLinkedHomePageIfAny()) {
                finish();
            } else {
                render();
            }
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
                        if (oldName != null) {
                            int index = model.names().indexOf(oldName);
                            migratePair(oldName, updated.names().get(index));
                        }
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
                        prefs.edit()
                                .remove(pairKey(name, 0))
                                .remove(pairKey(name, 1))
                                .remove(pinsKey(name))
                                .remove(pageKey(name))
                                .apply();
                        model = updated;
                        persist();
                        render();
                    } catch (IllegalArgumentException error) {
                        Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
                    }
                }).show();
    }

    private static String pageKey(String name) {
        return "home_page_" + name.toLowerCase(Locale.ROOT);
    }

    private boolean scheduleLinkedHomePageIfAny() {
        if (model.isScratchActive()) return false;
        int screenId = prefs.getInt(pageKey(model.activeName()), SpacePageBinding.NO_SCREEN);
        if (screenId < 0) return false;
        prefs.edit().putInt(PENDING_SCREEN_ID, screenId)
                .putLong(PENDING_SCREEN_REQUEST_TIME, SystemClock.elapsedRealtime()).apply();
        return true;
    }

    private static String pairKey(String name, int slot) {
        return "pair_" + slot + "_" + name.toLowerCase(Locale.ROOT);
    }

    private void migratePair(String oldName, String newName) {
        // Preserve explicit app pairs and pins through normalized space renames.
        SharedPreferences.Editor editor = prefs.edit();
        int previousPage = prefs.getInt(pageKey(oldName), SpacePageBinding.NO_SCREEN);
        if (previousPage >= 0) editor.putInt(pageKey(newName), previousPage);
        if (!pageKey(oldName).equals(pageKey(newName))) editor.remove(pageKey(oldName));
        String previousPins = prefs.getString(pinsKey(oldName), null);
        if (previousPins != null) editor.putString(pinsKey(newName), previousPins);
        if (!pinsKey(oldName).equals(pinsKey(newName))) editor.remove(pinsKey(oldName));
        for (int slot = 0; slot < 2; slot++) {
            String previous = prefs.getString(pairKey(oldName, slot), null);
            if (previous != null) editor.putString(pairKey(newName, slot), previous);
            if (!pairKey(oldName, slot).equals(pairKey(newName, slot))) {
                editor.remove(pairKey(oldName, slot));
            }
        }
        editor.apply();
    }

    private static String pinsKey(String name) {
        return "pins_" + name.toLowerCase(Locale.ROOT);
    }

    private List<String> currentPins() {
        if (model.isScratchActive()) return new ArrayList<>(scratchPins);
        return SpacePins.decode(prefs.getString(pinsKey(model.activeName()), null));
    }

    private void storePins(List<String> pins) {
        if (model.isScratchActive()) {
            scratchPins = new ArrayList<>(pins); // Memory only; no persisted Scratch data.
        } else {
            prefs.edit().putString(pinsKey(model.activeName()), SpacePins.encode(pins)).apply();
        }
    }

    private static LauncherActivityInfo findVisibleApp(
            String component, List<LauncherActivityInfo> visible) {
        for (LauncherActivityInfo app : visible) {
            if (app.getComponentName().flattenToString().equals(component)) return app;
        }
        return null;
    }

    private void pinSelected() {
        int index = primary.getSelectedItemPosition();
        if (index < 0 || index >= apps.size()) return;
        pinComponent(apps.get(index).getComponentName().flattenToString());
    }

    private void pinComponent(String component) {
        // Any cached search row can become stale when visibility settings change.
        if (findVisibleApp(component, loadPersonalApps()) == null) {
            Toast.makeText(this, R.string.zer0_pins_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            storePins(SpacePins.add(currentPins(), component));
            render();
        } catch (IllegalArgumentException error) {
            Toast.makeText(this, R.string.zer0_pins_limit, Toast.LENGTH_SHORT).show();
        }
    }

    private void unpin(String component) {
        storePins(SpacePins.remove(currentPins(), component));
        render();
    }

    private void launchPinned(String component) {
        // The shortcut is not launch authority: resolve current installed personal-profile
        // apps AND the current hidden-app preference again at the moment of invocation.
        LauncherActivityInfo app = findVisibleApp(component, loadPersonalApps());
        LauncherApps launcherApps = getSystemService(LauncherApps.class);
        if (app == null || launcherApps == null) {
            Toast.makeText(this, R.string.zer0_pins_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            launcherApps.startMainActivity(
                    app.getComponentName(), Process.myUserHandle(), null, null);
        } catch (RuntimeException error) {
            Toast.makeText(this, R.string.zer0_pins_unavailable, Toast.LENGTH_SHORT).show();
        }
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

    private void beginProbe(SplitProbe.Mode mode) {
        prefs.edit()
                .putString(PROBE_ID, UUID.randomUUID().toString())
                .putString(PROBE_MODE, mode.name())
                .putString(PROBE_STAGE, SplitProbe.Stage.NOT_SENT.name())
                .putString(PROBE_OBS, SplitProbe.Observation.UNREPORTED.name())
                .putInt(PROBE_API, Build.VERSION.SDK_INT)
                .putBoolean(PROBE_FREEFORM, getPackageManager().hasSystemFeature(
                        PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT))
                .putBoolean(PROBE_IN_MULTIWINDOW, isInMultiWindowMode())
                .apply();
    }

    private void updateProbeStage(SplitProbe.Stage stage) {
        prefs.edit().putString(PROBE_STAGE, stage.name()).apply();
    }

    private String probeReceipt() {
        SplitProbe.Mode mode = SplitProbe.modeOrNull(prefs.getString(PROBE_MODE, null));
        if (mode == null) return null;
        return SplitProbe.formatReceipt(
                prefs.getString(PROBE_ID, null),
                mode,
                SplitProbe.stageOrDefault(prefs.getString(PROBE_STAGE, null)),
                SplitProbe.observationOrDefault(prefs.getString(PROBE_OBS, null)),
                prefs.getInt(PROBE_API, Build.VERSION.SDK_INT),
                prefs.getBoolean(PROBE_FREEFORM, false),
                prefs.getBoolean(PROBE_IN_MULTIWINDOW, false));
    }

    private void setObservation(SplitProbe.Observation observation) {
        prefs.edit().putString(PROBE_OBS, observation.name()).apply();
        render();
    }

    private void addProbeControls(LinearLayout content) {
        String receipt = probeReceipt();
        if (receipt == null) return;
        content.addView(text(getString(R.string.zer0_probe_title), 20));
        content.addView(text(receipt, 12));
        content.addView(text(getString(R.string.zer0_probe_explanation), 13));
        content.addView(button(getString(R.string.zer0_probe_two),
                () -> setObservation(SplitProbe.Observation.TWO_VISIBLE)));
        content.addView(button(getString(R.string.zer0_probe_fullscreen),
                () -> setObservation(SplitProbe.Observation.FULLSCREEN_ONLY)));
        content.addView(button(getString(R.string.zer0_probe_one),
                () -> setObservation(SplitProbe.Observation.ONE_VISIBLE)));
        content.addView(button(getString(R.string.zer0_probe_none),
                () -> setObservation(SplitProbe.Observation.NO_CHANGE)));
        content.addView(button(getString(R.string.zer0_probe_copy), () -> {
            ClipboardManager clipboard = getSystemService(ClipboardManager.class);
            if (clipboard == null) return;
            clipboard.setPrimaryClip(ClipData.newPlainText(
                    "HyprSpaces multiwindow probe", probeReceipt()));
            Toast.makeText(this, R.string.zer0_probe_copied, Toast.LENGTH_SHORT).show();
        }));
        content.addView(button(getString(R.string.zer0_probe_clear), () -> {
            prefs.edit().remove(PROBE_ID).remove(PROBE_MODE).remove(PROBE_STAGE)
                    .remove(PROBE_OBS).remove(PROBE_API).remove(PROBE_FREEFORM)
                    .remove(PROBE_IN_MULTIWINDOW).apply();
            render();
        }));
    }

    private void requestOptInSystemSplit() {
        // A direct click and an ALREADY-bound Lawnchair AccessibilityService are required.
        // Availability is checked again immediately before execution; no Settings prompt.
        LawnchairApp launcherApp = LawnchairApp.getInstance();
        if (!launcherApp.isOptInSplitActionAvailable()) {
            Toast.makeText(this, R.string.zer0_probe_system_unavailable, Toast.LENGTH_LONG).show();
            return;
        }
        beginProbe(SplitProbe.Mode.ACCESSIBILITY_ACTION);
        if (launcherApp.tryOptInSplitAction()) {
            updateProbeStage(SplitProbe.Stage.ACCESSIBILITY_SENT);
            Toast.makeText(this, R.string.zer0_probe_request_sent, Toast.LENGTH_LONG).show();
        } else {
            updateProbeStage(SplitProbe.Stage.FAILED_ACCESSIBILITY);
            Toast.makeText(this, R.string.zer0_probe_system_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    private void requestAdjacentToSpaces() {
        int index = secondary.getSelectedItemPosition();
        if (index < 0 || index >= apps.size()) return;
        ComponentName target = apps.get(index).getComponentName();
        if (findVisibleApp(target.flattenToString(), loadPersonalApps()) == null) {
            Toast.makeText(this, R.string.zer0_pins_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        beginProbe(SplitProbe.Mode.ADJACENT_TO_SPACES);
        try {
            startActivity(mainIntent(target, true));
            updateProbeStage(SplitProbe.Stage.ADJACENT_SENT);
            Toast.makeText(this, R.string.zer0_probe_request_sent, Toast.LENGTH_LONG).show();
        } catch (RuntimeException error) {
            updateProbeStage(SplitProbe.Stage.FAILED_ADJACENT);
            Toast.makeText(this, R.string.zer0_spaces_pair_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void requestSplitPair() {
        if (!validSelection()) {
            Toast.makeText(this, R.string.zer0_spaces_pick_distinct, Toast.LENGTH_SHORT).show();
            return;
        }
        // Resolve both targets again before either is launched. Apps can be removed or
        // hidden between configuration and execution; reject without partial execution.
        ComponentName first = apps.get(primary.getSelectedItemPosition()).getComponentName();
        ComponentName second = apps.get(secondary.getSelectedItemPosition()).getComponentName();
        List<LauncherActivityInfo> visible = loadPersonalApps();
        if (findVisibleApp(first.flattenToString(), visible) == null
                || findVisibleApp(second.flattenToString(), visible) == null) {
            Toast.makeText(this, R.string.zer0_pins_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        // One UI decides whether it honors the public adjacent launch hint; result
        // remains unverified until the user/device test observes two visible tasks.
        beginProbe(SplitProbe.Mode.PAIR);
        try {
            startActivity(mainIntent(first, false));
            updateProbeStage(SplitProbe.Stage.FIRST_SENT);
        } catch (RuntimeException error) {
            updateProbeStage(SplitProbe.Stage.FAILED_FIRST);
            Toast.makeText(this, R.string.zer0_spaces_pair_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(mainIntent(second, true));
            updateProbeStage(SplitProbe.Stage.BOTH_SENT);
            Toast.makeText(this, R.string.zer0_spaces_pair_requested, Toast.LENGTH_LONG).show();
        } catch (RuntimeException error) {
            updateProbeStage(SplitProbe.Stage.FAILED_SECOND);
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
