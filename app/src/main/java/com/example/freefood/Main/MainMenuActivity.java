package com.example.freefood.Main;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.freefood.Model.Store;
import com.example.freefood.R;
import com.example.freefood.Auth.Login.LoginActivity;
import com.example.freefood.Main.AuthManager;

import java.util.List;

public class MainMenuActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "auth";

    private RecyclerView rvStores;
    private ProgressBar pbLoading;
    private MainMenuViewModel vm;
    private AuthManager auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_menu);

        // Toolbar setup
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("Available Stores");

        // Auth check
        auth = new AuthManager(this);
        auth.restoreSession();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String token = prefs.getString("token", null);
        if (token == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        NetworkTask.setSessionToken(token);

        // UI references
        rvStores  = findViewById(R.id.rvStores);
        pbLoading = findViewById(R.id.pbLoading);

        rvStores.setLayoutManager(new LinearLayoutManager(this));

        // ViewModel binding
        vm = new ViewModelProvider(this).get(MainMenuViewModel.class);
        vm.getStores().observe(this, this::showStores);

        // Load data
        pbLoading.setVisibility(View.VISIBLE);
        vm.loadStores();
    }

    private void showStores(List<Store> storeList) {
        pbLoading.setVisibility(View.GONE);
        rvStores.setAdapter(new StoreAdapter(storeList, store -> {
            // TODO: navigate into store detail
        }));
    }
}
