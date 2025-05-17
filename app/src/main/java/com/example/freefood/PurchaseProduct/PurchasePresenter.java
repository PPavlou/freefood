package com.example.freefood.PurchaseProduct;

/** Middle-man between Activity and ViewModel. */
public class PurchasePresenter {

    public interface PurchaseView {
        void showMessage(String msg);
        void clearQuantityField();
        void onPurchaseSuccess();
        void updateAvailableStock(int newAmount);
        }

    private final PurchaseViewModel vm;
    private final PurchaseView view;

    public PurchasePresenter(PurchaseViewModel vm, PurchaseView view) {
        this.vm   = vm;
        this.view = view;
    }

    /** Validates user input then delegates to ViewModel. */
    public void purchaseProduct(String store, String product, String qtyTxt) {
        if (qtyTxt.isEmpty()) {
            view.showMessage("Enter quantity"); return;
        }
        int qty;
        try {
            qty = Integer.parseInt(qtyTxt);
            if (qty <= 0) { view.showMessage("Quantity must be > 0"); return; }
        } catch (NumberFormatException e) {
            view.showMessage("Quantity must be numeric"); return;
        }

        vm.purchase(store, product, qty, result -> {
            view.showMessage(result);
            if (result != null && result.toLowerCase().contains("success")) {
                view.clearQuantityField();
                // split off the new stock count after the '|'
                String[] parts = result.split("\\|");
                if (parts.length > 1) {
                    String digitsOnly = parts[1].replaceAll("\\D+", "");
                    if (!digitsOnly.isEmpty()) {
                        int newAmt = Integer.parseInt(digitsOnly);
                        view.updateAvailableStock(newAmt);
                    }
                }
                view.onPurchaseSuccess();
            }
        });
    }
}
