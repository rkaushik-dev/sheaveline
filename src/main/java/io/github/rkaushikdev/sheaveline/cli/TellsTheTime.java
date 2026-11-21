package io.github.rkaushikdev.sheaveline.cli;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * What the day and moment options need, which is less than the whole console.
 *
 * <p>Asking for the shape rather than the console keeps the shared options free of everything else
 * the console carries, and lets a test hand them a fixed clock.
 */
interface TellsTheTime {

    /**
     * The day it is.
     *
     * @return today
     */
    LocalDate today();

    /**
     * The moment it is, to the minute or better.
     *
     * @return now
     */
    LocalDateTime now();
}
