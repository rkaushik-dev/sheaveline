package io.github.rkaushikdev.sheaveline.persistence.models;

import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.ENUM_LENGTH;

import io.github.rkaushikdev.sheaveline.ops.CheckOutcome;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** One item of a morning walk, by its sheet code. An item is walked once a day. */
@Entity
@Table(
        name = "preop_results",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "one_result_an_item",
                        columnNames = {"day_log_id", "code"}))
public class PreOpResultRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false, cascade = CascadeType.PERSIST)
    @JoinColumn(
            name = "day_log_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_preop_results_day_log_id_day_logs"))
    private DayLogRow dayLog;

    @Column(name = "code", length = 4, nullable = false)
    private String code;

    @Column(name = "outcome", length = ENUM_LENGTH, nullable = false)
    private CheckOutcome outcome;

    @Column(name = "by", length = 64, nullable = false)
    private String by;

    @Column(name = "note", length = 500, nullable = false)
    private String note = "";

    /** For the persistence provider. */
    protected PreOpResultRow() {}

    /**
     * A result with no note, on no day yet.
     *
     * @param code the item's code on the sheet
     * @param outcome how it went
     * @param by who walked it
     */
    public PreOpResultRow(String code, CheckOutcome outcome, String by) {
        this.code = code;
        this.outcome = outcome;
        this.by = by;
    }

    void walkedOn(DayLogRow owner) {
        this.dayLog = owner;
    }

    /**
     * The generated key.
     *
     * @return the key, or null until the row is written
     */
    public Integer getId() {
        return id;
    }

    public DayLogRow getDayLog() {
        return dayLog;
    }

    public String getCode() {
        return code;
    }

    public CheckOutcome getOutcome() {
        return outcome;
    }

    public String getBy() {
        return by;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
