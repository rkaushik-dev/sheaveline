package io.github.rkaushikdev.sheaveline.api.routes;

import io.github.rkaushikdev.sheaveline.api.Lookup;
import io.github.rkaushikdev.sheaveline.api.Paged;
import io.github.rkaushikdev.sheaveline.api.Paging;
import io.github.rkaushikdev.sheaveline.api.Requires;
import io.github.rkaushikdev.sheaveline.api.Role;
import io.github.rkaushikdev.sheaveline.api.Settings;
import io.github.rkaushikdev.sheaveline.api.schemas.HoldOut;
import io.github.rkaushikdev.sheaveline.api.schemas.ObservationIn;
import io.github.rkaushikdev.sheaveline.api.schemas.ObservationOut;
import io.github.rkaushikdev.sheaveline.api.schemas.StationIn;
import io.github.rkaushikdev.sheaveline.api.schemas.StationOut;
import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.persistence.UnitOfWork;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.github.rkaushikdev.sheaveline.weather.Observation;
import io.github.rkaushikdev.sheaveline.weather.Station;
import io.github.rkaushikdev.sheaveline.weather.WeatherHold;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stations, readings and the hold decision that comes off them.
 *
 * <p>The hold route picks its own station. The caller says which machine, and the station nearest
 * the top terminal is the one that answers, because that is where the wind is and asking the base
 * what the summit is doing has put people on a chairlift in forty knots more than once.
 *
 * <p>It also refuses a stale reading rather than answering from it. An operator who gets told there
 * is no fresh reading goes and looks; an operator who gets told the machine is clear on a reading
 * from forty minutes ago does not.
 */
@RestController
@Tag(name = "weather")
public class WeatherController {

    /** The earliest moment a reading could carry, which every reading is at or after. */
    static final LocalDateTime EARLIEST = LocalDateTime.of(1, 1, 1, 0, 0);

    private final Settings settings;

    /**
     * The weather routes for one running copy.
     *
     * @param settings its settings
     */
    public WeatherController(Settings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /**
     * Set up a station. Each name goes in once.
     *
     * @param body the station
     * @param work the request's unit of work
     * @return the station
     */
    @PostMapping("/weather/stations")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires(Role.MANAGER)
    public StationOut addStation(@Valid @RequestBody StationIn body, UnitOfWork work) {
        Station station = body.toDomain();
        work.weather().addStation(station);
        work.commit();
        return StationOut.of(station);
    }

    /**
     * Every station, in name order.
     *
     * @param work the request's unit of work
     * @return the stations
     */
    @GetMapping("/weather/stations")
    @Requires(Role.READER)
    public List<StationOut> listStations(UnitOfWork work) {
        return work.weather().stations().stream().map(StationOut::of).toList();
    }

    /**
     * File a reading from a station.
     *
     * @param body the reading
     * @param work the request's unit of work
     * @param look the request's lookup
     * @return the reading, with what follows from it
     */
    @PostMapping("/weather/observations")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires(Role.OPERATOR)
    public ObservationOut recordObservation(@Valid @RequestBody ObservationIn body, UnitOfWork work, Lookup look) {
        Station station = look.station(body.station());
        Observation seen = body.toDomain(station);
        work.weather().record(seen);
        work.commit();
        return ObservationOut.of(seen);
    }

    /**
     * The readings, oldest first, narrowed to one station or to those since a moment. Paged,
     * because every station files a reading every ten minutes all winter.
     *
     * @param station one station only, when given
     * @param since only readings at or after this moment, when given
     * @param work the request's unit of work
     * @param look the request's lookup
     * @param paging which page
     * @return the page
     */
    @GetMapping("/weather/observations")
    @Requires(Role.READER)
    public Paged<ObservationOut> listObservations(
            @RequestParam Optional<String> station,
            @RequestParam Optional<LocalDateTime> since,
            UnitOfWork work,
            Lookup look,
            @ParameterObject Paging paging) {
        Optional<Station> where = station.map(look::station);
        LocalDateTime moment = since.orElse(EARLIEST);
        List<Observation> found = where.isPresent()
                ? work.weather().since(moment, where.get())
                : work.weather().since(moment);
        return Paged.of(found.stream().map(ObservationOut::of).toList(), paging);
    }

    /**
     * Whether the weather holds a machine, read off the freshest reading at the station nearest its
     * top terminal.
     *
     * @param code the machine
     * @param now the moment it is; the start of the service's today when not given
     * @param staleAfterMinutes how old a reading may be and still answer
     * @param work the request's unit of work
     * @param look the request's lookup
     * @return the decision, with the reading it was made against
     */
    @GetMapping("/lifts/{code}/hold")
    @Requires(Role.READER)
    public HoldOut readHold(
            @PathVariable String code,
            @RequestParam Optional<LocalDateTime> now,
            @RequestParam(name = "stale_after_minutes", defaultValue = "20") @Min(1) int staleAfterMinutes,
            UnitOfWork work,
            Lookup look) {
        Lift lift = look.lift(code);
        LocalDateTime moment = now.orElseGet(() -> settings.today().atStartOfDay());
        Station station = work.weather().nearestTo(lift.top().ropeElevation());
        Observation seen = work.weather().latestFresh(station, moment, Duration.ofMinutes(staleAfterMinutes));
        return HoldOut.of(new WeatherHold(lift, seen), moment);
    }
}
