package com.example.freefood.Auth.Register;

import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.freefood.Main.AuthManager;

/**
 * ViewModel for RegisterActivity. Implements the Presenter’s callback.
 */
public class RegisterViewModel extends ViewModel implements RegisterPresenter.RegisterView {
    private final RegisterPresenter presenter;
    private final MutableLiveData<Boolean> registerSuccess = new MutableLiveData<>();
    private final MutableLiveData<String> registerFailure = new MutableLiveData<>();

    public RegisterViewModel(Context appContext) {
        AuthManager auth = new AuthManager(appContext);
        presenter = new RegisterPresenter(auth);
    }

    public void registerUser(String username, String password) {
        presenter.registerUser(username, password, this);
    }

    public LiveData<Boolean> getRegisterSuccess() {
        return registerSuccess;
    }

    public LiveData<String> getRegisterFailure() {
        return registerFailure;
    }

    @Override
    public void onRegisterSuccess() {
        registerSuccess.postValue(true);
    }

    @Override
    public void onRegisterFailure(String message) {
        registerFailure.postValue(message);
    }
}
