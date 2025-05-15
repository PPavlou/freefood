package com.example.freefood.PurchaseProduct;

public class PurchaseViewModelFactory {
    public PurchaseViewModel create(String host, int port) {
        return new PurchaseViewModel(host, port);
    }
}
