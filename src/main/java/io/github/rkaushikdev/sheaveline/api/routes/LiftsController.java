package io.github.rkaushikdev.sheaveline.api.routes;

import io.github.rkaushikdev.sheaveline.api.Lookup;
import io.github.rkaushikdev.sheaveline.api.Requires;
import io.github.rkaushikdev.sheaveline.api.Role;
import io.github.rkaushikdev.sheaveline.api.schemas.LiftOut;
import io.github.rkaushikdev.sheaveline.api.schemas.LiftSummaryOut;
import io.github.rkaushikdev.sheaveline.api.schemas.SpanOut;
import io.github.rkaushikdev.sheaveline.api.schemas.TerminalOut;
import io.github.rkaushikdev.sheaveline.api.schemas.TowerOut;
import io.github.rkaushikdev.sheaveline.persistence.UnitOfWork;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The machines, read over http.
 *
 * <p>Everything here is a read except the withdrawal, and that is a manager's key because taking a
 * lift off the register is not something a terminal tablet does by accident.
 */
@RestController
@RequestMapping("/lifts")
@Tag(name = "lifts")
public class LiftsController {

    /**
     * Every machine on the register, in code order.
     *
     * @param work the request's unit of work
     * @return their summaries
     */
    @GetMapping
    @Requires(Role.READER)
    public List<LiftSummaryOut> listLifts(UnitOfWork work) {
        return work.lifts().all().stream().map(LiftOut::summaryOf).toList();
    }

    /**
     * One machine in full.
     *
     * @param code its code, in either case
     * @param look the request's lookup
     * @return the machine
     */
    @GetMapping("/{code}")
    @Requires(Role.READER)
    public LiftOut readLift(@PathVariable String code, Lookup look) {
        return LiftOut.of(look.lift(code));
    }

    /**
     * A machine's towers, up the line.
     *
     * @param code its code
     * @param look the request's lookup
     * @return the towers
     */
    @GetMapping("/{code}/towers")
    @Requires(Role.READER)
    public List<TowerOut> readTowers(@PathVariable String code, Lookup look) {
        return look.lift(code).towers().towers().stream().map(TowerOut::of).toList();
    }

    /**
     * A machine's terminals, the bottom one first.
     *
     * @param code its code
     * @param look the request's lookup
     * @return the terminals
     */
    @GetMapping("/{code}/terminals")
    @Requires(Role.READER)
    public List<TerminalOut> readTerminals(@PathVariable String code, Lookup look) {
        Lift lift = look.lift(code);
        return List.of(TerminalOut.of(lift.bottom()), TerminalOut.of(lift.top()));
    }

    /**
     * A machine's spans, up the line.
     *
     * @param code its code
     * @param look the request's lookup
     * @return the spans
     */
    @GetMapping("/{code}/spans")
    @Requires(Role.READER)
    public List<SpanOut> readSpans(@PathVariable String code, Lookup look) {
        return look.lift(code).spans().spans().stream().map(SpanOut::of).toList();
    }

    /**
     * Take a machine off the register.
     *
     * @param code its code
     * @param work the request's unit of work
     * @param look the request's lookup, in the same unit
     */
    @DeleteMapping("/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Requires(Role.MANAGER)
    public void withdrawLift(@PathVariable String code, UnitOfWork work, Lookup look) {
        Lift lift = look.lift(code);
        work.lifts().remove(lift.code());
        work.commit();
    }
}
