package com.example.freefood.Auth.Register;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.freefood.R;
import com.example.freefood.Auth.Login.LoginActivity;

public class RegisterActivity extends ComponentActivity {
    private RegisterViewModel vm;
    private EditText etUser, etPass;
    private Button btnRegister;
    private TextView tvToLogin;  // ← change to TextView

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        RegisterViewModelFactory factory = new RegisterViewModelFactory(this);
        vm = new ViewModelProvider(this, factory).get(RegisterViewModel.class);

        etUser = findViewById(R.id.etUser);
        etPass = findViewById(R.id.etPass);
        btnRegister = findViewById(R.id.btnRegister);
        tvToLogin = findViewById(R.id.tvToLogin);

        vm.getRegisterSuccess().observe(this, success -> {
            if (success) {
                Toast.makeText(this, "Registered! Please log in.", Toast.LENGTH_LONG).show();
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            }
        });
        vm.getRegisterFailure().observe(this, error ->
                Toast.makeText(this, error, Toast.LENGTH_LONG).show()
        );

        btnRegister.setOnClickListener(v ->
                vm.registerUser(etUser.getText().toString().trim(),
                        etPass.getText().toString())
        );

        tvToLogin.setOnClickListener(v ->
                startActivity(new Intent(this, LoginActivity.class))
        );
    }
}
