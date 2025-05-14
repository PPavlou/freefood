package com.example.freefood.Main;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.freefood.Model.Store;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.List;

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
            try {
                Type listType = new TypeToken<List<Store>>(){}.getType();
                List<Store> list = new Gson().fromJson(resp, listType);
                stores.postValue(list);
            } catch (Exception e) {
                stores.postValue(List.of());
            }
        }).start();
    }
}
