package io.github.rkaushikdev.sheaveline.api;

import io.github.rkaushikdev.sheaveline.persistence.IncidentRepository;
import io.github.rkaushikdev.sheaveline.persistence.StorageError;
import io.github.rkaushikdev.sheaveline.persistence.UnitOfWork;
import io.github.rkaushikdev.sheaveline.plant.HaulRope;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.plant.RopeSerial;
import io.github.rkaushikdev.sheaveline.upkeep.WorkOrder;
import io.github.rkaushikdev.sheaveline.weather.Station;
import java.util.Objects;

/**
 * The one place a route turns a piece of the path into a thing on file, and says so plainly when it
 * is gone.
 *
 * <p>Every route that names a machine, a rope, a station, an order or an incident asks for one of
 * these rather than writing its own fetch, so every one of them words the sentence the same way and
 * no client ends up parsing several versions of our prose.
 *
 * <p>The lookup does the parse and the fetch together on purpose. A code that will not parse is a
 * 400 and a code that parses but is not on the register is a 404, and both say which.
 */
public final class Lookup {

    private final UnitOfWork work;

    /**
     * A lookup in a unit of work, sharing its session.
     *
     * @param work the unit of work
     */
    public Lookup(UnitOfWork work) {
        this.work = Objects.requireNonNull(work, "work");
    }

    /**
     * The code, checked against the register without loading the machine.
     *
     * @param code the code as the path gave it
     * @return the code
     * @throws io.github.rkaushikdev.sheaveline.ParseError when it is not a lift code
     * @throws NotFound when no machine on the register has it
     */
    public LiftCode liftCode(String code) {
        LiftCode wanted = LiftCode.parse(code);
        if (!work.lifts().exists(wanted)) {
            throw new NotFound("there is no lift " + wanted);
        }
        return wanted;
    }

    /**
     * The machine.
     *
     * @param code the code as the path gave it
     * @return the machine
     * @throws io.github.rkaushikdev.sheaveline.ParseError when it is not a lift code
     * @throws NotFound when no machine on the register has it
     */
    public Lift lift(String code) {
        LiftCode wanted = LiftCode.parse(code);
        try {
            return work.lifts().get(wanted);
        } catch (StorageError missing) {
            throw new NotFound("there is no lift " + wanted, missing);
        }
    }

    /**
     * The rope.
     *
     * @param serial the serial as the path gave it
     * @return the rope
     * @throws io.github.rkaushikdev.sheaveline.ParseError when it is not a rope serial
     * @throws NotFound when no rope on file has it
     */
    public HaulRope rope(String serial) {
        RopeSerial wanted = RopeSerial.parse(serial);
        try {
            return work.ropes().get(wanted);
        } catch (StorageError missing) {
            throw new NotFound("there is no rope " + wanted, missing);
        }
    }

    /**
     * The weather station.
     *
     * @param name its name
     * @return the station
     * @throws NotFound when no station has that name
     */
    public Station station(String name) {
        try {
            return work.weather().station(name);
        } catch (StorageError missing) {
            throw new NotFound("there is no station called " + name, missing);
        }
    }

    /**
     * The work order.
     *
     * @param number its number
     * @return the order
     * @throws NotFound when no order has that number
     */
    public WorkOrder workOrder(int number) {
        try {
            return work.workOrders().get(number);
        } catch (StorageError missing) {
            throw new NotFound("there is no work order " + number, missing);
        }
    }

    /**
     * The incident, with its filing number once it has been filed.
     *
     * @param incidentId its id
     * @return the incident
     * @throws NotFound when no incident has that id
     */
    public IncidentRepository.Numbered incident(int incidentId) {
        try {
            return work.incidents().get(incidentId);
        } catch (StorageError missing) {
            throw new NotFound("there is no incident " + incidentId, missing);
        }
    }
}
