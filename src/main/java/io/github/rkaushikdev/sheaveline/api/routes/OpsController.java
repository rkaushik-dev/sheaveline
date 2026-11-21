package io.github.rkaushikdev.sheaveline.api.routes;

import io.github.rkaushikdev.sheaveline.api.Lookup;
import io.github.rkaushikdev.sheaveline.api.Paged;
import io.github.rkaushikdev.sheaveline.api.Paging;
import io.github.rkaushikdev.sheaveline.api.Requires;
import io.github.rkaushikdev.sheaveline.api.Role;
import io.github.rkaushikdev.sheaveline.api.schemas.DayLogOut;
import io.github.rkaushikdev.sheaveline.api.schemas.MeterOut;
import io.github.rkaushikdev.sheaveline.api.schemas.RestartIn;
import io.github.rkaushikdev.sheaveline.api.schemas.StopIn;
import io.github.rkaushikdev.sheaveline.api.schemas.StopLogOut;
import io.github.rkaushikdev.sheaveline.api.schemas.StopOut;
import io.github.rkaushikdev.sheaveline.ops.MeterHistory;
import io.github.rkaushikdev.sheaveline.ops.StopEvent;
import io.github.rkaushikdev.sheaveline.persistence.UnitOfWork;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
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
 * The day book over http, which is what the terminal tablets write into.
 *
 * <p>Stopping and restarting are the two calls that get made in anger, so both are short and both
 * refuse rather than guess.
 *
 * <p>Nothing here invents a day. A stop can be posted against a machine that has no log written
 * up, because that is a real Tuesday, and the day log route will happily say there is no log for
 * that day rather than making one up to hang the stop on.
 */
@RestController
@Tag(name = "operations")
public class OpsController {

    /**
     * One machine on one day.
     *
     * @param code the machine
     * @param day the day
     * @param work the request's unit of work
     * @param look the request's lookup
     * @return the day, with its answers worked out
     */
    @GetMapping("/lifts/{code}/days/{day}")
    @Requires(Role.READER)
    public DayLogOut readDay(@PathVariable String code, @PathVariable LocalDate day, UnitOfWork work, Lookup look) {
        return DayLogOut.of(work.dayLogs().get(look.liftCode(code), day));
    }

    /**
     * The days a machine has been written up, oldest first. Paged, because a machine that has run
     * for ten seasons has fifteen hundred.
     *
     * @param code the machine
     * @param work the request's unit of work
     * @param look the request's lookup
     * @param paging which page
     * @return the page of ISO dates
     */
    @GetMapping("/lifts/{code}/days")
    @Requires(Role.READER)
    public Paged<String> listDays(
            @PathVariable String code, UnitOfWork work, Lookup look, @ParameterObject Paging paging) {
        List<LocalDate> days = work.dayLogs().daysLogged(look.liftCode(code));
        return Paged.of(days.stream().map(LocalDate::toString).toList(), paging);
    }

    /**
     * Every stop that touched a day, which is not every stop that began on it.
     *
     * @param code the machine
     * @param day the day
     * @param work the request's unit of work
     * @param look the request's lookup
     * @return the stops, totalled
     */
    @GetMapping("/lifts/{code}/days/{day}/stops")
    @Requires(Role.READER)
    public StopLogOut readStops(@PathVariable String code, @PathVariable LocalDate day, UnitOfWork work, Lookup look) {
        return StopLogOut.of(work.dayLogs().stopsOn(look.liftCode(code), day));
    }

    /**
     * Stop the machine. Everything that follows from the kind is worked out here.
     *
     * @param code the machine
     * @param body why and when
     * @param work the request's unit of work
     * @param look the request's lookup
     * @return the stop
     */
    @PostMapping("/lifts/{code}/stops")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires(Role.OPERATOR)
    public StopOut callStop(@PathVariable String code, @Valid @RequestBody StopIn body, UnitOfWork work, Lookup look) {
        StopEvent event =
                new StopEvent(look.liftCode(code), body.kind(), body.stopped(), body.stoppedBy(), body.note());
        work.dayLogs().stop(event);
        work.commit();
        return StopOut.of(event);
    }

    /**
     * Run it again. The domain decides whether that is allowed, not the route.
     *
     * @param code the machine
     * @param body which stop, and when it ran again
     * @param work the request's unit of work
     * @param look the request's lookup
     * @return the stop, closed and measured
     */
    @PostMapping("/lifts/{code}/stops/restart")
    @Requires(Role.OPERATOR)
    public StopOut restart(
            @PathVariable String code, @Valid @RequestBody RestartIn body, UnitOfWork work, Lookup look) {
        StopEvent event = work.dayLogs()
                .restart(look.liftCode(code), body.stopped(), body.restarted(), body.restartedBy(), body.lineChecked());
        work.commit();
        return StopOut.of(event);
    }

    /**
     * Everything on the hill that is stopped and has not run again.
     *
     * @param lift one machine only, when given
     * @param work the request's unit of work
     * @param look the request's lookup
     * @return the open stops
     */
    @GetMapping("/stops/open")
    @Requires(Role.READER)
    public List<StopOut> openStops(@RequestParam Optional<String> lift, UnitOfWork work, Lookup look) {
        Optional<LiftCode> code = lift.map(look::liftCode);
        List<StopEvent> open = code.isPresent()
                ? work.dayLogs().openStops(code.get())
                : work.dayLogs().openStops();
        return open.stream().map(StopOut::of).toList();
    }

    /**
     * The hour meter and cycle counter as they were read, oldest first.
     *
     * @param code the machine
     * @param work the request's unit of work
     * @param look the request's lookup
     * @param paging which page
     * @return the page
     */
    @GetMapping("/lifts/{code}/meters")
    @Requires(Role.MECHANIC)
    public Paged<MeterOut> readMeters(
            @PathVariable String code, UnitOfWork work, Lookup look, @ParameterObject Paging paging) {
        Optional<MeterHistory> history = work.dayLogs().meters(look.liftCode(code));
        return Paged.of(
                history.map(MeterHistory::readings).orElse(List.of()).stream()
                        .map(MeterOut::of)
                        .toList(),
                paging);
    }
}
