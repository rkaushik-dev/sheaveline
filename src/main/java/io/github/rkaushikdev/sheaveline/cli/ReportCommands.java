package io.github.rkaushikdev.sheaveline.cli;

import io.github.rkaushikdev.sheaveline.Configuration;
import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.ops.StopEvent;
import io.github.rkaushikdev.sheaveline.persistence.IncidentRepository.Numbered;
import io.github.rkaushikdev.sheaveline.persistence.UnitOfWork;
import io.github.rkaushikdev.sheaveline.plant.HaulRope;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.reports.BoardPacket;
import io.github.rkaushikdev.sheaveline.reports.Export;
import io.github.rkaushikdev.sheaveline.reports.FiledIncident;
import io.github.rkaushikdev.sheaveline.reports.Gather;
import io.github.rkaushikdev.sheaveline.reports.Morning;
import io.github.rkaushikdev.sheaveline.reports.SeasonSummary;
import io.github.rkaushikdev.sheaveline.ropework.WearSurvey;
import io.github.rkaushikdev.sheaveline.schedule.Season;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help.Visibility;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * The pages the office prints.
 *
 * <p>Every command here writes eighty columns of plain text to standard output, which is what the
 * printer in the lift shop takes and what a mail message carries without anybody having to open
 * anything. There is deliberately no other format.
 *
 * <p>The morning page is the one that runs on a timer. The season summary is run once in May and
 * then argued about until August.
 */
final class ReportCommands {

    private ReportCommands() {}

    /** Write one sheet of the season to the handle, and say how many rows went in. */
    private static int export(UnitOfWork work, String sheet, Season season, Appendable handle) throws IOException {
        return switch (sheet) {
            case "days" -> Export.writeDays(handle, Gather.seasonSummary(work, season));
            case "stops" -> {
                List<StopEvent> stops = new ArrayList<>();
                for (LiftCode code : work.lifts().codes()) {
                    for (LocalDate day : work.dayLogs().daysLogged(code)) {
                        if (season.covers(day)) {
                            stops.addAll(work.dayLogs().stopsOn(code, day).stops());
                        }
                    }
                }
                yield Export.writeStops(handle, once(stops));
            }
            case "incidents" -> {
                List<FiledIncident> incidents = new ArrayList<>();
                for (LiftCode code : work.lifts().codes()) {
                    for (Numbered one : work.incidents().numberedForLift(code)) {
                        if (season.covers(one.incident().at().toLocalDate())) {
                            incidents.add(new FiledIncident(one.incident(), one.filingNumber()));
                        }
                    }
                }
                yield Export.writeIncidents(handle, incidents);
            }
            default -> {
                List<WearSurvey> surveys = new ArrayList<>();
                for (HaulRope rope : work.ropes().all()) {
                    for (WearSurvey one : work.wearSurveys().forRope(rope)) {
                        if (season.covers(one.taken())) {
                            surveys.add(one);
                        }
                    }
                }
                yield Export.writeWear(handle, surveys);
            }
        };
    }

    /** A stop that ran overnight belongs to two days and to one row. */
    private static List<StopEvent> once(List<StopEvent> stops) {
        Map<String, StopEvent> seen = new LinkedHashMap<>();
        for (StopEvent one : stops) {
            seen.putIfAbsent(one.lift() + " " + one.stopped(), one);
        }
        return seen.values().stream()
                .sorted(Comparator.comparing(StopEvent::stopped)
                        .thenComparing(one -> one.lift().toString()))
                .toList();
    }

    /** The sheets there are, by name, in alphabetical order. */
    static final class Sheets extends Common.Choice<String> {

        Sheets() {
            super(sheets());
        }

        private static Map<String, String> sheets() {
            Map<String, String> named = new TreeMap<>();
            Export.SHEETS.keySet().forEach(name -> named.put(name, name));
            return named;
        }
    }

    /** Print a page. */
    @Command(
            name = "report",
            description = "Print a page.",
            subcommands = {MorningPage.class, SeasonPage.class, BoardPage.class, ExportSheet.class})
    static final class ReportGroup {}

    /** The seven o'clock page: what is opening and what is stopping the rest. */
    @Command(name = "morning", description = "The seven o'clock page: what is opening and what is stopping the rest.")
    static final class MorningPage implements Action {

        @Mixin
        private Common.DayOption on;

        @Option(names = "--resort", defaultValue = Configuration.DEFAULT_RESORT, showDefaultValue = Visibility.ALWAYS)
        private String resort;

        @Option(names = "--snow", defaultValue = "", description = "What the patrol reported overnight.")
        private String snow;

        @Option(names = "--tension", description = "What the rope is carrying, if the line is to be walked as well.")
        private String tension;

        @Override
        public int run(Console console) {
            LocalDate when = on.dayOf(console);
            Morning page;
            try (UnitOfWork work = console.work()) {
                page = Gather.morning(
                        work, when, resort, snow, Optional.ofNullable(tension).map(Load::parse));
            }
            console.page(page.render().lines());
            return Main.OK;
        }
    }

    /** Add up a whole season, machine by machine. */
    @Command(name = "season", description = "Add up a whole season, machine by machine.")
    static final class SeasonPage implements Action {

        @Parameters(paramLabel = "LABEL")
        private String label;

        @Option(names = "--opens", required = true, paramLabel = Common.DAY_LABEL, converter = Common.Days.class)
        private LocalDate opens;

        @Option(names = "--closes", required = true, paramLabel = Common.DAY_LABEL, converter = Common.Days.class)
        private LocalDate closes;

        @Override
        public int run(Console console) {
            Season season = new Season(label, opens, closes);
            SeasonSummary page;
            try (UnitOfWork work = console.work()) {
                page = Gather.seasonSummary(work, season);
            }
            console.page(page.render().lines());
            return Main.OK;
        }
    }

    /** The annual packet the board reads, which is the one page that leaves here. */
    @Command(
            name = "packet",
            description = "The annual packet the board reads, which is the one page that leaves here.")
    static final class BoardPage implements Action {

        @Parameters(paramLabel = "LABEL")
        private String label;

        @Option(names = "--opens", required = true, paramLabel = Common.DAY_LABEL, converter = Common.Days.class)
        private LocalDate opens;

        @Option(names = "--closes", required = true, paramLabel = Common.DAY_LABEL, converter = Common.Days.class)
        private LocalDate closes;

        @Option(names = "--resort", defaultValue = Configuration.DEFAULT_RESORT, showDefaultValue = Visibility.ALWAYS)
        private String resort;

        @Option(names = "--by", required = true, description = "Who is signing it.")
        private String by;

        @Mixin
        private Common.DayOption on;

        @Override
        public int run(Console console) {
            Season season = new Season(label, opens, closes);
            LocalDate prepared = on.dayOf(console);
            BoardPacket packet;
            try (UnitOfWork work = console.work()) {
                packet = Gather.boardPacket(work, season, resort, prepared, by);
            }
            console.page(packet.render().lines());
            return packet.isClean() ? Main.OK : Main.REFUSED;
        }
    }

    /**
     * Write one table as comma separated text, for a spreadsheet.
     *
     * <p>Every measure goes out as the integer this system holds it in, with the unit in the column
     * heading. A spreadsheet that reads a rope diameter as a date has already lost, and it does that
     * to anything written with an inch mark on it.
     */
    @Command(
            name = "export",
            description = {
                "Write one table as comma separated text, for a spreadsheet.",
                "",
                "Every measure goes out as the integer this system holds it in, with the unit in the column"
                        + " heading. A spreadsheet that reads a rope diameter as a date has already lost, and it"
                        + " does that to anything written with an inch mark on it."
            })
    static final class ExportSheet implements Action {

        @Parameters(
                paramLabel = "SHEET",
                converter = Sheets.class,
                completionCandidates = Sheets.class,
                description = "One of ${COMPLETION-CANDIDATES}.")
        private String sheet;

        @Option(names = "--opens", required = true, paramLabel = Common.DAY_LABEL, converter = Common.Days.class)
        private LocalDate opens;

        @Option(names = "--closes", required = true, paramLabel = Common.DAY_LABEL, converter = Common.Days.class)
        private LocalDate closes;

        @Option(names = "--label", defaultValue = "season", showDefaultValue = Visibility.ALWAYS)
        private String label;

        @Option(
                names = "--to",
                paramLabel = "PATH",
                converter = Common.WritableFile.class,
                description = "Where to write it. Standard output if you leave it off.")
        private Path destination;

        @Override
        public int run(Console console) throws IOException {
            Season season = new Season(label, opens, closes);
            if (destination == null) {
                try (UnitOfWork work = console.work()) {
                    export(work, sheet, season, console.terminal().out());
                }
                return Main.OK;
            }
            int written;
            try (Writer handle = Files.newBufferedWriter(destination, StandardCharsets.UTF_8);
                    UnitOfWork work = console.work()) {
                written = export(work, sheet, season, handle);
            }
            console.say(written + " rows to " + destination);
            return Main.OK;
        }
    }
}
