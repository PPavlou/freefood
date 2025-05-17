package com.example.freefood.Main;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.freefood.Auth.Login.LoginActivity;
import com.example.freefood.Model.Store;
import com.example.freefood.R;
import com.example.freefood.StoreDetail.StoreDetailActivity;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

public class MainMenuActivity extends AppCompatActivity {
    private static final String PREFS_NAME   = "auth";
    // These coordinates must exactly match the ones your store JSON uses.
    private static final double DEFAULT_LAT  = 37.994124;
    private static final double DEFAULT_LON  = 23.732089;

    private RecyclerView    rvStores;
    private View            pbLoading;
    private MainMenuViewModel vm;
    private StoreAdapter    adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_menu);

        // —— toolbar ——
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("Available Stores");

        // —— auth check ——
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String token = prefs.getString("token", null);
        if (token == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        AuthManager auth = new AuthManager(this);
        auth.restoreSession();
        NetworkTask.setSessionToken(token);

        // —— UI wiring ——
        rvStores  = findViewById(R.id.rvStores);
        pbLoading = findViewById(R.id.pbLoading);

        rvStores.setLayoutManager(new LinearLayoutManager(this));
        adapter = new StoreAdapter(new ArrayList<>(), store -> {
            Intent i = new Intent(this, StoreDetailActivity.class);
            i.putExtra("STORE_JSON", new Gson().toJson(store));
            startActivity(i);
        });
        rvStores.setAdapter(adapter);

        // —— ViewModel & observer ——
        vm = new ViewModelProvider(this).get(MainMenuViewModel.class);
        vm.getStores().observe(this, this::showStores);

        // —— always use DEFAULT coords ——
        vm = new ViewModelProvider(this).get(MainMenuViewModel.class);
        vm.getStores().observe(this, this::showStores);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // show loading spinner
        pbLoading.setVisibility(View.VISIBLE);

        // reload stores with default coordinates every time we resume
        vm.loadStores(DEFAULT_LAT, DEFAULT_LON);
    }

    private void showStores(List<Store> stores) {
        pbLoading.setVisibility(View.GONE);
        adapter.updateStores(stores);
    }
}