package in.mraditya.handleit;

import android.Manifest;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.LinearInterpolator;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CallHandling extends AppCompatActivity {

    private static final String PHONE_PERMISSION =
            Manifest.permission.CALL_PHONE;

    private static final String STATE_PERMISSION =
            Manifest.permission.READ_PHONE_STATE;

    private ForwardingState forwardingState;

    private MaterialSwitch switchForwarding;

    private TextView tvStatus;
    private TextView tvStatusChip;
    private TextView tvTarget;
    private TextView tvSim;
    private TextView tvLastChecked;
    private TextView tvHowDetails;

    private MaterialCardView cardPermPhone;
    private MaterialCardView cardPermState;
    private MaterialCardView cardHero;

    private MaterialButton btnGrantPhone;
    private MaterialButton btnGrantState;

    private ProgressBar progress;

    private View pulseRing;
    private View iconCircle;
    private View rowHow;

    private ImageView ivHowArrow;
    private ImageView ivStatusIcon;

    private boolean isEnabled = false;
    private boolean isLoading = false;
    private boolean updatingSwitch = false;
    private boolean hasRequestedPhone = false;
    private boolean hasRequestedState = false;
    private boolean hasEntered = false;

    private int subscriptionId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;

    private ActivityResultLauncher<String> phonePermissionLauncher;
    private ActivityResultLauncher<String> statePermissionLauncher;

    private Animation pulseAnimation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_call_handling);

        if (savedInstanceState != null) {
            hasRequestedPhone =
                    savedInstanceState.getBoolean("requested_phone");

            hasRequestedState =
                    savedInstanceState.getBoolean("requested_state");
        }

        initializeViews();
        initializePermissionLaunchers();
        initializeListeners();
        observeForwardingState();

        setupSimInformation();
        setupEntryAnimations();

        refreshPermissionCards();
    }

    private void initializeViews() {

        forwardingState = ForwardingState.get(this);

        switchForwarding = findViewById(R.id.switchForwarding);

        tvStatus = findViewById(R.id.tvStatus);
        tvStatusChip = findViewById(R.id.tvStatusChip);
        tvTarget = findViewById(R.id.tvTarget);
        tvSim = findViewById(R.id.tvSim);
        tvLastChecked = findViewById(R.id.tvLastChecked);

        cardPermPhone = findViewById(R.id.cardPermPhone);
        cardPermState = findViewById(R.id.cardPermState);
        cardHero = findViewById(R.id.cardHero);

        btnGrantPhone = findViewById(R.id.btnGrantPhone);
        btnGrantState = findViewById(R.id.btnGrantState);

        progress = findViewById(R.id.progress);

        pulseRing = findViewById(R.id.pulseRing);
        iconCircle = findViewById(R.id.iconCircle);
        ivStatusIcon = findViewById(R.id.ivStatusIcon);

        rowHow = findViewById(R.id.rowHow);
        tvHowDetails = findViewById(R.id.tvHowDetails);
        ivHowArrow = findViewById(R.id.ivHowArrow);

        pulseAnimation = AnimationUtils.loadAnimation(
                this,
                R.anim.pulse
        );

        tvTarget.setText("—");
    }

    private void initializePermissionLaunchers() {

        phonePermissionLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.RequestPermission(),
                        granted -> {

                            hasRequestedPhone = true;

                            refreshPermissionCards();

                            if (granted) {
                                showMessage("Phone permission granted");
                            }

                        }
                );

        statePermissionLauncher =
                registerForActivityResult(
                        new ActivityResultContracts.RequestPermission(),
                        granted -> {

                            hasRequestedState = true;

                            refreshPermissionCards();
                            setupSimInformation();

                            if (granted) {
                                showMessage("Phone state permission granted");
                            }

                        }
                );
    }

    private void initializeListeners() {

        switchForwarding.setOnCheckedChangeListener(
                (buttonView, checked) -> {

                    if (updatingSwitch || isLoading) {
                        return;
                    }

                    if (!checked) {
                        confirmDisableForwarding();
                    } else {
                        setSwitchState(isEnabled);
                    }
                }
        );

        btnGrantPhone.setOnClickListener(v -> requestPhonePermission());

        btnGrantState.setOnClickListener(v -> requestStatePermission());

        findViewById(R.id.btnRefresh).setOnClickListener(v -> refreshStatus());

        findViewById(R.id.btnCopy).setOnClickListener(v -> copyNumber());


        rowHow.setOnClickListener(v -> toggleHowItWorks());
    }
    private void observeForwardingState() {

        forwardingState.isEnabled().observe(this, enabled -> {

            if (enabled == null) return;

            isEnabled = enabled;

            updateStatusUI(enabled);
            setSwitchState(enabled);

        });

        forwardingState.getTarget().observe(this, number -> {

            if (number == null || number.trim().isEmpty()) {
                tvTarget.setText("—");
            } else {
                tvTarget.setText(number);
            }

        });
    }

    private void updateStatusUI(boolean enabled) {

        int oldColor = enabled
                ? Color.parseColor("#71717A")
                : Color.parseColor("#22C55E");

        int newColor = enabled
                ? Color.parseColor("#22C55E")
                : Color.parseColor("#71717A");

        if (!isReduceMotionEnabled()) {

            ObjectAnimator colorAnimator = ObjectAnimator.ofObject(
                    tvStatus,
                    "textColor",
                    new ArgbEvaluator(),
                    oldColor,
                    newColor
            );

            colorAnimator.setDuration(350);
            colorAnimator.start();
        }

        tvStatus.setText(
                enabled
                        ? R.string.status_on
                        : R.string.status_off
        );

        tvStatusChip.setText(
                enabled
                        ? R.string.active
                        : R.string.inactive
        );

        tvStatusChip.setTextColor(
                ContextCompat.getColor(
                        this,
                        enabled
                                ? R.color.status_green
                                : R.color.text_secondary
                )
        );

        tvStatusChip.setBackgroundResource(
                enabled
                        ? R.drawable.bg_chip_active
                        : R.drawable.bg_chip_inactive
        );

        GradientDrawable dot = new GradientDrawable();

        dot.setShape(GradientDrawable.OVAL);

        dot.setColor(
                ContextCompat.getColor(
                        this,
                        enabled
                                ? R.color.status_green
                                : R.color.inactive_gray
                )
        );

        dot.setSize(dp(7), dp(7));

        tvStatusChip.setCompoundDrawablesRelative(
                dot, null, null, null
        );

        ivStatusIcon.setImageAlpha(enabled ? 255 : 100);

        iconCircle.setAlpha(enabled ? 1f : 0.65f);

        if (enabled && !isReduceMotionEnabled()) {

            startPulse();

        } else {

            stopPulse();

        }

    }

    private void setSwitchState(boolean checked) {

        updatingSwitch = true;

        switchForwarding.setChecked(checked);

        updatingSwitch = false;
    }

    private void startPulse() {

        if (isReduceMotionEnabled()) return;

        pulseRing.setVisibility(View.VISIBLE);

        pulseRing.clearAnimation();

        pulseAnimation.setRepeatCount(Animation.INFINITE);

        pulseRing.startAnimation(pulseAnimation);
    }

    private void stopPulse() {

        pulseRing.clearAnimation();

        pulseRing.setVisibility(View.GONE);
    }

    private boolean isReduceMotionEnabled() {

        AccessibilityManager manager =
                (AccessibilityManager) getSystemService(
                        Context.ACCESSIBILITY_SERVICE
                );

        return manager != null &&
                !manager.isEnabled() ? false :
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                        android.provider.Settings.Global.getFloat(
                                getContentResolver(),
                                "animator_duration_scale",
                                1f
                        ) == 0f;
    }

    private void confirmDisableForwarding() {

        setSwitchState(true);

        if (!hasRequiredPermissions()) {

            showMessage(getString(R.string.permission_missing));
            refreshPermissionCards();

            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_disable_title)
                .setMessage(R.string.confirm_disable_message)
                .setNegativeButton(R.string.cancel, (dialog, which) -> {

                    setSwitchState(true);

                })
                .setPositiveButton(R.string.turn_off, (dialog, which) -> {

                    disableForwarding();

                })
                .setOnCancelListener(dialog -> setSwitchState(true))
                .show();
    }

    private void disableForwarding() {

        if (isLoading) return;

        if (!hasRequiredPermissions()) {
            showMessage(getString(R.string.permission_missing));
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

            showMessage("No active SIM subscription found");

            return;
        }

        subscriptionId = subId;

        runUssd(
                subscriptionId,
                "##21#",
                (success, response) -> {

                    runOnUiThread(() -> {

                        isLoading = false;

                        setLoading(false);

                        if (success) {

                            ForwardingState.get(this).set(false, "");

                            showMessage(getString(R.string.disable_success));

                            // Give LiveData observers time to update.
                            switchForwarding.post(() -> {

                                Intent intent = new Intent(
                                        CallHandling.this,
                                        MainActivity.class
                                );

                                intent.addFlags(
                                        Intent.FLAG_ACTIVITY_CLEAR_TOP |
                                                Intent.FLAG_ACTIVITY_SINGLE_TOP
                                );

                                startActivity(intent);

                                finish();

                            });

                        } else {

                            setSwitchState(true);

                            showMessage(
                                    response != null && !response.isEmpty()
                                            ? response
                                            : getString(R.string.disable_failed)
                            );

                        }

                    });

                }
        );
    }

    private void setLoading(boolean loading) {

        progress.setVisibility(
                loading ? View.VISIBLE : View.GONE
        );

        switchForwarding.setEnabled(!loading);


        findViewById(R.id.btnRefresh).setEnabled(!loading);

        cardHero.setAlpha(loading ? 0.85f : 1f);
    }

    // ---------------------------------------------------------
    // Refresh forwarding status
    // ---------------------------------------------------------

    private void refreshStatus() {

        if (isLoading) return;

        if (!hasRequiredPermissions()) {

            showMessage(getString(R.string.permission_missing));

            refreshPermissionCards();

            return;
        }

        int subId = getSelectedSubscriptionId();

        if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {

            showMessage("No active SIM subscription found");

            return;
        }

        progress.setVisibility(View.VISIBLE);

        checkForwarding(subId, (success, enabled, number) -> {

            runOnUiThread(() -> {

                progress.setVisibility(View.GONE);

                updateLastChecked();

                if (success) {

                    forwardingState.set(enabled, number != null ? number : "");

                    showMessage(getString(R.string.refresh_success));

                } else {

                    showMessage(getString(R.string.refresh_failed));

                }

            });

        });
    }

    private void updateLastChecked() {

        String time = new SimpleDateFormat(
                "hh:mm a",
                Locale.getDefault()
        ).format(new Date());

        tvLastChecked.setText(time);
    }

    @Override
    protected void onResume() {
        super.onResume();

        refreshPermissionCards();

        if (hasRequiredPermissions()) {
            setupSimInformation();
        }
    }

    private boolean hasRequiredPermissions() {

        return ContextCompat.checkSelfPermission(
                this,
                PHONE_PERMISSION
        ) == PackageManager.PERMISSION_GRANTED
                &&
                ContextCompat.checkSelfPermission(
                        this,
                        STATE_PERMISSION
                ) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPhonePermission() {

        if (ContextCompat.checkSelfPermission(
                this,
                PHONE_PERMISSION
        ) == PackageManager.PERMISSION_GRANTED) {

            refreshPermissionCards();

            return;
        }

        if (hasRequestedPhone &&
                !shouldShowRequestPermissionRationale(PHONE_PERMISSION)) {

            openAppSettings();

            return;
        }

        hasRequestedPhone = true;

        phonePermissionLauncher.launch(PHONE_PERMISSION);
    }

    private void requestStatePermission() {

        if (ContextCompat.checkSelfPermission(
                this,
                STATE_PERMISSION
        ) == PackageManager.PERMISSION_GRANTED) {

            refreshPermissionCards();

            return;
        }

        if (hasRequestedState &&
                !shouldShowRequestPermissionRationale(STATE_PERMISSION)) {

            openAppSettings();

            return;
        }

        hasRequestedState = true;

        statePermissionLauncher.launch(STATE_PERMISSION);
    }

    private void refreshPermissionCards() {

        boolean phoneMissing =
                ContextCompat.checkSelfPermission(
                        this,
                        PHONE_PERMISSION
                ) != PackageManager.PERMISSION_GRANTED;

        boolean stateMissing =
                ContextCompat.checkSelfPermission(
                        this,
                        STATE_PERMISSION
                ) != PackageManager.PERMISSION_GRANTED;

        updatePermissionCard(
                cardPermPhone,
                btnGrantPhone,
                phoneMissing,
                hasRequestedPhone &&
                        !shouldShowRequestPermissionRationale(PHONE_PERMISSION)
        );

        updatePermissionCard(
                cardPermState,
                btnGrantState,
                stateMissing,
                hasRequestedState &&
                        !shouldShowRequestPermissionRationale(STATE_PERMISSION)
        );
    }

    private void updatePermissionCard(
            MaterialCardView card,
            MaterialButton button,
            boolean missing,
            boolean permanentlyDenied
    ) {

        if (missing) {

            if (card.getVisibility() != View.VISIBLE) {

                card.setVisibility(View.VISIBLE);

                animateEnter(card);

            }

            button.setText(
                    permanentlyDenied
                            ? R.string.open_settings
                            : R.string.grant
            );

            button.setOnClickListener(v -> {

                if (card == cardPermPhone) {

                    if (permanentlyDenied) {
                        openAppSettings();
                    } else {
                        requestPhonePermission();
                    }

                } else {

                    if (permanentlyDenied) {
                        openAppSettings();
                    } else {
                        requestStatePermission();
                    }

                }

            });

        } else if (card.getVisibility() == View.VISIBLE) {

            if (isReduceMotionEnabled()) {

                card.setVisibility(View.GONE);

            } else {

                Animation exit = AnimationUtils.loadAnimation(
                        this,
                        R.anim.item_exit
                );

                exit.setAnimationListener(
                        new Animation.AnimationListener() {

                            @Override
                            public void onAnimationStart(Animation animation) {}

                            @Override
                            public void onAnimationEnd(Animation animation) {

                                card.setVisibility(View.GONE);

                            }

                            @Override
                            public void onAnimationRepeat(Animation animation) {}

                        }
                );

                card.startAnimation(exit);
            }

        }
    }

    private void openAppSettings() {

        Intent intent = new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null)
        );

        startActivity(intent);
    }

    private void setupSimInformation() {

        if (ContextCompat.checkSelfPermission(
                this,
                STATE_PERMISSION
        ) != PackageManager.PERMISSION_GRANTED) {

            tvSim.setText(R.string.sim_not_available);

            return;
        }

        try {

            SubscriptionManager manager =
                    (SubscriptionManager) getSystemService(
                            Context.TELEPHONY_SUBSCRIPTION_SERVICE
                    );

            if (manager == null) return;

            List<SubscriptionInfo> subscriptions =
                    manager.getActiveSubscriptionInfoList();

            if (subscriptions == null || subscriptions.isEmpty()) {

                tvSim.setText(R.string.sim_not_available);

                return;
            }

            int selectedId = getSelectedSubscriptionId();

            for (SubscriptionInfo info : subscriptions) {

                if (info.getSubscriptionId() == selectedId) {

                    String simName = info.getDisplayName().toString();

                    int slot = info.getSimSlotIndex() + 1;

                    tvSim.setText(
                            "SIM " + slot + " · " + simName
                    );

                    return;
                }
            }

            tvSim.setText(R.string.sim_not_available);

        } catch (SecurityException exception) {

            tvSim.setText(R.string.sim_not_available);

        }
    }

    private int getSelectedSubscriptionId() {

        if (ContextCompat.checkSelfPermission(
                this,
                STATE_PERMISSION
        ) != PackageManager.PERMISSION_GRANTED) {

            return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }

        SubscriptionManager manager =
                (SubscriptionManager) getSystemService(
                        Context.TELEPHONY_SUBSCRIPTION_SERVICE
                );

        if (manager == null) {
            return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        }

        try {

            int defaultId =
                    SubscriptionManager.getDefaultVoiceSubscriptionId();

            if (defaultId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                return defaultId;
            }

            List<SubscriptionInfo> list =
                    manager.getActiveSubscriptionInfoList();

            if (list != null && !list.isEmpty()) {
                return list.get(0).getSubscriptionId();
            }

        } catch (SecurityException ignored) {
        }

        return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }
    private void copyNumber() {

        String number = tvTarget.getText().toString();

        if (number.equals("—") || number.trim().isEmpty()) {

            showMessage(getString(R.string.no_number));

            return;
        }

        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(
                        CLIPBOARD_SERVICE
                );

        if (clipboard != null) {

            clipboard.setPrimaryClip(
                    ClipData.newPlainText("Forwarding number", number)
            );

            showMessage(getString(R.string.number_copied));
        }
    }

    private void showChangeNumberDialog() {

        EditText input = new EditText(this);

        input.setSingleLine(true);

        input.setInputType(
                android.text.InputType.TYPE_CLASS_PHONE
        );

        input.setHint(R.string.number_hint);

        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.GRAY);

        input.setPadding(
                dp(14), dp(14), dp(14), dp(14)
        );

        input.setText(tvTarget.getText().toString().equals("—")
                ? ""
                : tvTarget.getText().toString());

        new AlertDialog.Builder(this)
                .setTitle(R.string.change_number_title)
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save, (dialog, which) -> {

                    String number = input.getText().toString().trim();

                    if (number.replaceAll("[^0-9]", "").length() < 7) {

                        showMessage(getString(R.string.invalid_number));

                        return;
                    }

                    onChangeForwardingNumber(number);

                })
                .show();
    }

    private void toggleHowItWorks() {

        boolean expanded =
                tvHowDetails.getVisibility() == View.VISIBLE;

        tvHowDetails.setVisibility(
                expanded ? View.GONE : View.VISIBLE
        );

        ivHowArrow.animate()
                .rotation(expanded ? 0 : 180)
                .setDuration(isReduceMotionEnabled() ? 0 : 200)
                .start();
    }

    private void setupEntryAnimations() {

        if (isReduceMotionEnabled()) return;

        View[] views = {
                findViewById(R.id.header),
                cardPermPhone,
                cardPermState,
                cardHero,
                findViewById(R.id.cardControl),
                findViewById(R.id.cardTest),
                findViewById(R.id.cardHow)
        };

        for (int i = 0; i < views.length; i++) {

            View view = views[i];

            if (view == null) continue;

            if (view.getVisibility() == View.GONE) continue;

            view.setAlpha(0f);
            view.setTranslationY(dp(12));

            view.animate()
                    .alpha(1f)
                    .translationY(0)
                    .setStartDelay(i * 65L)
                    .setDuration(350)
                    .start();
        }

        hasEntered = true;
    }

    private void animateEnter(View view) {

        if (isReduceMotionEnabled()) {

            view.setAlpha(1f);
            view.setTranslationY(0);

            return;
        }

        view.setAlpha(0f);
        view.setTranslationY(dp(12));

        view.animate()
                .alpha(1f)
                .translationY(0)
                .setDuration(280)
                .start();
    }

    private int dp(float value) {

        return (int) (
                value * getResources()
                        .getDisplayMetrics()
                        .density + 0.5f
        );
    }

    private void showMessage(String message) {

        Snackbar.make(
                        findViewById(R.id.main),
                        message,
                        Snackbar.LENGTH_LONG
                ).setBackgroundTint(
                        ContextCompat.getColor(this, R.color.card_surface)
                ).setTextColor(Color.WHITE)
                .show();
    }

    public interface UssdCallback {

        void onResult(boolean success, String response);
    }

    public interface ForwardingCheckCallback {

        void onResult(
                boolean success,
                boolean enabled,
                String number
        );
    }

    protected void runUssd(
            int subId,
            String code,
            UssdCallback callback
    ) {

        // Replace with your existing runUssd(subId, code, callback).
        callback.onResult(
                false,
                "Connect your existing USSD implementation."
        );
    }

    protected void checkForwarding(
            int subId,
            ForwardingCheckCallback callback
    ) {

        // Replace with your existing checkForwarding implementation.
        callback.onResult(false, false, "");
    }

    protected void onChangeForwardingNumber(String number) {

        showMessage(
                "Connect your carrier activation flow to change the number."
        );
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {

        outState.putBoolean("requested_phone", hasRequestedPhone);

        outState.putBoolean("requested_state", hasRequestedState);

        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {

        stopPulse();

        super.onDestroy();
    }
}
