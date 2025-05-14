package com.example.freefood.Auth.Login;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.freefood.Main.AuthManager;

/**
 * ViewModel for LoginActivity. Implements the Presenter’s callback.
 */
public class LoginViewModel extends ViewModel implements LoginPresenter.LoginView {
    private final LoginPresenter presenter;
    private final MutableLiveData<Boolean> loginSuccess = new MutableLiveData<>();
    private final MutableLiveData<String> loginFailure = new MutableLiveData<>();

    public LoginViewModel(Context appContext) {
        AuthManager auth = new AuthManager(appContext);
        auth.restoreSession();
        presenter = new LoginPresenter(auth);
    }

    public void loginUser(String username, String password) {
        presenter.loginUser(username, password, this);
    }

    public LiveData<Boolean> getLoginSuccess() {
        return loginSuccess;
    }

    public LiveData<String> getLoginFailure() {
        return loginFailure;
    }

    @Override
    public void onLoginSuccess() {
        loginSuccess.postValue(true);
    }

    @Override
    public void onLoginFailure(String message) {
        loginFailure.postValue(message);
    }
}
