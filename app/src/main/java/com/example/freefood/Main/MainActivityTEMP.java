package com.example.freefood.Main;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.StrictMode;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RatingBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.freefood.R;
import com.example.freefood.Auth.Login.LoginActivity;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import java.util.Map;

public class MainActivityTEMP extends AppCompatActivity {

    // UI Components
    private Spinner searchOptionsSpinner;
    private EditText searchInputEditText;
    private Button searchButton;
    private TextView resultTextView;
    private EditText storeNameEditText;
    private EditText productNameEditText;
    private EditText quantityEditText;
    private Button purchaseButton;
    private EditText radiusEditText;
    private Button updateRadiusButton;

    // Location
    private FusedLocationProviderClient fusedLocationClient;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    // Customer Client
    private CustomerClient customerClient;
    public static final String SERVER_HOST = "10.0.2.2";
    public static final int SERVER_PORT = 12345;

    // Auth
    private AuthManager authManager;
    private static final String PREFS_NAME = "auth";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1) Initialize auth and restore any existing session
        authManager = new AuthManager(this);
        authManager.restoreSession();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String token = prefs.getString("token", null);
        if (token == null) {
            // No valid session → force login
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        // Inject into NetworkTask so all calls include the token
        NetworkTask.setSessionToken(token);

        // 2) Allow network on main thread temporarily
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder()
                .permitAll()
                .build();
        StrictMode.setThreadPolicy(policy);

        setContentView(R.layout.activity_main);

        initializeUI();

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        requestLocationPermission();
        getCurrentLocation();
    }

    private void initializeUI() {
        searchOptionsSpinner = findViewById(R.id.search_options_spinner);
        searchInputEditText = findViewById(R.id.search_input_edit_text);
        searchButton = findViewById(R.id.search_button);
        resultTextView = findViewById(R.id.result_text_view);
        storeNameEditText = findViewById(R.id.store_name_edit_text);
        productNameEditText = findViewById(R.id.product_name_edit_text);
        quantityEditText = findViewById(R.id.quantity_edit_text);
        purchaseButton = findViewById(R.id.purchase_button);
        radiusEditText = findViewById(R.id.radius_edit_text);
        updateRadiusButton = findViewById(R.id.update_radius_button);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.search_options, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        searchOptionsSpinner.setAdapter(adapter);

        searchOptionsSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateSearchHint(position);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });

        searchButton.setOnClickListener(v -> performSearch());

        purchaseButton.setOnClickListener(v -> purchaseProduct());

        updateRadiusButton.setOnClickListener(v -> updateSearchRadius());
    }

    private void updateSearchHint(int searchType) {
        switch (searchType) {
            case 0:
                searchInputEditText.setHint("Enter food category (e.g., pizzeria)");
                break;
            case 1:
                searchInputEditText.setHint("Enter star rating (1–5)");
                break;
            case 2:
                searchInputEditText.setHint("Enter average price (1–3)");
                break;
            case 3:
                searchInputEditText.setHint("Using current radius: " +
                        (customerClient != null ? customerClient.getRadius() : "5") + " km");
                break;
        }
    }

    private void performSearch() {
        if (customerClient == null) {
            Toast.makeText(this, "Client not ready", Toast.LENGTH_SHORT).show();
            return;
        }
        int type = searchOptionsSpinner.getSelectedItemPosition();
        String query = searchInputEditText.getText().toString().trim();
        String cmd = "SEARCH", data = "";

        switch (type) {
            case 0:
                if (query.isEmpty()) { Toast.makeText(this, "Enter category", Toast.LENGTH_SHORT).show(); return; }
                data = "FoodCategory=" + query;
                break;
            case 1:
                try {
                    int stars = Integer.parseInt(query);
                    if (stars<1 || stars>5) { Toast.makeText(this, "Stars 1–5", Toast.LENGTH_SHORT).show(); return; }
                    data = "Stars=" + stars;
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Enter valid rating", Toast.LENGTH_SHORT).show(); return;
                }
                break;
            case 2:
                try {
                    int price = Integer.parseInt(query);
                    data = "PriceTier=" + price;
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Enter valid price tier", Toast.LENGTH_SHORT).show(); return;
                }
                break;
            case 3:
                String msg = customerClient.getRadius() + "," +
                        customerClient.getLongitude() + "," +
                        customerClient.getLatitude();
                data = "Radius=" + msg;
                break;
        }

        resultTextView.setText("Searching...");
        new NetworkTask(SERVER_HOST, SERVER_PORT, result -> {
            String pretty = formatJsonResponse(result);
            runOnUiThread(() -> resultTextView.setText(pretty));
        }).execute(cmd, data);
    }

    private void purchaseProduct() {
        if (customerClient == null) {
            Toast.makeText(this, "Client not ready", Toast.LENGTH_SHORT).show();
            return;
        }
        String store = storeNameEditText.getText().toString().trim();
        String prod  = productNameEditText.getText().toString().trim();
        String qty   = quantityEditText.getText().toString().trim();
        if (store.isEmpty() || prod.isEmpty() || qty.isEmpty()) {
            Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Integer.parseInt(qty);
            resultTextView.setText("Processing purchase...");
            new NetworkTask(SERVER_HOST, SERVER_PORT, result -> {
                String pretty = formatJsonResponse(result);
                runOnUiThread(() -> {
                    resultTextView.setText(pretty);
                    if (result.contains("Successfully")) promptForReview(store);
                });
            }).execute("PURCHASE_PRODUCT", store + "|" + prod + "|" + qty);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Quantity must be numeric", Toast.LENGTH_SHORT).show();
        }
    }

    private void promptForReview(String storeName) {
        Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_rating);
        dialog.setTitle("Rate Your Experience");
        RatingBar ratingBar = dialog.findViewById(R.id.rating_bar);
        Button submit = dialog.findViewById(R.id.submit_button);

        submit.setOnClickListener(v -> {
            int rating = Math.max(1, Math.round(ratingBar.getRating()));
            new NetworkTask(SERVER_HOST, SERVER_PORT, res -> {
                String pretty = formatJsonResponse(res);
                runOnUiThread(() -> resultTextView.append("\n\nReview:\n" + pretty));
            }).execute("REVIEW", storeName + "|" + rating);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void updateSearchRadius() {
        if (customerClient == null) {
            Toast.makeText(this, "Client not ready", Toast.LENGTH_SHORT).show();
            return;
        }
        String r = radiusEditText.getText().toString().trim();
        if (r.isEmpty()) { Toast.makeText(this, "Enter radius", Toast.LENGTH_SHORT).show(); return; }
        try {
            int rad = Integer.parseInt(r);
            if (rad <= 0) { Toast.makeText(this, "Radius > 0", Toast.LENGTH_SHORT).show(); return; }
            customerClient.setRadius(rad);
            Toast.makeText(this, "Radius set to " + rad + " km", Toast.LENGTH_SHORT).show();
            if (searchOptionsSpinner.getSelectedItemPosition() == 3) updateSearchHint(3);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Enter valid radius", Toast.LENGTH_SHORT).show();
        }
    }

    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] grants) {
        super.onRequestPermissionsResult(code, perms, grants);
        if (code == LOCATION_PERMISSION_REQUEST_CODE &&
                grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) {
            getCurrentLocation();
        } else {
            initializeCustomerClient(37.994124, 23.732089);
        }
    }

    private void getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, (Location loc) -> {
                        if (loc != null) {
                            initializeCustomerClient(loc.getLatitude(), loc.getLongitude());
                        } else {
                            initializeCustomerClient(37.994124, 23.732089);
                        }
                    });
        } else {
            initializeCustomerClient(37.994124, 23.732089);
        }
    }

    private void initializeCustomerClient(double lat, double lon) {
        customerClient = new CustomerClient(SERVER_HOST, SERVER_PORT, lat, lon);
        Toast.makeText(this,
                "Client at lat:" + lat + " lon:" + lon,
                Toast.LENGTH_SHORT).show();
        if (searchOptionsSpinner.getSelectedItemPosition() == 3) {
            updateSearchHint(3);
        }
    }

    /**
     * Pretty-print JSON responses or return raw string if invalid JSON.
     */
    private String formatJsonResponse(String jsonResponse) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            JsonObject obj = JsonParser.parseString(jsonResponse).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                try {
                    JsonElement nested = JsonParser.parseString(e.getValue().getAsString());
                    obj.add(e.getKey(), nested);
                } catch (Exception ignored) { }
            }
            return gson.toJson(obj);
        } catch (Exception ex) {
            return jsonResponse;
        }
    }
}
