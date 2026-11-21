package io.github.rkaushikdev.sheaveline.persistence.models;

import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.ENUM_LENGTH;
import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.LIFT_CODE_LENGTH;

import io.github.rkaushikdev.sheaveline.ops.Post;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.schedule.TimeOfDay;
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
import java.time.LocalDate;
import org.hibernate.annotations.Check;

/**
 * One shift a person works at a post on a machine. A person may work several shifts a day, each
 * starting at its own time, and a shift runs forwards.
 */
@Entity
@Table(
        name = "assignments",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "one_shift_a_start",
                        columnNames = {"lift_code", "day", "person", "starts"}))
@Check(name = "ck_assignments_shift_runs_forwards", constraints = "ends > starts")
public class AssignmentRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "lift_code", length = LIFT_CODE_LENGTH, nullable = false)
    private LiftCode liftCode;

    @Column(name = "day", nullable = false)
    private LocalDate day;

    @Column(name = "person", length = 64, nullable = false)
    private String person;

    @Column(name = "post", length = ENUM_LENGTH, nullable = false)
    private Post post;

    @Column(name = "starts", nullable = false)
    private TimeOfDay starts;

    @Column(name = "ends", nullable = false)
    private TimeOfDay ends;

    /** The machine, read through {@code lift_code}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "lift_code",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_assignments_lift_code_lifts"))
    private LiftRow lift;

    /** For the persistence provider. */
    protected AssignmentRow() {}

    /**
     * A shift.
     *
     * @param liftCode the machine
     * @param day the day worked
     * @param person who works it
     * @param post where they stand
     * @param starts when it starts
     * @param ends when it ends
     */
    public AssignmentRow(LiftCode liftCode, LocalDate day, String person, Post post, TimeOfDay starts, TimeOfDay ends) {
        this.liftCode = liftCode;
        this.day = day;
        this.person = person;
        this.post = post;
        this.starts = starts;
        this.ends = ends;
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

    public LocalDate getDay() {
        return day;
    }

    public void setDay(LocalDate day) {
        this.day = day;
    }

    public String getPerson() {
        return person;
    }

    public void setPerson(String person) {
        this.person = person;
    }

    public Post getPost() {
        return post;
    }

    public void setPost(Post post) {
        this.post = post;
    }

    public TimeOfDay getStarts() {
        return starts;
    }

    public void setStarts(TimeOfDay starts) {
        this.starts = starts;
    }

    public TimeOfDay getEnds() {
        return ends;
    }

    public void setEnds(TimeOfDay ends) {
        this.ends = ends;
    }
}
