import java.util.function.ToLongFunction;

/** Each experiment adds a genuinely different hash calculation here. */
public enum HashFunction implements ToLongFunction<String> {
    LENGTH {
        /** Deliberately poor baseline: equal-length strings always collide. */
        @Override public long applyAsLong(String key) { return key.length(); }
    },
    POLYNOMIAL_31 {
        /**
         * Order-sensitive full-string hash: h = 31*h + UTF-16 code unit.
         * Intentional 32-bit overflow provides a cheap, deterministic wraparound.
         * Unlike length, equal-length titles can now reach different buckets.
         */
        @Override public long applyAsLong(String key) {
            int hash = 0;
            for (int i = 0; i < key.length(); i++) hash = 31 * hash + key.charAt(i);
            return hash;
        }
    },
    CHARACTER_SUM {
        /**
         * Deliberately weak full-string hash: sums code units without position.
         * Anagrams collide, and ordinary text reaches a narrow interval of sums.
         * This differs from both length-only and order-sensitive polynomial hashing.
         */
        @Override public long applyAsLong(String key) {
            long sum = 0;
            for (int i = 0; i < key.length(); i++) sum += key.charAt(i);
            return sum;
        }
    }
}
