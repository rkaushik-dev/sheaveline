package io.github.rkaushikdev.sheaveline.persistence.models;

import io.github.rkaushikdev.sheaveline.measures.Length;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One weather station, by its name, and every reading it has sent.
 *
 * <p>A station is a row rather than a name on every reading, because the elevation decides which
 * station a lift reads, and an elevation typed into four thousand readings is an elevation that is
 * wrong in one of them.
 */
@Entity
@Table(name = "weather_stations")
public class StationRow {

    @Id
    @Column(name = "name", length = 32, nullable = false)
    private String name;

    @Column(name = "elevation", nullable = false)
    private Length elevation;

    @OneToMany(mappedBy = "station", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ObservationRow> observations = new ArrayList<>();

    /** For the persistence provider. */
    protected StationRow() {}

    /**
     * A station that has sent nothing yet.
     *
     * @param name what the station is called
     * @param elevation how high it stands
     */
    public StationRow(String name, Length elevation) {
        this.name = name;
        this.elevation = elevation;
    }

    public String getName() {
        return name;
    }

    public Length getElevation() {
        return elevation;
    }

    /**
     * The readings the station has sent, as last read. They go when the station goes.
     *
     * @return the readings, read only
     */
    public List<ObservationRow> getObservations() {
        return Collections.unmodifiableList(observations);
    }
}
