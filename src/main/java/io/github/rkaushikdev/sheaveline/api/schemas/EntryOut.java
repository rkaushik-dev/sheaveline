package io.github.rkaushikdev.sheaveline.api.schemas;

import io.github.rkaushikdev.sheaveline.ops.LogEntry;
import java.time.LocalDateTime;

/**
 * One line in the day book.
 *
 * @param at when it was written
 * @param kind what sort of line it is
 * @param text what it says
 * @param by who wrote it
 */
public record EntryOut(LocalDateTime at, String kind, String text, String by) {

    /**
     * A line as the tablet shows it.
     *
     * @param entry the line
     * @return its shape
     */
    public static EntryOut of(LogEntry entry) {
        return new EntryOut(entry.at(), entry.kind().value(), entry.text(), entry.by());
    }
}
