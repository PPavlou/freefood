package mapreduce;

import Manager.ProductManager;
import Manager.StoreManager;
import model.Store;
import model.Product;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * ManagerCommandMapperReducer handles filtering for manager commands.
 * It groups the filters used by the Manager.
 */
public class ManagerCommandMapperReducer {

    public static class CommandMapper implements MapReduceFramework.Mapper<String, Store, String, String> {
        private String command;
        private String query = null;
        private StoreManager storeManager;
        private ProductManager productManager;
        private Gson gson = new Gson();

        public CommandMapper(String command, StoreManager storeManager, ProductManager productManager) {
            this.command = command;
            this.storeManager = storeManager;
            this.productManager = productManager;
        }

        public CommandMapper(String command, String query, StoreManager storeManager, ProductManager productManager) {
            this.command = command;
            this.query = query;
            this.storeManager = storeManager;
            this.productManager = productManager;
        }

        @Override
        public List<MapReduceFramework.Pair<String, String>> map(String key, Store storeObj) {
            List<MapReduceFramework.Pair<String, String>> results = new ArrayList<>();

            // Manager commands grouping
            switch (command.toUpperCase()) {
                case "ADD_STORE": {
                    String result = storeManager.addStore(storeObj);
                    results.add(new MapReduceFramework.Pair<>(storeObj.getStoreName(), result));
                    break;
                }
                case "REMOVE_STORE": {
                    String storeName = key.trim();
                    String result = storeManager.removeStore(storeName);
                    results.add(new MapReduceFramework.Pair<>(storeName, result));
                    break;
                }
                case "ADD_PRODUCT": {
                    String[] parts = key.split("\\|", 2);
                    if (parts.length < 2) {
                        results.add(new MapReduceFramework.Pair<>(storeObj.getStoreName(), "Invalid data for ADD_PRODUCT."));
                    } else {
                        String storeName = parts[0].trim();
                        String productJson = parts[1].trim();
                        if (storeObj.getStoreName().equals(storeName)) {
                            Product product = gson.fromJson(productJson, Product.class);
                            String result = productManager.addProduct(storeObj, product);
                            results.add(new MapReduceFramework.Pair<>(storeName, result));
                        }
                    }
                    break;
                }
                case "REMOVE_PRODUCT": {
                    String[] parts = key.split("\\|", 2);
                    if (parts.length < 2) {
                        results.add(new MapReduceFramework.Pair<>(storeObj.getStoreName(), "Invalid data for REMOVE_PRODUCT."));
                    } else {
                        String storeName = parts[0].trim();
                        String productName = parts[1].trim();
                        if (storeObj.getStoreName().equals(storeName)) {
                            String result = productManager.removeProduct(storeObj, productName);
                            results.add(new MapReduceFramework.Pair<>(storeName, result));
                        }
                    }
                    break;
                }
                case "UPDATE_PRODUCT_AMOUNT": {
                    String[] parts = key.split("\\|", 3);
                    if (parts.length < 3) {
                        results.add(new MapReduceFramework.Pair<>(storeObj.getStoreName(), "Invalid data for UPDATE_PRODUCT_AMOUNT."));
                    } else {
                        String storeName = parts[0].trim();
                        String productName = parts[1].trim();
                        int newAmount;
                        try {
                            newAmount = Integer.parseInt(parts[2].trim());
                        } catch (NumberFormatException e) {
                            results.add(new MapReduceFramework.Pair<>(storeObj.getStoreName(), "Invalid quantity format."));
                            break;
                        }
                        if (storeObj.getStoreName().equals(storeName)) {
                            String result = productManager.updateProductAmount(storeObj, productName, newAmount);
                            results.add(new MapReduceFramework.Pair<>(storeName, result));
                        }
                    }
                    break;
                }
                case "INCREMENT_PRODUCT_AMOUNT": {
                    String[] parts = key.split("\\|", 3);
                    if (parts.length < 3) {
                        results.add(new MapReduceFramework.Pair<>(storeObj.getStoreName(), "Invalid data for INCREMENT_PRODUCT_AMOUNT."));
                    } else {
                        String storeName = parts[0].trim();
                        String productName = parts[1].trim();
                        int increment;
                        try {
                            increment = Integer.parseInt(parts[2].trim());
                        } catch (NumberFormatException e) {
                            results.add(new MapReduceFramework.Pair<>(storeObj.getStoreName(), "Invalid increment format."));
                            break;
                        }
                        if (storeObj.getStoreName().equals(storeName)) {
                            boolean found = false;
                            for (Product product : storeObj.getProducts()) {
                                if (product.getProductName().equals(productName)) {
                                    int current = product.getAvailableAmount();
                                    product.setAvailableAmount(current + increment);
                                    results.add(new MapReduceFramework.Pair<>(storeName,
                                            "Product " + productName + " amount increased by " + increment +
                                                    " in store " + storeName + ". New amount: " + (current + increment) + "."));
                                    found = true;
                                    break;
                                }
                            }
                            if (!found) {
                                results.add(new MapReduceFramework.Pair<>(storeName,
                                        "Product " + productName + " not found in store " + storeName + "."));
                            }
                        }
                    }
                    break;
                }
                case "DECREMENT_PRODUCT_AMOUNT": {
                    String[] parts = key.split("\\|", 3);
                    if (parts.length < 3) {
                        results.add(new MapReduceFramework.Pair<>(storeObj.getStoreName(), "Invalid data for DECREMENT_PRODUCT_AMOUNT."));
                    } else {
                        String storeName = parts[0].trim();
                        String productName = parts[1].trim();
                        int decrement;
                        try {
                            decrement = Integer.parseInt(parts[2].trim());
                        } catch (NumberFormatException e) {
                            results.add(new MapReduceFramework.Pair<>(storeObj.getStoreName(), "Invalid decrement format."));
                            break;
                        }
                        if (storeObj.getStoreName().equals(storeName)) {
                            String result = productManager.decrementProductAmount(storeObj, productName, decrement);
                            results.add(new MapReduceFramework.Pair<>(storeName, result));
                        }
                    }
                    break;
                }
                case "DELETED_PRODUCTS": {
                    String report = productManager.getDeletedProductsReport();
                    results.add(new MapReduceFramework.Pair<>("DELETED_PRODUCTS", report));
                    break;
                }
                case "LIST_STORES": {
                    // Instead of returning the entire list as one string,
                    // output the store names individually.
                    for (String storeName : storeManager.getAllStores().keySet()) {
                        results.add(new MapReduceFramework.Pair<>("LIST_STORES", storeName));
                    }
                    break;
                }
                case "AGGREGATE_SALES_BY_PRODUCT_NAME": {
                    // Expect the query to be in the format "ProductName=<value>"
                    if (query == null) {
                        results.add(new MapReduceFramework.Pair<>(storeObj.getStoreName(),
                                "Invalid aggregation query format. Expected ProductName=<value>."));
                    } else {
                        // Extract the product name from the query.
                        String productName = query.trim().substring("ProductName=".length()).trim();
                        int aggregatedSales = storeObj.getSalesForProduct(productName);
                        results.add(new MapReduceFramework.Pair<>(storeObj.getStoreName(), String.valueOf(aggregatedSales)));
                    }
                    break;
                }

                default: {
                    results.add(new MapReduceFramework.Pair<>(storeObj.getStoreName(),
                            "Command " + command + " not supported in manager filter group."));
                }
            }
            return results;
        }
    }

    /**
     * Generic CommandReducer for manager commands.
     * For LIST_STORES, it merges duplicate store names; for other commands, it concatenates responses.
     */
    public static class CommandReducer implements MapReduceFramework.Reducer<String, String, String> {
        private String command;

        public CommandReducer(String command) {
            this.command = command;
        }

        @Override
        public String reduce(String key, List<String> values) {
            if ("LIST_STORES".equalsIgnoreCase(command)) {
                // Use a LinkedHashSet to preserve order and eliminate duplicates.
                java.util.Set<String> uniqueStores = new java.util.LinkedHashSet<>();
                for (String value : values) {
                    uniqueStores.add(value.trim());
                }
                return uniqueStores.toString();
            } else {
                StringBuilder sb = new StringBuilder();
                for (String value : values) {
                    sb.append(value).append(" | ");
                }
                if (sb.length() > 0) {
                    sb.setLength(sb.length() - 3);
                }
                return sb.toString();
            }
        }
    }
}
