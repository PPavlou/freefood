package com.example.freefood.StoreDetail;

import com.example.freefood.Model.Product;
import com.example.freefood.Model.Store;

import java.util.List;

/** Pure, synchronous holder – no networking needed (Store already has products). */
public class StoreDetailViewModel {
    private final Store store;
    public StoreDetailViewModel(Store store) { this.store = store; }

    public List<Product> getProducts() { return store.getProducts(); }
}