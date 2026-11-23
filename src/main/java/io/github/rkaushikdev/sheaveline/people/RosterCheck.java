package io.github.rkaushikdev.sheaveline.people;

import io.github.rkaushikdev.sheaveline.ops.Assignment;
import io.github.rkaushikdev.sheaveline.ops.Post;
import io.github.rkaushikdev.sheaveline.ops.Roster;
import io.github.rkaushikdev.sheaveline.plant.LiftClass;
import io.github.rkaushikdev.sheaveline.schedule.TimeOfDay;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * One day's roster on one machine, read against the card file, which nobody does by eye.
 *
 * <p>The roster knows there is somebody at the top of the quad from noon. The card file knows whether
 * that somebody is allowed to be. Neither of them knows the other, and the mistake this exists to
 * catch is the ordinary one: a lead operator goes home sick, the bottom operator moves up to cover,
 * and nobody notices that their card is an operator card rather than a lead one until the board
 * audits the season in April.
 *
 * <p>The answer is a list of problems rather than a yes or a no, because the person reading it has to
 * fix them one at a time and wants to know who is free.
 *
 * @param roster the day's roster
 * @param file the cards
 * @param liftClass the class of machine the roster is for
 * @param on the day
 * @param elsewhere the same day's shifts on every other machine, which is where a person who is
 *     down twice at once is found
 */
public record RosterCheck(
        Roster roster, PersonnelFile file, LiftClass liftClass, LocalDate on, List<Assignment> elsewhere) {

    /**
     * A check of a roster against the cards.
     *
     * @throws PeopleError when the roster is for another class of machine
     */
    public RosterCheck {
        Objects.requireNonNull(roster, "roster");
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(liftClass, "liftClass");
        Objects.requireNonNull(on, "on");
        elsewhere = List.copyOf(elsewhere);
        if (roster.liftClass() != liftClass) {
            throw new PeopleError("that roster is for another class of machine");
        }
    }

    /**
     * A check of one machine's roster on its own, with no other machine's shifts to read against.
     *
     * @param roster the day's roster
     * @param file the cards
     * @param liftClass the class of machine the roster is for
     * @param on the day
     */
    public RosterCheck(Roster roster, PersonnelFile file, LiftClass liftClass, LocalDate on) {
        this(roster, file, liftClass, on, List.of());
    }

    /**
     * Everybody on the roster whose cards do not let them stand where they are.
     *
     * @return a problem for each such stretch, saying which of the card questions failed
     */
    public List<RosterProblem> uncarded() {
        List<RosterProblem> found = new ArrayList<>();
        for (Assignment one : roster.assignments()) {
            if (file.mayHold(one.person(), one.post(), liftClass, on)) {
                continue;
            }
            found.add(new RosterProblem(why(one), one.post(), one.starts(), Optional.of(one.person())));
        }
        return List.copyOf(found);
    }

    /**
     * Every required post that stands empty for some of the day.
     *
     * @return a problem for each gap in the roster
     */
    public List<RosterProblem> uncovered() {
        return roster.gaps().stream()
                .map(gap -> new RosterProblem(ProblemKind.UNCOVERED, gap.post(), gap.at()))
                .toList();
    }

    /**
     * The people on this roster who are down on another machine at the same time.
     *
     * <p>The assign command warns about it as the shift goes in, but the warning scrolls off, and a
     * roster built on Tuesday for Saturday is read on Thursday by somebody who never saw it.
     *
     * @return one problem a shift that overlaps one elsewhere, at the shift's start
     */
    public List<RosterProblem> doubleBooked() {
        List<RosterProblem> found = new ArrayList<>();
        for (Assignment one : roster.assignments()) {
            boolean twice =
                    elsewhere.stream().anyMatch(other -> other.person().equals(one.person()) && other.overlaps(one));
            if (twice) {
                found.add(new RosterProblem(
                        ProblemKind.DOUBLE_BOOKED, one.post(), one.starts(), Optional.of(one.person())));
            }
        }
        return List.copyOf(found);
    }

    /**
     * Everything wrong.
     *
     * @return the uncovered posts, then the people without the cards, then the people who are down
     *     somewhere else at the same time
     */
    public List<RosterProblem> problems() {
        List<RosterProblem> all = new ArrayList<>(uncovered());
        all.addAll(uncarded());
        all.addAll(doubleBooked());
        return List.copyOf(all);
    }

    /**
     * Whether the roster may stand.
     *
     * @return true when nothing is wrong
     */
    public boolean isLegal() {
        return problems().isEmpty();
    }

    /**
     * Who else holds the card for a stretch and is not already working at its start.
     *
     * @param one the stretch to cover
     * @return the names, sorted
     */
    public List<String> substitutes(Assignment one) {
        return coverFor(one.post(), one.starts());
    }

    /**
     * Who holds the card for a post and is free at a minute, here and on every other machine.
     *
     * @param post the post
     * @param at the minute
     * @return the names, sorted
     */
    public List<String> coverFor(Post post, TimeOfDay at) {
        Set<String> busy = new HashSet<>(roster.peopleAt(at));
        elsewhere.stream().filter(other -> other.covers(at)).forEach(other -> busy.add(other.person()));
        return file.qualifiedFor(post, liftClass, on).stream()
                .filter(name -> !busy.contains(name))
                .toList();
    }

    /**
     * The problems of one kind.
     *
     * @param kind the kind
     * @return those problems, in order
     */
    public List<RosterProblem> problemsOf(ProblemKind kind) {
        return problems().stream().filter(problem -> problem.kind() == kind).toList();
    }

    /**
     * The people who need sorting out.
     *
     * @return each name once, sorted
     */
    public List<String> peopleToFix() {
        return problems().stream()
                .flatMap(problem -> problem.person().stream())
                .distinct()
                .sorted()
                .toList();
    }

    /** Such as {@code JP 2025-01-15, roster clear} or {@code JP 2025-01-15, 3 problems}. */
    @Override
    public String toString() {
        List<RosterProblem> problems = problems();
        if (problems.isEmpty()) {
            return roster.lift() + " " + on + ", roster clear";
        }
        return roster.lift() + " " + on + ", " + problems.size() + " problems";
    }

    /** Say which of the three card questions failed, because the fix differs. */
    private ProblemKind why(Assignment one) {
        List<Certificate> forPost = file.forPerson(one.person()).stream()
                .filter(card -> card.coversPost(one.post()))
                .toList();
        if (forPost.isEmpty()) {
            return ProblemKind.UNCARDED;
        }
        boolean forClass = forPost.stream().anyMatch(card -> card.coversClass(liftClass));
        return forClass ? ProblemKind.LAPSED : ProblemKind.WRONG_CLASS;
    }
}
