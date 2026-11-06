import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Validates the supplied schema, preserving empty keys, whitespace and Unicode. */
public final class MovieCsv {
    private static final List<String> HEADER = List.of("movie_title", "genre", "release_date",
            "director", "box_office_revenue", "rating", "duration_minutes", "production_company", "quote");

    private MovieCsv() { }

    public static List<MovieRecord> load(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return load(reader);
        }
    }

    static List<MovieRecord> load(Reader reader) throws IOException {
        List<List<String>> rows = CsvReader.read(reader);
        if (rows.isEmpty() || !rows.get(0).equals(HEADER)) {
            throw new IOException("Expected CSV header: " + String.join(",", HEADER));
        }
        List<MovieRecord> movies = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            if (rows.get(i).size() != HEADER.size()) {
                throw new IOException("CSV record " + (i + 1) + ": expected 9 fields, found " + rows.get(i).size());
            }
            movies.add(MovieRecord.fromFields(rows.get(i)));
        }
        return List.copyOf(movies);
    }
}
