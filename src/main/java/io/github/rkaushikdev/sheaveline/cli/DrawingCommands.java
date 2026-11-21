package io.github.rkaushikdev.sheaveline.cli;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.github.rkaushikdev.sheaveline.persistence.UnitOfWork;
import io.github.rkaushikdev.sheaveline.plant.Blueprint;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Loading a machine from its drawing and handing the drawing back.
 *
 * <p>The load is deliberately all or nothing. A drawing with a bad tower on it puts no machine on
 * file at all, because a half loaded lift is worse than none: the next person sees a code in the
 * list and believes the rest of it.
 *
 * <p>The check command exists because the load is all or nothing. Somebody editing a drawing wants
 * to know it reads before they run the load against the office database, and reading it costs
 * nothing.
 */
final class DrawingCommands {

    private static final ObjectMapper JSON = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .build();

    /**
     * Two spaces a level, a space after each colon and none before, every element on its own line,
     * and anything past plain ASCII escaped, so a drawing reads the same in any editor.
     */
    private static final ObjectWriter DRAWN = JSON.writer(new DefaultPrettyPrinter(Separators.createDefaultInstance()
                            .withObjectFieldValueSpacing(Separators.Spacing.AFTER)
                            .withObjectEmptySeparator("")
                            .withArrayEmptySeparator(""))
                    .withObjectIndenter(new DefaultIndenter("  ", "\n"))
                    .withArrayIndenter(new DefaultIndenter("  ", "\n")))
            .with(JsonWriteFeature.ESCAPE_NON_ASCII)
            .without(JsonWriteFeature.WRITE_HEX_UPPER_CASE);

    private DrawingCommands() {}

    /**
     * Read a drawing file, and say which file it was if it does not read.
     *
     * @param path the file
     * @return the drawing, a json object
     * @throws DrawingError when the file cannot be read, is not json, or holds something other than
     *     an object
     */
    static JsonNode readDocument(Path path) {
        String text;
        try {
            text = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException missing) {
            throw new DrawingError("cannot read " + path, missing);
        }
        JsonNode document;
        try {
            document = JSON.readTree(text);
        } catch (JsonProcessingException bad) {
            JsonLocation where = bad.getLocation();
            int line = where == null ? 1 : where.getLineNr();
            throw new DrawingError(path + " is not json: " + bad.getOriginalMessage() + " at line " + line, bad);
        }
        if (document.isMissingNode()) {
            throw new DrawingError(path + " is not json: there is nothing in it at line 1");
        }
        if (!document.isObject()) {
            throw new DrawingError(path + " holds " + kind(document) + ", not a drawing");
        }
        return document;
    }

    /**
     * A drawing written out as text, the way the load reads it back.
     *
     * @param drawing the drawing
     * @return the text, ending in a newline
     * @throws JsonProcessingException never, for a drawing built in memory
     */
    static String drawingText(JsonNode drawing) throws JsonProcessingException {
        return DRAWN.writeValueAsString(drawing) + "\n";
    }

    private static String kind(JsonNode document) {
        return switch (document.getNodeType()) {
            case ARRAY -> "a list";
            case STRING -> "a string";
            case NUMBER -> "a number";
            case BOOLEAN -> "a true or false";
            case NULL -> "a null";
            default -> "a " + document.getNodeType().name().toLowerCase(Locale.ROOT);
        };
    }

    /** Put a machine on file from its drawing. Nothing is written if it does not read. */
    @Command(
            name = "load",
            description = "Put a machine on file from its drawing. Nothing is written if it does not read.")
    static final class LoadLift implements Action {

        @Parameters(paramLabel = "DRAWING", converter = Common.ExistingFile.class)
        private Path drawing;

        @Option(names = "--replace", description = "Take the old machine off first.")
        private boolean replace;

        @Override
        public int run(Console console) {
            Lift machine = Blueprint.readLift(readDocument(drawing));
            try (UnitOfWork work = console.work()) {
                if (replace && work.lifts().exists(machine.code())) {
                    work.lifts().remove(machine.code());
                    work.flush();
                }
                work.lifts().add(machine);
                work.commit();
            }
            console.say(machine.code() + " " + machine.name() + " loaded");
            console.say(machine.towers().size() + " towers, " + machine.carriersOnLine() + " carriers");
            return Main.OK;
        }
    }

    /** Write a machine back out as the drawing it would be read from. */
    @Command(name = "export", description = "Write a machine back out as the drawing it would be read from.")
    static final class ExportLift implements Action {

        @Parameters(paramLabel = "CODE")
        private String code;

        @Option(
                names = "--output",
                paramLabel = "PATH",
                converter = Common.WritableFile.class,
                description = "Write to this file instead of the page.")
        private Path output;

        @Override
        public int run(Console console) throws IOException {
            Lift machine;
            try (UnitOfWork work = console.work()) {
                machine = Common.machine(work, code);
            }
            String text = drawingText(Blueprint.writeLift(machine));
            if (output == null) {
                console.say(text.stripTrailing());
                return Main.OK;
            }
            Files.writeString(output, text, StandardCharsets.UTF_8);
            console.say(machine.code() + " written to " + output);
            return Main.OK;
        }
    }

    /** Read a drawing and say whether it makes a machine, without writing anything. */
    @Command(
            name = "check",
            description = "Read a drawing and say whether it makes a machine, without writing anything.")
    static final class CheckDrawing implements Action {

        @Parameters(paramLabel = "DRAWING", converter = Common.ExistingFile.class)
        private Path drawing;

        @Override
        public int run(Console console) {
            Lift machine = Blueprint.readLift(readDocument(drawing));
            console.say(machine.code() + " " + machine.name() + " reads");
            console.say("class            " + machine.liftClass().value());
            console.say("towers           " + machine.towers().size());
            console.say("carriers         " + machine.carriersOnLine());
            console.say("slope length     " + Common.wholeFeet(machine.slopeLength()) + " ft");
            console.say("rope length fits " + (machine.ropeLengthAgrees() ? "yes" : "no"));
            return Main.OK;
        }
    }
}
