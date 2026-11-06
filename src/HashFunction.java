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
    },
    FNV1A_64 {
        /**
         * FNV-1a, 64 bits, hashing the two bytes of each UTF-16 code unit in
         * low-byte-first order. This covers all Java strings without allocating
         * an encoded byte array. XOR then multiply mixes character order.
         * Java long overflow intentionally computes modulo 2^64.
         * Constants/recurrence: https://www.rfc-editor.org/rfc/rfc9923.html
         */
        @Override public long applyAsLong(String key) {
            long hash = 0xcbf29ce484222325L;
            for (int i = 0; i < key.length(); i++) {
                char c = key.charAt(i);
                hash = (hash ^ (c & 0xff)) * 0x100000001b3L;
                hash = (hash ^ (c >>> 8)) * 0x100000001b3L;
            }
            return hash;
        }
    },
    POLYNOMIAL_131_MIXED {
        /**
         * Fifth attempt extends successful full-string polynomial chaining.
         * Accumulate in 64 bits with a different base (131), then xor shifted
         * high bits into low bits around multiplication by a fixed odd constant.
         * This is a noncryptographic mixing experiment, not a perfect hash.
         * Pair it with denser chaining to reduce empty bucket references.
         */
        @Override public long applyAsLong(String key) {
            long hash = 0;
            for (int i = 0; i < key.length(); i++) hash = 131 * hash + key.charAt(i);
            hash ^= hash >>> 32;
            hash *= 0x9e3779b97f4a7c15L;
            hash ^= hash >>> 29;
            return hash;
        }
    }
}
