package com.example.freefood.Auth.Register;

import com.example.freefood.Main.AuthManager;

/**
 * Presenter for register: uses AuthManager under the hood.
 */
public class RegisterPresenter {
    private final AuthManager auth;

    public interface RegisterView {
        void onRegisterSuccess();
        void onRegisterFailure(String message);
    }

    public RegisterPresenter(AuthManager authManager) {
        this.auth = authManager;
    }

    public void registerUser(String username, String password, RegisterView view) {
        auth.register(username, password, new AuthManager.Callback() {
            @Override public void onSuccess() {
                view.onRegisterSuccess();
            }
            @Override public void onError(String message) {
                view.onRegisterFailure(message);
            }
        });
    }
}
