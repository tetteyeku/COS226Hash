import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Small CSV state machine: commas, escaped double quotes, multiline quoted fields,
 * CRLF/LF/CR record endings, a UTF-8 BOM, and trailing empty fields are supported.
 * Empty physical lines are ignored; a line containing commas or quotes is data.
 * Malformed quoting is rejected instead of silently moving data into wrong columns.
 */
public final class CsvReader {
    private CsvReader() { }

    public static List<List<String>> read(Reader input) throws IOException {
        PushbackReader reader = new PushbackReader(input, 1);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        int state = 0; // 0: unquoted; 1: quoted; 2: closing quote seen
        boolean started = false;
        boolean first = true;
        int value;
        while ((value = reader.read()) != -1) {
            char c = (char) value;
            if (first && c == '\uFEFF') { first = false; continue; }
            first = false;
            if (state == 1) {
                if (c == '"') state = 2;
                else field.append(c);
                continue;
            }
            if (state == 2 && c == '"') {
                field.append('"');
                state = 1;
                continue;
            }
            if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
                state = 0;
                started = true;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r') {
                    int next = reader.read();
                    if (next != '\n' && next != -1) reader.unread(next);
                }
                if (started || !row.isEmpty() || field.length() > 0 || state == 2) {
                    row.add(field.toString());
                    rows.add(List.copyOf(row));
                }
                row.clear();
                field.setLength(0);
                started = false;
                state = 0;
            } else if (state == 2) {
                throw new IOException("Unexpected text after closing quote near record " + (rows.size() + 1));
            } else if (c == '"') {
                if (field.length() != 0) throw new IOException("Quote inside unquoted CSV field");
                state = 1;
                started = true;
            } else {
                field.append(c);
                started = true;
            }
        }
        if (state == 1) throw new IOException("Unterminated quoted CSV field");
        if (started || !row.isEmpty() || field.length() > 0 || state == 2) {
            row.add(field.toString());
            rows.add(List.copyOf(row));
        }
        return List.copyOf(rows);
    }
}
