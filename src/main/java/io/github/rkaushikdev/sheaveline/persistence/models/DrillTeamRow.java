package io.github.rkaushikdev.sheaveline.persistence.models;

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

/** One team that worked a drill. A team is counted once in a drill. */
@Entity
@Table(
        name = "drill_teams",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "one_team_a_drill",
                        columnNames = {"drill_id", "team"}))
public class DrillTeamRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "drill_id", nullable = false, foreignKey = @ForeignKey(name = "fk_drill_teams_drill_id_drills"))
    private DrillRow drill;

    @Column(name = "team", length = 32, nullable = false)
    private String team;

    /** For the persistence provider. */
    protected DrillTeamRow() {}

    /**
     * A team on no drill yet.
     *
     * @param team the team's name
     */
    public DrillTeamRow(String team) {
        this.team = team;
    }

    void workedIn(DrillRow owner) {
        this.drill = owner;
    }

    /**
     * The generated key.
     *
     * @return the key, or null until the row is written
     */
    public Integer getId() {
        return id;
    }

    public DrillRow getDrill() {
        return drill;
    }

    public String getTeam() {
        return team;
    }
}
