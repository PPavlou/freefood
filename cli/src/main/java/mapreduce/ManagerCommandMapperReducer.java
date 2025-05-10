package mapreduce;

import Manager.ProductManager;
import Manager.StoreManager;
import model.Store;
import model.Product;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;

/**
 * Mapper implementation for *manager* commands
 * (store and product administration as well as simple aggregations).
 */
public class ManagerCommandMapperReducer {

    public static class CommandMapper
            implements MapReduceFramework.Mapper<String, Store, String, String> {

        private final String         command;
        private final String         query;          // only for AGGREGATE_SALES_BY_PRODUCT_NAME
        private final StoreManager   storeManager;
        private final ProductManager productManager;
        private final Gson           gson = new Gson();

        /* ctor for commands with no extra query string */
        public CommandMapper(String command,
                             StoreManager storeManager,
                             ProductManager productManager) {
            this(command, null, storeManager, productManager);
        }

        /* ctor for commands that include a query (currently only aggregation) */
        public CommandMapper(String command, String query,
                             StoreManager storeManager,
                             ProductManager productManager) {
            this.command        = command;
            this.query          = query;
            this.storeManager   = storeManager;
            this.productManager = productManager;
        }

        @Override
        public List<MapReduceFramework.Pair<String, String>> map(String key,
                                                                 Store storeObj) {
            List<MapReduceFramework.Pair<String, String>> results = new ArrayList<>();

            switch (command.toUpperCase()) {
                /* ---------- store-level ops ---------- */
                case "ADD_STORE":
                    results.add(new MapReduceFramework.Pair<>(
                            storeObj.getStoreName(),
                            storeManager.addStore(storeObj)));
                    break;

                case "REMOVE_STORE":
                    String storeName = key.trim();
                    results.add(new MapReduceFramework.Pair<>(
                            storeName, storeManager.removeStore(storeName)));
                    break;

                /* ---------- product-level ops ---------- */
                case "ADD_PRODUCT":
                    handleAddProduct(key, storeObj, results);
                    break;

                case "REMOVE_PRODUCT": {
                    String[] parts = key.split("\\|", 2);
                    if (parts.length < 2) {
                        results.add(new MapReduceFramework.Pair<>(storeObj.getStoreName(), "Invalid data for REMOVE_PRODUCT."));
                    } else {
                        storeName = parts[0].trim();
                        String productName = parts[1].trim();
                        if (storeObj.getStoreName().equals(storeName)) {
                            String result = productManager.removeProduct(storeObj, productName);
                            results.add(new MapReduceFramework.Pair<>(storeName, result));
                        }
                    }
                    break;
                }

                case "UPDATE_PRODUCT_AMOUNT":
                    handleUpdateProduct(key, storeObj, results);
                    break;

                case "INCREMENT_PRODUCT_AMOUNT":
                    handleIncDecProduct(key, storeObj, true, results);
                    break;

                case "DECREMENT_PRODUCT_AMOUNT":
                    handleIncDecProduct(key, storeObj, false, results);
                    break;

                /* ---------- reports ---------- */
                case "DELETED_PRODUCTS":
                    results.add(new MapReduceFramework.Pair<>(
                            "DELETED_PRODUCTS", productManager.getDeletedProductsReport()));
                    break;

                case "LIST_STORES":
                    results.add(new MapReduceFramework.Pair<>(
                            "LIST_STORES", storeObj.getStoreName()));
                    break;

                case "AGGREGATE_SALES_BY_PRODUCT_NAME":
                    String productName = query.trim().substring("ProductName=".length()).trim();
                    int aggregated = storeObj.getSalesForProduct(productName);
                    results.add(new MapReduceFramework.Pair<>(
                            storeObj.getStoreName(), String.valueOf(aggregated)));
                    break;

                default:
                    results.add(new MapReduceFramework.Pair<>(
                            storeObj.getStoreName(),
                            "Command " + command + " not supported in manager mapper."));
            }
            return results;
        }

        /* ---------- helpers ---------- */

        private void handleAddProduct(String key, Store s,
                                      List<MapReduceFramework.Pair<String,String>> out) {
            String[] parts = key.split("\\|", 2);
            if (parts.length == 2 && s.getStoreName().equals(parts[0].trim())) {
                Product p = gson.fromJson(parts[1].trim(), Product.class);
                out.add(new MapReduceFramework.Pair<>(
                        s.getStoreName(), productManager.addProduct(s, p)));
            } else {
                out.add(new MapReduceFramework.Pair<>(s.getStoreName(), "Invalid data for ADD_PRODUCT."));
            }
        }

        private void handleUpdateProduct(String key, Store s,
                                         List<MapReduceFramework.Pair<String,String>> out) {
            String[] parts = key.split("\\|", 3);
            if (parts.length == 3 && s.getStoreName().equals(parts[0].trim())) {
                try {
                    int newAmount = Integer.parseInt(parts[2].trim());
                    out.add(new MapReduceFramework.Pair<>(
                            s.getStoreName(),
                            productManager.updateProductAmount(
                                    s, parts[1].trim(), newAmount)));
                } catch (NumberFormatException e) {
                    out.add(new MapReduceFramework.Pair<>(
                            s.getStoreName(), "Invalid quantity format."));
                }
            } else {
                out.add(new MapReduceFramework.Pair<>(s.getStoreName(), "Invalid data for UPDATE_PRODUCT_AMOUNT."));
            }
        }

        private void handleIncDecProduct(String key, Store s,
                                         boolean increment,
                                         List<MapReduceFramework.Pair<String,String>> out) {
            String[] parts = key.split("\\|", 3);
            if (parts.length == 3 && s.getStoreName().equals(parts[0].trim())) {
                try {
                    int delta = Integer.parseInt(parts[2].trim());
                    String result = increment
                            ? productManager.incrementProductAmount(s, parts[1].trim(), delta)
                            : productManager.decrementProductAmount(s, parts[1].trim(), delta);
                    out.add(new MapReduceFramework.Pair<>(s.getStoreName(), result));
                } catch (NumberFormatException e) {
                    out.add(new MapReduceFramework.Pair<>(s.getStoreName(),
                            "Invalid " + (increment ? "increment" : "decrement") + " format."));
                }
            } else {
                out.add(new MapReduceFramework.Pair<>(s.getStoreName(),
                        "Invalid data for " + (increment ? "INCREMENT" : "DECREMENT") + "_PRODUCT_AMOUNT."));
            }
        }
    }
}
