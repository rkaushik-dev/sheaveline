package io.github.rkaushikdev.sheaveline.weather;

import io.github.rkaushikdev.sheaveline.SheavelineError;

/** A weather record was built or read in a way that cannot hold. */
public class WeatherError extends SheavelineError {

    /**
     * An error carrying the sentence the weather desk sees.
     *
     * @param message the sentence
     */
    public WeatherError(String message) {
        super(message);
    }
}
