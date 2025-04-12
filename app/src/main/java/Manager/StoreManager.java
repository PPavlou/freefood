package Manager;

import model.Store;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Handles operations related to store management:
 * - Adding new stores
 * - Removing stores
 * - Providing access to the stored stores
 */
public class StoreManager {
    private Map<String, Store> storeMap;

    public StoreManager() {
        // Synchronized map to ensure thread-safety
        storeMap = Collections.synchronizedMap(new HashMap<>());
    }

    public synchronized void clear() {
        storeMap.clear();
    }

    /**
     * Adds a new store if it does not already exist.
     *
     * @param store The store to add.
     * @return A message indicating success or failure.
     */
    public synchronized String addStore(Store store) {
        if (storeMap.containsKey(store.getStoreName())) {
            return "Store already exists.";
        }
        storeMap.put(store.getStoreName(), store);
        return "Store " + store.getStoreName() + " added successfully.";
    }

    /**
     * Removes a store by its name.
     *
     * @param storeName The name of the store to remove.
     * @return A message indicating success or failure.
     */
    public synchronized String removeStore(String storeName) {
        if (storeMap.containsKey(storeName)) {
            storeMap.remove(storeName);
            return "Store " + storeName + " removed successfully.";
        } else {
            return "Store " + storeName + " not found.";
        }
    }

    /**
     * Retrieves a store by its name.
     *
     * @param storeName The name of the store.
     * @return The Store object, or null if not found.
     */
    public synchronized Store getStore(String storeName) {
        return storeMap.get(storeName);
    }

    /**
     * Returns all stores.
     *
     * @return The map of store names to Store objects.
     */
    public synchronized Map<String, Store> getAllStores() {
        return storeMap;
    }
}
