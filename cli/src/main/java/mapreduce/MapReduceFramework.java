package mapreduce;

import java.util.List;

/**
 * Minimal Map-Reduce building blocks actually needed by Freefooders.
 *
 * – {@link Pair} is the simple tuple that workers pass around.
 * – {@link Mapper} is implemented by the two concrete mappers used on each worker.
 *
 * No in-process Reducer or full MapReduce job runner is required by the current
 * architecture, so those constructs have been removed.
 */
public class MapReduceFramework {

    /** Immutable key-value pair used as the transport object between stages. */
    public static class Pair<K, V> {
        private final K key;
        private final V value;

        public Pair(K key, V value) {
            this.key  = key;
            this.value = value;
        }

        public K getKey()   { return key; }
        public V getValue() { return value; }
    }

    /**
     * Contract for the worker-side map phase.
     *
     * @param <K>   input key type  (usually the store-name or raw command string)
     * @param <V>   input value type (a {@code Store} instance)
     * @param <K2>  intermediate key type
     * @param <V2>  intermediate value type
     */
    public interface Mapper<K, V, K2, V2> {
        List<Pair<K2, V2>> map(K key, V value);
    }
}
