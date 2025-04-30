package mapreduce;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DistributedMapReduceJob aggregates intermediate JSON results from worker nodes
 * and performs the reduce phase using the provided Reducer implementation.
 *
 * @param <K2> type of intermediate keys
 * @param <V2> type of intermediate values
 * @param <R>  type of reduced result values
 */
public class DistributedMapReduceJob<K2, V2, R> {

    /** List of JSON strings representing intermediate pairs from workers. */
    private List<String> workerJsonResults;

    /** Reducer implementation to apply during the reduce phase. */
    private MapReduceFramework.Reducer<K2, V2, R> reducer;

    /** Gson instance for parsing JSON. */
    private Gson gson;

    /**
     * Constructs a DistributedMapReduceJob.
     *
     * @param workerJsonResults list of JSON-encoded intermediate pairs from workers
     * @param reducer           reducer to apply during the reduce phase
     */
    public DistributedMapReduceJob(List<String> workerJsonResults, MapReduceFramework.Reducer<K2, V2, R> reducer) {
        this.workerJsonResults = workerJsonResults;
        this.reducer = reducer;
        this.gson = new Gson();
    }

    /**
     * Executes the distributed reduce job by:
     * 1. Combining intermediate pairs from all worker JSON results
     * 2. Grouping values by key
     * 3. Applying the reducer to each key's values
     *
     * @return a map of keys to their reduced result values
     */
    public Map<K2, R> execute() {
        // Combine all intermediate pairs from workers
        List<MapReduceFramework.Pair<K2, V2>> combined = new ArrayList<>();
        Type pairListType = new TypeToken<List<MapReduceFramework.Pair<K2, V2>>>() {}.getType();
        for (String json : workerJsonResults) {
            List<MapReduceFramework.Pair<K2, V2>> list = gson.fromJson(json, pairListType);
            if (list != null) {
                combined.addAll(list);
            }
        }
        // Group intermediate values by key.
        Map<K2, List<V2>> intermediate = new HashMap<>();
        for (MapReduceFramework.Pair<K2, V2> pair : combined) {
            intermediate.computeIfAbsent(pair.getKey(), k -> new ArrayList<>()).add(pair.getValue());
        }
        // Reduce phase: call reducer on each key group.
        Map<K2, R> finalResults = new HashMap<>();
        for (Map.Entry<K2, List<V2>> entry : intermediate.entrySet()) {
            finalResults.put(entry.getKey(), reducer.reduce(entry.getKey(), entry.getValue()));
        }
        return finalResults;
    }
}
