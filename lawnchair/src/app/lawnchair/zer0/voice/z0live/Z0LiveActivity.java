package app.lawnchair.zer0.voice.z0live;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import app.lawnchair.zer0.voice.VoiceAppVisibility;
import com.android.launcher3.R;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;
import java.util.List;

/** Visible permission, pairing, and session controls. The service owns microphone audio. */
public final class Z0LiveActivity extends Activity implements Z0LiveStatus.Observer {
    private static final String INTERNAL_ACTION = "app.lawnchair.zer0.voice.z0live.OPEN";
    private static final String EXTRA_START_NOW = "app.lawnchair.zer0.voice.z0live.START_NOW";
    private static final String EXTRA_BAD_LINK = "app.lawnchair.zer0.voice.z0live.BAD_LINK";
    private static final int MICROPHONE_REQUEST = 301;
    private static final int NOTIFICATION_REQUEST = 302;
    private Z0LiveStore store;
    private TextView status;
    private TextView caption;
    private TextView message;
    private TextView telemetry;
    private TextView backend;
    private LinearLayout settings;
    private LinearLayout network;
    private EditText pairingLink;
    private EditText rippleUrl;
    private Button start;
    private Button mute;
    private Button end;
    private boolean resumed;
    private boolean autoStart;
    private boolean wantsStart;
    private boolean permissionInFlight;
    private boolean notificationAsked;
    private boolean replaceAfterEnd;
    private boolean clearAfterEnd;
    private PairingValidation.Pairing replacement;
    private String localMessage;

    // Retain only in memory until visible; process death discards it. Nothing enters a clean Intent.
    private static final PendingPairing PENDING_PAIRING = new PendingPairing();
    private boolean scanning;

    /** Exported no-history trampoline. It never requests permission or starts microphone audio. */
    public static final class LinkActivity extends Activity {
        @Override
        protected void onCreate(Bundle savedState) {
            super.onCreate(savedState);
            Intent incoming = getIntent();
            Uri data = incoming.getData();
            Intent clean = intent(this, false).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                if (savedState != null) {
                    clean.putExtra(EXTRA_BAD_LINK, true);
                } else if (Intent.ACTION_ASSIST.equals(incoming.getAction()) && data == null) {
                    // Android assistant invocation always opens visible controls with Start.
                } else if (Intent.ACTION_VIEW.equals(incoming.getAction()) && data != null
                        && "z0live://voice".equals(data.toString())) {
                    // An exported voice link does not authorize a microphone start.
                } else if (Intent.ACTION_VIEW.equals(incoming.getAction()) && data != null) {
                    PENDING_PAIRING.offer(data.toString());
                } else {
                    clean.putExtra(EXTRA_BAD_LINK, true);
                }
            } catch (IllegalArgumentException error) {
                clean.putExtra(EXTRA_BAD_LINK, true);
            }
            incoming.setData(null);
            incoming.setClipData(null);
            incoming.replaceExtras((Bundle) null);
            setIntent(new Intent(this, LinkActivity.class));
            try {
                startActivity(clean);
            } catch (RuntimeException error) {
                // Keep the validated request for the next visible opening of z0live.
            } finally {
                if (isTaskRoot()) finishAndRemoveTask();
                else finish();
            }
        }
    }

    public static Intent intent(Context context, boolean startNow) {
        return new Intent(context, Z0LiveActivity.class).setAction(INTERNAL_ACTION)
                .putExtra(EXTRA_START_NOW, startNow);
    }

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        buildUi();
        try {
            store = new Z0LiveStore(this);
            refreshSettings();
        } catch (RuntimeException error) {
            showMessage(R.string.z0live_storage_error);
        }
        captureIntent(getIntent(), savedState == null);
        render(Z0LiveStatus.get());
    }

    @Override
    protected void onNewIntent(Intent incoming) {
        super.onNewIntent(incoming);
        autoStart = false;
        wantsStart = false;
        captureIntent(incoming, true);
        if (resumed) consumePairing();
        if (resumed && autoStart) {
            autoStart = false;
            requestStart();
        }
    }

    private void captureIntent(Intent incoming, boolean allowInternalStart) {
        Uri data = incoming.getData();
        boolean badLink = incoming.getBooleanExtra(EXTRA_BAD_LINK, false);
        autoStart = allowInternalStart && data == null
                && incoming.getComponent() != null
                && Z0LiveActivity.class.getName().equals(incoming.getComponent().getClassName())
                && INTERNAL_ACTION.equals(incoming.getAction())
                && incoming.getBooleanExtra(EXTRA_START_NOW, false);
        // Pairing credentials must not remain in the Activity's current or saved intent.
        incoming.setData(null);
        incoming.setClipData(null);
        incoming.replaceExtras((Bundle) null);
        setIntent(intent(this, false).addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS));
        if (data != null || badLink) {
            autoStart = false;
            settings.setVisibility(View.VISIBLE);
            showMessage(R.string.z0live_pair_invalid);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Z0LiveStatus.observe(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        consumePairing();
        Z0LiveStatus.Snapshot snapshot = Z0LiveStatus.get();
        onChanged(snapshot);
        if (snapshot.pendingNetwork && snapshot.active) Z0LiveService.retry(this);
        if (autoStart) {
            autoStart = false;
            requestStart();
        } else if (wantsStart && !permissionInFlight) {
            continueStart();
        }
    }

    @Override
    protected void onPause() {
        resumed = false;
        super.onPause();
    }

    @Override
    protected void onStop() {
        wantsStart = false;
        autoStart = false;
        Z0LiveStatus.remove(this);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        replacement = null;
        replaceAfterEnd = false;
        super.onDestroy();
    }

    private void requestStart() {
        if (!resumed || store == null || replaceAfterEnd) return;
        try {
            if (store.pairing() == null) {
                settings.setVisibility(View.VISIBLE);
                showMessage(R.string.z0live_pair_first);
                return;
            }
        } catch (RuntimeException error) {
            showMessage(R.string.z0live_storage_error);
            return;
        }
        wantsStart = true;
        notificationAsked = false;
        continueStart();
    }

    private void continueStart() {
        if (!resumed || !wantsStart || permissionInFlight) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionInFlight = true;
            requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, MICROPHONE_REQUEST);
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationAsked
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            notificationAsked = true;
            permissionInFlight = true;
            requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_REQUEST);
            return;
        }
        wantsStart = false;
        localMessage = null;
        try {
            Z0LiveService.start(this);
        } catch (RuntimeException error) {
            showMessage(R.string.z0live_start_failed);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(code, permissions, grants);
        if (code != MICROPHONE_REQUEST && code != NOTIFICATION_REQUEST) return;
        permissionInFlight = false;
        if (code == MICROPHONE_REQUEST
                && (grants.length == 0 || grants[0] != PackageManager.PERMISSION_GRANTED)) {
            wantsStart = false;
            showMessage(R.string.z0live_mic_required);
            return;
        }
        // A grant cannot start audio until onResume, and onStop cancels the request.
        continueStart();
    }

    @Override
    public void onChanged(Z0LiveStatus.Snapshot snapshot) {
        if (replaceAfterEnd && !snapshot.active) saveReplacement();
        render(snapshot);
    }

    private void render(Z0LiveStatus.Snapshot snapshot) {
        status.setText(TextUtils.isEmpty(snapshot.status) ? getString(R.string.z0live_ready) : snapshot.status);
        caption.setText(snapshot.caption);
        caption.setVisibility(TextUtils.isEmpty(snapshot.caption) ? View.GONE : View.VISIBLE);
        String detail = localMessage != null ? localMessage : snapshot.error;
        message.setText(detail);
        message.setVisibility(TextUtils.isEmpty(detail) ? View.GONE : View.VISIBLE);
        telemetry.setVisibility(snapshot.telemetryFailed > 0 ? View.VISIBLE : View.GONE);
        telemetry.setText(getString(R.string.z0live_telemetry_failed, snapshot.telemetryFailed));
        start.setVisibility(snapshot.active ? View.GONE : View.VISIBLE);
        start.setEnabled(store != null && !replaceAfterEnd);
        mute.setVisibility(snapshot.active ? View.VISIBLE : View.GONE);
        mute.setText(snapshot.muted ? R.string.z0live_unmute : R.string.z0live_mute);
        end.setVisibility(snapshot.active ? View.VISIBLE : View.GONE);
        network.setVisibility(snapshot.pendingNetwork ? View.VISIBLE : View.GONE);
    }

    private void consumePairing() {
        if (!resumed || isFinishing() || isDestroyed()) return;
        PairingValidation.Pairing value = PENDING_PAIRING.take();
        if (value == null) return;
        autoStart = false;
        wantsStart = false;
        confirmPairing(value);
    }

    private void acceptPairingLink(String value) {
        PENDING_PAIRING.offer(value);
        consumePairing();
    }

    private void scanPairing() {
        if (scanning || store == null) return;
        scanning = true;
        GmsBarcodeScannerOptions options = new GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE).build();
        try {
            GmsBarcodeScanning.getClient(this, options).startScan()
                    .addOnSuccessListener(barcode -> {
                        if (isDestroyed()) return;
                        scanning = false;
                        try {
                            acceptPairingLink(barcode.getRawValue());
                        } catch (IllegalArgumentException error) {
                            showMessage(R.string.z0live_pair_invalid);
                        }
                    })
                    .addOnCanceledListener(() -> scanning = false)
                    .addOnFailureListener(error -> {
                        if (isDestroyed()) return;
                        scanning = false;
                        showMessage(R.string.z0live_pair_scan_failed);
                    });
        } catch (RuntimeException error) {
            scanning = false;
            showMessage(R.string.z0live_pair_scan_failed);
        }
    }

    private void confirmPairing(PairingValidation.Pairing pairing) {
        if (store == null) {
            showMessage(R.string.z0live_storage_error);
            return;
        }
        new AlertDialog.Builder(this).setTitle(R.string.z0live_pair_confirm)
                .setMessage(getString(R.string.z0live_pair_explanation, pairing.backendUrl))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.z0live_pair_action, (dialog, which) -> replacePairing(pairing, false))
                .show();
    }

    private void replacePairing(PairingValidation.Pairing pairing, boolean clear) {
        wantsStart = false;
        autoStart = false;
        replacement = pairing;
        clearAfterEnd = clear;
        replaceAfterEnd = true;
        showMessage(R.string.z0live_pair_stopping);
        Z0LiveService.end(this);
        onChanged(Z0LiveStatus.get());
    }

    private void saveReplacement() {
        replaceAfterEnd = false;
        PairingValidation.Pairing value = replacement;
        replacement = null;
        try {
            if (clearAfterEnd) store.clearPairing();
            else store.pair(value);
            pairingLink.setText("");
            refreshSettings();
            showMessage(clearAfterEnd ? R.string.z0live_pair_first : R.string.z0live_pair_saved);
        } catch (RuntimeException error) {
            showMessage(R.string.z0live_save_error);
        }
    }

    private void refreshSettings() {
        PairingValidation.Pairing saved = store.pairing();
        backend.setText(saved == null ? getString(R.string.z0live_pair_first)
                : getString(R.string.z0live_paired_backend, saved.backendUrl));
        String panel = store.rippleUrl();
        rippleUrl.setText(panel == null ? "" : panel);
        if (saved == null) settings.setVisibility(View.VISIBLE);
    }

    private void openTailscale() {
        try {
            LauncherApps launcherApps = getSystemService(LauncherApps.class);
            Intent launch = getPackageManager().getLaunchIntentForPackage("com.tailscale.ipn");
            List<LauncherActivityInfo> activities = launcherApps == null ? null
                    : launcherApps.getActivityList("com.tailscale.ipn", Process.myUserHandle());
            if (launch != null && launch.getComponent() != null && activities != null) {
                for (LauncherActivityInfo activity : VoiceAppVisibility.visibleApps(this, activities)) {
                    if (launch.getComponent().equals(activity.getComponentName())) {
                        startActivity(launch);
                        return;
                    }
                }
            }
        } catch (RuntimeException error) {
            // Failure must keep launcher hidden-app restrictions in force.
        }
        showMessage(R.string.z0live_tailscale_unavailable);
    }

    private void openRipple() {
        try {
            if (store == null || store.rippleUrl() == null) {
                settings.setVisibility(View.VISIBLE);
                showMessage(R.string.z0live_ripple_missing);
                return;
            }
            startActivity(new Intent(this, RippleActivity.class));
        } catch (RuntimeException error) {
            showMessage(R.string.z0live_storage_error);
        }
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout body = column();
        body.setPadding(dp(20), dp(20), dp(20), dp(12));
        scroll.addView(body);
        TextView title = text(body, getString(R.string.z0live_title), 26);
        title.setTextColor(0xFFA3E9DD);
        status = text(body, getString(R.string.z0live_ready), 19);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        caption = text(body, "", 18);
        caption.setTextIsSelectable(true);
        caption.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        message = text(body, "", 15);
        telemetry = text(body, "", 14);
        start = button(body, R.string.z0live_start, this::requestStart);
        LinearLayout controls = row(body);
        mute = button(controls, R.string.z0live_mute, () -> Z0LiveService.toggleMute(this));
        end = button(controls, R.string.z0live_end, () -> {
            wantsStart = false;
            autoStart = false;
            Z0LiveService.end(this);
        });
        network = column();
        body.addView(network);
        text(network, getString(R.string.z0live_network_help), 15);
        button(network, R.string.z0live_tailscale, this::openTailscale);
        button(network, R.string.z0live_retry, () -> Z0LiveService.retry(this));
        button(body, R.string.z0live_ripple, this::openRipple);
        settings = column();
        body.addView(settings);
        settings.setVisibility(View.GONE);
        backend = text(settings, "", 14);
        button(settings, R.string.z0live_pair_scan, this::scanPairing);
        pairingLink = input(settings, R.string.z0live_pair_hint, true);
        button(settings, R.string.z0live_pair_review, () -> {
            String value = pairingLink.getText().toString();
            pairingLink.setText("");
            try {
                acceptPairingLink(value);
            } catch (IllegalArgumentException error) {
                showMessage(R.string.z0live_pair_invalid);
            }
        });
        button(settings, R.string.z0live_forget, () -> new AlertDialog.Builder(this)
                .setTitle(R.string.z0live_forget_confirm).setMessage(R.string.z0live_forget_explanation)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.z0live_forget, (dialog, which) -> replacePairing(null, true)).show());
        rippleUrl = input(settings, R.string.z0live_ripple_hint, false);
        button(settings, R.string.z0live_ripple_save, () -> {
            if (store == null) {
                showMessage(R.string.z0live_storage_error);
                return;
            }
            try {
                store.setRippleUrl(rippleUrl.getText().toString());
                showMessage(R.string.z0live_ripple_saved);
            } catch (IllegalArgumentException error) {
                showMessage(R.string.z0live_ripple_invalid);
            } catch (RuntimeException error) {
                showMessage(R.string.z0live_save_error);
            }
        });
        LinearLayout footer = row(body);
        button(footer, R.string.z0live_settings, () -> settings.setVisibility(
                settings.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE));
        button(footer, R.string.z0live_close, this::finish);
        setContentView(scroll);
    }

    private void showMessage(int resource) {
        localMessage = getString(resource);
        message.setText(localMessage);
        message.setVisibility(View.VISIBLE);
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout row(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    private TextView text(LinearLayout parent, String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(Color.WHITE);
        view.setPadding(0, dp(6), 0, dp(6));
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }

    private Button button(LinearLayout parent, int label, Runnable action) {
        Button view = new Button(this);
        view.setText(label);
        view.setAllCaps(false);
        view.setMinHeight(dp(48));
        view.setOnClickListener(ignored -> action.run());
        LinearLayout.LayoutParams params = parent.getOrientation() == LinearLayout.HORIZONTAL
                ? new LinearLayout.LayoutParams(0, -2, 1) : new LinearLayout.LayoutParams(-1, -2);
        parent.addView(view, params);
        return view;
    }

    private EditText input(LinearLayout parent, int hint, boolean secret) {
        EditText view = new EditText(this);
        view.setHint(hint);
        view.setTextColor(Color.WHITE);
        view.setHintTextColor(0xFFB2BEC5);
        view.setSingleLine(true);
        view.setInputType(InputType.TYPE_CLASS_TEXT | (secret
                ? InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_TEXT_VARIATION_URI));
        view.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        view.setSaveEnabled(false);
        view.setPrivateImeOptions("nm");
        view.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
