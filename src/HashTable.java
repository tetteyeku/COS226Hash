import java.util.List;

/** A fixed-capacity multimap: duplicate keys retain every input movie, in input order. */
public interface HashTable {
    void insert(MovieRecord movie);
    List<MovieRecord> getAll(String key);
    int size();
    int capacity();
    int usedSlots();
    long collisions();

    default int unusedSlots() { return capacity() - usedSlots(); }
}
