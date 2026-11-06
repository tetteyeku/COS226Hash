# Supplied movie records

`MOCK_DATA.csv` is the user-provided fake movie dataset for this assignment, with
15,000 records and nine columns. No records were removed, rewritten or generated
for these experiments. The full-file SHA-256 is checked by the test runner.

The tables retain all nine fields as text so dates, money formatting, punctuation,
empty strings and Unicode survive without conversion loss. Only `movie_title`
and `quote` are used as hash keys.
