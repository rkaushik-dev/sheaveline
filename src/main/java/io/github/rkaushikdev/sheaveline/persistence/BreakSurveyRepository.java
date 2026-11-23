package io.github.rkaushikdev.sheaveline.persistence;

import io.github.rkaushikdev.sheaveline.persistence.mappers.RopeworkMapper;
import io.github.rkaushikdev.sheaveline.persistence.models.BreakSurveyRow;
import io.github.rkaushikdev.sheaveline.plant.HaulRope;
import io.github.rkaushikdev.sheaveline.ropework.BreakSurvey;
import java.util.List;

/**
 * Broken wire counts, by the rope they were counted on.
 *
 * <p>The count is usually made on the same walk as the caliper readings, by the same mechanic, but
 * it is its own record: one count a rope a day, with a line for every station where something was
 * found.
 */
public final class BreakSurveyRepository extends Repository {

    /**
     * The broken wire counts in a session.
     *
     * @param session the session
     */
    public BreakSurveyRepository(Session session) {
        super(session);
    }

    /**
     * File a count.
     *
     * @param survey the count
     */
    public void add(BreakSurvey survey) {
        session().persist(RopeworkMapper.breaksToRow(survey));
    }

    /**
     * Every count on a rope, oldest first, read against that rope.
     *
     * @param rope the rope
     * @return the counts
     */
    public List<BreakSurvey> forRope(HaulRope rope) {
        return many(query(
                                "select b from BreakSurveyRow b where b.ropeSerial = :serial order by b.taken",
                                BreakSurveyRow.class)
                        .setParameter("serial", rope.serial()))
                .stream()
                .map(row -> RopeworkMapper.breaksFromRow(row, rope))
                .toList();
    }
}
