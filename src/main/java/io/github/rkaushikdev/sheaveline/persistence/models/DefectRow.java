package io.github.rkaushikdev.sheaveline.persistence.models;

import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.ENUM_LENGTH;

import io.github.rkaushikdev.sheaveline.upkeep.DefectLevel;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Optional;
import org.hibernate.annotations.Check;

/**
 * One defect, on the record of the inspection that found it: where it is, how bad, what is wrong,
 * and when it was raised and cleared. It cannot be cleared before it was raised.
 */
@Entity
@Table(
        name = "defects",
        // What is still open is asked on every morning page and once a machine.
        indexes = @Index(name = "ix_defects_still_open", columnList = "cleared, raised"))
@Check(name = "ck_defects_cleared_after_raised", constraints = "cleared is null or cleared >= raised")
public class DefectRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false, cascade = CascadeType.PERSIST)
    @JoinColumn(
            name = "record_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_defects_record_id_inspection_records"))
    private InspectionRecordRow record;

    @Column(name = "\"WHERE\"", length = 32, nullable = false)
    private String where;

    @Column(name = "level", length = ENUM_LENGTH, nullable = false)
    private DefectLevel level;

    @Column(name = "detail", length = 500, nullable = false)
    private String detail;

    @Column(name = "raised", nullable = false)
    private LocalDate raised;

    @Column(name = "cleared")
    private LocalDate cleared;

    @Column(name = "cleared_by", length = 64)
    private String clearedBy;

    /** For the persistence provider. */
    protected DefectRow() {}

    /**
     * An open defect on no record yet.
     *
     * @param where where it is, such as a tower reference
     * @param level how bad it is
     * @param detail what is wrong
     * @param raised the day it was raised
     */
    public DefectRow(String where, DefectLevel level, String detail, LocalDate raised) {
        this.where = where;
        this.level = level;
        this.detail = detail;
        this.raised = raised;
    }

    void foundBy(InspectionRecordRow owner) {
        this.record = owner;
    }

    /**
     * The generated key.
     *
     * @return the key, or null until the row is written
     */
    public Integer getId() {
        return id;
    }

    /**
     * The inspection that found it.
     *
     * @return the record, or null until the defect is written against one
     */
    public InspectionRecordRow getRecord() {
        return record;
    }

    public String getWhere() {
        return where;
    }

    public DefectLevel getLevel() {
        return level;
    }

    /**
     * Regrade the defect. A work order already raised from it keeps the priority it was given.
     *
     * @param level the new level
     */
    public void setLevel(DefectLevel level) {
        this.level = level;
    }

    public String getDetail() {
        return detail;
    }

    public LocalDate getRaised() {
        return raised;
    }

    public Optional<LocalDate> getCleared() {
        return Optional.ofNullable(cleared);
    }

    /**
     * Record the day it was cleared.
     *
     * @param cleared the day, or null while it is open
     */
    public void setCleared(LocalDate cleared) {
        this.cleared = cleared;
    }

    public Optional<String> getClearedBy() {
        return Optional.ofNullable(clearedBy);
    }

    /**
     * Record who cleared it.
     *
     * @param clearedBy who cleared it, or null while it is open
     */
    public void setClearedBy(String clearedBy) {
        this.clearedBy = clearedBy;
    }
}
