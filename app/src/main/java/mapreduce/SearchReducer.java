package mapreduce;

import mapreduce.MapReduceFramework.Reducer;
import model.Store;
import java.util.ArrayList;
import java.util.List;

public class SearchReducer implements Reducer<String, Store, List<Store>> {
    @Override
    public List<Store> reduce(String key, List<Store> values) {
        return new ArrayList<>(values);
    }
}
