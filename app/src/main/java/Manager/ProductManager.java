package Manager;

import model.Product;
import model.Store;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles product operations for a store:
 * - Adding a new product to a store
 * - Removing an existing product from a store
 * - Updating the available amount of a product
 * Also maintains a list of deleted product names.
 */
public class ProductManager {
    // List to keep track of deleted products
    private List<String> deletedProducts;

    public ProductManager() {
        deletedProducts = new ArrayList<>();
    }

    /**
     * Adds a product to the given store.
     *
     * @param store   The store where the product is to be added.
     * @param product The product to add.
     * @return A message indicating success.
     */
    public String addProduct(Store store, Product product) {
        store.addProduct(product);
        return "Product " + product.getProductName() + " added to store " + store.getStoreName() + ".";
    }

    /**
     * Removes a product from the given store.
     * Also records the product name in the deleted products list.
     *
     * @param store       The store from which to remove the product.
     * @param productName The name of the product to remove.
     * @return A message indicating success or failure.
     */
    public String removeProduct(Store store, String productName) {
        boolean removed = store.removeProduct(productName);
        if (removed) {
            deletedProducts.add(productName);
            return "Product " + productName + " removed from store " + store.getStoreName() + ".";
        } else {
            return "Product " + productName + " not found in store " + store.getStoreName() + ".";
        }
    }

    /**
     * Updates the available amount for a given product in a store.
     *
     * @param store       The store containing the product.
     * @param productName The product whose amount is to be updated.
     * @param newAmount   The new available amount.
     * @return A message indicating success or failure.
     */
    public String updateProductAmount(Store store, String productName, int newAmount) {
        for (Product product : store.getProducts()) {
            if (product.getProductName() != null && product.getProductName().equals(productName)) {
                product.setAvailableAmount(newAmount);
                return "Product " + productName + " amount updated to " + newAmount + " in store " + store.getStoreName() + ".";
            }
        }
        return "Product " + productName + " not found in store " + store.getStoreName() + ".";
    }

    /**
     * Retrieves a list of deleted product names.
     *
     * @return A formatted string listing deleted products.
     */
    public String getDeletedProductsReport() {
        if (deletedProducts.isEmpty()) {
            return "No products have been deleted.";
        }
        StringBuilder report = new StringBuilder("DELETED PRODUCTS: ");
        for (String productName : deletedProducts) {
            report.append(productName).append(", ");
        }
        // Remove trailing comma and space
        if (report.length() > 0) {
            report.setLength(report.length() - 2);
        }
        return report.toString();
    }

    /**
     * Decreases the available amount for a given product in a store.
     * Checks if the removal quantity exceeds the current amount.
     *
     * @param store       The store containing the product.
     * @param productName The product whose amount is to be decreased.
     * @param decrement   The number of units to remove.
     * @return A message indicating success or an error if the decrement is too high.
     */
    public String decrementProductAmount(Store store, String productName, int decrement) {
        // Trim the incoming productName
        productName = productName.trim();
        for (Product product : store.getProducts()) {
            // Check with trimmed stored product name
            if (product.getProductName() != null && product.getProductName().trim().equals(productName)) {
                int currentAmount = product.getAvailableAmount();
                if (decrement > currentAmount) {
                    return "You cannot remove " + decrement + " units; only " + currentAmount + " available.";
                } else {
                    product.setAvailableAmount(currentAmount - decrement);
                    return "Product " + productName + " amount decreased by " + decrement +
                            " in store " + store.getStoreName() + ". New amount: " + (currentAmount - decrement) + ".";
                }
            }
        }
        return "Product " + productName + " not found in store " + store.getStoreName() + ".";
    }

}
