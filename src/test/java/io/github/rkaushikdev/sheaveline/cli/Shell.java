package io.github.rkaushikdev.sheaveline.cli;

import io.github.rkaushikdev.sheaveline.persistence.Database;
import io.github.rkaushikdev.sheaveline.persistence.UnitOfWork;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A terminal for the command line tests: an environment, what the person at the keyboard types, and
 * what the command printed on each stream.
 */
final class Shell {

    private final Map<String, String> environment;
    private final String typed;

    Shell() {
        this(Map.of(), "");
    }

    private Shell(Map<String, String> environment, String typed) {
        this.environment = Map.copyOf(environment);
        this.typed = typed;
    }

    /** The same shell with one more variable exported. */
    Shell exporting(String name, String value) {
        Map<String, String> more = new HashMap<>(environment);
        more.put(name, value);
        return new Shell(more, typed);
    }

    /** The same shell with {@code text} waiting to be typed at the first prompt. */
    Shell typing(String text) {
        return new Shell(environment, text);
    }

    /** Run one command line. */
    Reply run(String... args) {
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        Terminal terminal = new Terminal(
                environment::get,
                new BufferedReader(new StringReader(typed)),
                new PrintWriter(out, true),
                new PrintWriter(err, true));
        int code = Main.run(terminal, args);
        return new Reply(code, out.toString(), err.toString());
    }

    /** Run one command line against the database at {@code url}. */
    Reply on(String url, String... args) {
        List<String> line = new ArrayList<>(List.of("--database", url));
        line.addAll(List.of(args));
        return run(line.toArray(String[]::new));
    }

    /** Open the database at {@code url}, let {@code work} change it, and commit. */
    static void change(String url, Consumer<UnitOfWork> work) {
        try (Database database = Database.open(url);
                UnitOfWork opened = UnitOfWork.on(database).open()) {
            work.accept(opened);
            opened.commit();
        }
    }

    /**
     * What one command line came back with.
     *
     * @param code the exit code
     * @param out what went to standard output
     * @param err what went to standard error
     */
    record Reply(int code, String out, String err) {

        /** Both streams, the way a terminal shows them. */
        String output() {
            return out + err;
        }

        /** Every line on both streams. */
        List<String> lines() {
            return output().lines().toList();
        }

        /** How many times {@code wanted} appears on both streams, none of them overlapping. */
        int count(String wanted) {
            int found = 0;
            for (int at = output().indexOf(wanted); at >= 0; at = output().indexOf(wanted, at + wanted.length())) {
                found += 1;
            }
            return found;
        }
    }
}
