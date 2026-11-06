import java.util.List;

/** Immutable configurations keep each measured historical experiment reproducible. */
public record Attempt(int id, String description, Method method, HashFunction hash, double targetLoad) {
    public enum Method { CHAINING, LINEAR_PROBING }

    public static List<Attempt> all() {
        return List.of(
            new Attempt(1, "Linked list / string length", Method.CHAINING, HashFunction.LENGTH, 1.0),
            // Same capacity and collision policy isolate the effect of full-string hashing.
            new Attempt(2, "Linked list / polynomial 31", Method.CHAINING, HashFunction.POLYNOMIAL_31, 1.0),
            // Switching collision strategy and using an order-insensitive sum exposes clustering.
            new Attempt(3, "Linear probing / character sum", Method.LINEAR_PROBING, HashFunction.CHARACTER_SUM, 0.8),
            // Same probing policy and capacity isolate stronger, order-sensitive mixing.
            new Attempt(4, "Linear probing / FNV-1a 64", Method.LINEAR_PROBING, HashFunction.FNV1A_64, 0.8),
            // Attempt 2 won collision count and timing among 1..4. Compact its bucket
            // array by one third while changing the polynomial width/base and mixing.
            // Chaining safely permits a record load above one; probing would not.
            new Attempt(5, "Compact linked list / mixed polynomial 131", Method.CHAINING,
                    HashFunction.POLYNOMIAL_131_MIXED, 1.5)
        );
    }

    public HashTable create(int recordCount, Key key) {
        if (recordCount < 0) throw new IllegalArgumentException("Record count must be nonnegative");
        if (!Double.isFinite(targetLoad) || targetLoad <= 0) throw new IllegalArgumentException("Invalid target load");
        long required = (long) Math.ceil(recordCount / targetLoad);
        int capacity = nextPrime(required);
        if (method == Method.LINEAR_PROBING && capacity <= recordCount) {
            throw new IllegalArgumentException("Linear probing requires spare capacity");
        }
        return switch (method) {
            case CHAINING -> new ChainedHashTable(capacity, key, hash);
            case LINEAR_PROBING -> new LinearProbingHashTable(capacity, key, hash);
        };
    }

    /** Prime bucket counts avoid accidental common factors in modular indexing. */
    static int nextPrime(long minimum) {
        if (minimum <= 2) return 2;
        for (long candidate = minimum % 2 == 0 ? minimum + 1 : minimum;
             candidate <= Integer.MAX_VALUE; candidate += 2) {
            boolean prime = true;
            for (long divisor = 3; divisor * divisor <= candidate; divisor += 2) {
                if (candidate % divisor == 0) { prime = false; break; }
            }
            if (prime) return (int) candidate;
        }
        throw new IllegalArgumentException("Requested hash table is too large");
    }
}
