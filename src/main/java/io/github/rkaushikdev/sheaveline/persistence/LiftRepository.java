package io.github.rkaushikdev.sheaveline.persistence;

import io.github.rkaushikdev.sheaveline.persistence.mappers.PlantMapper;
import io.github.rkaushikdev.sheaveline.persistence.models.HaulRopeRow;
import io.github.rkaushikdev.sheaveline.persistence.models.LiftRow;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import java.util.List;

/**
 * Machines, by the code painted on the terminal.
 *
 * <p>A machine's rope is read from the rope file rather than from the machine, because a rope moves
 * between machines: taking a machine off file leaves its rope behind as a spare, and putting the
 * machine back claims that rope again.
 */
public final class LiftRepository extends Repository {

    private final RopeRepository ropes;

    /**
     * The machines in a session.
     *
     * @param session the session
     */
    public LiftRepository(Session session) {
        super(session);
        this.ropes = new RopeRepository(session);
    }

    /**
     * Put a machine on file, and its rope with it: a new rope goes on file, and a spare already on
     * file is claimed.
     *
     * @param lift the machine
     * @throws StorageError when the machine is already on file, or its rope is on another machine
     */
    public void add(Lift lift) {
        if (exists(lift.code())) {
            throw new StorageError("lift " + lift.code() + " is already on file");
        }
        // The machine goes in first so the rope has something to point at, whether it is new or a
        // spare being claimed back.
        session().persist(PlantMapper.liftToRow(lift));
        session().flush();
        if (ropes.exists(lift.rope().serial())) {
            ropes.attach(lift.rope().serial(), lift.code());
        } else {
            ropes.add(lift.rope(), lift.code());
        }
    }

    /**
     * A machine by its code, with the rope it carries.
     *
     * @param code the code
     * @return the machine
     * @throws StorageError when there is no such machine, it has no rope, or its row is incomplete
     */
    public Lift get(LiftCode code) {
        LiftRow row = fetch(LiftRow.class, code, "there is no lift " + code);
        List<HaulRopeRow> on = ropes.rowsOnLift(code);
        if (on.isEmpty()) {
            throw new StorageError("lift " + code + " has no rope on it");
        }
        return PlantMapper.liftFromRow(row, PlantMapper.ropeFromRow(on.get(0)));
    }

    /**
     * Whether a machine is on file.
     *
     * @param code the code
     * @return true when it is
     */
    public boolean exists(LiftCode code) {
        return find(LiftRow.class, code).isPresent();
    }

    /**
     * Every machine on file, in code order.
     *
     * @return the machines
     */
    public List<Lift> all() {
        return codes().stream().map(this::get).toList();
    }

    /**
     * The codes of every machine on file, in order.
     *
     * @return the codes
     */
    public List<LiftCode> codes() {
        return many(query("select l.code from LiftRow l order by l.code", LiftCode.class));
    }

    /**
     * Take a machine off file, with everything bolted to it. Its rope stays on file as a spare.
     *
     * @param code the code
     * @throws StorageError when there is no such machine
     */
    public void remove(LiftCode code) {
        LiftRow row = fetch(LiftRow.class, code, "there is no lift " + code);
        for (HaulRopeRow rope : ropes.rowsOnLift(code)) {
            rope.setLiftCode(null);
        }
        session().remove(row);
    }
}
