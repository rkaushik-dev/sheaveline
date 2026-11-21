package io.github.rkaushikdev.sheaveline.persistence.models;

import static io.github.rkaushikdev.sheaveline.persistence.ColumnTypes.ENUM_LENGTH;

import io.github.rkaushikdev.sheaveline.measures.Bearing;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Speed;
import io.github.rkaushikdev.sheaveline.measures.Temperature;
import io.github.rkaushikdev.sheaveline.weather.Precipitation;
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
 * One reading from a station: one a station a moment.
 *
 * <p>The wind is three columns rather than one, because a gust is not a wind and the hold rules read
 * them separately. Lightning is a distance or nothing rather than a flag, because how far away it
 * was is the whole question.
 */
@Entity
@Table(
        name = "observations",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "one_reading_a_moment",
                        columnNames = {"station_name", "taken"}),
        // Everything since a moment, across every station, is what the hold log reads.
        indexes = @Index(name = "ix_observations_by_moment", columnList = "taken"))
@Check(name = "ck_observations_visibility_not_negative", constraints = "visibility >= 0")
@Check(name = "ck_observations_gust_above_sustained", constraints = "wind_gust is null or wind_gust >= wind_speed")
public class ObservationRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Integer id;

    @Column(name = "station_name", length = 32, nullable = false)
    private String stationName;

    @Column(name = "taken", nullable = false)
    private LocalDateTime taken;

    @Column(name = "wind_speed", nullable = false)
    private Speed windSpeed;

    @Column(name = "wind_bearing", nullable = false)
    private Bearing windBearing;

    @Column(name = "wind_gust")
    private Speed windGust;

    @Column(name = "temperature", nullable = false)
    private Temperature temperature;

    @Column(name = "visibility", nullable = false)
    private Length visibility;

    @Column(name = "precipitation", length = ENUM_LENGTH, nullable = false)
    private Precipitation precipitation;

    @Column(name = "rime", nullable = false)
    private boolean rime;

    @Column(name = "lightning_within")
    private Length lightningWithin;

    /** The station, read through {@code station_name}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "station_name",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_observations_station_name_weather_stations"))
    private StationRow station;

    /** For the persistence provider. */
    protected ObservationRow() {}

    /**
     * A reading with no gust, no rime and no lightning.
     *
     * @param stationName the station that sent it
     * @param taken when it was taken
     * @param windSpeed the sustained wind
     * @param windBearing where the wind came from
     * @param temperature the air temperature
     * @param visibility how far could be seen
     * @param precipitation what was falling
     */
    public ObservationRow(
            String stationName,
            LocalDateTime taken,
            Speed windSpeed,
            Bearing windBearing,
            Temperature temperature,
            Length visibility,
            Precipitation precipitation) {
        this.stationName = stationName;
        this.taken = taken;
        this.windSpeed = windSpeed;
        this.windBearing = windBearing;
        this.temperature = temperature;
        this.visibility = visibility;
        this.precipitation = precipitation;
    }

    void sentBy(StationRow owner) {
        this.station = owner;
        this.stationName = owner.getName();
    }

    /**
     * The generated key.
     *
     * @return the key, or null until the row is written
     */
    public Integer getId() {
        return id;
    }

    public String getStationName() {
        return stationName;
    }

    public StationRow getStation() {
        return station;
    }

    public LocalDateTime getTaken() {
        return taken;
    }

    public Speed getWindSpeed() {
        return windSpeed;
    }

    public Bearing getWindBearing() {
        return windBearing;
    }

    public Optional<Speed> getWindGust() {
        return Optional.ofNullable(windGust);
    }

    /**
     * Give the gust.
     *
     * @param windGust the gust, or null for a reading with none
     */
    public void setWindGust(Speed windGust) {
        this.windGust = windGust;
    }

    public Temperature getTemperature() {
        return temperature;
    }

    public Length getVisibility() {
        return visibility;
    }

    public Precipitation getPrecipitation() {
        return precipitation;
    }

    public boolean isRime() {
        return rime;
    }

    public void setRime(boolean rime) {
        this.rime = rime;
    }

    public Optional<Length> getLightningWithin() {
        return Optional.ofNullable(lightningWithin);
    }

    /**
     * Say how close the nearest lightning was.
     *
     * @param lightningWithin the distance, or null for none seen
     */
    public void setLightningWithin(Length lightningWithin) {
        this.lightningWithin = lightningWithin;
    }
}
