package com.example.freefood;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Dialog;
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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.Map;

public class MainActivity extends AppCompatActivity {

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
    private static final String SERVER_HOST = "10.0.2.2";
    private static final int SERVER_PORT = 12345;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Allow network on main for now (you'll want to move this off main thread later)
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

        // Set up spinner item selection listener
        searchOptionsSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateSearchHint(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        // Set up button listeners
        searchButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performSearch();
            }
        });

        purchaseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                purchaseProduct();
            }
        });

        updateRadiusButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateSearchRadius();
            }
        });
    }

    private void updateSearchHint(int searchType) {
        switch (searchType) {
            case 0: // Food Category
                searchInputEditText.setHint("Enter food category (e.g., pizzeria)");
                break;
            case 1: // Star Rating
                searchInputEditText.setHint("Enter star rating (1-5)");
                break;
            case 2: // Average Price
                searchInputEditText.setHint("Enter average price (1-3)");
                break;
            case 3: // Radius Search
                searchInputEditText.setHint("Using current radius: " +
                        (customerClient != null ? customerClient.getRadius() : "5") + " km");
                break;
        }
    }

    private void performSearch() {
        if (customerClient == null) {
            Toast.makeText(this, "Customer client not initialized yet", Toast.LENGTH_SHORT).show();
            return;
        }

        int searchType = searchOptionsSpinner.getSelectedItemPosition();
        String query = searchInputEditText.getText().toString().trim();
        String command = "SEARCH";
        String data = "";

        switch (searchType) {
            case 0: // Food Category
                if (!query.isEmpty()) {
                    data = "FoodCategory=" + query;
                } else {
                    Toast.makeText(this, "Please enter a food category", Toast.LENGTH_SHORT).show();
                    return;
                }
                break;
            case 1: // Star Rating
                try {
                    int stars = Integer.parseInt(query);
                    if (stars < 1 || stars > 5) {
                        Toast.makeText(this, "Star rating must be between 1 and 5", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    data = "Stars=" + stars;
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Please enter a valid star rating", Toast.LENGTH_SHORT).show();
                    return;
                }
                break;
            case 2: // Average Price
                try {
                    int price = Integer.parseInt(query);
                    if (price < 1 || price > 3) {
                        Toast.makeText(this, "Price must be between 1 and 3", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    data = "AvgPrice=" + price;
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Please enter a valid price", Toast.LENGTH_SHORT).show();
                    return;
                }
                break;
            case 3: // Radius Search
                String messageForRadiusFilter = customerClient.getRadius() + "," +
                        customerClient.getLongitude() + "," + customerClient.getLatitude();
                data = "Radius=" + messageForRadiusFilter;
                break;
        }

        // Show progress indicator
        resultTextView.setText("Searching...");

        // Execute network task
        new NetworkTask(customerClient.getSERVER_HOST(), customerClient.getSERVER_PORT(),
                new NetworkTask.NetworkCallback() {
                    @Override
                    public void onNetworkTaskComplete(String result) {
                        // Format and display the response
                        String prettyResponse = formatJsonResponse(result);
                        resultTextView.setText(prettyResponse);
                    }
                }).execute(command, data);
    }

    /**
     * Parses a raw JSON response, expands nested JSON elements, and returns it
     * in pretty-printed format. If the response is not valid JSON, returns the
     * original string.
     *
     * @param jsonResponse the raw response from the server
     * @return the pretty-printed JSON or the original string if not valid JSON
     */
    private String formatJsonResponse(String jsonResponse) {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            // Parse the raw response string as a JSON object.
            JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
            // Iterate over entries to check if any value is a nested JSON string.
            for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                try {
                    // Attempt to parse the value; if it's a nested JSON, replace the string value.
                    JsonElement nested = JsonParser.parseString(entry.getValue().getAsString());
                    jsonObject.add(entry.getKey(), nested);
                } catch (Exception e) {
                    // If parsing fails, leave the entry as-is.
                }
            }
            return gson.toJson(jsonObject);
        } catch (Exception ex) {
            // If not a valid JSON, output the original response.
            return jsonResponse;
        }
    }

    private void purchaseProduct() {
        if (customerClient == null) {
            Toast.makeText(this, "Customer client not initialized yet", Toast.LENGTH_SHORT).show();
            return;
        }

        String storeName = storeNameEditText.getText().toString().trim();
        String productName = productNameEditText.getText().toString().trim();
        String quantity = quantityEditText.getText().toString().trim();

        if (storeName.isEmpty() || productName.isEmpty() || quantity.isEmpty()) {
            Toast.makeText(this, "Please fill in all purchase fields", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Integer.parseInt(quantity); // Just validate it's a number

            // Show progress indicator
            resultTextView.setText("Processing purchase...");

            // Execute network task for purchase
            new NetworkTask(customerClient.getSERVER_HOST(), customerClient.getSERVER_PORT(),
                    new NetworkTask.NetworkCallback() {
                        @Override
                        public void onNetworkTaskComplete(String result) {
                            String prettyResponse = formatJsonResponse(result);
                            resultTextView.setText(prettyResponse);

                            // If purchase was successful, prompt for review
                            if (result.contains("Successfully")) {
                                promptForReview(storeName);
                            }
                        }
                    }).execute("PURCHASE_PRODUCT", storeName + "|" + productName + "|" + quantity);

        } catch (NumberFormatException e) {
            Toast.makeText(this, "Quantity must be a number", Toast.LENGTH_SHORT).show();
        }
    }

    private void promptForReview(final String storeName) {
        // Create a dialog with a rating bar
        final Dialog dialog = new Dialog(this);
        dialog.setContentView(R.layout.dialog_rating);
        dialog.setTitle("Rate Your Experience");

        final RatingBar ratingBar = dialog.findViewById(R.id.rating_bar);
        Button submitButton = dialog.findViewById(R.id.submit_button);

        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int rating = Math.round(ratingBar.getRating());
                if (rating < 1) rating = 1;

                // Execute network task for review
                new NetworkTask(customerClient.getSERVER_HOST(), customerClient.getSERVER_PORT(),
                        new NetworkTask.NetworkCallback() {
                            @Override
                            public void onNetworkTaskComplete(String result) {
                                String prettyResponse = formatJsonResponse(result);
                                resultTextView.append("\n\nReview Response:\n" + prettyResponse);
                            }
                        }).execute("REVIEW", storeName + "|" + rating);

                dialog.dismiss();
            }
        });

        dialog.show();
    }

    private void updateSearchRadius() {
        if (customerClient == null) {
            Toast.makeText(this, "Customer client not initialized yet", Toast.LENGTH_SHORT).show();
            return;
        }

        String radiusStr = radiusEditText.getText().toString().trim();
        if (!radiusStr.isEmpty()) {
            try {
                int radius = Integer.parseInt(radiusStr);
                if (radius > 0) {
                    customerClient.setRadius(radius);
                    Toast.makeText(this, "Search radius updated to " + radius + " km",
                            Toast.LENGTH_SHORT).show();
                    // Update hint if radius search is selected
                    if (searchOptionsSpinner.getSelectedItemPosition() == 3) {
                        updateSearchHint(3);
                    }
                } else {
                    Toast.makeText(this, "Radius must be positive", Toast.LENGTH_SHORT).show();
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Please enter a valid radius", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "Please enter a radius value", Toast.LENGTH_SHORT).show();
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
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation();
            } else {
                // Use default location if permission denied
                initializeCustomerClient(37.994124, 23.732089);
            }
        }
    }

    private void getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                        @Override
                        public void onSuccess(Location location) {
                            // Got last known location. In some rare situations this can be null.
                            if (location != null) {
                                initializeCustomerClient(location.getLatitude(), location.getLongitude());
                            } else {
                                // Use default location if unable to get current location
                                initializeCustomerClient(37.994124, 23.732089);
                            }
                        }
                    });
        } else {
            // Use default location if no permission
            initializeCustomerClient(37.994124, 23.732089);
        }
    }

    private void initializeCustomerClient(double latitude, double longitude) {
        customerClient = new CustomerClient(SERVER_HOST, SERVER_PORT, latitude, longitude);
        Toast.makeText(this, "Client initialized at lat: " + latitude + ", long: " + longitude,
                Toast.LENGTH_SHORT).show();

        // Update the radius hint
        if (searchOptionsSpinner.getSelectedItemPosition() == 3) {
            updateSearchHint(3);
        }
    }
}