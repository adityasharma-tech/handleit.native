package in.mraditya.handleit;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

public class ForwardingState {
    private static ForwardingState instance;
    private final SharedPreferences prefs;
    private final MutableLiveData<Boolean> enabled = new MutableLiveData<>();
    private final MutableLiveData<String> target = new MutableLiveData<>();

    private ForwardingState(Context ctx) {
        prefs = ctx.getApplicationContext().getSharedPreferences("fwd_state", Context.MODE_PRIVATE);
        enabled.setValue(prefs.getBoolean("enabled", false));
        target.setValue(prefs.getString("target", ""));
    }

    public static synchronized ForwardingState get(Context ctx) {
        if (instance == null) instance = new ForwardingState(ctx);
        return instance;
    }

    public LiveData<Boolean> isEnabled() { return enabled; }
    public LiveData<String> getTarget() { return target; }

    public boolean isEnabledNow() { return Boolean.TRUE.equals(enabled.getValue()); }

    public void set(boolean on, String number) {
        prefs.edit().putBoolean("enabled", on).putString("target", number).apply();
        enabled.postValue(on);
        target.postValue(number);
    }
}