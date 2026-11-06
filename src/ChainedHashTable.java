import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.ToLongFunction;

/**
 * Separate chaining with actual singly linked nodes. Insertion traverses to the
 * tail. Every occupied node examined adds one collision, exactly as defined in
 * the assignment. No tail cache or synthetic collision formula hides that work.
 * Unused space counts empty bucket references, not the separately allocated nodes.
 */
public final class ChainedHashTable implements HashTable {
    private static final class Node {
        private final String key;
        private final MovieRecord movie;
        private Node next;
        Node(String key, MovieRecord movie) { this.key = key; this.movie = movie; }
    }

    private final Node[] buckets;
    private final Key keyType;
    private final ToLongFunction<String> hash;
    private int size;
    private int used;
    private long collisions;

    public ChainedHashTable(int capacity, Key keyType, ToLongFunction<String> hash) {
        if (capacity <= 0) throw new IllegalArgumentException("Capacity must be positive");
        this.buckets = new Node[capacity];
        this.keyType = Objects.requireNonNull(keyType);
        this.hash = Objects.requireNonNull(hash);
    }

    @Override public void insert(MovieRecord movie) {
        String key = Objects.requireNonNull(keyType.of(Objects.requireNonNull(movie)), "Movie key");
        int index = Math.floorMod(hash.applyAsLong(key), buckets.length);
        Node added = new Node(key, movie);
        if (buckets[index] == null) {
            buckets[index] = added;
            used++;
        } else {
            Node current = buckets[index];
            while (true) {
                collisions++;
                if (current.next == null) { current.next = added; break; }
                current = current.next;
            }
        }
        size++;
    }

    @Override public List<MovieRecord> getAll(String key) {
        Objects.requireNonNull(key);
        int index = Math.floorMod(hash.applyAsLong(key), buckets.length);
        List<MovieRecord> found = new ArrayList<>();
        for (Node node = buckets[index]; node != null; node = node.next) {
            if (node.key.equals(key)) found.add(node.movie);
        }
        return List.copyOf(found);
    }

    @Override public int size() { return size; }
    @Override public int capacity() { return buckets.length; }
    @Override public int usedSlots() { return used; }
    @Override public long collisions() { return collisions; }
}
