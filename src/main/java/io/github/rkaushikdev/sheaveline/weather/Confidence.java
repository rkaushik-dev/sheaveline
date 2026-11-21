package io.github.rkaushikdev.sheaveline.weather;

import io.github.rkaushikdev.sheaveline.util.Valued;

/** How much of the forecast the office is prepared to act on. */
public enum Confidence implements Valued {
    /** A guess the forecaster would not plan a crew against. */
    LOW("low"),
    /** The usual forecast. */
    MODERATE("moderate"),
    /** A forecast the forecaster would stake the morning on. */
    HIGH("high");

    private final String value;

    Confidence(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /**
     * Whether the office plans against a period forecast with this confidence.
     *
     * @return false only for low confidence
     */
    public boolean worthPlanningAgainst() {
        return this != LOW;
    }
}
