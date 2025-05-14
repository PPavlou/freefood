package com.example.freefood.Main;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.freefood.R;
import com.example.freefood.Auth.Login.LoginActivity;
import com.example.freefood.Model.Store;

import android.view.View;
import android.widget.ProgressBar;

import java.util.ArrayList;
import java.util.List;

public class MainMenuActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "auth";

    private RecyclerView rvStores;
    private ProgressBar pbLoading;
    private MainMenuViewModel vm;
    private AuthManager auth;
    private StoreAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_menu);

        // Toolbar
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

        // UI
        rvStores  = findViewById(R.id.rvStores);
        pbLoading = findViewById(R.id.pbLoading);

        rvStores.setLayoutManager(new LinearLayoutManager(this));
        adapter = new StoreAdapter(new ArrayList<>(), store -> {
            // TODO: open store detail
        });
        rvStores.setAdapter(adapter);  // attach empty adapter immediately

        // ViewModel
        vm = new ViewModelProvider(this).get(MainMenuViewModel.class);
        vm.getStores().observe(this, this::showStores);

        // Load
        pbLoading.setVisibility(View.VISIBLE);
        vm.loadStores();
    }

    private void showStores(List<Store> stores) {
        pbLoading.setVisibility(View.GONE);
        adapter.updateStores(stores);
    }
}
