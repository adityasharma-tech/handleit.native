package in.mraditya.handleit;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

public class ForwardingState {
    private static ForwardingState instance;
    private final SharedPreferences prefs;
    private final MutableLiveData<Boolean> enabled = new MutableLiveData<>();
    private final MutableLiveData<Integer> slot = new MutableLiveData<>();

    private ForwardingState(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences("fwd_state", Context.MODE_PRIVATE);
        enabled.setValue(prefs.getBoolean("enabled", false));
        slot.setValue(prefs.getInt("slot", -1));
    }

    public static synchronized ForwardingState get(Context ctx) {
        if (instance == null) instance = new ForwardingState(ctx);
        return instance;
    }

    public LiveData<Boolean> isEnabled() { return enabled; }
    public LiveData<Integer> getSelectedSlot() { return slot; }

    public boolean isEnabledNow() { return Boolean.TRUE.equals(enabled.getValue()); }

    public void set(boolean on, Integer number) {
        prefs.edit().putBoolean("enabled", on).putInt("slot", number).apply();
        enabled.postValue(on);
        slot.postValue(number);
    }
}