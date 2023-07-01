package sahlaysta.swing;

import java.lang.ref.WeakReference;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Set;

class WeakIdentityHashMap<K, V> extends AbstractMap<K, V> {

    private final LinkedHashMap<HashWrapper, Object> lhm = new LinkedHashMap<>();
    private static final NullHashWrapper NHW = new NullHashWrapper();

    private final Set<Entry<K, V>> entrySet = new AbstractSet<Entry<K, V>>() {
        @Override
        public Iterator<Entry<K, V>> iterator() {
            clean();
            return new Iterator<Entry<K, V>>() {
                final Iterator<Entry<HashWrapper, Object>> it = lhm.entrySet().iterator();
                @Override
                public boolean hasNext() {
                    return it.hasNext();
                }
                @Override
                public Entry<K, V> next() {
                    Entry<HashWrapper, Object> entry = it.next();
                    @SuppressWarnings("unchecked")
                    K key = (K)entry.getKey().ref();
                    @SuppressWarnings("unchecked")
                    V value = (V)entry.getValue();
                    return new SimpleEntry<K, V>(key, value) {
                        @Override
                        public V setValue(V value) {
                            entry.setValue(value);
                            return super.setValue(value);
                        }
                    };
                }
                @Override
                public void remove() {
                    it.remove();
                }
            };
        }
        @Override
        public int size() {
            return lhm.size();
        }
        @Override
        public boolean isEmpty() {
            return lhm.isEmpty();
        }
        @Override
        public void clear() {
            lhm.clear();
        }
        @Override
        public boolean contains(Object o) {
            if (!(o instanceof Entry<?, ?>)) return false;
            Entry<?, ?> entry = (Entry<?, ?>)o;
            Object key = entry.getKey();
            return containsKey(key) && get(key) == entry.getValue();
        }
        @Override
        public boolean remove(Object o) {
            if (!(o instanceof Entry<?, ?>)) return false;
            Entry<?, ?> entry = (Entry<?, ?>)o;
            return WeakIdentityHashMap.this.remove(entry.getKey(), entry.getValue());
        }
    };

    @Override
    public Set<Entry<K, V>> entrySet() {
        return entrySet;
    }

    @Override
    public V put(K key, V value) {
        clean();
        @SuppressWarnings("unchecked")
        V r = (V) lhm.put(key == null ? NHW : new StoringHashWrapper(key), value);
        return r;
    }

    @Override
    public V putIfAbsent(K key, V value) {
        clean();
        @SuppressWarnings("unchecked")
        V r = (V) lhm.putIfAbsent(key == null ? NHW : new StoringHashWrapper(key), value);
        return r;
    }

    @Override
    public V get(Object key) {
        clean();
        @SuppressWarnings("unchecked")
        V r = (V) lhm.get(key == null ? NHW : new QueryingHashWrapper(key));
        return r;
    }

    @Override
    public V getOrDefault(Object key, V defaultValue) {
        clean();
        @SuppressWarnings("unchecked")
        V r = (V) lhm.getOrDefault(key == null ? NHW : new QueryingHashWrapper(key), defaultValue);
        return r;
    }

    @Override
    public boolean containsKey(Object key) {
        clean();
        return lhm.containsKey(key == null ? NHW : new QueryingHashWrapper(key));
    }

    @Override
    public boolean containsValue(Object value) {
        clean();
        return lhm.containsValue(value);
    }

    @Override
    public V remove(Object key) {
        clean();
        @SuppressWarnings("unchecked")
        V r = (V) lhm.remove(key == null ? NHW : new QueryingHashWrapper(key));
        return r;
    }

    @Override
    public boolean remove(Object key, Object value) {
        clean();
        return lhm.remove(key == null ? NHW : new QueryingHashWrapper(key), value);
    }

    @Override
    public V replace(K key, V value) {
        clean();
        @SuppressWarnings("unchecked")
        V r = (V) lhm.replace(key == null ? NHW : new StoringHashWrapper(key), value);
        return r;
    }

    @Override
    public boolean replace(K key, V oldValue, V newValue) {
        clean();
        return lhm.replace(key == null ? NHW : new StoringHashWrapper(key), oldValue, newValue);
    }

    @Override
    public int size() {
        return lhm.size();
    }

    @Override
    public boolean isEmpty() {
        return lhm.isEmpty();
    }

    @Override
    public void clear() {
        lhm.clear();
    }

    private void clean() {
        lhm.keySet().removeIf(k -> k != NHW && k.ref() == null);
    }

    private static abstract class HashWrapper {
        public abstract Object ref();
        @Override
        public boolean equals(Object obj) {
            return obj == this || (obj instanceof HashWrapper && ((HashWrapper)obj).ref() == this.ref());
        }
        public abstract int hashCode();
    }

    private static final class StoringHashWrapper extends HashWrapper {
        private final WeakReference<Object> wr;
        private final int hashCode;
        public StoringHashWrapper(Object object) {
            this.wr = new WeakReference<>(object);
            this.hashCode = System.identityHashCode(object);
        }
        @Override
        public Object ref() {
            return wr.get();
        }
        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    private static final class QueryingHashWrapper extends HashWrapper {
        private final Object object;
        public QueryingHashWrapper(Object object) {
            this.object = object;
        }
        @Override
        public Object ref() {
            return object;
        }
        @Override
        public int hashCode() {
            return System.identityHashCode(object);
        }
    }

    private static final class NullHashWrapper extends HashWrapper {
        @Override
        public Object ref() {
            return null;
        }
        @Override
        public int hashCode() {
            return 0;
        }
        @Override
        public boolean equals(Object obj) {
            return obj == this;
        }
    }

}
