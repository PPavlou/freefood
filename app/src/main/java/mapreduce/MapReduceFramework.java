package mapreduce;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapReduceFramework {

    // A simple generic Pair class.
    public static class Pair<K, V> {
        private final K key;
        private final V value;

        public Pair(K key, V value) {
            this.key = key;
            this.value = value;
        }

        public K getKey() {
            return key;
        }

        public V getValue() {
            return value;
        }
    }

    // Mapper interface: processes an input key/value pair and returns a list of intermediate pairs.
    public interface Mapper<K, V, K2, V2> {
        List<Pair<K2, V2>> map(K key, V value);
    }

    // Reducer interface: processes a key and a list of its intermediate values and produces a final result.
    public interface Reducer<K2, V2, R> {
        R reduce(K2 key, List<V2> values);
    }

    // MapReduceJob class: runs the map functions sequentially and then performs the reduce phase.
    public static class MapReduceJob<K, V, K2, V2, R> {
        private final List<Pair<K, V>> input;
        private final Mapper<K, V, K2, V2> mapper;
        private final Reducer<K2, V2, R> reducer;

        public MapReduceJob(List<Pair<K, V>> input, Mapper<K, V, K2, V2> mapper, Reducer<K2, V2, R> reducer) {
            this.input = input;
            this.mapper = mapper;
            this.reducer = reducer;
        }

        public Map<K2, R> execute() {
            // Map phase: apply the mapper to each input pair.
            Map<K2, List<V2>> intermediate = new HashMap<>();
            for (Pair<K, V> pair : input) {
                List<Pair<K2, V2>> mappedPairs = mapper.map(pair.getKey(), pair.getValue());
                for (Pair<K2, V2> p : mappedPairs) {
                    // Group the intermediate values by key.
                    intermediate.computeIfAbsent(p.getKey(), k -> new ArrayList<>()).add(p.getValue());
                }
            }
            // Reduce phase: for each intermediate key, call the reducer.
            Map<K2, R> finalResults = new HashMap<>();
            for (Map.Entry<K2, List<V2>> entry : intermediate.entrySet()) {
                finalResults.put(entry.getKey(), reducer.reduce(entry.getKey(), entry.getValue()));
            }
            return finalResults;
        }
    }
}
