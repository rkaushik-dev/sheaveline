package io.github.rkaushikdev.sheaveline.ops;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * One line in the day book.
 *
 * @param at when it was written
 * @param kind what sort of line it is
 * @param text what it says
 * @param by who signed it
 */
public record LogEntry(LocalDateTime at, EntryKind kind, String text, String by) {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    /**
     * A signed line that says something.
     *
     * @throws OperationsError when the text or the signature is blank
     */
    public LogEntry {
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(by, "by");
        if (text.strip().isEmpty()) {
            throw new OperationsError("a log line says something");
        }
        if (by.strip().isEmpty()) {
            throw new OperationsError("a log line is signed");
        }
    }

    /** Such as {@code 11:05 K Ostler: wind picking up}. */
    @Override
    public String toString() {
        return at.format(CLOCK) + " " + by + ": " + text;
    }
}
