package com.example.freefood.Main;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.freefood.Auth.Login.LoginActivity;
import com.example.freefood.Model.Store;
import com.example.freefood.R;
import com.example.freefood.StoreDetail.StoreDetailActivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.slider.Slider;
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
    private SwipeRefreshLayout swipeRefreshLayout;
    private FloatingActionButton fabFilter;
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
        rvStores = findViewById(R.id.rvStores);
        pbLoading = findViewById(R.id.pbLoading);
        swipeRefreshLayout = findViewById(R.id.swipeRefresh);
        fabFilter = findViewById(R.id.fabFilter);

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
        vm.getErrorMessage().observe(this, this::showError);
        vm.getIsLoading().observe(this, isLoading -> {
            if (isLoading) {
                pbLoading.setVisibility(View.VISIBLE);
            } else {
                pbLoading.setVisibility(View.GONE);
                swipeRefreshLayout.setRefreshing(false);
            }
        });

        // —— Setup swipe refresh ——
        swipeRefreshLayout.setOnRefreshListener(() -> {
            vm.loadStores(DEFAULT_LAT, DEFAULT_LON);
        });

        // —— Setup filter FAB ——
        fabFilter.setOnClickListener(v -> {
            showFilterDialog();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        // show loading spinner
        pbLoading.setVisibility(View.VISIBLE);

        // reload stores with default coordinates every time we resume
        vm.loadStores(DEFAULT_LAT, DEFAULT_LON);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_refresh) {
            pbLoading.setVisibility(View.VISIBLE);
            vm.loadStores(DEFAULT_LAT, DEFAULT_LON);
            return true;
        } else if (id == R.id.action_logout) {
            // Clear auth token
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            prefs.edit().remove("token").apply();

            // Return to login screen
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void showStores(List<Store> stores) {
        pbLoading.setVisibility(View.GONE);
        adapter.updateStores(stores);
    }

    private void showError(String errorMessage) {
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
    }

    private void showFilterDialog() {
        // Create dialog with filter options
        String[] filterOptions = {"Food Category", "Star Rating", "Price", "Distance"};

        new MaterialAlertDialogBuilder(this)
                .setTitle("Filter Stores")
                .setItems(filterOptions, (dialog, which) -> {
                    switch (which) {
                        case 0: // Food Category
                            showFoodCategoryFilter();
                            break;
                        case 1: // Star Rating
                            showStarRatingFilter();
                            break;
                        case 2: // Price
                            showPriceFilter();
                            break;
                        case 3: // Distance (Radius)
                            showRadiusFilter();
                            break;
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showFoodCategoryFilter() {
        // Example food categories - replace with your actual categories
        String[] foodCategories = {"pizzeria", "burger", "asian", "bakery", "coffee", "seafood","greek","desserts","mexican","vegan"};

        new MaterialAlertDialogBuilder(this)
                .setTitle("Select Food Category")
                .setItems(foodCategories, (dialog, which) -> {
                    String selectedCategory = foodCategories[which];
                    pbLoading.setVisibility(View.VISIBLE);
                    vm.searchByFoodCategory(selectedCategory);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showStarRatingFilter() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_star_rating, null);
        Slider ratingSlider = dialogView.findViewById(R.id.ratingSlider);
        TextView tvRatingValue = dialogView.findViewById(R.id.tvRatingValue);

        // Update text when slider value changes
        ratingSlider.addOnChangeListener((slider, value, fromUser) -> {
            int stars = (int) value;
            tvRatingValue.setText(stars + " stars");
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Minimum Star Rating")
                .setView(dialogView)
                .setPositiveButton("Apply", (dialogInterface, i) -> {
                    int stars = (int) ratingSlider.getValue();
                    pbLoading.setVisibility(View.VISIBLE);
                    vm.searchByStars(stars);
                })
                .setNegativeButton("Cancel", null)
                .create();

        dialog.show();
    }

    private void showPriceFilter() {
        String[] priceOptions = {"$", "$$", "$$$"};

        new MaterialAlertDialogBuilder(this)
                .setTitle("Select Price Level")
                .setItems(priceOptions, (dialog, which) -> {
                    // Price levels are 1-3
                    int priceLevel = which + 1;
                    pbLoading.setVisibility(View.VISIBLE);
                    vm.searchByAvgPrice(priceLevel);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showRadiusFilter() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_radius, null);
        Slider radiusSlider = dialogView.findViewById(R.id.radiusSlider);
        TextView tvRadiusValue = dialogView.findViewById(R.id.tvRadiusValue);

        // Set initial value to current radius
        radiusSlider.setValue(vm.getRadius());
        tvRadiusValue.setText(vm.getRadius() + " km");

        // Update text when slider value changes
        radiusSlider.addOnChangeListener((slider, value, fromUser) -> {
            int radius = (int) value;
            tvRadiusValue.setText(radius + " km");
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle("Search Radius (km)")
                .setView(dialogView)
                .setPositiveButton("Apply", (dialogInterface, i) -> {
                    int radius = (int) radiusSlider.getValue();
                    vm.setRadius(radius);
                    pbLoading.setVisibility(View.VISIBLE);
                    vm.searchByRadius();
                })
                .setNegativeButton("Cancel", null)
                .create();

        dialog.show();
    }
}