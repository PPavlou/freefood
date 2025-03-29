package mapreduceframework;

import mapreduceframework.MapReduceFramework;
import model.Store;
import java.util.ArrayList;
import java.util.List;

/**
 * Reducer that merges all Store objects for a given key into a single List.
 * Key: storeName
 * Values: List<Store> that matched the filter.
 * Result: A single List<Store> for that key.
 */
public class SearchReducer implements MapReduceFramework.Reducer<String, Store, List<Store>> {

    @Override
    public List<Store> reduce(String storeName, List<Store> storeObjects) {
        return new ArrayList<>(storeObjects);
    }
}
