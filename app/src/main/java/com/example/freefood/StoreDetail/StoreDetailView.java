package com.example.freefood.StoreDetail;

import com.example.freefood.Model.Product;
import java.util.List;

public interface StoreDetailView {
    void showProducts(List<Product> products);
    void showError(String message);
}