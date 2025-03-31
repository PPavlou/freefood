package mapreduce;

import model.Store;
import java.util.ArrayList;
import java.util.List;

/**
 * Mapper for filtering stores by a search criterion.
 * Input Key:   storeName (String)
 * Input Value: Store object
 * Output Key:  storeName (String)
 * Output Value: Store (only if it matches the filter)
 */
public class SearchMapper implements MapReduceFramework.Mapper<String, Store, String, Store> {

    private String filterKey;   // e.g. "FoodCategory", "Stars", "AvgPrice", "Radius"
    private String filterValue; // e.g. "pizzeria", "4", "$$", or for Radius: "5,23.732089,37.994124"

    public SearchMapper(String filterKey, String filterValue) {
        this.filterKey = filterKey;
        this.filterValue = filterValue;
    }

    @Override
    public List<MapReduceFramework.Pair<String, Store>> map(String storeName, Store storeObj) {
        List<MapReduceFramework.Pair<String, Store>> results = new ArrayList<>();

        if ("FoodCategory".equalsIgnoreCase(filterKey)) {
            if (storeObj.getFoodCategory().equalsIgnoreCase(filterValue)) {
                results.add(new MapReduceFramework.Pair<>(storeName, storeObj));
            }
        } else if ("Stars".equalsIgnoreCase(filterKey)) {
            try {
                int starsFilter = Integer.parseInt(filterValue);
                if (storeObj.getStars() >= starsFilter) {
                    results.add(new MapReduceFramework.Pair<>(storeName, storeObj));
                }
            } catch (NumberFormatException e) {
                // Ignore invalid format.
            }
        } else if ("AvgPrice".equalsIgnoreCase(filterKey)) {
            if (storeObj.getAveragePriceOfStoreSymbol().equals(filterValue)) {
                results.add(new MapReduceFramework.Pair<>(storeName, storeObj));
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
                        results.add(new MapReduceFramework.Pair<>(storeName, storeObj));
                    }
                } catch (NumberFormatException e) {
                    // Invalid numeric format.
                }
            }
        }
        return results;
    }

    // Haversine formula to calculate distance between two geographic points.
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
