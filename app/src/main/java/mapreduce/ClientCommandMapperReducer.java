package mapreduce;

import model.Product;
import model.Store;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Mapper implementation for *client* commands.
 *
 * Supported commands:
 *   • SEARCH  (filterKey=filterValue)
 *   • REVIEW  (storeName|stars)
 *   • AGGREGATE_SALES_BY_PRODUCT_NAME (ProductName=<value>)
 *   • PURCHASE_PRODUCT (storeName|productName|qty)
 *
 * Each invocation emits zero or more {@code Pair<String,String>} objects that the
 * worker serialises and (optionally) forwards to the external reduce server.
 */
public class ClientCommandMapperReducer {

    public static class ClientCommandMapper
            implements MapReduceFramework.Mapper<String, Store, String, String> {

        private final String command;
        private final Gson   gson = new Gson();

        // SEARCH-only helpers
        private String filterKey;
        private String filterValue;

        public ClientCommandMapper(String command,
                                   String data,
                                   Map<String, Store> localStores) {
            this.command = command;

            if ("SEARCH".equalsIgnoreCase(command)) {
                String[] parts = data.split("=", 2);
                if (parts.length == 2) {
                    this.filterKey   = parts[0].trim();
                    this.filterValue = parts[1].trim();
                }
            }
        }

        @Override
        public List<MapReduceFramework.Pair<String, String>> map(String key,
                                                                 Store storeObj) {
            List<MapReduceFramework.Pair<String, String>> results = new ArrayList<>();

            switch (command.toUpperCase()) {
                case "SEARCH": {
                    applySearchFilters(storeObj, results);
                    break;
                }
                case "REVIEW": {
                    handleReview(key, storeObj, results);
                    break;
                }
                case "AGGREGATE_SALES_BY_PRODUCT_NAME": {
                    handleAggregation(key, storeObj, results);
                    break;
                }
                case "PURCHASE_PRODUCT": {
                    // Expect key format: "storeName|productName|quantity"
                    String[] parts = key.split("\\|");
                    if (parts.length < 3) {
                        results.add(new MapReduceFramework.Pair<>(storeObj.getStoreName(), "Invalid data for PURCHASE_PRODUCT."));
                        break;
                    }
                    String storeName = parts[0].trim();
                    String productName = parts[1].trim();
                    int quantity;
                    try {
                        quantity = Integer.parseInt(parts[2].trim());
                    } catch (NumberFormatException e) {
                        results.add(new MapReduceFramework.Pair<>(storeObj.getStoreName(), "Invalid quantity format."));
                        break;
                    }
                    if (storeObj.getStoreName().equals(storeName)) {
                        boolean success = storeObj.purchaseProduct(productName, quantity);
                        if (success) {
                            int new_available_amount = 0;
                            for (Product product : storeObj.getProducts()) {
                                if (product.getProductName().equals(productName))
                                    new_available_amount = product.getAvailableAmount();
                            }
                            results.add(new MapReduceFramework.Pair<>(storeName,
                                    "Successfully purchased " + quantity + " of " + productName + " from store " + storeName + "." + "|" + new_available_amount));
                        } else {
                            results.add(new MapReduceFramework.Pair<>(storeName,
                                    "Purchase failed: insufficient stock or product not found."));
                        }
                    }
                    break;
                }
                default:
                    results.add(new MapReduceFramework.Pair<>(
                            storeObj.getStoreName(),
                            "Command " + command + " not supported in client mapper."));
            }
            return results;
        }

        /* ---------- helper methods ---------- */

        private void applySearchFilters(Store s,
                                        List<MapReduceFramework.Pair<String,String>> out) {
            switch (filterKey.toLowerCase()) {
                case "foodcategory":
                    if (s.getFoodCategory().equalsIgnoreCase(filterValue)) {
                        out.add(new MapReduceFramework.Pair<>(s.getStoreName(), gson.toJson(s)));
                    }
                    break;
                case "stars":
                    try {
                        int stars = Integer.parseInt(filterValue);
                        if (s.getStars() == stars) {
                            out.add(new MapReduceFramework.Pair<>(s.getStoreName(), gson.toJson(s)));
                        }
                    } catch (NumberFormatException ignored) { }
                    break;
                case "avgprice":
                    int cnt = Integer.parseInt(filterValue);
                    if (s.getAveragePriceOfStoreSymbol().equals("$".repeat(cnt))) {
                        out.add(new MapReduceFramework.Pair<>(s.getStoreName(), gson.toJson(s)));
                    }
                    break;
                case "radius":
                    // radius,lon,lat
                    String[] parts = filterValue.split(",");
                    if (parts.length == 3) {
                        try {
                            int     radius   = Integer.parseInt(parts[0].trim());
                            double  clientLo = Double.parseDouble(parts[1].trim());
                            double  clientLa = Double.parseDouble(parts[2].trim());
                            double  distKm   = calculateDistance(
                                    s.getLongitude(), s.getLatitude(), clientLo, clientLa);
                            if (distKm <= radius) {
                                out.add(new MapReduceFramework.Pair<>(s.getStoreName(), gson.toJson(s)));
                            }
                        } catch (NumberFormatException ignored) { }
                    }
                    break;
                default:
                    // unknown filter ⇒ nothing emitted
            }
        }

        private void handleReview(String key, Store s,
                                  List<MapReduceFramework.Pair<String,String>> out) {
            String[] parts = key.split("\\|");
            if (parts.length >= 2 && s.getStoreName().equals(parts[0].trim())) {
                try {
                    int stars = Integer.parseInt(parts[1].trim());
                    s.updateStoreReviews(stars);
                    out.add(new MapReduceFramework.Pair<>(s.getStoreName(),
                            "Gave " + "*".repeat(stars) + " Stars Review for: " + s.getStoreName()));
                } catch (NumberFormatException e) {
                    out.add(new MapReduceFramework.Pair<>(s.getStoreName(), "Invalid review format."));
                }
            }
        }

        private void handleAggregation(String key, Store s,
                                       List<MapReduceFramework.Pair<String,String>> out) {
            String[] parts = key.split("=", 2);
            if (parts.length == 2) {
                String product = parts[1].trim();
                if (s.getSalesRecord().containsKey(product)) {
                    int qty = s.getSalesRecord().get(product).getQuantity();
                    out.add(new MapReduceFramework.Pair<>(s.getStoreName(),
                            s.getStoreName() + ": " + product + " = " + qty));
                }
            } else {
                out.add(new MapReduceFramework.Pair<>(s.getStoreName(),
                        "Invalid aggregation query format. Expected ProductName=<value>."));
            }
        }

        /** Haversine formula. */
        private double calculateDistance(double lon1, double lat1,
                                         double lon2, double lat2) {
            int R = 6371;
            double dLat = Math.toRadians(lat2 - lat1);
            double dLon = Math.toRadians(lon2 - lon1);
            double a = Math.sin(dLat/2) * Math.sin(dLat/2)
                    + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                    * Math.sin(dLon/2) * Math.sin(dLon/2);
            return 2 * R * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        }
    }
}
