package io.github.rkaushikdev.sheaveline.cli;

import java.io.IOException;

/**
 * One command: what it does with the console once its options have been read.
 *
 * <p>A command group is not an action. Naming a group on its own prints its help.
 */
interface Action {

    /**
     * Do the work and say what was done.
     *
     * @param console the database, the clock and the streams
     * @return the exit code: {@link Main#OK}, or {@link Main#REFUSED} when something the person at
     *     the keyboard has to act on was found
     * @throws IOException when a file the command was pointed at cannot be written
     */
    int run(Console console) throws IOException;
}
