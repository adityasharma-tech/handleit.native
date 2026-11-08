package in.mraditya.handleit;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresPermission;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CALL_PHONE = 100;
    private static final int REQUEST_PHONE_STATE = 101;
    private LinearLayout sim1, sim2, activateBtn, simContainer;
    private MaterialCardView cardPermPhone, cardPermState;
    private MaterialButton btnGrantPhone, btnGrantState;
    private int selectedSubscriptionId = -1;
    private int selectedSimSlot = -1;
    private static final String FORWARD_PHONE_NUMBER = "08047290650";

    private List<SubscriptionInfo> simSubscriptions;

    private ProgressBar progress;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean hasRequestedPhone;
    private boolean hasRequestedState;
    private boolean simCardsLoaded;

    interface UssdResult { void done(boolean ok, String text); }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        progress = findViewById(R.id.progress);
        requestPermissions();
        initializePermissionViews();
        initializeSimViews();
        checkPhonePermission();
        if (ForwardingState.get(this).isEnabledNow()) {
            startActivity(new Intent(this, CallHandling.class));
            finish();
            return;
        }
        activateBtn.setOnClickListener(v -> {
            runUssd(selectedSubscriptionId, "##002#", (k, rtext) -> {
                if (k) {
                    runUssd(selectedSubscriptionId, "**67*" + FORWARD_PHONE_NUMBER + "#", (ok, text) -> {
                        ForwardingState.get(this).set(ok, selectedSimSlot);
                        if (ok) {
                            startActivity(new Intent(this, CallHandling.class));
                            finish();
                        } else {
                            Toast.makeText(this, "Forwarding did not activate", Toast.LENGTH_LONG);
                        }
                    });
                } else {
                    Log.d("ForwardingCheckErr", "Error: " + rtext);
                }
            });
        });
    }

    private void initializeSimViews() {
        sim1 = findViewById(R.id.sim1);
        sim2 = findViewById(R.id.sim2);
        simContainer = findViewById(R.id.simContainer);
        activateBtn = findViewById(R.id.activate_btn);

        sim1.setVisibility(View.GONE);
        sim2.setVisibility(View.GONE);

        sim1.setOnClickListener(v -> selectSim(0));
        sim2.setOnClickListener(v -> selectSim(1));
    }

    private void initializePermissionViews() {
        cardPermPhone = findViewById(R.id.cardPermPhone);
        cardPermState = findViewById(R.id.cardPermState);
        btnGrantPhone = findViewById(R.id.btnGrantPhone);
        btnGrantState = findViewById(R.id.btnGrantState);

        btnGrantPhone.setOnClickListener(v -> requestPhonePermission());
        btnGrantState.setOnClickListener(v -> requestStatePermission());
    }

    @RequiresPermission(Manifest.permission.CALL_PHONE)
    void runUssd(int subId, String code, UssdResult cb) {
        progress.setVisibility(View.VISIBLE);
        updateActivateButton();

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

    private void finish(UssdResult cb, boolean ok, String text) {
        progress.setVisibility(View.GONE);
        updateActivateButton();
        cb.done(ok, text);
    }

    @RequiresPermission(Manifest.permission.CALL_PHONE)
    void checkForwarding(int subId, String expectedNumber, java.util.function.Consumer<Boolean> out) {
        runUssd(subId, "*#67#", (ok, text) -> {
            if (!ok) {
                out.accept(false);
                return;
            }
            String digits = text.replaceAll("[^0-9]", "");
            String want = expectedNumber.replaceAll("[^0-9]", "");
            String t = text.toLowerCase();
            boolean on = digits.contains(want)
                    || (t.contains("forward") && !t.contains("not") && !t.contains("inactive"));
            out.accept(on);
        });
    }

    private void selectSim(int slot) {
        if (simSubscriptions == null) return;

        SubscriptionInfo selected = null;

        for (SubscriptionInfo info : simSubscriptions) {
            if (info.getSimSlotIndex() == slot) {
                selected = info;
                break;
            }
        }

        if (selected == null) return;

        selectedSubscriptionId = selected.getSubscriptionId();
        selectedSimSlot = slot;

        sim1.setBackgroundResource(
                slot == 0 ? R.drawable.bg_sim_selected : R.drawable.bg_sim_card
        );

        sim2.setBackgroundResource(
                slot == 1 ? R.drawable.bg_sim_selected : R.drawable.bg_sim_card
        );

        TextView buttonText = (TextView) activateBtn.getChildAt(0);
        buttonText.setText("Activate SIM " + (slot + 1));
        updateActivateButton();
    }

    private void listSimCards() {
        SubscriptionManager sm = (SubscriptionManager)
                getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_PHONE_STATE
        ) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        List<SubscriptionInfo> subs = sm.getActiveSubscriptionInfoList();

        if (subs == null || subs.isEmpty()) {
            sim1.setVisibility(View.GONE);
            sim2.setVisibility(View.GONE);
            simSubscriptions = null;
            selectedSubscriptionId = -1;
            selectedSimSlot = -1;
            updateActivateButton();
            Toast.makeText(this, "No active SIM cards found", Toast.LENGTH_SHORT).show();
            return;
        }

        simSubscriptions = subs;

        sim1.setVisibility(View.GONE);
        sim2.setVisibility(View.GONE);

        for (SubscriptionInfo info : subs) {
            int slot = info.getSimSlotIndex();
            int subscriptionId = info.getSubscriptionId();
            String carrier = String.valueOf(info.getCarrierName());
            String number = "";

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.READ_PHONE_NUMBERS
                ) == PackageManager.PERMISSION_GRANTED) {
                    number = sm.getPhoneNumber(subscriptionId);
                }
            } else {
                number = info.getNumber();
            }

            if (number == null || number.isEmpty()) {
                number = "Number unavailable";
            }

            if (slot == 0) {
                ImageView icon = findViewById(R.id.sim1_icon);
                icon.setImageResource(getSimIcon(carrier));
                sim1.setVisibility(View.VISIBLE);

                TextView name = findViewById(R.id.sim1_name);
                TextView phone = findViewById(R.id.sim1_number);

                name.setText("SIM 1: " + carrier);
                phone.setText(number);
            } else if (slot == 1) {
                ImageView icon = findViewById(R.id.sim2_icon);
                icon.setImageResource(getSimIcon(carrier));
                sim2.setVisibility(View.VISIBLE);

                TextView name = findViewById(R.id.sim2_name);
                TextView phone = findViewById(R.id.sim2_number);

                name.setText("SIM 2: " + carrier);
                phone.setText(number);
            }
        }

        if (selectedSubscriptionId == -1) {
            SubscriptionInfo first = null;

            for (SubscriptionInfo info : subs) {
                if (first == null || info.getSimSlotIndex() < first.getSimSlotIndex()) {
                    first = info;
                }
            }

            if (first != null) {
                selectSim(first.getSimSlotIndex());
            }
        } else {
            updateActivateButton();
        }
    }

    private void checkPhonePermission() {
        refreshPermissionCards();
        if (hasPermission(Manifest.permission.READ_PHONE_STATE)) {
            loadSimCards();
        }
    }

    private void requestPermissions(){
        if(hasPermission(Manifest.permission.CALL_PHONE) && hasPermission(Manifest.permission.READ_PHONE_NUMBERS) && hasPermission(Manifest.permission.READ_PHONE_STATE)){
            return;
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_NUMBERS, Manifest.permission.READ_PHONE_STATE},
                    REQUEST_CALL_PHONE
            );
        }
    }

    private void requestPhonePermission() {
        if (hasPermission(Manifest.permission.CALL_PHONE)) {
            refreshPermissionCards();
            return;
        }

        if (hasRequestedPhone && !shouldShowRequestPermissionRationale(Manifest.permission.CALL_PHONE)) {
            openAppSettings();
            return;
        }

        hasRequestedPhone = true;
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_NUMBERS},
                REQUEST_CALL_PHONE
        );
    }

    private void requestStatePermission() {
        if (hasPermission(Manifest.permission.READ_PHONE_STATE)) {
            refreshPermissionCards();
            return;
        }

        if (hasRequestedState && !shouldShowRequestPermissionRationale(Manifest.permission.READ_PHONE_STATE)) {
            openAppSettings();
            return;
        }

        hasRequestedState = true;
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.READ_PHONE_STATE},
                REQUEST_PHONE_STATE
        );
    }

    private void refreshPermissionCards() {
        boolean phoneMissing = !hasPermission(Manifest.permission.CALL_PHONE);
        boolean stateMissing = !hasPermission(Manifest.permission.READ_PHONE_STATE);
        boolean phoneNumberMissing = !hasPermission(Manifest.permission.READ_PHONE_NUMBERS);

        cardPermPhone.setVisibility(phoneMissing ? View.VISIBLE : View.GONE);
        cardPermState.setVisibility(stateMissing ? View.VISIBLE : View.GONE);
        simContainer.setVisibility(stateMissing ? View.GONE : View.VISIBLE);

        if (stateMissing) {
            sim1.setVisibility(View.GONE);
            sim2.setVisibility(View.GONE);
            simSubscriptions = null;
            selectedSubscriptionId = -1;
            selectedSimSlot = -1;
            simCardsLoaded = false;
        }

        btnGrantPhone.setText(
                hasRequestedPhone && !shouldShowRequestPermissionRationale(Manifest.permission.CALL_PHONE)
                        ? R.string.open_settings
                        : R.string.grant
        );
        btnGrantState.setText(
                hasRequestedState && !shouldShowRequestPermissionRationale(Manifest.permission.READ_PHONE_STATE)
                        ? R.string.open_settings
                        : R.string.grant
        );

        updateActivateButton();
    }

    private boolean hasPermission(String permission) {
        return ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private void updateActivateButton() {
        boolean ready = hasPermission(Manifest.permission.CALL_PHONE)
                && hasPermission(Manifest.permission.READ_PHONE_STATE)
                && selectedSubscriptionId != -1;
        boolean loading = progress != null && progress.getVisibility() == View.VISIBLE;
        boolean enabled = ready && !loading;

        activateBtn.setEnabled(enabled);
        activateBtn.setClickable(enabled);
        activateBtn.setAlpha(enabled ? 1f : 0.45f);
    }

    private void openAppSettings() {
        Intent intent = new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null)
        );
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPermissionCards();
        if (hasPermission(Manifest.permission.READ_PHONE_STATE) && !simCardsLoaded) {
            loadSimCards();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_CALL_PHONE) {
            refreshPermissionCards();
        } else if (requestCode == REQUEST_PHONE_STATE) {
            if (hasPermission(Manifest.permission.READ_PHONE_STATE)) {
                simCardsLoaded = false;
                refreshPermissionCards();
                loadSimCards();
            } else {
                refreshPermissionCards();
            }
        }
    }

    private void loadSimCards() {
        if (!hasPermission(Manifest.permission.READ_PHONE_STATE)) return;
        listSimCards();
        simCardsLoaded = true;
    }

    private int getSimIcon(String carrier) {
        carrier = carrier.toLowerCase();

        if (carrier.contains("jio")) {
            return R.drawable.jio;
        }

        if (carrier.contains("airtel")) {
            return R.drawable.airtel;
        }

        return R.drawable.other_sim;
    }
}
