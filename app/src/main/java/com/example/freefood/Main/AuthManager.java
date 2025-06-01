package com.example.freefood.Main;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.example.freefood.Main.NetworkTask;

/**
 * Manages authentication: login, register, session persistence, and logout.
 */
public class AuthManager {
    private static final String PREFS_NAME = "auth";
    private static final String KEY_TOKEN  = "token";

    public interface Callback {
        void onSuccess();
        void onError(String message);
    }

    private final SharedPreferences prefs;
    private final NetworkTask nt;
    private final Handler mainHandler;

    public AuthManager(Context ctx) {
        prefs       = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        nt          = new NetworkTask(MainMenuActivity.SERVER_HOST, MainMenuActivity.SERVER_PORT, null);
        mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Restores a saved session token (if any) into NetworkTask.
     */
    public void restoreSession() {
        String token = prefs.getString(KEY_TOKEN, null);
        if (token != null) {
            NetworkTask.setSessionToken(token);
        }
    }

    /**
     * Attempts to log in with given credentials.
     * On success saves token and invokes cb.onSuccess() on main thread.
     */
    public void login(String user, String pass, Callback cb) {
        new Thread(() -> {
            String resp = nt.sendCommand("LOGIN", user + "|" + pass);
            if (resp != null && resp.startsWith("LOGIN_SUCCESS|")) {
                String token = resp.split("\\|", 2)[1];
                NetworkTask.setSessionToken(token);
                prefs.edit().putString(KEY_TOKEN, token).apply();
                mainHandler.post(cb::onSuccess);
            } else {
                String err = (resp != null) ? resp : "Network error";
                mainHandler.post(() -> cb.onError(err));
            }
        }).start();
    }

    /**
     * Attempts to register a new user.
     * On success invokes cb.onSuccess() on main thread.
     */
    public void register(String user, String pass, Callback cb) {
        new Thread(() -> {
            String resp = nt.sendCommand("REGISTER", user + "|" + pass);
            if ("REGISTER_SUCCESS".equals(resp)) {
                mainHandler.post(cb::onSuccess);
            } else {
                String err = (resp != null) ? resp : "Network error";
                mainHandler.post(() -> cb.onError(err));
            }
        }).start();
    }

    /**
     * Clears session both in memory and in preferences.
     */
    public void logout() {
        NetworkTask.clearSessionToken();
        prefs.edit().remove(KEY_TOKEN).apply();
    }
}
