package io.github.rkaushikdev.sheaveline.board;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * One thing that happened on one machine, and how long the department has before the board hears
 * about it.
 *
 * <p>Two things decide the deadline and they are read independently, and a third joins them when
 * more than a couple of people were hurt. What happened to the machine,
 * and what happened to the people. A deropement with nobody hurt is still a two hour telephone
 * call, because the board wants somebody looking at the tower before the evidence is groomed over.
 * A twisted ankle at the unload ramp of a machine that behaved perfectly is a line in the monthly
 * return.
 *
 * <p>When both fire, the shorter deadline wins. That is the only rule here that is not a lookup,
 * and it is the one people get wrong, because the instinct is to file under the more dramatic
 * heading rather than the more urgent one.
 *
 * @param lift the machine
 * @param at when it happened
 * @param kind what the machine did
 * @param injury what happened to the people
 * @param people how many people were involved
 * @param narrative what happened, in the reporter's words
 * @param reportedBy who reported it
 */
public record Incident(
        LiftCode lift,
        LocalDateTime at,
        IncidentKind kind,
        InjuryLevel injury,
        int people,
        String narrative,
        String reportedBy) {

    /** A telephone call to the board: two hours. */
    public static final Duration IMMEDIATE = Duration.ofHours(2);

    /** A written report by the next day: twenty four hours. */
    public static final Duration NEXT_DAY = Duration.ofHours(24);

    /** A line in the monthly return: thirty days. */
    public static final Duration MONTHLY = Duration.ofHours(24 * 30);

    /** This many people involved, with somebody taken to the clinic, is a multiple casualty. */
    public static final int MULTIPLE_CASUALTIES = 3;

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm", Locale.ROOT);

    /**
     * An incident.
     *
     * @throws BoardError when fewer than nobody was involved, somebody was hurt and nobody was
     *     involved, or it has no narrative or no reporter
     */
    public Incident {
        Objects.requireNonNull(lift, "lift");
        Objects.requireNonNull(at, "at");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(injury, "injury");
        Objects.requireNonNull(narrative, "narrative");
        Objects.requireNonNull(reportedBy, "reportedBy");
        if (people < 0) {
            throw new BoardError("an incident cannot involve fewer than nobody");
        }
        if (injury != InjuryLevel.NONE && people < 1) {
            throw new BoardError("somebody was hurt, so somebody was involved");
        }
        if (narrative.strip().isEmpty()) {
            throw new BoardError("an incident carries a narrative");
        }
        if (reportedBy.strip().isEmpty()) {
            throw new BoardError("an incident is signed by whoever reported it");
        }
    }

    /**
     * How long the department has to tell the board: the shortest of the deadline for what the
     * machine did, the deadline for what happened to the people, and the deadline for how many of
     * them it happened to.
     *
     * @return the deadline
     */
    public Duration deadline() {
        Duration shortest = kind.deadline();
        for (Duration one : List.of(injury.deadline(), casualtyDeadline())) {
            if (one.compareTo(shortest) < 0) {
                shortest = one;
            }
        }
        return shortest;
    }

    /**
     * The deadline for the number of people hurt, read apart from how badly the worst of them was.
     *
     * <p>Three people off the same chair to the clinic is a two hour call however each of them is
     * doing, because the board treats a multiple casualty as an event in its own right. Anything less
     * than that leaves it to the other two clocks.
     *
     * @return {@link #IMMEDIATE} for {@link #MULTIPLE_CASUALTIES} or more people involved in an
     *     incident where somebody was transported, otherwise {@link #MONTHLY}
     */
    public Duration casualtyDeadline() {
        boolean transported = injury.deadline().compareTo(NEXT_DAY) <= 0;
        return transported && people >= MULTIPLE_CASUALTIES ? IMMEDIATE : MONTHLY;
    }

    /**
     * Which of the deadlines decided it.
     *
     * @return {@code kind} or {@code injury} when one alone did, {@code both} when those two are equal
     *     and shortest, and {@code people} named alongside whichever others it equals when the number
     *     of casualties set it too
     */
    public String deadlineDriver() {
        Duration wanted = deadline();
        boolean byKind = kind.deadline().equals(wanted);
        boolean byInjury = injury.deadline().equals(wanted);
        boolean byPeople =
                casualtyDeadline().equals(wanted) && casualtyDeadline().equals(IMMEDIATE);
        List<String> named = new ArrayList<>();
        if (byKind && byInjury) {
            named.add("both");
        } else if (byKind) {
            named.add("kind");
        } else if (byInjury) {
            named.add("injury");
        }
        if (byPeople) {
            named.add("people");
        }
        return String.join(" and ", named);
    }

    /**
     * When the board has to have heard.
     *
     * @return the deadline after the moment it happened
     */
    public LocalDateTime dueBy() {
        return at.plusSeconds(deadline().seconds());
    }

    /**
     * Whether it is a two hour call.
     *
     * @return true when the deadline is {@link #IMMEDIATE}
     */
    public boolean isImmediate() {
        return deadline().equals(IMMEDIATE);
    }

    /**
     * Whether it only ever needs a line in the month's return.
     *
     * @return true when the deadline is {@link #MONTHLY}
     */
    public boolean goesInTheMonthlyReturn() {
        return deadline().equals(MONTHLY);
    }

    /**
     * Whether the machine stays stopped until somebody has looked at it.
     *
     * @return true when the kind stops the machine or the injury is serious
     */
    public boolean holdsTheMachine() {
        return kind.stopsTheMachine() || injury.isSerious();
    }

    /**
     * Whether the deadline has passed with nothing filed.
     *
     * @param now the moment asked about
     * @return true once the moment is past the due time
     */
    public boolean isOverdue(LocalDateTime now) {
        return now.isAfter(dueBy());
    }

    /**
     * Whether the filing went out after the deadline. Once there is a filing, its moment is what
     * counts and the moment asked about is not read.
     *
     * @param now the moment asked about
     * @param filed when the filing went out
     * @return true when it went out past the due time
     */
    public boolean isOverdue(LocalDateTime now, LocalDateTime filed) {
        Objects.requireNonNull(now, "now");
        return isOverdue(filed);
    }

    /** Such as {@code JP deropement at 2025-01-15 11:30, none}. */
    @Override
    public String toString() {
        return lift + " " + kind.value() + " at " + at.format(STAMP) + ", " + injury.value();
    }
}
