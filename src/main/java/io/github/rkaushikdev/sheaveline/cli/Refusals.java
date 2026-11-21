package io.github.rkaushikdev.sheaveline.cli;

import io.github.rkaushikdev.sheaveline.SheavelineError;
import io.github.rkaushikdev.sheaveline.journal.Journal;
import jakarta.persistence.PersistenceException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;
import java.util.function.Supplier;
import org.flywaydb.core.api.FlywayException;
import org.springframework.dao.DataAccessException;

/**
 * A refusal printed as one sentence and an exit code, rather than as a stack trace.
 *
 * <p>The person reading it is a lift mechanic, and a stack trace tells them nothing they can act
 * on. A database that will not open or will not answer counts as a refusal too: it is the
 * commonest failure there is, it is nearly always a path or a permission, and the forty lines of
 * driver stack under it say nothing the person at the keyboard can use. It gets its own exit code,
 * so a script can tell a broken database from a record that was refused.
 *
 * <p>Anything else is a fault in the program and is left alone.
 */
final class Refusals {

    private Refusals() {}

    /** Some work that ends in an exit code. */
    @FunctionalInterface
    interface Work {

        /**
         * Do it.
         *
         * @return the exit code
         * @throws IOException when a file cannot be written
         */
        int run() throws IOException;
    }

    /**
     * Do the work, and turn a refusal into a sentence on the error stream and an exit code.
     *
     * @param terminal where the sentence goes
     * @param journal the journal the refusal is written in as well, asked for only when there is one
     * @param command the command, as the journal names it
     * @param work the work
     * @return the work's own exit code, {@link Main#REFUSED} for a refusal, or {@link
     *     Main#NOT_ANSWERING} for a database that will not answer
     * @throws IOException when the work cannot write a file
     */
    static int handled(Terminal terminal, Supplier<Journal> journal, String command, Work work) throws IOException {
        try {
            return work.run();
        } catch (RuntimeException trouble) {
            Optional<Throwable> broken = databaseFailure(trouble);
            if (broken.isPresent()) {
                journal.get()
                        .error(
                                "database",
                                "command",
                                command,
                                "error",
                                broken.get().getClass().getSimpleName());
                terminal.err().println("the database will not answer: " + firstLine(broken.get()));
                return Main.NOT_ANSWERING;
            }
            if (trouble instanceof SheavelineError refusal) {
                String kind = refusal.getClass().getSimpleName();
                journal.get().warn("refused", "command", command, "error", kind);
                terminal.err().println(kind + ": " + refusal.getMessage());
                return Main.REFUSED;
            }
            throw trouble;
        }
    }

    /**
     * The first thing in the chain of causes that came from the database or its driver.
     *
     * @param trouble what was thrown
     * @return the failure, or empty when the database had nothing to do with it
     */
    static Optional<Throwable> databaseFailure(Throwable trouble) {
        for (Throwable one = trouble; one != null; one = one.getCause()) {
            if (one instanceof PersistenceException
                    || one instanceof DataAccessException
                    || one instanceof FlywayException
                    || one instanceof SQLException) {
                return Optional.of(one);
            }
        }
        return Optional.empty();
    }

    /**
     * The driver's own sentence, without the wrapping and the stack it usually arrives with: the
     * first line of the deepest SQL exception in the chain, or of the deepest cause that says anything
     * when the driver was never reached. Underneath the driver there is usually only a file name.
     *
     * @param error the failure
     * @return one line
     */
    static String firstLine(Throwable error) {
        String said = error.getClass().getSimpleName();
        String driver = null;
        for (Throwable one = error; one != null; one = one.getCause()) {
            String message = one.getMessage();
            if (message != null && !message.isBlank()) {
                said = message;
                if (one instanceof SQLException) {
                    driver = message;
                }
            }
        }
        String sentence = driver == null ? said : driver;
        return sentence.strip().lines().findFirst().orElse(sentence);
    }
}
