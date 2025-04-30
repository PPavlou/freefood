package mapreduce;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides core MapReduce framework components: Pair, Mapper, Reducer, and MapReduceJob.
 */
public class MapReduceFramework {

    /**
     * Represents a key-value pair.
     *
     * @param <K> type of the key
     * @param <V> type of the value
     */
    public static class Pair<K, V> {
        private final K key;
        private final V value;

        /**
         * Constructs a Pair with the specified key and value.
         *
         * @param key   the key
         * @param value the value
         */
        public Pair(K key, V value) {
            this.key = key;
            this.value = value;
        }

        /**
         * Returns the key of this pair.
         *
         * @return the key
         */
        public K getKey() {
            return key;
        }

        /**
         * Returns the value of this pair.
         *
         * @return the value
         */
        public V getValue() {
            return value;
        }
    }

    /**
     * Processes input key/value pairs to produce intermediate key/value pairs.
     *
     * @param <K>  input key type
     * @param <V>  input value type
     * @param <K2> intermediate key type
     * @param <V2> intermediate value type
     */
    public interface Mapper<K, V, K2, V2> {
        /**
         * Maps an input key and value to a list of intermediate pairs.
         *
         * @param key   the input key
         * @param value the input value
         * @return list of intermediate key/value pairs
         */
        List<Pair<K2, V2>> map(K key, V value);
    }

    /**
     * Reduces a key and its list of intermediate values to a final result.
     *
     * @param <K2> intermediate key type
     * @param <V2> intermediate value type
     * @param <R>  result type
     */
    public interface Reducer<K2, V2, R> {
        /**
         * Reduces a key and its values into a single result.
         *
         * @param key    the intermediate key
         * @param values the list of values for that key
         * @return the reduced result
         */
        R reduce(K2 key, List<V2> values);
    }

    /**
     * Runs a MapReduce job by applying a mapper to each input pair and then reducing grouped results.
     *
     * @param <K>  input key type
     * @param <V>  input value type
     * @param <K2> intermediate key type
     * @param <V2> intermediate value type
     * @param <R>  result type
     */
    public static class MapReduceJob<K, V, K2, V2, R> {
        private final List<Pair<K, V>> input;
        private final Mapper<K, V, K2, V2> mapper;
        private final Reducer<K2, V2, R> reducer;

        /**
         * Constructs a MapReduceJob with the provided input data, mapper, and reducer.
         *
         * @param input   list of input pairs
         * @param mapper  mapper implementation
         * @param reducer reducer implementation
         */
        public MapReduceJob(List<Pair<K, V>> input, Mapper<K, V, K2, V2> mapper, Reducer<K2, V2, R> reducer) {
            this.input = input;
            this.mapper = mapper;
            this.reducer = reducer;
        }

        /**
         * Executes the map and reduce phases of the job.
         *
         * @return a map from intermediate keys to reduced results
         */
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
            // Reduce phase: apply the reducer to each intermediate key and its values.
            Map<K2, R> finalResults = new HashMap<>();
            for (Map.Entry<K2, List<V2>> entry : intermediate.entrySet()) {
                finalResults.put(entry.getKey(), reducer.reduce(entry.getKey(), entry.getValue()));
            }
            return finalResults;
        }
    }
}
