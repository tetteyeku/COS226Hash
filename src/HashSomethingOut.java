import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * COS 226: Hash something out.
 *
 * Loads the supplied CSV once and builds TWO independent custom hash multimaps:
 * one keyed by movie title and one by quote. Every row is stored, even when keys
 * repeat. getAll returns matching records in input order. Empty strings are valid
 * keys; UTF-8, punctuation, quotes, and whitespace remain unchanged.
 *
 * Each experiment changes the hash calculation and/or collision strategy. Table
 * construction includes allocation, hashing, insertion, and collision accounting.
 * CSV I/O, correctness verification, console output, and result writing are OUTSIDE
 * the timer. Warmups and repeated samples reduce (but cannot eliminate) JVM noise.
 *
 * A collision is EACH occupied location examined while inserting, including
 * duplicate-key records. Lookups never change construction counters. Wasted space
 * is unused bucket references for chaining and unused array slots for probing;
 * these are not estimates of total bytes or linked-node allocation overhead.
 *
 * java.util.HashMap is used only as an independent correctness oracle outside the
 * measurements; neither custom table delegates storage or hashing to a library map.
 */
public final class HashSomethingOut {
    private HashSomethingOut() { }

    public static void main(String[] args) {
        try {
            Options options = Options.parse(args);
            if (options.help) { usage(); return; }
            List<MovieRecord> movies = MovieCsv.load(options.data);
            String dataHash = sha256(Files.readAllBytes(options.data));
            List<Attempt> attempts = Attempt.all().stream()
                    .filter(a -> options.attempt == 0 || a.id() == options.attempt).toList();
            if (attempts.isEmpty()) throw new IllegalArgumentException("Unknown attempt: " + options.attempt);
            if (options.output != null) Files.createDirectories(options.output);
            for (Attempt attempt : attempts) {
                List<Measurement> measurements = new ArrayList<>();
                for (Key key : Key.values()) {
                    measurements.add(measure(attempt, key, movies, options));
                }
                String report = report(attempt, measurements, options, dataHash);
                System.out.print(report);
                if (options.output != null) {
                    String name = String.format(Locale.ROOT, "attempt-%02d", attempt.id());
                    Files.writeString(options.output.resolve(name + ".txt"), report, StandardCharsets.UTF_8);
                    Files.writeString(options.output.resolve(name + ".csv"), csv(measurements), StandardCharsets.UTF_8);
                }
            }
        } catch (IOException | IllegalArgumentException | IllegalStateException error) {
            System.err.println("Error: " + error.getMessage());
            System.exit(1);
        }
    }

    private static Measurement measure(Attempt attempt, Key key, List<MovieRecord> movies, Options options) {
        for (int i = 0; i < options.warmups; i++) consume(build(attempt, key, movies));
        long[] nanos = new long[options.runs];
        HashTable last = null;
        long expectedCollisions = -1;
        for (int i = 0; i < options.runs; i++) {
            long start = System.nanoTime();
            last = build(attempt, key, movies);
            nanos[i] = System.nanoTime() - start;
            consume(last);
            if (expectedCollisions != -1 && last.collisions() != expectedCollisions) {
                throw new IllegalStateException("Collision counts changed between identical builds");
            }
            expectedCollisions = last.collisions();
        }
        if (last == null) throw new IllegalStateException("No measurement samples");
        int distinctKeys = verify(last, key, movies);
        String query = key == Key.TITLE ? options.title : options.quote;
        if (query != null) {
            System.out.println("Lookup " + key + " [" + query + "]: " + last.getAll(query).size() + " records");
            for (MovieRecord movie : last.getAll(query)) System.out.println("  " + movie);
        }
        return new Measurement(attempt, key, last.size(), distinctKeys, last.capacity(),
                last.usedSlots(), last.collisions(), nanos);
    }

    // Observable consumption prevents a JVM from treating unused warmup tables as dead work.
    private static volatile long sink;
    private static void consume(HashTable table) { sink = table.size() + table.collisions(); }

    static HashTable build(Attempt attempt, Key key, List<MovieRecord> movies) {
        HashTable table = attempt.create(movies.size(), key);
        for (MovieRecord movie : movies) table.insert(movie);
        return table;
    }

    /** Checks every distinct key and every duplicate record against an independent oracle. */
    static int verify(HashTable table, Key key, List<MovieRecord> movies) {
        Map<String, List<MovieRecord>> expected = new HashMap<>();
        for (MovieRecord movie : movies) {
            expected.computeIfAbsent(key.of(movie), unused -> new ArrayList<>()).add(movie);
        }
        long before = table.collisions();
        int retrieved = 0;
        for (Map.Entry<String, List<MovieRecord>> entry : expected.entrySet()) {
            List<MovieRecord> actual = table.getAll(entry.getKey());
            if (!actual.equals(entry.getValue())) {
                throw new IllegalStateException("Retrieval mismatch for " + key + ": " + entry.getKey());
            }
            retrieved += actual.size();
        }
        String missing = "\u0000COS226_MISSING_KEY";
        while (expected.containsKey(missing)) missing += "_";
        if (!table.getAll(missing).isEmpty()) throw new IllegalStateException("Missing key returned data");
        if (retrieved != movies.size() || table.size() != movies.size()) {
            throw new IllegalStateException("Record count mismatch");
        }
        if (table.collisions() != before) throw new IllegalStateException("Lookups changed insertion statistics");
        return expected.size();
    }

    private static String report(Attempt attempt, List<Measurement> measurements, Options options, String dataHash) {
        StringBuilder text = new StringBuilder();
        text.append("COS 226 | HASH SOMETHING OUT\n");
        text.append("Attempt ").append(attempt.id()).append(" | ").append(attempt.description()).append('\n');
        text.append("Hash: ").append(attempt.hash()).append(" | Collision method: ").append(attempt.method()).append('\n');
        text.append("Dataset: ").append(options.data).append('\n');
        text.append("Dataset SHA-256: ").append(dataHash).append('\n');
        text.append("Captured UTC: ").append(Instant.now()).append('\n');
        text.append("Environment: Java ").append(System.getProperty("java.version")).append(" | ")
                .append(System.getProperty("os.name")).append(' ').append(System.getProperty("os.arch")).append('\n');
        text.append("Timing: ").append(options.warmups).append(" warmups + ").append(options.runs)
                .append(" samples; construction only; median reported\n");
        for (Measurement m : measurements) {
            text.append('\n').append(m.key).append(" HASH TABLE\n");
            text.append(String.format(Locale.ROOT,
                    "Records: %,d | Distinct keys: %,d | Capacity: %,d%n", m.records, m.distinct, m.capacity));
            text.append(String.format(Locale.ROOT,
                    "Used %s: %,d | Wasted %s: %,d (%.2f%%)%n",
                    m.attempt.method() == Attempt.Method.CHAINING ? "buckets" : "slots", m.used,
                    m.attempt.method() == Attempt.Method.CHAINING ? "buckets" : "slots",
                    m.capacity - m.used, 100.0 * (m.capacity - m.used) / m.capacity));
            text.append(String.format(Locale.ROOT, "Record load: %.4f | Collisions: %,d%n", (double) m.records / m.capacity, m.collisions));
            text.append(String.format(Locale.ROOT, "Construction: median %.3f ms | min %.3f ms | max %.3f ms%n",
                    m.medianMs(), m.minMs(), m.maxMs()));
            text.append("Samples (ms): ").append(m.samplesMs()).append('\n');
            text.append("Verified: every record and duplicate retrieved; missing key empty; counters unchanged\n");
        }
        text.append("\nCollisions = occupied insertion locations examined (each linked node / probe).\n");
        text.append("Waste counts bucket/slot locations, not total allocated memory bytes.\n");
        text.append("Both tables verified independently against the original input.\n\n");
        return text.toString();
    }

    private static String csv(List<Measurement> measurements) {
        StringBuilder csv = new StringBuilder("attempt,key,method,hash,records,distinct_keys,capacity,used,wasted,waste_percent,load,collisions,median_ms,min_ms,max_ms,samples_ms\n");
        for (Measurement m : measurements) {
            csv.append(String.format(Locale.ROOT, "%d,%s,%s,%s,%d,%d,%d,%d,%d,%.6f,%.6f,%d,%.6f,%.6f,%.6f,%s%n",
                    m.attempt.id(), m.key, m.attempt.method(), m.attempt.hash(), m.records, m.distinct,
                    m.capacity, m.used, m.capacity - m.used, 100.0 * (m.capacity - m.used) / m.capacity,
                    (double) m.records / m.capacity, m.collisions, m.medianMs(), m.minMs(), m.maxMs(), m.samplesMs()));
        }
        return csv.toString();
    }

    private record Measurement(Attempt attempt, Key key, int records, int distinct, int capacity,
                               int used, long collisions, long[] nanos) {
        double medianMs() {
            long[] sorted = nanos.clone();
            Arrays.sort(sorted);
            return sorted[sorted.length / 2] / 1_000_000.0;
        }
        double minMs() { return Arrays.stream(nanos).min().orElseThrow() / 1_000_000.0; }
        double maxMs() { return Arrays.stream(nanos).max().orElseThrow() / 1_000_000.0; }
        String samplesMs() {
            List<String> samples = new ArrayList<>();
            for (long sample : nanos) samples.add(String.format(Locale.ROOT, "%.6f", sample / 1_000_000.0));
            return String.join(";", samples);
        }
    }

    static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }

    private static void usage() {
        System.out.println("Usage: java -cp build HashSomethingOut [options]\n"
                + "  --data PATH       UTF-8 CSV (default: data/MOCK_DATA.csv)\n"
                + "  --attempt N|all   Experiment to run (default: all)\n"
                + "  --warmups N       Unmeasured builds (default: 3)\n"
                + "  --runs N          Odd number of measured builds (default: 9)\n"
                + "  --output DIR      Save per-attempt text/CSV evidence (optional)\n"
                + "  --title TEXT      Retrieve all records matching this title\n"
                + "  --quote TEXT      Retrieve all records matching this quote\n"
                + "  --help            Show this help");
    }

    private static final class Options {
        Path data = Path.of("data/MOCK_DATA.csv");
        Path output;
        int attempt;
        int warmups = 3;
        int runs = 9;
        String title;
        String quote;
        boolean help;

        static Options parse(String[] args) {
            Options options = new Options();
            for (int i = 0; i < args.length; i++) {
                String flag = args[i];
                if (flag.equals("--help")) { options.help = true; continue; }
                if (i + 1 == args.length) throw new IllegalArgumentException("Missing value for " + flag);
                String value = args[++i];
                switch (flag) {
                    case "--data" -> options.data = Path.of(value);
                    case "--output" -> options.output = Path.of(value);
                    case "--attempt" -> options.attempt = value.equals("all") ? 0 : Integer.parseInt(value);
                    case "--warmups" -> options.warmups = Integer.parseInt(value);
                    case "--runs" -> options.runs = Integer.parseInt(value);
                    case "--title" -> options.title = value;
                    case "--quote" -> options.quote = value;
                    default -> throw new IllegalArgumentException("Unknown option: " + flag);
                }
            }
            if (options.attempt < 0) throw new IllegalArgumentException("Attempt must be positive or all");
            if (options.warmups < 0 || options.warmups > 100) throw new IllegalArgumentException("Warmups must be 0..100");
            if (options.runs < 1 || options.runs > 101 || options.runs % 2 == 0) {
                throw new IllegalArgumentException("Runs must be odd, in 1..101");
            }
            return options;
        }
    }
}
