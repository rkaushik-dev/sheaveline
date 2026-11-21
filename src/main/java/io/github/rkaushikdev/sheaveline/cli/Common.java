package io.github.rkaushikdev.sheaveline.cli;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.persistence.UnitOfWork;
import io.github.rkaushikdev.sheaveline.plant.HaulRope;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.plant.RopeSerial;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import io.github.rkaushikdev.sheaveline.util.Quote;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import picocli.CommandLine;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.TypeConversionException;

/**
 * What every command module would otherwise write out for itself.
 *
 * <p>Sixteen commands take a day and default it to today, and each of them once had its own copy of
 * the option and its own helper to turn what came back into a date. They live here now, with the
 * formats: a date is written the way a date is written on a tramway board form, and a moment is that
 * plus a wall clock time, and neither of them is negotiable per command.
 */
final class Common {

    /** A day, the way a tramway board form writes it. */
    static final String DAY_PATTERN = "uuuu-MM-dd";

    /** A moment: the day and the wall clock time. */
    static final String MOMENT_PATTERN = "uuuu-MM-dd HH:mm";

    /** How a day is printed. */
    static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern(DAY_PATTERN, Locale.ROOT);

    /** How a moment is printed. */
    static final DateTimeFormatter MOMENT = DateTimeFormatter.ofPattern(MOMENT_PATTERN, Locale.ROOT);

    /** The day option's name. */
    static final String ON = "--on";

    /** The moment option's name. */
    static final String AT = "--at";

    /** What a day looks like in the help. */
    static final String DAY_LABEL = "YYYY-MM-DD";

    /** What a moment looks like in the help. */
    static final String MOMENT_LABEL = "'YYYY-MM-DD HH:MM'";

    /** What the day option means when a command does not say otherwise. */
    static final String DAY_HELP = "The day it happened. Today if you leave it off.";

    /** What the moment option means. */
    static final String MOMENT_HELP = "When it happened, to the minute. Now if you leave it off.";

    /** Reading is kinder than printing: a month or a day may be written with one digit. */
    private static final DateTimeFormatter DAY_READ =
            DateTimeFormatter.ofPattern("uuuu-M-d", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);

    private static final DateTimeFormatter MOMENT_READ =
            DateTimeFormatter.ofPattern("uuuu-M-d H:m", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);

    private Common() {}

    /**
     * The day that was given, or the day the clock says it is.
     *
     * @param clock the clock
     * @param given the day from the command line
     * @return the day
     */
    static LocalDate dayOf(TellsTheTime clock, Optional<LocalDate> given) {
        return given.orElseGet(clock::today);
    }

    /**
     * The moment that was given, or the moment the clock says it is.
     *
     * @param clock the clock
     * @param given the moment from the command line
     * @return the moment
     */
    static LocalDateTime momentOf(TellsTheTime clock, Optional<LocalDateTime> given) {
        return given.orElseGet(clock::now);
    }

    /**
     * A date on a page, or nothing when there is not one.
     *
     * @param wanted the date
     * @return the date, written {@code YYYY-MM-DD}, or empty text
     */
    static String printed(Optional<LocalDate> wanted) {
        return printed(wanted, "");
    }

    /**
     * A date on a page, or a word saying there is not one.
     *
     * @param wanted the date
     * @param missing what to print when there is no date
     * @return the date, written {@code YYYY-MM-DD}, or the word
     */
    static String printed(Optional<LocalDate> wanted, String missing) {
        return wanted.map(LocalDate::toString).orElse(missing);
    }

    /**
     * A moment on a page, to the minute.
     *
     * @param at the moment
     * @return the moment, written {@code YYYY-MM-DD HH:MM}
     */
    static String printed(LocalDateTime at) {
        return MOMENT.format(at);
    }

    /**
     * A length to the nearest foot, without the unit, the way every table here prints one.
     *
     * @param length the length
     * @return the whole feet
     */
    static String wholeFeet(Length length) {
        return Decimals.fixed(length.feet(), 0);
    }

    /**
     * A machine off the register, named the way it is named on the radio.
     *
     * @param work the unit of work
     * @param code the code as typed
     * @return the machine
     */
    static Lift machine(UnitOfWork work, String code) {
        return work.lifts().get(LiftCode.parse(code));
    }

    /**
     * A rope off the file, named the way the mill stamped it.
     *
     * @param work the unit of work
     * @param serial the serial as typed
     * @return the rope
     */
    static HaulRope haulRope(UnitOfWork work, String serial) {
        return work.ropes().get(RopeSerial.parse(serial));
    }

    /**
     * Give every command in the tree that carries {@link DayHelp} its own sentence for {@code --on}.
     *
     * @param tree the command and its subcommands
     */
    static void sayTheDay(CommandLine tree) {
        CommandSpec spec = tree.getCommandSpec();
        DayHelp help = spec.userObject().getClass().getAnnotation(DayHelp.class);
        OptionSpec on = spec.findOption(ON);
        if (help != null && on != null) {
            spec.remove(on);
            spec.addOption(on.toBuilder().description(help.value()).build());
        }
        tree.getSubcommands().values().forEach(Common::sayTheDay);
    }

    /** A command that says in its own words what the day it is given means. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface DayHelp {

        /**
         * The sentence.
         *
         * @return the help for {@code --on}
         */
        String value();
    }

    /** The {@code --on} option, written once. */
    static final class DayOption {

        @Option(names = ON, paramLabel = DAY_LABEL, converter = Days.class, description = DAY_HELP)
        private LocalDate on;

        /**
         * The day that was given.
         *
         * @return the day, or empty when the option was left off
         */
        Optional<LocalDate> given() {
            return Optional.ofNullable(on);
        }

        /**
         * The day that was given, or the day the clock says it is.
         *
         * @param clock the clock
         * @return the day
         */
        LocalDate dayOf(TellsTheTime clock) {
            return Common.dayOf(clock, given());
        }
    }

    /**
     * The {@code --at} option, which takes a whole day as well as a moment on purpose.
     *
     * <p>Somebody writing up yesterday's incident at eight the next morning has the date in front of
     * them and not the minute, and refusing the date would only teach them to invent a time.
     */
    static final class MomentOption {

        @Option(names = AT, paramLabel = MOMENT_LABEL, converter = Moments.class, description = MOMENT_HELP)
        private LocalDateTime at;

        /**
         * The moment that was given, or the moment the clock says it is.
         *
         * @param clock the clock
         * @return the moment
         */
        LocalDateTime momentOf(TellsTheTime clock) {
            return Common.momentOf(clock, Optional.ofNullable(at));
        }
    }

    /** A day on the command line, written {@code YYYY-MM-DD}. */
    static final class Days implements ITypeConverter<LocalDate> {

        @Override
        public LocalDate convert(String text) {
            try {
                return LocalDate.parse(text, DAY_READ);
            } catch (DateTimeParseException wrong) {
                throw new TypeConversionException(
                        Quote.of(text) + " does not match the format " + Quote.of(DAY_LABEL) + ".");
            }
        }
    }

    /** A moment on the command line, written {@code YYYY-MM-DD HH:MM}, or a day meaning its start. */
    static final class Moments implements ITypeConverter<LocalDateTime> {

        @Override
        public LocalDateTime convert(String text) {
            try {
                return LocalDateTime.parse(text, MOMENT_READ);
            } catch (DateTimeParseException notAMoment) {
                try {
                    return LocalDate.parse(text, DAY_READ).atStartOfDay();
                } catch (DateTimeParseException notADay) {
                    throw new TypeConversionException(Quote.of(text) + " does not match the formats "
                            + Quote.of("YYYY-MM-DD HH:MM") + ", " + Quote.of(DAY_LABEL) + ".");
                }
            }
        }
    }

    /** A file that has to be there already, and has to be a file. */
    static final class ExistingFile implements ITypeConverter<Path> {

        @Override
        public Path convert(String text) {
            Path path = Path.of(text);
            if (!Files.exists(path)) {
                throw new TypeConversionException("File " + Quote.of(text) + " does not exist.");
            }
            if (Files.isDirectory(path)) {
                throw new TypeConversionException("File " + Quote.of(text) + " is a directory.");
            }
            return path;
        }
    }

    /** A file to be written: not a directory, and writable if it is there already. */
    static final class WritableFile implements ITypeConverter<Path> {

        @Override
        public Path convert(String text) {
            Path path = Path.of(text);
            if (Files.isDirectory(path)) {
                throw new TypeConversionException("File " + Quote.of(text) + " is a directory.");
            }
            if (Files.exists(path) && !Files.isWritable(path)) {
                throw new TypeConversionException("File " + Quote.of(text) + " is not writable.");
            }
            return path;
        }
    }

    /**
     * One of a fixed set of words, and nothing else. The words are also the completion candidates,
     * so the help can list them.
     *
     * @param <T> what a word stands for
     */
    abstract static class Choice<T> implements ITypeConverter<T>, Iterable<String> {

        private final Map<String, T> choices;

        /**
         * A choice between the given words.
         *
         * @param choices each word and what it stands for, in the order they are listed
         */
        Choice(Map<String, T> choices) {
            this.choices = Collections.unmodifiableMap(new LinkedHashMap<>(choices));
        }

        /**
         * Every constant of an enum, by its name in lower case.
         *
         * @param type the enum
         * @param <E> the enum
         * @return the words, in declaration order
         */
        static <E extends Enum<E>> Map<String, E> named(Class<E> type) {
            Map<String, E> named = new LinkedHashMap<>();
            for (E one : type.getEnumConstants()) {
                named.put(one.name().toLowerCase(Locale.ROOT), one);
            }
            return named;
        }

        @Override
        public T convert(String value) {
            T found = choices.get(value);
            if (found == null) {
                String every = choices.keySet().stream().map(Quote::of).collect(Collectors.joining(", "));
                throw new TypeConversionException(Quote.of(value) + " is not one of " + every + ".");
            }
            return found;
        }

        @Override
        public Iterator<String> iterator() {
            return choices.keySet().iterator();
        }
    }
}
