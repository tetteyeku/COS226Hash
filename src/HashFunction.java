import java.util.function.ToLongFunction;

/** Each experiment adds a genuinely different hash calculation here. */
public enum HashFunction implements ToLongFunction<String> {
    LENGTH {
        /** Deliberately poor baseline: equal-length strings always collide. */
        @Override public long applyAsLong(String key) { return key.length(); }
    }
}
