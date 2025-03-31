package mapreduce;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// This class aggregates JSON results from workers and performs the reduce phase.
public class DistributedMapReduceJob<K2, V2, R> {
    private List<String> workerJsonResults;
    // Import the Reducer interface from our MapReduceFramework
    private MapReduceFramework.Reducer<K2, V2, R> reducer;
    private Gson gson;

    public DistributedMapReduceJob(List<String> workerJsonResults, MapReduceFramework.Reducer<K2, V2, R> reducer) {
        this.workerJsonResults = workerJsonResults;
        this.reducer = reducer;
        this.gson = new Gson();
    }

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
