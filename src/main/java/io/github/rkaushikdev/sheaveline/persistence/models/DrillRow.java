package io.github.rkaushikdev.sheaveline.persistence.models;

import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.ENUM_LENGTH;
import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.LIFT_CODE_LENGTH;

import io.github.rkaushikdev.sheaveline.evac.DrillKind;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.hibernate.annotations.Check;

/**
 * One evacuation drill on a machine, with the teams that worked it: one drill a machine a day.
 *
 * <p>Drills sit with the board's paperwork because they are part of the department's answer when
 * the board asks whether the people on the hill were allowed to be there. The day column is called
 * {@code on}, which is a keyword in SQL, so its name is quoted wherever it is written.
 */
@Entity
@Table(
        name = "drills",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "one_drill_a_day",
                        columnNames = {"lift_code", "\"ON\""}))
@Check(name = "ck_drills_carriers_not_negative", constraints = "carriers_worked >= 0")
public class DrillRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "lift_code", length = LIFT_CODE_LENGTH, nullable = false)
    private LiftCode liftCode;

    @Column(name = "\"ON\"", nullable = false)
    private LocalDate on;

    @Column(name = "kind", length = ENUM_LENGTH, nullable = false)
    private DrillKind kind;

    @Column(name = "carriers_worked", nullable = false)
    private int carriersWorked;

    @Column(name = "elapsed_seconds", nullable = false)
    private int elapsedSeconds;

    @Column(name = "observer", length = 64, nullable = false)
    private String observer;

    /** The machine, read through {@code lift_code}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "lift_code",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_drills_lift_code_lifts"))
    private LiftRow lift;

    @OneToMany(mappedBy = "drill", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DrillTeamRow> teams = new ArrayList<>();

    /** For the persistence provider. */
    protected DrillRow() {}

    /**
     * A drill with no teams on it yet.
     *
     * @param liftCode the machine drilled
     * @param on the day it was drilled
     * @param kind how much of the line was worked
     * @param carriersWorked how many carriers were actually worked
     * @param elapsedSeconds how long it took, in seconds
     * @param observer who watched it and signed it off
     */
    public DrillRow(
            LiftCode liftCode, LocalDate on, DrillKind kind, int carriersWorked, int elapsedSeconds, String observer) {
        this.liftCode = liftCode;
        this.on = on;
        this.kind = kind;
        this.carriersWorked = carriersWorked;
        this.elapsedSeconds = elapsedSeconds;
        this.observer = observer;
    }

    /**
     * The generated key.
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

    public LocalDate getOn() {
        return on;
    }

    public DrillKind getKind() {
        return kind;
    }

    public int getCarriersWorked() {
        return carriersWorked;
    }

    public int getElapsedSeconds() {
        return elapsedSeconds;
    }

    public String getObserver() {
        return observer;
    }

    /**
     * The teams that took part.
     *
     * @return the teams, read only
     */
    public List<DrillTeamRow> getTeams() {
        return Collections.unmodifiableList(teams);
    }

    /**
     * Put a team on the drill.
     *
     * @param team the team, which now points back at this drill
     */
    public void addTeam(DrillTeamRow team) {
        team.workedIn(this);
        teams.add(team);
    }
}
