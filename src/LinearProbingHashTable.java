import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.ToLongFunction;

/**
 * Open addressing: hash to a home slot, then move one slot at a time, wrapping
 * at the end. Each occupied slot examined during insertion adds one collision.
 * Duplicate-key records occupy separate slots; getAll continues to the first
 * empty slot (or one full cycle), preserving duplicate insertion order even when
 * the cluster wraps. No deletion means no tombstones are needed.
 *
 * Experiments preallocate spare capacity from the known record count. Direct use
 * rejects a full table immediately, and every lookup is bounded by capacity;
 * neither insertion nor a missing-key lookup can loop forever in a full table.
 */
public final class LinearProbingHashTable implements HashTable {
    private final MovieRecord[] slots;
    private final String[] keys;
    private final Key keyType;
    private final ToLongFunction<String> hash;
    private int size;
    private long collisions;

    public LinearProbingHashTable(int capacity, Key keyType, ToLongFunction<String> hash) {
        if (capacity <= 0) throw new IllegalArgumentException("Capacity must be positive");
        this.slots = new MovieRecord[capacity];
        this.keys = new String[capacity];
        this.keyType = Objects.requireNonNull(keyType);
        this.hash = Objects.requireNonNull(hash);
    }

    @Override public void insert(MovieRecord movie) {
        String key = Objects.requireNonNull(keyType.of(Objects.requireNonNull(movie)), "Movie key");
        if (size == slots.length) throw new IllegalStateException("Linear-probing table is full");
        int index = Math.floorMod(hash.applyAsLong(key), slots.length);
        while (slots[index] != null) {
            collisions++;
            index = next(index);
        }
        slots[index] = movie;
        keys[index] = key;
        size++;
    }

    private int next(int index) { return index + 1 == slots.length ? 0 : index + 1; }

    @Override public List<MovieRecord> getAll(String key) {
        Objects.requireNonNull(key);
        int index = Math.floorMod(hash.applyAsLong(key), slots.length);
        List<MovieRecord> found = new ArrayList<>();
        for (int inspected = 0; inspected < slots.length && slots[index] != null; inspected++) {
            if (keys[index].equals(key)) found.add(slots[index]);
            index = next(index);
        }
        return List.copyOf(found);
    }

    @Override public int size() { return size; }
    @Override public int capacity() { return slots.length; }
    @Override public int usedSlots() { return size; }
    @Override public long collisions() { return collisions; }
}
