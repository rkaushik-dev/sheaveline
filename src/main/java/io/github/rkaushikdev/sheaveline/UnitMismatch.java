package io.github.rkaushikdev.sheaveline;

/** Two measures of different kinds were combined. */
public class UnitMismatch extends MeasureError {

    private final String left;
    private final String right;

    /**
     * Two kinds that do not combine.
     *
     * @param left the kind on the left of the operation
     * @param right the kind on the right
     */
    public UnitMismatch(String left, String right) {
        super("cannot combine " + left + " with " + right);
        this.left = left;
        this.right = right;
    }

    /**
     * The kind on the left.
     *
     * @return its name
     */
    public String left() {
        return left;
    }

    /**
     * The kind on the right.
     *
     * @return its name
     */
    public String right() {
        return right;
    }
}
