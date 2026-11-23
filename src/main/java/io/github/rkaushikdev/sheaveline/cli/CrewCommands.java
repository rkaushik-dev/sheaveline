package io.github.rkaushikdev.sheaveline.cli;

import io.github.rkaushikdev.sheaveline.ops.Assignment;
import io.github.rkaushikdev.sheaveline.ops.Post;
import io.github.rkaushikdev.sheaveline.ops.Roster;
import io.github.rkaushikdev.sheaveline.people.Certificate;
import io.github.rkaushikdev.sheaveline.people.Endorsement;
import io.github.rkaushikdev.sheaveline.people.PersonnelFile;
import io.github.rkaushikdev.sheaveline.people.RosterCheck;
import io.github.rkaushikdev.sheaveline.people.RosterProblem;
import io.github.rkaushikdev.sheaveline.persistence.PersonnelRepository.Placement;
import io.github.rkaushikdev.sheaveline.persistence.UnitOfWork;
import io.github.rkaushikdev.sheaveline.plant.LiftClass;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.reports.Align;
import io.github.rkaushikdev.sheaveline.reports.Column;
import io.github.rkaushikdev.sheaveline.reports.Table;
import io.github.rkaushikdev.sheaveline.schedule.OperatingWindow;
import io.github.rkaushikdev.sheaveline.schedule.TimeOfDay;
import io.github.rkaushikdev.sheaveline.util.Valued;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Commands about who is carded and who is standing where.
 *
 * <p>The gap command is the one the lift supervisor runs on Thursday for the weekend. It answers one
 * question a machine: with the cards that are current on that day, can every required post be
 * filled, and if not, who is short of what.
 *
 * <p>A card is issued rather than edited. Renewing writes a new one and leaves the old in the file,
 * because the question an inspector asks is not what somebody holds today but what they held on the
 * day of the incident.
 */
final class CrewCommands {

    /** How far ahead the card list looks for cards running out. */
    static final int WARN_WITHIN_DAYS = 45;

    private CrewCommands() {}

    /** A card names the classes by the short name, the way the radio does; none named means all. */
    private static Set<LiftClass> classes(List<LiftClass> given) {
        if (given.isEmpty()) {
            return Collections.unmodifiableSet(EnumSet.allOf(LiftClass.class));
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(given));
    }

    /** The endorsements a card can carry, by name. */
    static final class Endorsements extends Common.Choice<Endorsement> {

        Endorsements() {
            super(named(Endorsement.class));
        }
    }

    /** The classes of machine, by name. */
    static final class LiftClasses extends Common.Choice<LiftClass> {

        LiftClasses() {
            super(named(LiftClass.class));
        }
    }

    /** The posts on a machine, by name. */
    static final class Posts extends Common.Choice<Post> {

        Posts() {
            super(named(Post.class));
        }
    }

    /** Cards, shifts and whether the posts can be filled. */
    @Command(
            name = "crew",
            description = "Cards, shifts and whether the posts can be filled.",
            subcommands = {IssueCard.class, RenewCard.class, ListCards.class, AssignShift.class, StaffingGap.class})
    static final class CrewGroup {}

    /** Write a card into the file. */
    @Command(name = "card", description = "Write a card into the file.")
    static final class IssueCard implements Action {

        @Parameters(index = "0", paramLabel = "PERSON")
        private String person;

        @Parameters(
                index = "1",
                paramLabel = "ENDORSEMENT",
                converter = Endorsements.class,
                completionCandidates = Endorsements.class,
                description = "One of ${COMPLETION-CANDIDATES}.")
        private Endorsement endorsement;

        @Option(names = "--issued", required = true, paramLabel = Common.DAY_LABEL, converter = Common.Days.class)
        private LocalDate issued;

        @Option(names = "--expires", required = true, paramLabel = Common.DAY_LABEL, converter = Common.Days.class)
        private LocalDate expires;

        @Option(names = "--by", required = true, description = "Who signed it.")
        private String by;

        @Option(
                names = "--only",
                converter = LiftClasses.class,
                completionCandidates = LiftClasses.class,
                description = "Limit the card to these classes. Give it once a class. One of ${COMPLETION-CANDIDATES}.")
        private List<LiftClass> only = new ArrayList<>();

        @Override
        public int run(Console console) {
            Certificate card = new Certificate(person, endorsement, issued, expires, by, classes(only));
            try (UnitOfWork work = console.work()) {
                work.personnel().issue(card);
                work.commit();
            }
            console.say(card.toString());
            if (!only.isEmpty()) {
                console.say("covers   "
                        + card.classes().stream().map(Valued::value).sorted().collect(Collectors.joining(", ")));
            }
            return Main.OK;
        }
    }

    /** Renew a card. The old one stays in the file. */
    @Command(name = "renew", description = "Renew a card. The old one stays in the file.")
    static final class RenewCard implements Action {

        @Parameters(index = "0", paramLabel = "PERSON")
        private String person;

        @Parameters(
                index = "1",
                paramLabel = "ENDORSEMENT",
                converter = Endorsements.class,
                completionCandidates = Endorsements.class,
                description = "One of ${COMPLETION-CANDIDATES}.")
        private Endorsement endorsement;

        @Option(names = "--expires", required = true, paramLabel = Common.DAY_LABEL, converter = Common.Days.class)
        private LocalDate expires;

        @Option(names = "--by", required = true, description = "Who signed it.")
        private String by;

        @Override
        public int run(Console console) {
            Certificate renewed;
            try (UnitOfWork work = console.work()) {
                renewed = work.personnel().renew(person, endorsement, expires, by);
                work.commit();
            }
            console.say(renewed.toString());
            return Main.OK;
        }
    }

    /** Who is carded for what, and what runs out soon. */
    @Command(name = "cards", description = "Who is carded for what, and what runs out soon.")
    static final class ListCards implements Action {

        @Option(names = "--person", description = "Just this one.")
        private String person;

        @Option(
                names = "--expiring",
                defaultValue = WARN_WITHIN_DAYS + "",
                showDefaultValue = Visibility.ALWAYS,
                description = "Days ahead.")
        private int expiring;

        @Mixin
        private Common.DayOption on;

        @Override
        public int run(Console console) {
            LocalDate when = on.dayOf(console);
            PersonnelFile held;
            List<Certificate> cards;
            try (UnitOfWork work = console.work()) {
                held = work.personnel().file();
                cards = person != null && !person.isEmpty() ? work.personnel().forPerson(person) : held.certificates();
            }
            if (cards.isEmpty()) {
                console.say("nothing is on file");
                return Main.OK;
            }
            List<List<Object>> rows = new ArrayList<>();
            cards.stream()
                    .sorted(Comparator.comparing(Certificate::person).thenComparing(Certificate::expires))
                    .forEach(one -> rows.add(List.of(
                            one.person(),
                            one.endorsement().value(),
                            one.expires().toString(),
                            one.daysLeft(when),
                            one.isCurrent(when) ? "current" : "lapsed")));
            console.page(Table.of(
                            List.of(
                                    new Column("person", 16),
                                    new Column("card", 16),
                                    new Column("expires", 12),
                                    new Column("days", 6, Align.RIGHT),
                                    new Column("state", 10)),
                            rows)
                    .lines());
            for (Certificate one : held.expiringWithin(when, expiring)) {
                console.say(one.person() + " " + one.endorsement().value() + " runs out " + one.expires());
            }
            return Main.OK;
        }
    }

    /** Put somebody on a post for the day. */
    @Command(name = "assign", description = "Put somebody on a post for the day.")
    static final class AssignShift implements Action {

        @Parameters(index = "0", paramLabel = "CODE")
        private String code;

        @Parameters(index = "1", paramLabel = "PERSON")
        private String person;

        @Parameters(
                index = "2",
                paramLabel = "POST",
                converter = Posts.class,
                completionCandidates = Posts.class,
                description = "One of ${COMPLETION-CANDIDATES}.")
        private Post post;

        @Option(names = "--starts", defaultValue = "08:30", showDefaultValue = Visibility.ALWAYS)
        private String starts;

        @Option(names = "--ends", defaultValue = "16:45", showDefaultValue = Visibility.ALWAYS)
        private String ends;

        @Mixin
        private Common.DayOption on;

        @Override
        public int run(Console console) {
            LocalDate when = on.dayOf(console);
            LiftCode lift = LiftCode.parse(code);
            Assignment shift = new Assignment(person, post, TimeOfDay.parse(starts), TimeOfDay.parse(ends));
            List<Placement> elsewhere = new ArrayList<>();
            try (UnitOfWork work = console.work()) {
                for (Placement one : work.personnel().working(person, when)) {
                    if (!one.lift().equals(lift) && one.shift().overlaps(shift)) {
                        elsewhere.add(one);
                    }
                }
                work.personnel().assign(lift, when, shift);
                work.commit();
            }
            console.say(person + " on " + lift + " at " + shift.post().value() + ", " + shift.starts() + " to "
                    + shift.ends());
            for (Placement clash : elsewhere) {
                console.say("but " + person + " is also down on " + clash.lift() + " "
                        + clash.shift().starts() + " to " + clash.shift().ends());
            }
            return Main.OK;
        }
    }

    /** Whether every required post can be filled, and who is short of what. */
    @Command(
            name = "gap",
            description = {
                "Whether every required post can be filled, and who is short of what.",
                "",
                "With no machine it walks the whole register, which is the version the supervisor runs on"
                        + " Thursday for the weekend."
            })
    static final class StaffingGap implements Action {

        @Parameters(arity = "0..1", paramLabel = "CODE")
        private String code;

        @Option(names = "--first-load", defaultValue = "09:00", showDefaultValue = Visibility.ALWAYS)
        private String firstLoad;

        @Option(names = "--last-load", defaultValue = "16:00", showDefaultValue = Visibility.ALWAYS)
        private String lastLoad;

        @Option(names = "--sweep-by", defaultValue = "16:30", showDefaultValue = Visibility.ALWAYS)
        private String sweepBy;

        @Mixin
        private Common.DayOption on;

        @Override
        public int run(Console console) {
            LocalDate when = on.dayOf(console);
            OperatingWindow window = OperatingWindow.parse(firstLoad, lastLoad, sweepBy);
            int unstaffed = 0;
            try (UnitOfWork work = console.work()) {
                PersonnelFile held = work.personnel().file();
                Map<LiftCode, Roster> rosters = new LinkedHashMap<>();
                for (LiftCode lift : work.lifts().codes()) {
                    rosters.put(lift, work.personnel().roster(lift, when, window));
                }
                List<LiftCode> wanted =
                        code != null && !code.isEmpty() ? List.of(LiftCode.parse(code)) : List.copyOf(rosters.keySet());
                for (LiftCode lift : wanted) {
                    Roster roster = rosters.computeIfAbsent(
                            lift, one -> work.personnel().roster(one, when, window));
                    List<Assignment> elsewhere = rosters.entrySet().stream()
                            .filter(other -> !other.getKey().equals(lift))
                            .flatMap(other -> other.getValue().assignments().stream())
                            .toList();
                    RosterCheck check = new RosterCheck(roster, held, roster.liftClass(), when, elsewhere);
                    console.say(lift + "  " + (check.isLegal() ? "legal" : "SHORT"));
                    if (check.isLegal()) {
                        continue;
                    }
                    unstaffed += 1;
                    for (RosterProblem problem : check.problems()) {
                        List<String> cover = check.coverFor(problem.post(), problem.at());
                        String needed = Endorsement.neededFor(problem.post()).value();
                        String spare =
                                cover.isEmpty() ? "nobody carded " + needed + " is free" : String.join(", ", cover);
                        console.say("  " + problem);
                        console.say("    cover: " + spare);
                    }
                }
            }
            if (unstaffed > 0) {
                console.say();
                console.say(unstaffed + " machines cannot be staffed on " + when);
                return Main.REFUSED;
            }
            return Main.OK;
        }
    }
}
