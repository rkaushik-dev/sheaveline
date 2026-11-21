package io.github.rkaushikdev.sheaveline.cli;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * What a command talks to: the environment it was started in, the keyboard, and the two streams.
 *
 * <p>Pages go to {@code out}. Nothing goes to {@code err} unless something was refused or the
 * journal was asked for, because a stray line in the middle of an eighty column page is a page
 * nobody can read.
 *
 * @param environment the variables, by name, answering null for one that is not set
 * @param in what is typed at a prompt
 * @param out where the pages go
 * @param err where refusals and the journal go
 */
public record Terminal(UnaryOperator<String> environment, BufferedReader in, PrintWriter out, PrintWriter err) {

    /** A terminal with nothing missing. */
    public Terminal {
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(in, "in");
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(err, "err");
    }

    /**
     * The process's own environment, keyboard and streams.
     *
     * @return the terminal
     */
    public static Terminal system() {
        return new Terminal(
                System.getenv()::get,
                new BufferedReader(new InputStreamReader(System.in, Charset.defaultCharset())),
                new PrintWriter(System.out, true, System.out.charset()),
                new PrintWriter(System.err, true, System.err.charset()));
    }

    /**
     * The variables with those names that are set, in the order asked for.
     *
     * @param names the variables wanted
     * @return each one that is set, with its value
     */
    Map<String, String> variables(Collection<String> names) {
        Map<String, String> set = new LinkedHashMap<>();
        for (String name : names) {
            String value = environment.apply(name);
            if (value != null) {
                set.put(name, value);
            }
        }
        return set;
    }
}
