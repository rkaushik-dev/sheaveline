package io.github.rkaushikdev.sheaveline.ops;

import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.plant.LiftClass;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.schedule.OperatingWindow;
import io.github.rkaushikdev.sheaveline.schedule.TimeOfDay;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Everybody assigned to one machine on one day, and the minutes nobody is where they should be.
 *
 * <p>The gap this exists to find is the one nobody notices: two operators, one leaves at noon for
 * lunch, the other arrives at ten past, and for ten minutes the top terminal is unmanned while the
 * lift keeps turning.
 *
 * @param lift the machine
 * @param liftClass its class, which decides the posts it needs
 * @param day the day
 * @param window when it runs that day
 * @param assignments who is where, by post and then by start
 */
public record Roster(
        LiftCode lift, LiftClass liftClass, LocalDate day, OperatingWindow window, List<Assignment> assignments) {

    /**
     * A roster in which nobody is in two places at once.
     *
     * @throws OperationsError when one person holds two stretches that overlap
     */
    public Roster {
        Objects.requireNonNull(lift, "lift");
        Objects.requireNonNull(liftClass, "liftClass");
        Objects.requireNonNull(day, "day");
        Objects.requireNonNull(window, "window");
        assignments = List.copyOf(assignments);
        for (int first = 0; first < assignments.size(); first++) {
            Assignment one = assignments.get(first);
            for (Assignment other : assignments.subList(first + 1, assignments.size())) {
                if (one.person().equals(other.person()) && one.overlaps(other)) {
                    throw new OperationsError(one.person() + " is in two places at once");
                }
            }
        }
    }

    /**
     * A roster with nobody on it yet.
     *
     * @param lift the machine
     * @param liftClass its class
     * @param day the day
     * @param window when it runs that day
     * @return the empty roster
     */
    public static Roster of(LiftCode lift, LiftClass liftClass, LocalDate day, OperatingWindow window) {
        return new Roster(lift, liftClass, day, window, List.of());
    }

    /**
     * A roster with its stretches put in order by post and then by start.
     *
     * @param lift the machine
     * @param liftClass its class
     * @param day the day
     * @param window when it runs that day
     * @param assignments who is where, in any order
     * @return the roster
     */
    public static Roster of(
            LiftCode lift, LiftClass liftClass, LocalDate day, OperatingWindow window, List<Assignment> assignments) {
        Comparator<Assignment> order =
                Comparator.comparing((Assignment one) -> one.post().value()).thenComparing(Assignment::starts);
        return new Roster(
                lift, liftClass, day, window, assignments.stream().sorted(order).toList());
    }

    /**
     * The posts this machine needs covered.
     *
     * @return the required posts
     */
    public Set<Post> required() {
        return Post.requiredOn(liftClass);
    }

    /**
     * The stretches at one post.
     *
     * @param post the post
     * @return those stretches, in order of start
     */
    public List<Assignment> atPost(Post post) {
        return assignments.stream().filter(one -> one.post() == post).toList();
    }

    /**
     * The posts held at one minute.
     *
     * @param moment the minute
     * @return the posts somebody covers then
     */
    public Set<Post> staffingAt(TimeOfDay moment) {
        Set<Post> held = EnumSet.noneOf(Post.class);
        for (Assignment one : assignments) {
            if (one.covers(moment)) {
                held.add(one.post());
            }
        }
        return Collections.unmodifiableSet(held);
    }

    /**
     * Whether every required post is held at one minute.
     *
     * @param moment the minute
     * @return true when nothing required is empty
     */
    public boolean isStaffedAt(TimeOfDay moment) {
        return staffingAt(moment).containsAll(required());
    }

    /**
     * Who is on at one minute.
     *
     * @param moment the minute
     * @return their names, sorted
     */
    public List<String> peopleAt(TimeOfDay moment) {
        return assignments.stream()
                .filter(one -> one.covers(moment))
                .map(Assignment::person)
                .sorted()
                .toList();
    }

    /**
     * Every stretch of the running day where a required post is empty.
     *
     * @return each gap by the post and the minute it opens, posts in the order their names sort, from
     *     first load to sweep
     */
    public List<Gap> gaps() {
        List<Gap> found = new ArrayList<>();
        List<Post> posts =
                required().stream().sorted(Comparator.comparing(Post::value)).toList();
        for (Post post : posts) {
            List<Assignment> held = atPost(post);
            boolean inGap = false;
            TimeOfDay minute = window.firstLoad();
            while (minute.compareTo(window.sweepBy()) <= 0) {
                TimeOfDay now = minute;
                boolean covered = held.stream().anyMatch(one -> one.covers(now));
                if (!covered && !inGap) {
                    inGap = true;
                    found.add(new Gap(post, minute));
                } else if (covered) {
                    inGap = false;
                }
                minute = new TimeOfDay(minute.minutes() + 1);
            }
        }
        return List.copyOf(found);
    }

    /**
     * Whether every required post is held from first load to sweep.
     *
     * @return true when there are no gaps
     */
    public boolean isCovered() {
        return gaps().isEmpty();
    }

    /**
     * How long one person is on the machine.
     *
     * @param person the name
     * @return the summed length of their stretches
     */
    public Duration hoursWorked(String person) {
        Duration total = Duration.ZERO;
        for (Assignment one : assignments) {
            if (one.person().equals(person)) {
                total = total.plus(one.length());
            }
        }
        return total;
    }

    /**
     * Everybody on the roster.
     *
     * @return each name once, sorted
     */
    public List<String> people() {
        return assignments.stream().map(Assignment::person).distinct().sorted().toList();
    }

    /**
     * The same roster with one more stretch on it.
     *
     * @param assignment the stretch
     * @return a new roster in order; this one is unchanged
     * @throws OperationsError when the stretch puts somebody in two places at once
     */
    public Roster withAssignment(Assignment assignment) {
        List<Assignment> all = new ArrayList<>(assignments);
        all.add(assignment);
        return of(lift, liftClass, day, window, all);
    }

    /** Such as {@code JP 2025-01-15, 2 on, covered} or {@code JP 2025-01-15, 1 on, 1 gaps}. */
    @Override
    public String toString() {
        List<Gap> gaps = gaps();
        String state = gaps.isEmpty() ? "covered" : gaps.size() + " gaps";
        return lift + " " + day + ", " + people().size() + " on, " + state;
    }

    /**
     * A required post found empty.
     *
     * @param post the post
     * @param at the first minute it is empty
     */
    public record Gap(Post post, TimeOfDay at) {

        /** A gap. */
        public Gap {
            Objects.requireNonNull(post, "post");
            Objects.requireNonNull(at, "at");
        }
    }
}
