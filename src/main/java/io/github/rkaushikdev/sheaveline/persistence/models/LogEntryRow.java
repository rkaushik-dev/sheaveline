package io.github.rkaushikdev.sheaveline.persistence.models;

import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.ENUM_LENGTH;

import io.github.rkaushikdev.sheaveline.ops.EntryKind;
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
import java.time.LocalDateTime;

/** One line in a machine's day book: when, what kind, what it says, and who wrote it. */
@Entity
@Table(name = "log_entries")
public class LogEntryRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false, cascade = CascadeType.PERSIST)
    @JoinColumn(
            name = "day_log_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_log_entries_day_log_id_day_logs"))
    private DayLogRow dayLog;

    @Column(name = "at", nullable = false)
    private LocalDateTime at;

    @Column(name = "kind", length = ENUM_LENGTH, nullable = false)
    private EntryKind kind;

    @Column(name = "text", length = 500, nullable = false)
    private String text;

    @Column(name = "by", length = 64, nullable = false)
    private String by;

    /** For the persistence provider. */
    protected LogEntryRow() {}

    /**
     * A line in no book yet.
     *
     * @param at when it was written
     * @param kind what kind of line it is
     * @param text what it says
     * @param by who wrote it
     */
    public LogEntryRow(LocalDateTime at, EntryKind kind, String text, String by) {
        this.at = at;
        this.kind = kind;
        this.text = text;
        this.by = by;
    }

    void writtenIn(DayLogRow owner) {
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

    public LocalDateTime getAt() {
        return at;
    }

    public EntryKind getKind() {
        return kind;
    }

    public String getText() {
        return text;
    }

    public String getBy() {
        return by;
    }
}
