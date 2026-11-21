package io.github.rkaushikdev.sheaveline.persistence;

import io.github.rkaushikdev.sheaveline.persistence.mappers.RopeworkMapper;
import io.github.rkaushikdev.sheaveline.persistence.models.WearSurveyRow;
import io.github.rkaushikdev.sheaveline.plant.HaulRope;
import io.github.rkaushikdev.sheaveline.ropework.WearSurvey;
import java.time.LocalDate;
import java.util.List;

/** Rope walks, by the rope they were taken on. */
public final class WearSurveyRepository extends Repository {

    /**
     * The rope walks in a session.
     *
     * @param session the session
     */
    public WearSurveyRepository(Session session) {
        super(session);
    }

    /**
     * File a rope walk.
     *
     * @param survey the walk
     */
    public void add(WearSurvey survey) {
        session().persist(RopeworkMapper.wearToRow(survey));
    }

    /**
     * Every walk of a rope, oldest first, read against that rope.
     *
     * @param rope the rope
     * @return the walks
     */
    public List<WearSurvey> forRope(HaulRope rope) {
        return many(query(
                                "select w from WearSurveyRow w where w.ropeSerial = :serial order by w.taken",
                                WearSurveyRow.class)
                        .setParameter("serial", rope.serial()))
                .stream()
                .map(row -> RopeworkMapper.wearFromRow(row, rope))
                .toList();
    }

    /**
     * The most recent walk of a rope.
     *
     * @param rope the rope
     * @return the walk
     * @throws StorageError when the rope has never been walked
     */
    public WearSurvey latest(HaulRope rope) {
        List<WearSurvey> found = forRope(rope);
        if (found.isEmpty()) {
            throw new StorageError("rope " + rope.serial() + " has never been walked");
        }
        return found.get(found.size() - 1);
    }

    /**
     * The walk of a rope on one day.
     *
     * @param rope the rope
     * @param taken the day
     * @return the walk
     * @throws StorageError when the rope was not walked that day
     */
    public WearSurvey on(HaulRope rope, LocalDate taken) {
        WearSurveyRow row = one(
                query(
                                "select w from WearSurveyRow w where w.ropeSerial = :serial and w.taken = :taken",
                                WearSurveyRow.class)
                        .setParameter("serial", rope.serial())
                        .setParameter("taken", taken),
                "rope " + rope.serial() + " was not walked on " + taken);
        return RopeworkMapper.wearFromRow(row, rope);
    }

    /**
     * How many times a rope has been walked.
     *
     * @param rope the rope
     * @return the count
     */
    public int countFor(HaulRope rope) {
        return forRope(rope).size();
    }
}
