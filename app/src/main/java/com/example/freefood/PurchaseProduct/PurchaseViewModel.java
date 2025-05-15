package com.example.freefood.PurchaseProduct;

import com.example.freefood.Main.NetworkTask;

/** Handles the network call. */
public class PurchaseViewModel {

    public interface Callback { void onResult(String serverMsg); }

    private final String host;
    private final int    port;

    public PurchaseViewModel(String host, int port) {
        this.host = host;
        this.port = port;
    }

    /** Fire-and-forget AsyncTask that returns result on main thread via cb. */
    public void purchase(String store, String product, int qty, Callback cb) {
        new NetworkTask(host, port, result -> {
            if (result == null) result = "Network error";
            cb.onResult(result);
        }).execute("PURCHASE_PRODUCT", store + "|" + product + "|" + qty);
    }
}
