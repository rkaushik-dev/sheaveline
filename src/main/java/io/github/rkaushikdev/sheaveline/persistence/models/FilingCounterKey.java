package io.github.rkaushikdev.sheaveline.persistence.models;

import java.io.Serializable;

/**
 * The key of a {@link FilingCounterRow}: one counter a state and a year.
 *
 * @param state the two letter state code, upper case
 * @param year the filing year
 */
public record FilingCounterKey(String state, int year) implements Serializable {}
