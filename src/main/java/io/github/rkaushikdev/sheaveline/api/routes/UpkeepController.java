package io.github.rkaushikdev.sheaveline.api.routes;

import io.github.rkaushikdev.sheaveline.api.Lookup;
import io.github.rkaushikdev.sheaveline.api.Paged;
import io.github.rkaushikdev.sheaveline.api.Paging;
import io.github.rkaushikdev.sheaveline.api.Requires;
import io.github.rkaushikdev.sheaveline.api.Role;
import io.github.rkaushikdev.sheaveline.api.Settings;
import io.github.rkaushikdev.sheaveline.api.schemas.CloseOrderIn;
import io.github.rkaushikdev.sheaveline.api.schemas.DefectOut;
import io.github.rkaushikdev.sheaveline.api.schemas.InspectionIn;
import io.github.rkaushikdev.sheaveline.api.schemas.InspectionOut;
import io.github.rkaushikdev.sheaveline.api.schemas.WorkOrderIn;
import io.github.rkaushikdev.sheaveline.api.schemas.WorkOrderOut;
import io.github.rkaushikdev.sheaveline.persistence.UnitOfWork;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.upkeep.Defect;
import io.github.rkaushikdev.sheaveline.upkeep.InspectionRecord;
import io.github.rkaushikdev.sheaveline.upkeep.Priority;
import io.github.rkaushikdev.sheaveline.upkeep.WorkOrder;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
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
 * Inspections, defects and the work orders that come off them.
 *
 * <p>The one route worth reading twice is the raise. It takes a defect and returns an order, and
 * the priority on the order is not in the request body at all. A client cannot ask for a serious
 * defect to be scheduled for the summer, which is exactly what a client would eventually ask for.
 */
@RestController
@Tag(name = "upkeep")
public class UpkeepController {

    private final Settings settings;

    /**
     * The upkeep routes for one running copy.
     *
     * @param settings its settings
     */
    public UpkeepController(Settings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /**
     * Sign off one job on a machine, with whatever it found.
     *
     * @param code the machine
     * @param body the job
     * @param work the request's unit of work
     * @param look the request's lookup
     * @return the record
     */
    @PostMapping("/lifts/{code}/inspections")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires(Role.MECHANIC)
    public InspectionOut recordInspection(
            @PathVariable String code, @Valid @RequestBody InspectionIn body, UnitOfWork work, Lookup look) {
        LiftCode lift = look.liftCode(code);
        InspectionRecord record = InspectionRecord.of(
                lift,
                body.code(),
                body.done(),
                body.by(),
                body.hours().map(Decimals::of),
                body.cycles(),
                body.defects().stream().map(one -> one.toDomain(body.done())).toList());
        work.inspections().add(record);
        work.commit();
        return InspectionOut.of(record);
    }

    /**
     * The jobs signed off on a machine, oldest first, narrowed to one job when asked. Paged,
     * because a machine carries every walk it has ever had.
     *
     * @param code the machine
     * @param job one job in the manual, in either case, when given
     * @param work the request's unit of work
     * @param look the request's lookup
     * @param paging which page
     * @return the page
     */
    @GetMapping("/lifts/{code}/inspections")
    @Requires(Role.READER)
    public Paged<InspectionOut> listInspections(
            @PathVariable String code,
            @RequestParam Optional<String> job,
            UnitOfWork work,
            Lookup look,
            @ParameterObject Paging paging) {
        LiftCode lift = look.liftCode(code);
        List<InspectionRecord> found = job.isPresent()
                ? work.inspections().forLift(lift, job.get())
                : work.inspections().forLift(lift);
        return Paged.of(found.stream().map(InspectionOut::of).toList(), paging);
    }

    /**
     * The defects still open on a machine.
     *
     * @param code the machine
     * @param work the request's unit of work
     * @param look the request's lookup
     * @return the defects
     */
    @GetMapping("/lifts/{code}/defects")
    @Requires(Role.READER)
    public List<DefectOut> listDefects(@PathVariable String code, UnitOfWork work, Lookup look) {
        LiftCode lift = look.liftCode(code);
        return work.inspections().openDefects(lift).stream().map(DefectOut::of).toList();
    }

    /**
     * Raise an order off a defect. The priority comes from the defect, not the caller.
     *
     * @param code the machine
     * @param body the defect
     * @param work the request's unit of work
     * @param look the request's lookup
     * @return the order, with the next number
     */
    @PostMapping("/lifts/{code}/work-orders")
    @ResponseStatus(HttpStatus.CREATED)
    @Requires(Role.MECHANIC)
    public WorkOrderOut raiseOrder(
            @PathVariable String code, @Valid @RequestBody WorkOrderIn body, UnitOfWork work, Lookup look) {
        LiftCode lift = look.liftCode(code);
        Defect defect = new Defect(body.where(), body.level(), body.detail(), body.raised());
        WorkOrder order = WorkOrder.fromDefect(work.workOrders().nextNumber(), lift, defect, body.raisedBy());
        work.workOrders().add(order);
        work.commit();
        return WorkOrderOut.of(order, settings.today());
    }

    /**
     * The open orders, narrowed to one machine or, instead, every order of one priority.
     *
     * @param lift one machine only, when given
     * @param priority every order of this priority, open or not, when given
     * @param work the request's unit of work
     * @param look the request's lookup
     * @param paging which page
     * @return the page
     */
    @GetMapping("/work-orders")
    @Requires(Role.READER)
    public Paged<WorkOrderOut> listOrders(
            @RequestParam Optional<String> lift,
            @RequestParam Optional<Priority> priority,
            UnitOfWork work,
            Lookup look,
            @ParameterObject Paging paging) {
        List<WorkOrder> found;
        if (priority.isPresent()) {
            found = work.workOrders().ofPriority(priority.get());
        } else if (lift.isPresent()) {
            found = work.workOrders().openOrders(look.liftCode(lift.get()));
        } else {
            found = work.workOrders().openOrders();
        }
        LocalDate today = settings.today();
        return Paged.of(found.stream().map(one -> WorkOrderOut.of(one, today)).toList(), paging);
    }

    /**
     * The open orders past their due day, by the day the service thinks it is.
     *
     * @param work the request's unit of work
     * @return the orders
     */
    @GetMapping("/work-orders/overdue")
    @Requires(Role.READER)
    public List<WorkOrderOut> listOverdue(UnitOfWork work) {
        LocalDate today = settings.today();
        return work.workOrders().openOrders().stream()
                .filter(one -> one.isOverdue(today))
                .map(one -> WorkOrderOut.of(one, today))
                .toList();
    }

    /**
     * One order.
     *
     * @param number its number
     * @param look the request's lookup
     * @return the order
     */
    @GetMapping("/work-orders/{number}")
    @Requires(Role.READER)
    public WorkOrderOut readOrder(@PathVariable int number, Lookup look) {
        return WorkOrderOut.of(look.workOrder(number), settings.today());
    }

    /**
     * Close an order.
     *
     * @param number its number
     * @param body when it was finished and who finished it
     * @param work the request's unit of work
     * @param look the request's lookup
     * @return the order, closed
     */
    @PostMapping("/work-orders/{number}/closure")
    @Requires(Role.MANAGER)
    public WorkOrderOut closeOrder(
            @PathVariable int number, @Valid @RequestBody CloseOrderIn body, UnitOfWork work, Lookup look) {
        look.workOrder(number);
        WorkOrder closed = work.workOrders().close(number, body.closed(), body.closedBy());
        work.commit();
        return WorkOrderOut.of(closed, settings.today());
    }
}
