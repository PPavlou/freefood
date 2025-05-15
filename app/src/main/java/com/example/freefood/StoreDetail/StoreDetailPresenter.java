package com.example.freefood.StoreDetail;

import com.example.freefood.Model.Product;
import java.util.List;

public class StoreDetailPresenter {
    private final StoreDetailViewModel vm;
    private final StoreDetailView      view;

    public StoreDetailPresenter(StoreDetailViewModel vm, StoreDetailView view) {
        this.vm   = vm;
        this.view = view;
    }

    /** simple, synchronous – data is already local */
    public void loadProducts() {
        List<Product> list = vm.getProducts();
        if (list == null || list.isEmpty()) {
            view.showError("No products available");
        } else {
            view.showProducts(list);
        }
    }
}