package mapreduce;

import model.Store;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ClientCommandMapperReducer contains both the mapper and reducer for client commands.
 *
 * Supported commands:
 *   - SEARCH: expects data "filterKey=filterValue" (e.g., "FoodCategory=pizzeria")
 *   - REVIEW: expects data "storeName|review"
 *   - AGGREGATE_SALES_BY_PRODUCT_NAME: expects data "ProductName=<value>"
 *   - PURCHASE_PRODUCT: expects data "storeName|productName|quantity"
 */
public class ClientCommandMapperReducer {

    public static class ClientCommandMapper implements MapReduceFramework.Mapper<String, Store, String, String> {
        private String command;
        private Gson gson = new Gson();
        // For SEARCH only.
        private String filterKey;
        private String filterValue;
        // Local store map (provided by the worker)
        private Map<String, Store> localStores;

        /**
         * @param command     The client command ("SEARCH", "REVIEW", "AGGREGATE_SALES_BY_PRODUCT_NAME", or "PURCHASE_PRODUCT").
         * @param data        The raw data string from the client.
         * @param localStores The local store map.
         */
        public ClientCommandMapper(String command, String data, Map<String, Store> localStores) {
            this.command = command;
            this.localStores = localStores;
            if ("SEARCH".equalsIgnoreCase(command)) {
                String[] parts = data.split("=", 2);
                if (parts.length == 2) {
                    this.filterKey = parts[0].trim();
                    this.filterValue = parts[1].trim();
                }
            }
        }

        @Override
        public List<MapReduceFramework.Pair<String, String>> map(String key, Store storeObj) {
            List<MapReduceFramework.Pair<String, String>> results = new ArrayList<>();
            switch (command.toUpperCase()) {
                case "SEARCH": {
                    // Apply different filters based on filterKey.
                    if ("FoodCategory".equalsIgnoreCase(filterKey)) {
                        if (storeObj.getFoodCategory().equalsIgnoreCase(filterValue)) {
                            results.add(new MapReduceFramework.Pair<>(storeObj.getStoreName(), gson.toJson(storeObj)));
                        }
                    } else if ("Stars".equalsIgnoreCase(filterKey)) {
                        try {
                            int starsFilter = Integer.parseInt(filterValue);
                            if (storeObj.getStars() >= starsFilter) {
                                results.add(new MapReduceFramework.Pair<>(storeObj.getStoreName(), gson.toJson(storeObj)));
                            }
                        } catch (NumberFormatException e) {
                            // Ignore invalid format.
                        }
                    } else if ("AvgPrice".equalsIgnoreCase(filterKey)) {
                        if (storeObj.getAveragePriceOfStoreSymbol().equals(filterValue)) {
                            results.add(new MapReduceFramework.Pair<>(storeObj.getStoreName(), gson.toJson(storeObj)));
                        }
                    } else if ("Radius".equalsIgnoreCase(filterKey)) {
                        // Expected filterValue format: "radius,clientLongitude,clientLatitude"
                        String[] parts = filterValue.split(",");
                        if (parts.length == 3) {
                            try {
                                int radius = Integer.parseInt(parts[0].trim());
                                double clientLon = Double.parseDouble(parts[1].trim());
                                double clientLat = Double.parseDouble(parts[2].trim());
                                double distance = calculateDistance(storeObj.getLongitude(), storeObj.getLatitude(), clientLon, clientLat);
                                if (distance <= radius) {
                                    results.add(new MapReduceFramework.Pair<>(storeObj.getStoreName(), gson.toJson(storeObj)));
                                }
                            } catch (NumberFormatException e) {
                                // Ignore invalid numeric format.
                            }
                        }
                    }
                    break;
                }
                case "REVIEW": {
                    // Expect key format: "storeName|review"
                    String[] parts = key.split("\\|");
                    if (parts.length >= 2) {
                        String storeName = parts[0].trim();
                        try {
                            int review = Integer.parseInt(parts[1].trim());
                            if (storeObj.getStoreName().equals(storeName)) {
                                storeObj.updateStoreReviews(review);
                                results.add(new MapReduceFramework.Pair<>(storeName,
                                        storeName + " Reviews Updated: Stars = " + storeObj.getStars()));
                            }
                        } catch (NumberFormatException e) {
                            results.add(new MapReduceFramework.Pair<>(storeObj.getStoreName(), "Invalid review format."));
                        }
                    } else {
                        results.add(new MapReduceFramework.Pair<>(storeObj.getStoreName(), "Invalid data for REVIEW."));
                    }
                    break;
                }
                case "AGGREGATE_SALES_BY_PRODUCT_NAME": {
                    // Expect key format: "ProductName=<value>"
                    String[] parts = key.split("=", 2);
                    if (parts.length == 2) {
                        String productNameQuery = parts[1].trim();
                        if (storeObj.getSalesRecord().containsKey(productNameQuery)) {
                            int qty = storeObj.getSalesRecord().get(productNameQuery).getQuantity();
                            results.add(new MapReduceFramework.Pair<>(storeObj.getStoreName(),
                                    storeObj.getStoreName() + ": " + productNameQuery + " = " + qty));
                        }
                    } else {
                        results.add(new MapReduceFramework.Pair<>(storeObj.getStoreName(),
                                "Invalid aggregation query format. Expected ProductName=<value>."));
                    }
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
                            results.add(new MapReduceFramework.Pair<>(storeName,
                                    "Successfully purchased " + quantity + " of " + productName + " from store " + storeName + "."));
                        } else {
                            results.add(new MapReduceFramework.Pair<>(storeName,
                                    "Purchase failed: insufficient stock or product not found."));
                        }
                    }
                    break;
                }
                default: {
                    results.add(new MapReduceFramework.Pair<>(storeObj.getStoreName(),
                            "Command " + command + " not supported in client mapper."));
                }
            }
            return results;
        }

        private double calculateDistance(double lon1, double lat1, double lon2, double lat2) {
            int R = 6371; // Earth's radius in km.
            double dLat = Math.toRadians(lat2 - lat1);
            double dLon = Math.toRadians(lon2 - lon1);
            double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                    + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                    * Math.sin(dLon / 2) * Math.sin(dLon / 2);
            double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
            return R * c;
        }
    }

    public static class ClientCommandReducer implements MapReduceFramework.Reducer<String, String, String> {
        private String command;

        public ClientCommandReducer(String command) {
            this.command = command;
        }

        @Override
        public String reduce(String key, List<String> values) {
            StringBuilder sb = new StringBuilder();
            for (String value : values) {
                sb.append(value).append("\n");
            }
            return sb.toString().trim();
        }
    }
}
