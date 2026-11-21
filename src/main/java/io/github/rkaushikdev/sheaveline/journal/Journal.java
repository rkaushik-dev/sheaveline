package io.github.rkaushikdev.sheaveline.journal;

import java.io.Flushable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * What the software did, which is a different book from what the machines did.
 *
 * <p>The word log is taken. On this hill a log is the day book an operator writes in, and calling
 * the application's own output a log would guarantee that somebody eventually greps the wrong one
 * during an incident. So this is the journal.
 *
 * <p>One line a record and the fields in a fixed order, because the thing that reads it is usually
 * grep on somebody's laptop at nine at night. Fields go on as {@code key=value}, with the value
 * quoted if it has a space in it.
 *
 * <p>Nothing here reaches for a global logger. The journal is built by whoever is running, handed
 * down, and can be switched off entirely, which a command that prints an eighty column report needs:
 * a stray journal line in the middle of the report is a report nobody can parse.
 *
 * <p>Fields are passed as alternating names and values, in the order they should print: {@code
 * journal.info("request", "method", "GET", "status", 200)}.
 */
public final class Journal {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final Appendable stream;
    private final Level level;
    private final String clock;
    private final SequencedMap<String, Object> context;

    /**
     * A journal writing to a stream at a level, stamping each line with the wall clock.
     *
     * @param stream where the lines go
     * @param level the lowest level written
     */
    public Journal(Appendable stream, Level level) {
        this(stream, level, "");
    }

    /**
     * A journal with a fixed stamp, which is what a test wants.
     *
     * @param stream where the lines go
     * @param level the lowest level written
     * @param clock the stamp every line carries, or empty for the wall clock
     */
    public Journal(Appendable stream, Level level, String clock) {
        this(stream, level, clock, new LinkedHashMap<>());
    }

    private Journal(Appendable stream, Level level, String clock, SequencedMap<String, Object> context) {
        this.stream = stream;
        this.level = level;
        this.clock = clock;
        this.context = Collections.unmodifiableSequencedMap(context);
    }

    /**
     * A journal on standard error.
     *
     * @param level the lowest level written
     * @return the journal
     */
    public static Journal toStderr(Level level) {
        return new Journal(System.err, level);
    }

    /**
     * A journal that writes nothing, which is what a report command wants.
     *
     * @return the journal
     */
    public static Journal silent() {
        return new Journal(System.err, Level.OFF);
    }

    /**
     * Where the lines go.
     *
     * @return the stream
     */
    public Appendable stream() {
        return stream;
    }

    /**
     * The lowest level written.
     *
     * @return the level
     */
    public Level level() {
        return level;
    }

    /**
     * Whether a line at that level would be written.
     *
     * @param wanted the level of the line
     * @return true when it clears the bar and the journal is not off
     */
    public boolean wants(Level wanted) {
        return level != Level.OFF && wanted.compareTo(level) >= 0;
    }

    /**
     * The same journal with more on every line it writes from here on. This journal is unchanged.
     *
     * @param pairs alternating field names and values
     * @return the journal carrying the extra fields
     */
    public Journal about(Object... pairs) {
        SequencedMap<String, Object> carried = new LinkedHashMap<>(context);
        carried.putAll(paired(pairs));
        return new Journal(stream, level, clock, carried);
    }

    /**
     * Write a line.
     *
     * @param at its level
     * @param event what happened, one word or a dotted name
     * @param pairs alternating field names and values
     */
    public void write(Level at, String event, Object... pairs) {
        if (!wants(at)) {
            return;
        }
        List<String> parts = new ArrayList<>();
        parts.add(clock.isEmpty() ? LocalDateTime.now().format(STAMP) : clock);
        parts.add(at.label());
        parts.add(event);
        context.forEach((name, value) -> parts.add(field(name, value)));
        paired(pairs).forEach((name, value) -> parts.add(field(name, value)));
        try {
            stream.append(String.join(" ", parts)).append('\n');
            if (stream instanceof Flushable flushable) {
                flushable.flush();
            }
        } catch (IOException failed) {
            throw new UncheckedIOException(failed);
        }
    }

    /**
     * Write a line at debug.
     *
     * @param event what happened
     * @param pairs alternating field names and values
     */
    public void debug(String event, Object... pairs) {
        write(Level.DEBUG, event, pairs);
    }

    /**
     * Write a line at info.
     *
     * @param event what happened
     * @param pairs alternating field names and values
     */
    public void info(String event, Object... pairs) {
        write(Level.INFO, event, pairs);
    }

    /**
     * Write a line at warn.
     *
     * @param event what happened
     * @param pairs alternating field names and values
     */
    public void warn(String event, Object... pairs) {
        write(Level.WARN, event, pairs);
    }

    /**
     * Write a line at error.
     *
     * @param event what happened
     * @param pairs alternating field names and values
     */
    public void error(String event, Object... pairs) {
        write(Level.ERROR, event, pairs);
    }

    /**
     * Do the work, then write one line saying how long it took and whether it went through.
     *
     * <p>When the work throws, the line is written at error with the exception's simple class name
     * and the exception carries on.
     *
     * @param event what the work is
     * @param work the work
     * @param pairs alternating field names and values
     * @param <T> what the work hands back
     * @return what the work handed back
     */
    public <T> T timing(String event, Supplier<T> work, Object... pairs) {
        long started = System.nanoTime();
        T made;
        try {
            made = work.get();
        } catch (RuntimeException | Error wentWrong) {
            write(Level.ERROR, event, joined(started, "failed", wentWrong, pairs));
            throw wentWrong;
        }
        write(Level.INFO, event, joined(started, "ok", null, pairs));
        return made;
    }

    /**
     * Do work that hands nothing back, then write the timing line.
     *
     * @param event what the work is
     * @param work the work
     * @param pairs alternating field names and values
     */
    public void timing(String event, Runnable work, Object... pairs) {
        timing(
                event,
                () -> {
                    work.run();
                    return null;
                },
                pairs);
    }

    private static Object[] joined(long started, String outcome, Throwable wentWrong, Object... pairs) {
        List<Object> all = new ArrayList<>();
        all.add("ms");
        all.add(Math.round((System.nanoTime() - started) / 1_000_000.0));
        all.add("outcome");
        all.add(outcome);
        if (wentWrong != null) {
            all.add("error");
            all.add(wentWrong.getClass().getSimpleName());
        }
        all.addAll(List.of(pairs));
        return all.toArray();
    }

    /**
     * One key and its value, the value quoted only when it needs to be: when it is empty, or holds
     * white space or a double quote. Inside quotes a backslash and a double quote are escaped.
     *
     * @param name the key
     * @param value the value, or null for an empty one
     * @return {@code name=value} or {@code name="the value"}
     */
    public static String field(String name, Object value) {
        String text = value == null ? "" : String.valueOf(value);
        boolean quoted =
                text.isEmpty() || text.indexOf('"') >= 0 || text.codePoints().anyMatch(Journal::isSpace);
        if (!quoted) {
            return name + "=" + text;
        }
        return name + "=\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /**
     * A request id: short enough to read out over the radio, long enough not to collide.
     *
     * @return twelve lower case hex digits
     */
    public static String newRequestId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private static boolean isSpace(int point) {
        return Character.isWhitespace(point) || Character.isSpaceChar(point);
    }

    private static SequencedMap<String, Object> paired(Object... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("journal fields come in name and value pairs");
        }
        SequencedMap<String, Object> fields = new LinkedHashMap<>();
        for (int at = 0; at < pairs.length; at += 2) {
            if (!(pairs[at] instanceof String name)) {
                throw new IllegalArgumentException("a journal field name is text, not " + pairs[at]);
            }
            fields.put(name, pairs[at + 1]);
        }
        return fields;
    }

    /**
     * The fields every line carries, in the order they were added.
     *
     * @return the carried fields
     */
    public Map<String, Object> context() {
        return context;
    }
}
