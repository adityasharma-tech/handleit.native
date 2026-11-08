package in.mraditya.handleit;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

public class CallHandling extends AppCompatActivity {

    private static final String PHONE_PERMISSION = Manifest.permission.CALL_PHONE;
    private static final String STATE_PERMISSION = Manifest.permission.READ_PHONE_STATE;

    private ForwardingState forwardingState;
    private MaterialSwitch switchForwarding;
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView tvStatus;
    private MaterialCardView cardPermPhone;
    private MaterialCardView cardPermState;
    private MaterialButton btnGrantPhone;
    private MaterialButton btnGrantState;
    private ProgressBar progress;

    private boolean isEnabled = false;
    private boolean isLoading = false;
    private boolean updatingSwitch = false;
    private boolean hasRequestedPhone = false;
    private boolean hasRequestedState = false;

    interface UssdResult { void done(boolean ok, String text); }

    private ActivityResultLauncher<String> phonePermissionLauncher;
    private ActivityResultLauncher<String> statePermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call_handling);

        if (savedInstanceState != null) {
            hasRequestedPhone = savedInstanceState.getBoolean("requested_phone");
            hasRequestedState = savedInstanceState.getBoolean("requested_state");
        }

        initializeViews();
        initializePermissionLaunchers();
        initializeListeners();
        observeForwardingState();
    }

    private void initializeViews() {
        forwardingState = ForwardingState.get(this);
        switchForwarding = findViewById(R.id.switchForwarding);
        tvStatus = findViewById(R.id.tvStatus);
        cardPermPhone = findViewById(R.id.cardPermPhone);
        cardPermState = findViewById(R.id.cardPermState);
        btnGrantPhone = findViewById(R.id.btnGrantPhone);
        btnGrantState = findViewById(R.id.btnGrantState);
        progress = findViewById(R.id.progress);
    }

    private void initializePermissionLaunchers() {
        phonePermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    hasRequestedPhone = true;
                    refreshPermissionCards();
                    if (granted && hasRequiredPermissions()) {
                        refreshStatus();
                    }
                }
        );

        statePermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    hasRequestedState = true;
                    refreshPermissionCards();
                    if (granted && hasRequiredPermissions()) {
                        refreshStatus();
                    }
                }
        );
    }

    private void initializeListeners() {
        switchForwarding.setOnCheckedChangeListener((buttonView, checked) -> {
            if (updatingSwitch || isLoading) {
                return;
            }
            confirmSwitchChange(checked);
        });
    }

    private void observeForwardingState() {
        forwardingState.isEnabled().observe(this, enabled -> {
            if (enabled == null) {
                return;
            }

            updateStatusUI(enabled);
        });
    }

    private void updateStatusUI(boolean enabled) {
        isEnabled = enabled;
        tvStatus.setText(enabled ? "Enabled" : "Disabled");
        tvStatus.setTextColor(enabled ? Color.WHITE : Color.parseColor("#A3A3A3"));
        setSwitchState(enabled);
        switchForwarding.setEnabled(true);
    }

    private void setSwitchState(boolean checked) {
        updatingSwitch = true;
        switchForwarding.setChecked(checked);
        updatingSwitch = false;
    }

    private void confirmSwitchChange(boolean requestedState) {
        setSwitchState(isEnabled);

        AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setTitle(requestedState ? "Enable call forwarding?" : "Turn off call forwarding?")
                .setMessage(requestedState
                        ? "Continue to SIM setup to enable call forwarding."
                        : "Call forwarding will be disabled.")
                .setNegativeButton("Cancel", (dialogInterface, which) -> setSwitchState(isEnabled))
                .setOnCancelListener(dialogInterface -> setSwitchState(isEnabled));

        if (requestedState) {
            dialog.setPositiveButton("Continue", (dialogInterface, which) -> {
                Intent intent = new Intent(CallHandling.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
            });
        } else {
            dialog.setPositiveButton("Turn off", (dialogInterface, which) -> disableForwarding());
        }

        dialog.show();
    }


    @RequiresPermission(Manifest.permission.CALL_PHONE)
    void runUssd(int subId, String code, CallHandling.UssdResult cb) {
        progress.setVisibility(View.VISIBLE);

        final boolean[] finished = {false};
        Runnable timeout = () -> {
            if (finished[0]) return;
            finished[0] = true;
            finish(cb, false, "Timed out");
        };
        main.postDelayed(timeout, 20000);

        TelephonyManager tm = ((TelephonyManager) getSystemService(TELEPHONY_SERVICE))
                .createForSubscriptionId(subId);

        tm.sendUssdRequest(code, new TelephonyManager.UssdResponseCallback() {
            @Override
            public void onReceiveUssdResponse(TelephonyManager t, String req, CharSequence resp) {
                if (finished[0]) return;
                finished[0] = true;
                main.removeCallbacks(timeout);
                finish(cb, true, resp.toString());
            }

            @Override
            public void onReceiveUssdResponseFailed(TelephonyManager t, String req, int code) {
                if (finished[0]) return;
                finished[0] = true;
                main.removeCallbacks(timeout);
                finish(cb, false, "Failed (code " + code + ")");
            }
        }, main);
    }

    private void finish(CallHandling.UssdResult cb, boolean ok, String text) {
        progress.setVisibility(View.GONE);
        cb.done(ok, text);
    }

    private void disableForwarding() {
        if (isLoading) {
            return;
        }

        if (!hasRequiredPermissions()) {
            showMessage("Permissions are required to manage call forwarding.");
            refreshPermissionCards();
            setSwitchState(true);
            return;
        }

        isLoading = true;
        setLoading(true);

        int subId = getSelectedSubscriptionId();
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            isLoading = false;
            setLoading(false);
            setSwitchState(true);
            showMessage("No active SIM subscription found.");
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestPhonePermission();
            requestStatePermission();
        }
        runUssd(subId, "##002#", (success, response) -> {
            isLoading = false;
            setLoading(false);

            if (success) {
                Integer target = forwardingState.getSelectedSlot().getValue();
                forwardingState.set(false, target);
                showMessage("Call forwarding disabled.");

                switchForwarding.post(() -> {
                    Intent intent = new Intent(CallHandling.this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    finish();
                });
            } else {
                setSwitchState(true);
                showMessage(response != null && !response.isEmpty()
                        ? response
                        : "Unable to disable call forwarding.");
            }
        });
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        switchForwarding.setEnabled(!loading);
    }

    private void refreshStatus() {
        if (isLoading) {
            return;
        }

        if (!hasRequiredPermissions()) {
            tvStatus.setText("Permission required");
            switchForwarding.setEnabled(true);
            showMessage("Permissions are required to check call forwarding.");
            refreshPermissionCards();
            return;
        }

        int subId = getSelectedSubscriptionId();
        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            tvStatus.setText("Unavailable");
            switchForwarding.setEnabled(true);
            showMessage("No active SIM subscription found.");
            return;
        }

        progress.setVisibility(View.VISIBLE);
        boolean isForwarding = forwardingState.isEnabled().getValue();
        if(isForwarding){
            tvStatus.setText("Enabled");
        } else {
            tvStatus.setText("Disabled");
            switchForwarding.setEnabled(true);
        }
        progress.setVisibility(View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPermissionCards();

        if (hasRequiredPermissions()) {
            refreshStatus();
        }
    }

    private boolean hasRequiredPermissions() {
        return ContextCompat.checkSelfPermission(this, PHONE_PERMISSION)
                == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, STATE_PERMISSION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPhonePermission() {
        if (ContextCompat.checkSelfPermission(this, PHONE_PERMISSION)
                == PackageManager.PERMISSION_GRANTED) {
            refreshPermissionCards();
            return;
        }

        if (hasRequestedPhone && !shouldShowRequestPermissionRationale(PHONE_PERMISSION)) {
            openAppSettings();
            return;
        }

        hasRequestedPhone = true;
        phonePermissionLauncher.launch(PHONE_PERMISSION);
    }

    private void requestStatePermission() {
        if (ContextCompat.checkSelfPermission(this, STATE_PERMISSION)
                == PackageManager.PERMISSION_GRANTED) {
            refreshPermissionCards();
            return;
        }

        if (hasRequestedState && !shouldShowRequestPermissionRationale(STATE_PERMISSION)) {
            openAppSettings();
            return;
        }

        hasRequestedState = true;
        statePermissionLauncher.launch(STATE_PERMISSION);
    }

    private void refreshPermissionCards() {
        boolean phoneMissing = ContextCompat.checkSelfPermission(this, PHONE_PERMISSION)
                != PackageManager.PERMISSION_GRANTED;
        boolean stateMissing = ContextCompat.checkSelfPermission(this, STATE_PERMISSION)
                != PackageManager.PERMISSION_GRANTED;

        updatePermissionCard(
                cardPermPhone,
                btnGrantPhone,
                phoneMissing,
                hasRequestedPhone && !shouldShowRequestPermissionRationale(PHONE_PERMISSION)
        );

        updatePermissionCard(
                cardPermState,
                btnGrantState,
                stateMissing,
                hasRequestedState && !shouldShowRequestPermissionRationale(STATE_PERMISSION)
        );

        if (phoneMissing || stateMissing) {
            tvStatus.setText("Permission required");
            switchForwarding.setEnabled(true);
        }
    }

    private void updatePermissionCard(
            MaterialCardView card,
            MaterialButton button,
            boolean missing,
            boolean permanentlyDenied
    ) {
        if (missing) {
            card.setVisibility(View.VISIBLE);
            button.setText(permanentlyDenied ? "Open settings" : "Grant");
            button.setOnClickListener(view -> {
                if (permanentlyDenied) {
                    openAppSettings();
                } else if (card == cardPermPhone) {
                    requestPhonePermission();
                } else {
                    requestStatePermission();
                }
            });
        } else {
            card.setVisibility(View.GONE);
        }
    }

    private void openAppSettings() {
        Intent intent = new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null)
        );
        startActivity(intent);
    }

    private int getSelectedSubscriptionId() {
        Log.d("SubscriptionLog", "!");
        if (ContextCompat.checkSelfPermission(this, STATE_PERMISSION)
                != PackageManager.PERMISSION_GRANTED) {
            return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }

        SubscriptionManager manager = (SubscriptionManager) getSystemService(
                Context.TELEPHONY_SUBSCRIPTION_SERVICE
        );

        if (manager == null) {
            return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }

        try {
            List<SubscriptionInfo> subscriptions =
                    manager.getActiveSubscriptionInfoList();
            Log.d("SubscriptionLog", "2");

            if (subscriptions != null && !subscriptions.isEmpty()) {
                Integer target = forwardingState.getSelectedSlot().getValue();
                Log.d("SubscriptionLog", "3" + target);

                if(target != null && target != -1){
                    Log.d("SubscriptionLog", "oooo");

                    return subscriptions.get(target).getSubscriptionId();
                }
            }
        } catch (SecurityException ignored) {
        }

        Log.d("SubscriptionLog", "!!");

        return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }

    private void showMessage(String message) {
        Snackbar.make(findViewById(R.id.main), message, Snackbar.LENGTH_LONG)
                .setBackgroundTint(Color.parseColor("#171717"))
                .setTextColor(Color.WHITE)
                .show();
    }


    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBoolean("requested_phone", hasRequestedPhone);
        outState.putBoolean("requested_state", hasRequestedState);
        super.onSaveInstanceState(outState);
    }
}

