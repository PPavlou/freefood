package com.example.freefood.Auth.Login;

import com.example.freefood.Main.AuthManager;

/**
 * Presenter for login: bridges AuthManager and the ViewModel.
 */
public class LoginPresenter {
    private final AuthManager auth;

    public interface LoginView {
        void onLoginSuccess();
        void onLoginFailure(String message);
    }

    public LoginPresenter(AuthManager authManager) {
        this.auth = authManager;
    }

    public void loginUser(String username, String password, LoginView view) {
        auth.login(username, password, new AuthManager.Callback() {
            @Override public void onSuccess() {
                view.onLoginSuccess();
            }
            @Override public void onError(String message) {
                view.onLoginFailure(message);
            }
        });
    }
}
