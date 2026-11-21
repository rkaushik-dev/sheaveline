package io.github.rkaushikdev.sheaveline.api.routes;

import io.github.rkaushikdev.sheaveline.api.Lookup;
import io.github.rkaushikdev.sheaveline.api.Requires;
import io.github.rkaushikdev.sheaveline.api.Role;
import io.github.rkaushikdev.sheaveline.api.Settings;
import io.github.rkaushikdev.sheaveline.api.schemas.CapacityOut;
import io.github.rkaushikdev.sheaveline.api.schemas.GeometryOut;
import io.github.rkaushikdev.sheaveline.api.schemas.LineFindingOut;
import io.github.rkaushikdev.sheaveline.api.schemas.SpacingOut;
import io.github.rkaushikdev.sheaveline.api.schemas.StagingIn;
import io.github.rkaushikdev.sheaveline.api.schemas.StagingOut;
import io.github.rkaushikdev.sheaveline.api.schemas.TeamIn;
import io.github.rkaushikdev.sheaveline.capacity.LineSpacing;
import io.github.rkaushikdev.sheaveline.capacity.UphillCapacity;
import io.github.rkaushikdev.sheaveline.evac.EvacuationProfile;
import io.github.rkaushikdev.sheaveline.evac.RescueKit;
import io.github.rkaushikdev.sheaveline.evac.RescueTeam;
import io.github.rkaushikdev.sheaveline.evac.Staging;
import io.github.rkaushikdev.sheaveline.evac.TeamPool;
import io.github.rkaushikdev.sheaveline.line.Catenary;
import io.github.rkaushikdev.sheaveline.line.ClearanceReading;
import io.github.rkaushikdev.sheaveline.line.LineGeometry;
import io.github.rkaushikdev.sheaveline.line.SagModel;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.measures.LoadPerFoot;
import io.github.rkaushikdev.sheaveline.measures.Speed;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.IntStream;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Capacity and line geometry, worked out against a machine on file.
 *
 * <p>Every route here is a question with conditions attached, and the conditions are query values
 * because that is what they are: what if we slow it to six hundred, what if we take twenty chairs
 * off, what does the line look like with eight feet of snow on it. The department asks these on the
 * telephone and the answer has to come back while the person is still holding.
 *
 * <p>Nothing is stored. Two people can ask different what ifs about the same machine at the same
 * moment and neither sees the other's.
 */
@RestController
@RequestMapping("/lifts/{code}")
@Tag(name = "capacity")
public class CapacityController {

    private final Settings settings;

    /**
     * The capacity routes for one running copy.
     *
     * @param settings its settings
     */
    public CapacityController(Settings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /**
     * How far apart the carriers are and how often they come in.
     *
     * @param code the machine
     * @param carriers how many carriers to ask about; the machine's own when not given
     * @param speed the rope speed to ask about; its design speed when not given
     * @param look the request's lookup
     * @return the spacing, against the class floor
     */
    @GetMapping("/spacing")
    @Requires(Role.READER)
    public SpacingOut readSpacing(
            @PathVariable String code,
            @RequestParam(required = false) @Min(1) Integer carriers,
            @RequestParam Optional<String> speed,
            Lookup look) {
        Lift lift = look.lift(code);
        LineSpacing line = spacingOf(lift, Optional.ofNullable(carriers), speed);
        return new SpacingOut(
                line.carriers(),
                line.speed(),
                line.spacing(),
                line.interval(),
                LineSpacing.MINIMUM_INTERVAL.get(lift.liftClass()),
                line.meets(lift.liftClass()),
                line.shortfall(lift.liftClass()),
                line.mostCarriersFor(lift.liftClass()));
    }

    /**
     * What the machine carries uphill in an hour.
     *
     * @param code the machine
     * @param carriers how many carriers to ask about; the machine's own when not given
     * @param speed the rope speed to ask about; its design speed when not given
     * @param occupancy the share of seats filled
     * @param look the request's lookup
     * @return the figures, three ways
     */
    @GetMapping("/capacity")
    @Requires(Role.READER)
    public CapacityOut readCapacity(
            @PathVariable String code,
            @RequestParam(required = false) @Min(1) Integer carriers,
            @RequestParam Optional<String> speed,
            @RequestParam(defaultValue = "0.85") String occupancy,
            Lookup look) {
        Lift lift = look.lift(code);
        LineSpacing line = spacingOf(lift, Optional.ofNullable(carriers), speed);
        UphillCapacity figures = new UphillCapacity(line, lift.carriers().kind().seats(), Decimals.of(occupancy));
        return new CapacityOut(
                line.carriers(),
                line.speed(),
                occupancy,
                figures.carriersPerHour(),
                figures.seatsPerHour(),
                figures.personsPerHour(),
                figures.verticalFeetPerHour(lift.verticalRise()));
    }

    /**
     * The line walked under some snow at some tension.
     *
     * @param code the machine
     * @param snow the snow depth
     * @param tension the haul rope tension
     * @param look the request's lookup
     * @return the verdict, the worst clearance, and what the walk found
     */
    @GetMapping("/geometry")
    @Requires(Role.READER)
    public GeometryOut readGeometry(
            @PathVariable String code,
            @RequestParam(defaultValue = "0 ft") String snow,
            @RequestParam(defaultValue = "34500 lb") String tension,
            Lookup look) {
        Lift lift = look.lift(code);
        SagModel sag = new SagModel(lineWeightOf(lift), Load.parse(tension));
        LineGeometry walk = new LineGeometry(lift, sag, Length.parse(snow));
        ClearanceReading worst = walk.worstClearance();
        return new GeometryOut(
                walk.verdict().value(),
                walk.verdict().mayRun(),
                walk.snow(),
                sag.tension(),
                worst.available(),
                worst.margin(),
                worst.station(),
                walk.findings().stream().map(LineFindingOut::of).toList());
    }

    /**
     * Where to park the teams so the busiest one finishes soonest.
     *
     * <p>The teams come up in the request because who is on the hill this afternoon is not
     * something this system holds. The line is, and that is the half nobody can type in from
     * memory.
     *
     * @param code the machine
     * @param body the teams and the conditions
     * @param look the request's lookup
     * @return the beat sheet
     */
    @PostMapping("/evacuation")
    @Requires(Role.OPERATOR)
    public StagingOut planEvacuation(@PathVariable String code, @Valid @RequestBody StagingIn body, Lookup look) {
        Lift lift = look.lift(code);
        LocalDate today = settings.today();
        EvacuationProfile profile = new EvacuationProfile(
                lift, new SagModel(lineWeightOf(lift), Load.parse(body.tension())), Length.parse(body.snow()));
        TeamPool pool =
                TeamPool.of(body.teams().stream().map(one -> team(one, today)).toList());
        return StagingOut.of(Staging.stage(profile, pool, Length.ZERO, today, body.seatsFilled()));
    }

    private static LineSpacing spacingOf(Lift lift, Optional<Integer> carriers, Optional<String> speed) {
        LineSpacing line = speed.isPresent() ? LineSpacing.of(lift, Speed.parse(speed.get())) : LineSpacing.of(lift);
        return carriers.isPresent() ? line.withCarriers(carriers.get()) : line;
    }

    /** The weight per foot of the loaded line: the rope, with a loaded carrier at every spacing. */
    private static LoadPerFoot lineWeightOf(Lift lift) {
        return Catenary.lineWeight(
                lift.rope().spec().weight(),
                lift.carriers().onLine().get(0).gross(),
                LineSpacing.of(lift).spacing());
    }

    /** A team off the truck, its members numbered after it, trained until tomorrow. */
    private static RescueTeam team(TeamIn given, LocalDate on) {
        return RescueTeam.of(
                given.name(),
                IntStream.rangeClosed(1, given.people())
                        .mapToObj(one -> given.name() + " " + one)
                        .toList(),
                new RescueKit(
                        given.ladderFeet().map(Length::ofFeet),
                        given.ropeFeet().map(Length::ofFeet),
                        2,
                        given.people(),
                        1),
                Length.ZERO,
                on.plusDays(1));
    }
}
