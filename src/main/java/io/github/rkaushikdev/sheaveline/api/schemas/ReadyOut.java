package io.github.rkaushikdev.sheaveline.api.schemas;

import java.time.LocalDate;
import java.util.List;

/**
 * The answer that costs a query and says what it found.
 *
 * @param ready whether the database is there, has the tables in it, and answers
 * @param database the database's url
 * @param tables how many tables it holds
 * @param missing the tables the service needs that it does not hold
 * @param lifts how many machines are on the register
 * @param today the day the service thinks it is
 * @param detail what was found, in a sentence
 */
public record ReadyOut(
        boolean ready, String database, int tables, List<String> missing, int lifts, LocalDate today, String detail) {

    /** A readiness answer. */
    public ReadyOut {
        missing = List.copyOf(missing);
    }
}
