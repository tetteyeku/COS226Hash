import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.ToLongFunction;

/** Dependency-free regression tests; checks throw even without the Java -ea flag. */
public final class HashTableTests {
    private static int assertions;
    private HashTableTests() { }

    public static void main(String[] args) throws Exception {
        csvCases();
        chainCases();
        probingCases();
        hashAndCapacityCases();
        experimentCases();
        realDatasetCases();
        System.out.println("PASS: " + assertions + " assertions; " + Attempt.all().size()
                + " attempts; both keys; all 15,000 real records checked for each table.");
    }

    private static void csvCases() throws Exception {
        List<List<String>> rows = CsvReader.read(new StringReader("\uFEFFa,b,c\r\n\"A, B\",\"He said \"\"hi\"\"\",\r\n\"line\nline\",é,🙂\r\n\n"));
        equal(rows.size(), 3, "CSV row count including blank-line skip");
        equal(rows.get(1), List.of("A, B", "He said \"hi\"", ""), "Escaped quotes, commas, trailing empty field");
        equal(rows.get(2), List.of("line\nline", "é", "🙂"), "Multiline and Unicode");
        equal(CsvReader.read(new StringReader("a,b\rc,d")), List.of(List.of("a", "b"), List.of("c", "d")), "CR and EOF");
        equal(CsvReader.read(new StringReader("\"\"")), List.of(List.of("")), "Quoted empty field at EOF");
        equal(CsvReader.read(new StringReader(",,")), List.of(List.of("", "", "")), "All empty fields at EOF");
        equal(CsvReader.read(new StringReader("")), List.of(), "Empty file");
        equal(CsvReader.read(new StringReader(" a , b ")).get(0), List.of(" a ", " b "), "Whitespace retained");
        fails(IOException.class, () -> CsvReader.read(new StringReader("\"unfinished")), "Unterminated quote");
        fails(IOException.class, () -> CsvReader.read(new StringReader("a\"b,c")), "Quote in unquoted field");
        fails(IOException.class, () -> CsvReader.read(new StringReader("\"a\"x,b")), "Junk after closing quote");
        fails(IOException.class, () -> MovieCsv.load(new StringReader("wrong,header\n")), "Bad header");
        String header = "movie_title,genre,release_date,director,box_office_revenue,rating,duration_minutes,production_company,quote\n";
        equal(MovieCsv.load(new StringReader(header)).size(), 0, "Header-only dataset");
        equal(MovieCsv.load(new StringReader(header + ",,,,,,,,\n")).get(0).title(), "", "Empty title allowed");
        fails(IOException.class, () -> MovieCsv.load(new StringReader(header + "a,b\n")), "Incorrect field count");
        fails(IOException.class, () -> MovieCsv.load(Path.of("this-file-does-not-exist.csv")), "Missing input");
    }

    private static void chainCases() throws Exception {
        HashTable table = new ChainedHashTable(5, Key.TITLE, key -> 0);
        MovieRecord a = movie("same", "q1");
        MovieRecord b = movie("different", "q2");
        MovieRecord c = movie("same", "q3");
        table.insert(a); table.insert(b); table.insert(c);
        equal(table.collisions(), 3L, "Chaining counts every traversed node: 0+1+2");
        equal(table.getAll("same"), List.of(a, c), "Duplicate title preserves records and order");
        equal(table.getAll("missing"), List.of(), "Missing key in occupied chain");
        equal(table.collisions(), 3L, "Lookup does not mutate construction count");
        equal(table.size(), 3, "Every record stored");
        equal(table.usedSlots(), 1, "Occupied buckets");
        equal(table.unusedSlots(), 4, "Unused buckets");
        fails(UnsupportedOperationException.class, () -> table.getAll("same").clear(), "Read-only retrieval list");
        fails(IllegalArgumentException.class, () -> new ChainedHashTable(0, Key.TITLE, key -> 0), "Zero capacity");
        fails(NullPointerException.class, () -> table.getAll(null), "Null lookup key rejected");
        for (ToLongFunction<String> hash : List.<ToLongFunction<String>>of(key -> -1, key -> Long.MIN_VALUE)) {
            HashTable negative = new ChainedHashTable(7, Key.QUOTE, hash);
            negative.insert(movie("x", ""));
            equal(negative.getAll("").size(), 1, "Negative hashes and empty keys");
        }
    }

    private static void probingCases() throws Exception {
        HashTable table = new LinearProbingHashTable(3, Key.TITLE, key -> -1);
        MovieRecord a = movie("same", "a");
        MovieRecord b = movie("other", "b");
        MovieRecord c = movie("same", "c");
        table.insert(a); table.insert(b); table.insert(c);
        equal(table.collisions(), 3L, "Linear probing counts occupied slots: 0+1+2");
        equal(table.getAll("same"), List.of(a, c), "Wrapped duplicate cluster order");
        equal(table.getAll("missing"), List.of(), "Full-table missing lookup terminates");
        equal(table.unusedSlots(), 0, "Full-table waste");
        equal(table.usedSlots(), 3, "Probing occupied slots");
        fails(IllegalStateException.class, () -> table.insert(movie("overflow", "d")), "Full insertion rejects");
        equal(table.collisions(), 3L, "Failed full insert leaves counter unchanged");
        fails(IllegalArgumentException.class, () -> new LinearProbingHashTable(0, Key.TITLE, key -> 0), "Zero probing capacity");
        fails(NullPointerException.class, () -> table.getAll(null), "Null probing key rejected");
        HashTable extremes = new LinearProbingHashTable(5, Key.QUOTE, key -> Long.MIN_VALUE);
        MovieRecord empty = movie("empty", "");
        extremes.insert(empty);
        equal(extremes.getAll(""), List.of(empty), "Probing empty key and minimum hash");
    }

    private static void hashAndCapacityCases() throws Exception {
        equal(HashFunction.LENGTH.applyAsLong("abcd"), 4L, "Length baseline");
        equal(HashFunction.POLYNOMIAL_31.applyAsLong("abc"), 96354L, "Polynomial known value");
        equal(HashFunction.CHARACTER_SUM.applyAsLong("abc"), 294L, "Character sum known value");
        equal(HashFunction.FNV1A_64.applyAsLong(""), 0xcbf29ce484222325L, "FNV offset basis for empty string");
        equal(HashFunction.FNV1A_64.applyAsLong("a"), 0x089be207b544f1e4L, "FNV over UTF-16LE known value");
        equal(HashFunction.FNV1A_64.applyAsLong("🙂"), 0xf47a500fb71281b8L, "FNV surrogate pair known value");
        equal(HashFunction.POLYNOMIAL_131_MIXED.applyAsLong(""), 0L, "Mixed polynomial empty value");
        check(HashFunction.POLYNOMIAL_131_MIXED.applyAsLong("ab") != HashFunction.POLYNOMIAL_131_MIXED.applyAsLong("ba"),
                "Mixed polynomial distinguishes order");
        check(HashFunction.POLYNOMIAL_131_MIXED.applyAsLong("abcd") != HashFunction.POLYNOMIAL_31.applyAsLong("abcd"),
                "Fifth hash calculation differs from polynomial 31");
        equal(HashFunction.CHARACTER_SUM.applyAsLong("ab"), HashFunction.CHARACTER_SUM.applyAsLong("ba"),
                "Sum demonstrates anagram collision");
        check(HashFunction.POLYNOMIAL_31.applyAsLong("ab") != HashFunction.POLYNOMIAL_31.applyAsLong("ba"),
                "Polynomial hashing distinguishes order");
        equal(Attempt.nextPrime(0), 2, "Empty input capacity");
        equal(Attempt.nextPrime(14), 17, "Next prime");
        equal(Attempt.nextPrime(17), 17, "Prime retained");
        fails(IllegalArgumentException.class, () -> Attempt.nextPrime(2147483648L), "Oversize capacity");
        for (HashFunction hash : HashFunction.values()) {
            equal(hash.applyAsLong("é\u0000🙂"), hash.applyAsLong("é\u0000🙂"), "Deterministic Unicode hashing");
        }
    }

    private static void experimentCases() {
        Random random = new Random(226);
        List<MovieRecord> records = new ArrayList<>();
        records.add(movie("", ""));
        records.add(movie("Café 🙂", "\"quoted\", comma\nand newline"));
        records.add(movie("x".repeat(100_000), "q".repeat(100_000)));
        for (int i = 0; i < 400; i++) records.add(movie("title-" + random.nextInt(53), "quote-" + random.nextInt(79)));
        for (Attempt attempt : Attempt.all()) {
            for (Key key : Key.values()) {
                HashTable table = HashSomethingOut.build(attempt, key, records);
                check(HashSomethingOut.verify(table, key, records) > 1, "Randomized multimap oracle");
                equal(table.size(), records.size(), "Record preservation");
                check(table.unusedSlots() >= 0 && table.unusedSlots() <= table.capacity(), "Waste bounds");
                HashTable empty = HashSomethingOut.build(attempt, key, List.of());
                equal(HashSomethingOut.verify(empty, key, List.of()), 0, "Empty dataset retrieval");
                equal(empty.collisions(), 0L, "Empty dataset collisions");
                equal(empty.unusedSlots(), empty.capacity(), "Empty dataset waste");
            }
        }
    }

    private static void realDatasetCases() throws Exception {
        Path path = Path.of("data/MOCK_DATA.csv");
        equal(HashSomethingOut.sha256(Files.readAllBytes(path)),
                "8d9ef41d7117c3043121d3a69cd62c76ffbe823732668fc4ef64b084a016cdc6", "Exact supplied dataset");
        List<MovieRecord> records = MovieCsv.load(path);
        equal(records.size(), 15_000, "Real dataset count");
        check(records.stream().anyMatch(m -> m.quote().length() > 48_000), "Very long real quote parsed");
        for (Attempt attempt : Attempt.all()) {
            for (Key key : Key.values()) {
                HashTable table = HashSomethingOut.build(attempt, key, records);
                equal(HashSomethingOut.verify(table, key, records), key == Key.TITLE ? 11_309 : 14_891,
                        "Every real key and record retrieved: attempt " + attempt.id() + " " + key);
            }
        }
    }

    private static MovieRecord movie(String title, String quote) {
        return new MovieRecord(title, "Drama", "", "", "", "", "", "", quote);
    }

    private static void equal(Object actual, Object expected, String message) {
        check(actual.equals(expected), message + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    @FunctionalInterface private interface CheckedAction { void run() throws Exception; }
    private static void fails(Class<? extends Throwable> type, CheckedAction action, String message) throws Exception {
        assertions++;
        try { action.run(); }
        catch (Throwable error) {
            if (type.isInstance(error)) return;
            throw new AssertionError(message + ": wrong exception", error);
        }
        throw new AssertionError(message + ": expected " + type.getSimpleName());
    }
}
