package io.github.rkaushikdev.sheaveline;

/** A measure fell outside the range the domain allows. */
public class RangeError extends MeasureError {

    private final String what;
    private final Object value;
    private final Object low;
    private final Object high;

    /**
     * A value outside its range.
     *
     * @param what the name of the quantity, as it reads in the sentence
     * @param value the value that was refused
     * @param low the bottom of the range, or a phrase standing in for it
     * @param high the top of the range, or a phrase standing in for it
     */
    public RangeError(String what, Object value, Object low, Object high) {
        super(what + " " + value + " is outside " + low + " to " + high);
        this.what = what;
        this.value = value;
        this.low = low;
        this.high = high;
    }

    /**
     * The quantity that was out of range.
     *
     * @return its name
     */
    public String what() {
        return what;
    }

    /**
     * The refused value.
     *
     * @return the value as it was given
     */
    public Object value() {
        return value;
    }

    /**
     * The bottom of the range.
     *
     * @return the bound, or the phrase standing in for it
     */
    public Object low() {
        return low;
    }

    /**
     * The top of the range.
     *
     * @return the bound, or the phrase standing in for it
     */
    public Object high() {
        return high;
    }
}
