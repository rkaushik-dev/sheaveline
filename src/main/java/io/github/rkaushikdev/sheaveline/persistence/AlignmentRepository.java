package io.github.rkaushikdev.sheaveline.persistence;

import io.github.rkaushikdev.sheaveline.persistence.mappers.PlantMapper;
import io.github.rkaushikdev.sheaveline.persistence.models.AlignmentSurveyRow;
import io.github.rkaushikdev.sheaveline.plant.AlignmentHistory;
import io.github.rkaushikdev.sheaveline.plant.AlignmentSurvey;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.plant.TowerLine;
import java.util.List;

/** The autumn total station work, kept so a tower's movement reads as a rate. */
public final class AlignmentRepository extends Repository {

    private final LiftRepository lifts;

    /**
     * The alignment surveys in a session.
     *
     * @param session the session
     */
    public AlignmentRepository(Session session) {
        super(session);
        this.lifts = new LiftRepository(session);
    }

    /**
     * File a survey.
     *
     * @param survey the survey
     * @throws StorageError when the machine was already shot that day
     */
    public void add(AlignmentSurvey survey) {
        LiftCode code = survey.line().lift();
        boolean clash = first(query(
                                "select a from AlignmentSurveyRow a where a.liftCode = :code and a.taken = :taken",
                                AlignmentSurveyRow.class)
                        .setParameter("code", code)
                        .setParameter("taken", survey.taken()))
                .isPresent();
        if (clash) {
            throw new StorageError(code + " was already shot on " + survey.taken());
        }
        session().persist(PlantMapper.alignmentToRow(survey));
        session().flush();
    }

    /**
     * Every survey of a machine, oldest first, read against its towers.
     *
     * @param code the machine
     * @return the surveys
     * @throws StorageError when the machine is not on file
     */
    public List<AlignmentSurvey> surveys(LiftCode code) {
        TowerLine line = lifts.get(code).towers();
        return many(query(
                                "select a from AlignmentSurveyRow a where a.liftCode = :code order by a.taken",
                                AlignmentSurveyRow.class)
                        .setParameter("code", code))
                .stream()
                .map(row -> PlantMapper.alignmentFromRow(row, line))
                .toList();
    }

    /**
     * The most recent survey of a machine.
     *
     * @param code the machine
     * @return the survey
     * @throws StorageError when the machine is not on file or has never been shot
     */
    public AlignmentSurvey latest(LiftCode code) {
        TowerLine line = lifts.get(code).towers();
        AlignmentSurveyRow row = one(
                query(
                                "select a from AlignmentSurveyRow a where a.liftCode = :code order by a.taken desc",
                                AlignmentSurveyRow.class)
                        .setParameter("code", code),
                code + " has never been shot");
        return PlantMapper.alignmentFromRow(row, line);
    }

    /**
     * Every survey of a machine, as a history its towers' movement can be read from.
     *
     * @param code the machine
     * @return the history
     * @throws StorageError when the machine is not on file or has never been shot
     */
    public AlignmentHistory history(LiftCode code) {
        List<AlignmentSurvey> found = surveys(code);
        if (found.isEmpty()) {
            throw new StorageError(code + " has never been shot");
        }
        return AlignmentHistory.of(found);
    }
}
