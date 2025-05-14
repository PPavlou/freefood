package com.example.freefood.Main;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.freefood.Model.Store;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MainMenuViewModel extends ViewModel {
    private final MutableLiveData<List<Store>> stores = new MutableLiveData<>();

    public LiveData<List<Store>> getStores() {
        return stores;
    }

    public void loadStores() {
        new Thread(() -> {
            String resp = new NetworkTask(
                    MainActivityTEMP.SERVER_HOST, MainActivityTEMP.SERVER_PORT, null
            ).sendCommand("LIST_STORES", "");
            if (resp == null) {
                stores.postValue(Collections.emptyList());
                return;
            }

            try {
                JsonElement root = JsonParser.parseString(resp);
                Gson gson = new Gson();
                Type listType = new TypeToken<List<Store>>(){}.getType();
                List<Store> list;

                if (root.isJsonArray()) {
                    // backend returned [ {...}, {...}, ... ]
                    list = gson.fromJson(root, listType);

                } else if (root.isJsonObject()) {
                    var obj = root.getAsJsonObject();

                    // 1) special case: LIST_STORES = "Name1, Name2, Name3…"
                    if (obj.has("LIST_STORES") && obj.get("LIST_STORES").isJsonPrimitive()) {
                        String csv = obj.get("LIST_STORES").getAsString();
                        String[] names = csv.split(",");
                        list = new ArrayList<>(names.length);
                        for (String n : names) {
                            Store s = new Store();
                            s.setStoreName(n.trim());
                            list.add(s);
                        }

                        // 2) old case: { "stores": [ {...}, ... ] }
                    } else if (obj.has("stores") && obj.get("stores").isJsonArray()) {
                        list = gson.fromJson(obj.get("stores"), listType);

                        // 3) fallback: each property is a full-blown Store object
                    } else {
                        list = new ArrayList<>();
                        for (Map.Entry<String, JsonElement> e : obj.entrySet()) {
                            try {
                                Store s = gson.fromJson(e.getValue(), Store.class);
                                list.add(s);
                            } catch (Exception ignore) { }
                        }
                    }

                } else {
                    list = Collections.emptyList();
                }

                stores.postValue(list);
            } catch (Exception e) {
                e.printStackTrace();
                stores.postValue(Collections.emptyList());
            }
        }).start();
    }
}
