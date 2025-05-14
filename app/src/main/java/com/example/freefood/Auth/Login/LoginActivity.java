package com.example.freefood.Auth.Login;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.freefood.R;
import com.example.freefood.Auth.Register.RegisterActivity;
import com.example.freefood.Main.MainMenuActivity;

public class LoginActivity extends ComponentActivity {
    private LoginViewModel vm;
    private EditText etUser, etPass;
    private Button btnLogin;
    private TextView tvToRegister;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        LoginViewModelFactory factory = new LoginViewModelFactory(this);
        vm = new ViewModelProvider(this, factory).get(LoginViewModel.class);

        etUser = findViewById(R.id.etUser);
        etPass = findViewById(R.id.etPass);
        btnLogin = findViewById(R.id.btnLogin);
        tvToRegister = findViewById(R.id.tvToRegister);

        vm.getLoginSuccess().observe(this, success -> {
            if (success) {
                startActivity(new Intent(this, MainMenuActivity.class));
                finish();
            }
        });
        vm.getLoginFailure().observe(this, error ->
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
        );

        btnLogin.setOnClickListener(v ->
                vm.loginUser(etUser.getText().toString().trim(),
                        etPass.getText().toString())
        );

        tvToRegister.setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class))
        );
    }
}
