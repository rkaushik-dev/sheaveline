package io.github.rkaushikdev.sheaveline.weather;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * One machine held for one reason, with who did it and when.
 *
 * @param lift the machine
 * @param reason what held it
 * @param started when the hold was called
 * @param startedBy who called it
 * @param detail the figures the hold was called on
 * @param ended when it came off, while it is closed
 * @param endedBy who took it off, while it is closed
 */
public record HoldRecord(
        LiftCode lift,
        HoldReason reason,
        LocalDateTime started,
        String startedBy,
        String detail,
        Optional<LocalDateTime> ended,
        Optional<String> endedBy) {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    /**
     * A hold.
     *
     * @throws WeatherError when it is unsigned, ends before it started, or has only one of the
     *     closing moment and the person who closed it
     */
    public HoldRecord {
        Objects.requireNonNull(lift, "lift");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(started, "started");
        Objects.requireNonNull(startedBy, "startedBy");
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(ended, "ended");
        Objects.requireNonNull(endedBy, "endedBy");
        if (startedBy.strip().isEmpty()) {
            throw new WeatherError("a hold is signed by whoever called it");
        }
        if (ended.isPresent() && ended.get().isBefore(started)) {
            throw new WeatherError("a hold cannot end before it started");
        }
        if (ended.isPresent() != endedBy.isPresent()) {
            throw new WeatherError("a hold is closed by somebody at some time, or neither");
        }
    }

    /**
     * An open hold with no figures written against it.
     *
     * @param lift the machine
     * @param reason what held it
     * @param started when the hold was called
     * @param startedBy who called it
     */
    public HoldRecord(LiftCode lift, HoldReason reason, LocalDateTime started, String startedBy) {
        this(lift, reason, started, startedBy, "");
    }

    /**
     * An open hold.
     *
     * @param lift the machine
     * @param reason what held it
     * @param started when the hold was called
     * @param startedBy who called it
     * @param detail the figures the hold was called on
     */
    public HoldRecord(LiftCode lift, HoldReason reason, LocalDateTime started, String startedBy, String detail) {
        this(lift, reason, started, startedBy, detail, Optional.empty(), Optional.empty());
    }

    /**
     * Whether the hold is still on.
     *
     * @return true until somebody closes it
     */
    public boolean isOpen() {
        return ended.isEmpty();
    }

    /**
     * How long a closed hold lasted.
     *
     * @return the whole seconds from start to end
     * @throws WeatherError when the hold is still open
     */
    public Duration length() {
        return measure(Optional.empty());
    }

    /**
     * How long the hold lasted, or has lasted so far.
     *
     * @param now the moment an open hold is measured to; a closed hold is measured to its end
     * @return the whole seconds
     * @throws WeatherError when the moment is before the hold started
     */
    public Duration length(LocalDateTime now) {
        return measure(Optional.of(now));
    }

    /**
     * The same hold, closed.
     *
     * @param when when it came off
     * @param by who took it off
     * @return a new record; this one is unchanged
     * @throws WeatherError when the hold is already closed
     */
    public HoldRecord closed(LocalDateTime when, String by) {
        if (!isOpen()) {
            throw new WeatherError("the " + reason.value() + " hold on " + lift + " is already closed");
        }
        return new HoldRecord(lift, reason, started, startedBy, detail, Optional.of(when), Optional.of(by));
    }

    /** Such as {@code JP gust from 10:00, open} or {@code JP gust from 10:00, closed 11:30}. */
    @Override
    public String toString() {
        String state = ended.map(end -> "closed " + end.format(CLOCK)).orElse("open");
        return lift + " " + reason.value() + " from " + started.format(CLOCK) + ", " + state;
    }

    private Duration measure(Optional<LocalDateTime> now) {
        LocalDateTime finish = ended.or(() -> now)
                .orElseThrow(() -> new WeatherError("an open hold needs a moment to be measured against"));
        if (finish.isBefore(started)) {
            throw new WeatherError("that moment is before the hold started");
        }
        return Duration.ofSeconds(ChronoUnit.SECONDS.between(started, finish));
    }
}
