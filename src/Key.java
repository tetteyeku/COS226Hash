/** Separate table instances index the same records by these two independent keys. */
public enum Key {
    TITLE, QUOTE;

    public String of(MovieRecord movie) {
        return this == TITLE ? movie.title() : movie.quote();
    }
}
