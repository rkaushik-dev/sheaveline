package io.github.rkaushikdev.sheaveline.persistence.models;

import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.ENUM_LENGTH;
import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.FILING_NUMBER_LENGTH;
import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.LIFT_CODE_LENGTH;

import io.github.rkaushikdev.sheaveline.board.IncidentKind;
import io.github.rkaushikdev.sheaveline.board.InjuryLevel;
import io.github.rkaushikdev.sheaveline.plant.FilingNumber;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Optional;
import org.hibernate.annotations.Check;

/**
 * One incident on a machine: one a machine a moment, with the filing that answers for it once
 * somebody files.
 */
@Entity
@Table(
        name = "incidents",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "one_incident_a_moment",
                        columnNames = {"lift_code", "at"}),
        // What has not gone to the board yet is the question the register exists to answer, and
        // it was reading every incident ever recorded to answer it.
        indexes = @Index(name = "ix_incidents_not_yet_filed", columnList = "filing_number, at"))
@Check(name = "ck_incidents_people_not_negative", constraints = "people >= 0")
public class IncidentRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "lift_code", length = LIFT_CODE_LENGTH, nullable = false)
    private LiftCode liftCode;

    @Column(name = "at", nullable = false)
    private LocalDateTime at;

    @Column(name = "kind", length = ENUM_LENGTH, nullable = false)
    private IncidentKind kind;

    @Column(name = "injury", length = ENUM_LENGTH, nullable = false)
    private InjuryLevel injury;

    @Column(name = "people", nullable = false)
    private int people;

    @Column(name = "narrative", length = 2000, nullable = false)
    private String narrative;

    @Column(name = "reported_by", length = 64, nullable = false)
    private String reportedBy;

    @Column(name = "filing_number", length = FILING_NUMBER_LENGTH)
    private FilingNumber filingNumber;

    /** The machine, read through {@code lift_code}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "lift_code",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_incidents_lift_code_lifts"))
    private LiftRow lift;

    /** The filing, read through {@code filing_number}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "filing_number",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_incidents_filing_number_filings"))
    private FilingRow filing;

    /** For the persistence provider. */
    protected IncidentRow() {}

    /**
     * An incident nobody has filed yet.
     *
     * @param liftCode the machine
     * @param at when it happened
     * @param kind what the machine did
     * @param injury what happened to the people
     * @param people how many people were involved
     * @param narrative what happened, in the reporter's words
     * @param reportedBy who reported it
     */
    public IncidentRow(
            LiftCode liftCode,
            LocalDateTime at,
            IncidentKind kind,
            InjuryLevel injury,
            int people,
            String narrative,
            String reportedBy) {
        this.liftCode = liftCode;
        this.at = at;
        this.kind = kind;
        this.injury = injury;
        this.people = people;
        this.narrative = narrative;
        this.reportedBy = reportedBy;
    }

    /**
     * The generated key, which is what the incident is addressed by.
     *
     * @return the key, or null until the row is written
     */
    public Integer getId() {
        return id;
    }

    public LiftCode getLiftCode() {
        return liftCode;
    }

    public LiftRow getLift() {
        return lift;
    }

    public LocalDateTime getAt() {
        return at;
    }

    public IncidentKind getKind() {
        return kind;
    }

    public InjuryLevel getInjury() {
        return injury;
    }

    public int getPeople() {
        return people;
    }

    public String getNarrative() {
        return narrative;
    }

    public String getReportedBy() {
        return reportedBy;
    }

    /**
     * The number of the filing that answers for this incident.
     *
     * @return the number, or empty until somebody files
     */
    public Optional<FilingNumber> getFilingNumber() {
        return Optional.ofNullable(filingNumber);
    }

    /**
     * Hang a filing on the incident by its number. The filing itself is read on the next load.
     *
     * @param filingNumber the filing's number, or null for unfiled
     */
    public void setFilingNumber(FilingNumber filingNumber) {
        this.filingNumber = filingNumber;
        if (filing != null && !filing.getNumber().equals(filingNumber)) {
            filing = null;
        }
    }

    /**
     * The filing that answers for this incident.
     *
     * @return the filing, or empty until somebody files
     */
    public Optional<FilingRow> getFiling() {
        return Optional.ofNullable(filing);
    }

    void unfile() {
        this.filing = null;
        this.filingNumber = null;
    }
}
