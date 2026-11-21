package io.github.rkaushikdev.sheaveline.persistence.models;

import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.ENUM_LENGTH;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.measures.LoadPerFoot;
import io.github.rkaushikdev.sheaveline.plant.LayDirection;
import io.github.rkaushikdev.sheaveline.plant.RopeConstruction;
import io.github.rkaushikdev.sheaveline.plant.RopeGrade;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** What a rope was made to: its construction, grade, lay, diameter, strength and weight. */
@Entity
@Table(name = "rope_specs")
public class RopeSpecRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "construction", length = ENUM_LENGTH, nullable = false)
    private RopeConstruction construction;

    @Column(name = "grade", length = ENUM_LENGTH, nullable = false)
    private RopeGrade grade;

    @Column(name = "lay", length = ENUM_LENGTH, nullable = false)
    private LayDirection lay;

    @Column(name = "nominal_diameter", nullable = false)
    private Length nominalDiameter;

    @Column(name = "breaking_strength", nullable = false)
    private Load breakingStrength;

    @Column(name = "weight", nullable = false)
    private LoadPerFoot weight;

    /** For the persistence provider. */
    protected RopeSpecRow() {}

    /**
     * A specification.
     *
     * @param construction strands by wires
     * @param grade the steel's grade
     * @param lay which way and how the strands are laid
     * @param nominalDiameter the diameter the mill made it to
     * @param breakingStrength the minimum breaking strength
     * @param weight what a foot of it weighs
     */
    public RopeSpecRow(
            RopeConstruction construction,
            RopeGrade grade,
            LayDirection lay,
            Length nominalDiameter,
            Load breakingStrength,
            LoadPerFoot weight) {
        this.construction = construction;
        this.grade = grade;
        this.lay = lay;
        this.nominalDiameter = nominalDiameter;
        this.breakingStrength = breakingStrength;
        this.weight = weight;
    }

    /**
     * The generated key.
     *
     * @return the key, or null until the row is written
     */
    public Integer getId() {
        return id;
    }

    public RopeConstruction getConstruction() {
        return construction;
    }

    public RopeGrade getGrade() {
        return grade;
    }

    public LayDirection getLay() {
        return lay;
    }

    public Length getNominalDiameter() {
        return nominalDiameter;
    }

    public Load getBreakingStrength() {
        return breakingStrength;
    }

    public LoadPerFoot getWeight() {
        return weight;
    }
}
