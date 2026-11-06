import java.util.List;

/** All nine original CSV fields are retained without lossy numeric conversions. */
public record MovieRecord(String title, String genre, String releaseDate, String director,
                          String boxOfficeRevenue, String rating, String durationMinutes,
                          String productionCompany, String quote) {
    public static MovieRecord fromFields(List<String> fields) {
        if (fields.size() != 9) throw new IllegalArgumentException("Expected nine movie fields");
        return new MovieRecord(fields.get(0), fields.get(1), fields.get(2), fields.get(3),
                fields.get(4), fields.get(5), fields.get(6), fields.get(7), fields.get(8));
    }
}
