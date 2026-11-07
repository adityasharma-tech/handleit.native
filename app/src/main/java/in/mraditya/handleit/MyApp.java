package in.mraditya.handleit;

import android.app.Application;

public class MyApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        ForwardingState.get(this);
    }
}