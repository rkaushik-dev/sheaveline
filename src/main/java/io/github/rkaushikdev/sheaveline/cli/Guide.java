package io.github.rkaushikdev.sheaveline.cli;

import io.github.rkaushikdev.sheaveline.reports.Report;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * The order the office actually does things in, printed at the keyboard.
 *
 * <p>Every command in this program has a help string and none of them says what to run before it.
 * Somebody starting a season needs to know that a machine goes on the register before it can be
 * drilled, and that it has to be drilled before the morning page will let it open, and that is not a
 * thing {@code --help} can tell you.
 *
 * <p>So the sequences live here, as data, and the command prints them. Deliberately not a document
 * in the repository: a document nobody opens goes stale, and this is in the same program as the
 * commands it names, so a command that gets renamed breaks the test that checks the guide names real
 * commands.
 */
@Command(
        name = "guide",
        description = {
            "The order the office does things in, which help cannot tell you.",
            "",
            "With no argument it prints all of them. With one it prints the sequence whose name contains"
                    + " that word, so guide day is enough for a day on the hill."
        })
final class Guide implements Action {

    /**
     * One command, and why it is at this point in the order.
     *
     * @param command the command, as typed after the program's name
     * @param why the reason, starting in lower case
     */
    record Step(String command, String why) {

        /** A step with both halves. */
        Step {
            Objects.requireNonNull(command, "command");
            Objects.requireNonNull(why, "why");
        }
    }

    /**
     * One thing the office does, and the order it does it in.
     *
     * @param name what it is called
     * @param when when it is done
     * @param steps the commands, in order
     */
    record Sequence(String name, String when, List<Step> steps) {

        /** A sequence with its steps copied. */
        Sequence {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(when, "when");
            steps = List.copyOf(steps);
        }

        /**
         * The commands, in order.
         *
         * @return the commands
         */
        List<String> commands() {
            return steps.stream().map(Step::command).toList();
        }
    }

    /** Everything the office does, in the order the year does it. */
    static final List<Sequence> SEQUENCES = List.of(
            new Sequence(
                    "opening a machine",
                    "once, when a lift goes on the register",
                    List.of(
                            new Step("setup", "build the tables, which is safe to run again"),
                            new Step("lift load", "read the machine off its drawing file"),
                            new Step("lift show", "check the register has what the drawing said"),
                            new Step("lift shoot", "shoot the towers, so there is something to trend against"))),
            new Sequence(
                    "starting a season",
                    "November, before anybody rides",
                    List.of(
                            new Step("upkeep due", "read the ladder and see what the summer left"),
                            new Step("upkeep record", "sign off every job, oldest first"),
                            new Step("rope walk", "caliper the rope so the file has a second point"),
                            new Step("evac drill", "run the annual, or the machine does not carry the public"),
                            new Step("crew card", "put this year's cards in the file"),
                            new Step("crew gap", "check every post can be filled before the first weekend"))),
            new Sequence(
                    "a day on the hill",
                    "every operating day",
                    List.of(
                            new Step("day start", "walk the sheet and open the book"),
                            new Step("report morning", "print the seven o'clock page"),
                            new Step("day stop", "when it stops, and say why"),
                            new Step("day restart", "when it runs again"),
                            new Step("day meter", "read the hour meter and the counter at the end of it"),
                            new Step("day show", "read the book back before signing off"))),
            new Sequence(
                    "something went wrong",
                    "the same afternoon, not the next morning",
                    List.of(
                            new Step("day stop", "stop it and write it up"),
                            new Step("board record", "put it on the register and read the filing deadline"),
                            new Step("upkeep record", "write up the walk that found the cause"),
                            new Step("upkeep raise", "raise the order off the defect"),
                            new Step("board file", "file it before the deadline the record printed"),
                            new Step("upkeep close", "close the order when the work is done"))),
            new Sequence(
                    "closing a season",
                    "May, before the board meets",
                    List.of(
                            new Step("report season", "add up every day that was written up"),
                            new Step("rope forecast", "see which ropes want ordering before next winter"),
                            new Step("lift drift", "see which towers have moved and how fast"),
                            new Step("report packet", "print the annual packet for the state"),
                            new Step("report export", "hand the numbers to finance as a spreadsheet"))));

    @Parameters(arity = "0..1", paramLabel = "NAME")
    private String name;

    /**
     * The sequence whose name holds that word. Any word of the name will do, because nobody types
     * a day on the hill.
     *
     * @param name the word, in any case and with any space around it
     * @return the first sequence whose name holds it, or empty for none or for no word at all
     */
    static Optional<Sequence> sequence(String name) {
        String wanted = name.toLowerCase(Locale.ROOT).strip();
        if (wanted.isEmpty()) {
            return Optional.empty();
        }
        return SEQUENCES.stream().filter(one -> one.name().contains(wanted)).findFirst();
    }

    /**
     * The sequences as a page, every step numbered from one.
     *
     * @param wanted the sequences
     * @return the page
     */
    static Report render(List<Sequence> wanted) {
        Report page = new Report();
        page.heading("what the office does, in order");
        for (Sequence one : wanted) {
            page.section(one.name());
            page.line("  " + one.when());
            page.blank();
            int number = 1;
            for (Step step : one.steps()) {
                page.line("  " + number + ". sheaveline " + step.command());
                page.line("     " + step.why());
                number += 1;
            }
        }
        return page;
    }

    @Override
    public int run(Console console) {
        if (name == null) {
            console.page(render(SEQUENCES).lines());
            console.say();
            console.say(SEQUENCES.size() + " sequences. Name one to print just that.");
            return Main.OK;
        }
        Optional<Sequence> found = sequence(name);
        if (found.isEmpty()) {
            console.say("there is no sequence called " + name);
            console.say("try: " + SEQUENCES.stream().map(Sequence::name).collect(Collectors.joining(", ")));
            return Main.REFUSED;
        }
        console.page(render(List.of(found.get())).lines());
        return Main.OK;
    }
}
