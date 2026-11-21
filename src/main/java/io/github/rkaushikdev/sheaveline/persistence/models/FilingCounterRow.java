package io.github.rkaushikdev.sheaveline.persistence.models;

import io.github.rkaushikdev.sheaveline.persistence.Numbering;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * One row a state and a year, holding the next board number to hand out.
 *
 * <p>It lives with the numbering rather than with the board's tables, and is mapped with every
 * other row so that the schema always has it.
 */
@Entity
@Table(name = "filing_counters")
@IdClass(FilingCounterKey.class)
public class FilingCounterRow {

    @Id
    @Column(name = "state", length = 2, nullable = false)
    private String state;

    @Id
    @Column(name = "year", nullable = false)
    private int year;

    @Column(name = "next_sequence", nullable = false)
    private int nextSequence = Numbering.FIRST_SEQUENCE;

    /** For the persistence provider. */
    protected FilingCounterRow() {}

    /**
     * A counter that has handed out nothing yet.
     *
     * @param state the two letter state code, upper case
     * @param year the filing year
     */
    public FilingCounterRow(String state, int year) {
        this.state = state;
        this.year = year;
    }

    /**
     * The state.
     *
     * @return the two letter code
     */
    public String getState() {
        return state;
    }

    /**
     * The year.
     *
     * @return the filing year
     */
    public int getYear() {
        return year;
    }

    /**
     * The sequence the next number takes.
     *
     * @return the next sequence
     */
    public int getNextSequence() {
        return nextSequence;
    }

    /**
     * Move the counter.
     *
     * @param nextSequence the sequence the next number takes
     */
    public void setNextSequence(int nextSequence) {
        this.nextSequence = nextSequence;
    }
}
