package io.github.rkaushikdev.sheaveline;

/** Something in the environment does not read as what it has to be. */
public class ConfigurationError extends SheavelineError {

    /**
     * A configuration mistake, named by the variable that carries it.
     *
     * @param message the sentence
     */
    public ConfigurationError(String message) {
        super(message);
    }

    /**
     * A configuration mistake and what it broke underneath.
     *
     * @param message the sentence
     * @param cause the underlying failure
     */
    public ConfigurationError(String message, Throwable cause) {
        super(message, cause);
    }
}
