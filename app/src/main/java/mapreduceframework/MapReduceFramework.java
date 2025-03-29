package mapreduceframework;

import java.util.*;

// This generic framework runs the MapReduce pipeline sequentially.
public class MapReduceFramework {

    // A simple generic Pair class.
    public static class Pair<K, V> {
        private K key;
        private V value;

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

    // Mapper interface.
    public interface Mapper<K, V, K2, V2> {
        List<Pair<K2, V2>> map(K key, V value);
    }

    // Reducer interface.
    public interface Reducer<K2, V2, R> {
        R reduce(K2 key, List<V2> values);
    }

    // MapReduceJob class: runs the map functions sequentially and then performs the reduce phase.
    public static class MapReduceJob<K, V, K2, V2, R> {
        private List<Pair<K, V>> input;
        private Mapper<K, V, K2, V2> mapper;
        private Reducer<K2, V2, R> reducer;

        public MapReduceJob(List<Pair<K, V>> input, Mapper<K, V, K2, V2> mapper, Reducer<K2, V2, R> reducer) {
            this.input = input;
            this.mapper = mapper;
            this.reducer = reducer;
        }

        public Map<K2, R> execute() {
            // Map phase: process each input pair sequentially.
            Map<K2, List<V2>> intermediate = new HashMap<>();
            for (Pair<K, V> pair : input) {
                List<Pair<K2, V2>> mapped = mapper.map(pair.getKey(), pair.getValue());
                for (Pair<K2, V2> p : mapped) {
                    if (!intermediate.containsKey(p.getKey())) {
                        intermediate.put(p.getKey(), new ArrayList<V2>());
                    }
                    intermediate.get(p.getKey()).add(p.getValue());
                }
            }

            // Reduce phase: for each key, call the reducer.
            Map<K2, R> results = new HashMap<>();
            for (Map.Entry<K2, List<V2>> entry : intermediate.entrySet()) {
                results.put(entry.getKey(), reducer.reduce(entry.getKey(), entry.getValue()));
            }

            return results;
        }
    }
}

