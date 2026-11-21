package io.github.rkaushikdev.sheaveline.persistence;

import io.github.rkaushikdev.sheaveline.evac.Drill;
import io.github.rkaushikdev.sheaveline.evac.DrillRegister;
import io.github.rkaushikdev.sheaveline.persistence.mappers.EvacMapper;
import io.github.rkaushikdev.sheaveline.persistence.models.DrillRow;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** The evacuation drill register, which is what proves the plan works. */
public final class DrillRepository extends Repository {

    /**
     * The drill register in a session.
     *
     * @param session the session
     */
    public DrillRepository(Session session) {
        super(session);
    }

    /**
     * Write a drill.
     *
     * @param drill the drill
     * @throws StorageError when the machine was already drilled that day
     */
    public void add(Drill drill) {
        boolean clash = first(query("select d from DrillRow d where d.liftCode = :code and d.on = :on", DrillRow.class)
                        .setParameter("code", drill.lift())
                        .setParameter("on", drill.on()))
                .isPresent();
        if (clash) {
            throw new StorageError(drill.lift() + " was already drilled on " + drill.on());
        }
        session().persist(EvacMapper.drillToRow(drill));
        session().flush();
    }

    /**
     * Every drill across the hill, as the register.
     *
     * @return the register, oldest first
     */
    public DrillRegister register() {
        return EvacMapper.registerFromRows(many(query("select d from DrillRow d order by d.on", DrillRow.class)));
    }

    /**
     * One machine's drills, oldest first.
     *
     * @param code the machine
     * @return the drills
     */
    public List<Drill> onLift(LiftCode code) {
        return many(query("select d from DrillRow d where d.liftCode = :code order by d.on", DrillRow.class)
                        .setParameter("code", code))
                .stream()
                .map(EvacMapper::drillFromRow)
                .toList();
    }

    /**
     * The last drill on a machine that counts for the annual.
     *
     * @param code the machine
     * @return the drill, or empty when it never had one
     */
    public Optional<Drill> lastFull(LiftCode code) {
        return register().lastFull(code);
    }

    /**
     * When a machine's annual drill runs out.
     *
     * @param code the machine
     * @return the day, or empty when it never had a drill that counts
     */
    public Optional<LocalDate> expires(LiftCode code) {
        return register().liftExpires(code);
    }

    /**
     * Every machine whose annual drill has run out, which stops it running.
     *
     * @param on the day asked about
     * @return the machines, in code order
     */
    public List<LiftCode> outOfDate(LocalDate on) {
        DrillRegister held = register();
        return many(query("select l.code from LiftRow l order by l.code", LiftCode.class)).stream()
                .filter(code -> !held.liftIsCurrent(code, on))
                .toList();
    }
}
