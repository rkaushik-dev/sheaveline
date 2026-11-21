package io.github.rkaushikdev.sheaveline.persistence;

import io.github.rkaushikdev.sheaveline.persistence.mappers.PlantMapper;
import io.github.rkaushikdev.sheaveline.persistence.models.HaulRopeRow;
import io.github.rkaushikdev.sheaveline.plant.HaulRope;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.plant.RopeSerial;
import java.util.List;
import java.util.Optional;

/** Haul ropes, by their mill serial. */
public final class RopeRepository extends Repository {

    /**
     * The ropes in a session.
     *
     * @param session the session
     */
    public RopeRepository(Session session) {
        super(session);
    }

    /**
     * Put a spare rope on file.
     *
     * @param rope the rope
     * @throws StorageError when a rope with its serial is already on file
     */
    public void add(HaulRope rope) {
        add(rope, Optional.empty());
    }

    /**
     * Put a rope on file, on a machine.
     *
     * @param rope the rope
     * @param liftCode the machine it is on
     * @throws StorageError when a rope with its serial is already on file
     */
    public void add(HaulRope rope, LiftCode liftCode) {
        add(rope, Optional.of(liftCode));
    }

    private void add(HaulRope rope, Optional<LiftCode> liftCode) {
        if (exists(rope.serial())) {
            throw new StorageError("rope " + rope.serial() + " is already on file");
        }
        session().persist(PlantMapper.ropeToRow(rope, liftCode));
    }

    /**
     * A rope by its serial.
     *
     * @param serial the serial
     * @return the rope
     * @throws StorageError when there is no such rope
     */
    public HaulRope get(RopeSerial serial) {
        return PlantMapper.ropeFromRow(row(serial));
    }

    /**
     * Whether a rope is on file.
     *
     * @param serial the serial
     * @return true when it is
     */
    public boolean exists(RopeSerial serial) {
        return find(HaulRopeRow.class, serial).isPresent();
    }

    /**
     * Every rope on file, in serial order.
     *
     * @return the ropes
     */
    public List<HaulRope> all() {
        return many(query("select r from HaulRopeRow r order by r.serial", HaulRopeRow.class)).stream()
                .map(PlantMapper::ropeFromRow)
                .toList();
    }

    /**
     * The ropes on one machine, in serial order.
     *
     * @param liftCode the machine
     * @return the ropes
     */
    public List<HaulRope> onLift(LiftCode liftCode) {
        return rowsOnLift(liftCode).stream().map(PlantMapper::ropeFromRow).toList();
    }

    /**
     * The ropes on no machine, in serial order.
     *
     * @return the spares
     */
    public List<HaulRope> spares() {
        return many(query("select r from HaulRopeRow r where r.liftCode is null order by r.serial", HaulRopeRow.class))
                .stream()
                .map(PlantMapper::ropeFromRow)
                .toList();
    }

    /**
     * Put a rope already on file onto a machine.
     *
     * <p>A rope taken off a machine is left on file as a spare rather than deleted, so putting the
     * same machine back has to find it again and claim it. A rope that is on some other machine is
     * refused, because a rope in two places is a records error rather than a spare.
     *
     * @param serial the rope
     * @param liftCode the machine
     * @throws StorageError when there is no such rope, or it is on another machine
     */
    public void attach(RopeSerial serial, LiftCode liftCode) {
        HaulRopeRow row = row(serial);
        Optional<LiftCode> on = row.getLiftCode();
        if (on.isPresent() && !on.get().equals(liftCode)) {
            throw new StorageError("rope " + serial + " is already on " + on.get());
        }
        row.setLiftCode(liftCode);
    }

    /**
     * Take a rope off its machine and leave it on file as a spare.
     *
     * @param serial the rope
     * @throws StorageError when there is no such rope
     */
    public void detach(RopeSerial serial) {
        row(serial).setLiftCode(null);
    }

    /**
     * Take a rope off file.
     *
     * @param serial the rope
     * @throws StorageError when there is no such rope
     */
    public void remove(RopeSerial serial) {
        session().remove(row(serial));
    }

    /**
     * The rows of the ropes on one machine, as the database has them now.
     *
     * @param liftCode the machine
     * @return the rows, in serial order
     */
    List<HaulRopeRow> rowsOnLift(LiftCode liftCode) {
        return many(query("select r from HaulRopeRow r where r.liftCode = :code order by r.serial", HaulRopeRow.class)
                .setParameter("code", liftCode));
    }

    private HaulRopeRow row(RopeSerial serial) {
        return fetch(HaulRopeRow.class, serial, "there is no rope " + serial);
    }
}
