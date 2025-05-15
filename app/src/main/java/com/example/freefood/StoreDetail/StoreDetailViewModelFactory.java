package com.example.freefood.StoreDetail;

import com.example.freefood.Model.Store;

public class StoreDetailViewModelFactory {
    public StoreDetailViewModel create(Store store) {
        return new StoreDetailViewModel(store);
    }
}