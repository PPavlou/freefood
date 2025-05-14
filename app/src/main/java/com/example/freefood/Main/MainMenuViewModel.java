package com.example.freefood.Main;

import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.freefood.Model.Store;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainMenuViewModel extends ViewModel {
    private final MutableLiveData<List<Store>> stores = new MutableLiveData<>();

    /** Expose LiveData so Activity can observe. */
    public LiveData<List<Store>> getStores() {
        return stores;
    }

    /**
     * 1) Get the CSV of names via LIST_STORES
     * 2) Split into names[], build minimal Store(name)
     * 3) For each name, call STORE_DETAILS and overwrite that Store with full JSON
     * 4) Post the final List<Store>
     */
    public void loadStores() {
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
                stores.postValue(Collections.emptyList());
                return;
            }

            String csv;
            try {
                JsonObject root = JsonParser.parseString(listResp).getAsJsonObject();
                csv = root.get("LIST_STORES").getAsString();
            } catch (Exception e) {
                Log.e("MainMenuVM", "Failed to parse LIST_STORES", e);
                stores.postValue(Collections.emptyList());
                return;
            }

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
                    full.setStoreName(stub.getStoreName());
                    interim.set(i, full);
                } catch (Exception ex) {
                    Log.w("MainMenuVM", "Failed to parse STORE_DETAILS JSON for "
                            + stub.getStoreName(), ex);
                }
            }

            // 4) publish
            stores.postValue(interim);
        }).start();
    }
}
