package com.example.freefood.Main;

import android.location.Location;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.freefood.Model.Store;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class MainMenuViewModel extends ViewModel {
    private final MutableLiveData<List<Store>> stores = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> purchaseResult = new MutableLiveData<>();
    private final MutableLiveData<String> reviewResult = new MutableLiveData<>();

    private double userLat = 0;
    private double userLon = 0;
    private int radius = 5; // Default radius in kilometers

    // Expose LiveData so Activity can observe
    public LiveData<List<Store>> getStores() {
        return stores;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public LiveData<String> getPurchaseResult() {
        return purchaseResult;
    }

    public LiveData<String> getReviewResult() {
        return reviewResult;
    }

    /**
     * Sets user location coordinates
     *
     * @param lat User latitude
     * @param lon User longitude
     */
    public void setUserLocation(double lat, double lon) {
        this.userLat = lat;
        this.userLon = lon;
    }

    /**
     * Gets current user latitude
     */
    public double getUserLat() {
        return userLat;
    }

    /**
     * Gets current user longitude
     */
    public double getUserLon() {
        return userLon;
    }

    /**
     * Sets search radius in kilometers
     *
     * @param radius Search radius in kilometers
     */
    public void setRadius(int radius) {
        this.radius = radius;
    }

    /**
     * Gets current search radius in kilometers
     */
    public int getRadius() {
        return radius;
    }

    private void fetchStoresAndPublish(NetworkTask nt,String csv,String listResp,Gson gson,double userLat, double userLon) {

        // 2) build stubs
        String[] names = csv.split(",");
        List<Store> interim = new ArrayList<>(names.length);
        for (String raw : names) {
            Store s = new Store();
            s.setStoreName(raw.trim());
            interim.add(s);
        }

        // 3) fetch and unwrap STORE_DETAILS
        Type listOfString = new TypeToken<List<String>>() {}.getType();
        for (int i = 0; i < interim.size(); i++) {
            Store stub = interim.get(i);
            String detailResp = nt.sendCommand("STORE_DETAILS", stub.getStoreName());
            if (detailResp == null) continue;

            String jsonObjString;

            // if it looks like [ "...", ... ] ⇒ unwrap
            if (detailResp.trim().startsWith("[")) {
                try {
                    List<String> arr = gson.fromJson(detailResp, listOfString);
                    if (!arr.isEmpty()) {
                        jsonObjString = arr.get(0);
                    } else {
                        continue;
                    }
                } catch (Exception ex) {
                    Log.w("MainMenuVM", "Failed to unwrap STORE_DETAILS array for "
                            + stub.getStoreName(), ex);
                    continue;
                }
            } else {
                jsonObjString = detailResp;
            }

            // now parse that single JSON object
            try {
                Store full = gson.fromJson(jsonObjString, Store.class);

                // 4) compute distanceKm
                float[] results = new float[1];
                Location.distanceBetween(
                        userLat, userLon,
                        full.getLatitude(), full.getLongitude(),
                        results
                );
                double km = results[0] / 1000.0;
                // round to one decimal
                full.setDistanceKm(Math.round(km * 10) / 10.0);

                interim.set(i, full);

                // 5) fetch logo
                String urlResp = nt.sendCommand("GET_LOGO", full.getStoreName());

                try {
                    List<String> arr = gson.fromJson(urlResp, listOfString);
                    if (!arr.isEmpty()) {
                        full.setStoreLogo(arr.get(0));
                    }
                } catch (JsonSyntaxException e) {
                    Log.e("StoreFetch", "Invalid JSON for logo URL: " + urlResp, e);
                }

                interim.set(i, full);
            } catch (Exception ex) {
                Log.w("MainMenuVM", "Failed to parse STORE_DETAILS JSON for "
                        + stub.getStoreName(), ex);
            }
        }

        // 6) publish
        stores.postValue(interim);
        isLoading.postValue(false);
    }
    /**
     * Loads all stores and calculates distances from user location
     *
     * @param userLat User latitude
     * @param userLon User longitude
     */
    public void loadStores(double userLat, double userLon) {
        isLoading.postValue(true);
        setUserLocation(userLat, userLon);

        new Thread(() -> {
            NetworkTask nt = new NetworkTask(
                    MainActivityTEMP.SERVER_HOST,
                    MainActivityTEMP.SERVER_PORT,
                    null
            );
            Gson gson = new Gson();

            // 1) LIST_STORES
            String listResp = nt.sendCommand("LIST_STORES", "");
            if (listResp == null) {
                errorMessage.postValue("Failed to retrieve stores list");
                stores.postValue(Collections.emptyList());
                isLoading.postValue(false);
                return;
            }

            String csv;
            try {
                JsonObject root = JsonParser.parseString(listResp).getAsJsonObject();
                csv = root.get("LIST_STORES").getAsString();
            } catch (Exception e) {
                Log.e("MainMenuVM", "Failed to parse LIST_STORES", e);
                errorMessage.postValue("Failed to parse stores list");
                stores.postValue(Collections.emptyList());
                isLoading.postValue(false);
                return;
            }

            // 2) delegate into your helper
            fetchStoresAndPublish(nt, csv,listResp, gson, userLat, userLon);
        }).start();
    }


    /**
     * Search stores by food category
     *
     * @param category Food category to search for
     */
    public void searchByFoodCategory(String category) {
        isLoading.postValue(true);

        new Thread(() -> {
            NetworkTask nt = new NetworkTask(
                    MainActivityTEMP.SERVER_HOST,
                    MainActivityTEMP.SERVER_PORT,
                    null
            );
            Gson gson = new Gson();

            // send the SEARCH command
            String searchResponse = nt.sendCommand("SEARCH", "FoodCategory=" + category);
            if (searchResponse == null) {
                errorMessage.postValue("No response from server");
                stores.postValue(Collections.emptyList());
                isLoading.postValue(false);
                return;
            }

            // hand off to the same helper (it will attempt to parse "LIST_STORES" from that JSON)
            fetchAggregatedStores(nt, searchResponse, gson, getUserLat(), getUserLon());
        }).start();
    }

    /**
     * Search stores by star rating
     *
     * @param stars Star rating to search for (1-5)
     */
    public void searchByStars(int stars) {
        if (stars < 1 || stars > 5) {
            errorMessage.postValue("Star rating must be between 1 and 5");
            return;
        }
        isLoading.postValue(true);

        new Thread(() -> {
            NetworkTask nt = new NetworkTask(
                    MainActivityTEMP.SERVER_HOST,
                    MainActivityTEMP.SERVER_PORT,
                    null
            );
            Gson gson = new Gson();

            String searchResponse = nt.sendCommand("SEARCH", "Stars=" + stars);
            if (searchResponse == null) {
                errorMessage.postValue("No response from server");
                stores.postValue(Collections.emptyList());
                isLoading.postValue(false);
                return;
            }

            fetchAggregatedStores(nt, searchResponse, gson, getUserLat(), getUserLon());
        }).start();
    }

    /**
     * Search stores by average price
     *
     * @param avgPrice Average price level to search for (1-3)
     */
    public void searchByAvgPrice(int avgPrice) {
        if (avgPrice < 1 || avgPrice > 3) {
            errorMessage.postValue("Price level must be between 1 and 3");
            return;
        }
        isLoading.postValue(true);

        new Thread(() -> {
            NetworkTask nt = new NetworkTask(
                    MainActivityTEMP.SERVER_HOST,
                    MainActivityTEMP.SERVER_PORT,
                    null
            );
            Gson gson = new Gson();

            String searchResponse = nt.sendCommand("SEARCH", "AvgPrice=" + avgPrice);
            if (searchResponse == null) {
                errorMessage.postValue("No response from server");
                stores.postValue(Collections.emptyList());
                isLoading.postValue(false);
                return;
            }

            fetchAggregatedStores(nt, searchResponse, gson, getUserLat(), getUserLon());
        }).start();
    }

    /**
     * Search stores within radius of current location
     */
    public void searchByRadius() {
        isLoading.postValue(true);

        new Thread(() -> {
            NetworkTask nt = new NetworkTask(
                    MainActivityTEMP.SERVER_HOST,
                    MainActivityTEMP.SERVER_PORT,
                    null
            );
            Gson gson = new Gson();

            String messageForRadiusFilter = radius + "," + getUserLon() + "," + getUserLat();
            String searchResponse = nt.sendCommand("SEARCH", "Radius=" + messageForRadiusFilter);
            if (searchResponse == null) {
                errorMessage.postValue("No response from server");
                stores.postValue(Collections.emptyList());
                isLoading.postValue(false);
                return;
            }

            fetchAggregatedStores(nt, searchResponse, gson, getUserLat(), getUserLon());
        }).start();
    }

    private void fetchAggregatedStores(NetworkTask nt, String jsonResponse, Gson gson, double userLat, double userLon) {
        List<Store> storesList = new ArrayList<>();

        try {
            JsonObject root = JsonParser.parseString(jsonResponse).getAsJsonObject();

            Set<String> storeNames;

            // Prefer LIST_STORES key if it exists
            if (root.has("LIST_STORES")) {
                String csv = root.get("LIST_STORES").getAsString();
                storeNames = Arrays.stream(csv.split(","))
                        .map(String::trim)
                        .collect(Collectors.toSet());
            } else {
                // Otherwise just use all keys (e.g., for SEARCH response)
                storeNames = root.keySet();
            }

            for (String storeName : storeNames) {
                try {
                    String jsonString = root.get(storeName).getAsString(); // stringified JSON
                    Store store = gson.fromJson(jsonString, Store.class);

                    // Compute distance
                    float[] results = new float[1];
                    Location.distanceBetween(
                            userLat, userLon,
                            store.getLatitude(), store.getLongitude(),
                            results
                    );
                    double km = results[0] / 1000.0;
                    store.setDistanceKm(Math.round(km * 10) / 10.0);

                    storesList.add(store);
                } catch (Exception ex) {
                    Log.w("MainMenuVM", "Failed to parse store: " + storeName, ex);
                }
            }

            stores.postValue(storesList);
        } catch (Exception e) {
            Log.e("MainMenuVM", "Error parsing store list", e);
            errorMessage.postValue("Failed to parse store data.");
            stores.postValue(Collections.emptyList());
        } finally {
            isLoading.postValue(false);
        }
    }

    /**
     * Process search response from server and update stores LiveData
     *
     * @param jsonResponse JSON response from server
     */
    private void processSearchResponse(String jsonResponse) {
        if (jsonResponse == null || jsonResponse.isEmpty()) {
            errorMessage.postValue("No response from server");
            stores.postValue(Collections.emptyList());
            isLoading.postValue(false);
            return;
        }

        try {
            // Try to parse the search response and convert to Store objects
            Gson gson = new Gson();
            JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();

            // Expand any nested JSON strings
            for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                try {
                    JsonElement nested = JsonParser.parseString(entry.getValue().getAsString());
                    jsonObject.add(entry.getKey(), nested);
                } catch (Exception e) {
                    // If parsing fails, leave the entry as-is
                }
            }

            // Extract stores from the response and compute distances
            List<Store> searchResults = new ArrayList<>();
            // Processing logic depends on the exact response format
            // This is a simplified version that assumes stores are in the response

            // For demonstration, we'll assume the response contains a "stores" array
            // The exact implementation will depend on the actual server response format
            if (jsonObject.has("stores")) {
                Type storeListType = new TypeToken<List<Store>>() {}.getType();
                searchResults = gson.fromJson(jsonObject.get("stores"), storeListType);

                // Compute distances for each store
                for (Store store : searchResults) {
                    float[] results = new float[1];
                    Location.distanceBetween(
                            userLat, userLon,
                            store.getLatitude(), store.getLongitude(),
                            results
                    );
                    double km = results[0] / 1000.0;
                    store.setDistanceKm(Math.round(km * 10) / 10.0);
                }
            }

            stores.postValue(searchResults);
        } catch (Exception e) {
            Log.e("MainMenuVM", "Failed to parse search response", e);
            errorMessage.postValue("Failed to parse search results");
            stores.postValue(Collections.emptyList());
        } finally {
            isLoading.postValue(false);
        }
    }
}