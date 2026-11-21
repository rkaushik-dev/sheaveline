package io.github.rkaushikdev.sheaveline.persistence;

import io.github.rkaushikdev.sheaveline.persistence.models.DefectRow;
import io.github.rkaushikdev.sheaveline.persistence.models.InspectionRecordRow;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.upkeep.Defect;
import io.github.rkaushikdev.sheaveline.upkeep.InspectionRecord;
import jakarta.persistence.TypedQuery;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Inspection records and the defects hanging off them. */
public final class InspectionRepository extends Repository {

    /**
     * The inspection records in a session.
     *
     * @param session the session
     */
    public InspectionRepository(Session session) {
        super(session);
    }

    /**
     * File an inspection record with every defect it found.
     *
     * @param record the record
     */
    public void add(InspectionRecord record) {
        InspectionRecordRow row = new InspectionRecordRow(record.lift(), record.code(), record.done(), record.by());
        row.setHours(record.hours().orElse(null));
        row.setCycles(record.cycles().map(Math::toIntExact).orElse(null));
        for (Defect defect : record.defects()) {
            DefectRow found = new DefectRow(defect.where(), defect.level(), defect.detail(), defect.raised());
            found.setCleared(defect.cleared().orElse(null));
            found.setClearedBy(defect.clearedBy().orElse(null));
            row.addDefect(found);
        }
        session().persist(row);
    }

    /**
     * Every record on one machine, oldest first.
     *
     * @param code the machine
     * @return the records
     */
    public List<InspectionRecord> forLift(LiftCode code) {
        return forLift(code, Optional.empty());
    }

    /**
     * The records of one job on one machine, oldest first.
     *
     * @param code the machine
     * @param job the job's code, in either case
     * @return the records
     */
    public List<InspectionRecord> forLift(LiftCode code, String job) {
        return forLift(code, Optional.of(job));
    }

    private List<InspectionRecord> forLift(LiftCode code, Optional<String> job) {
        String statement = "select r from InspectionRecordRow r where r.liftCode = :code"
                + job.map(any -> " and r.code = :job").orElse("")
                + " order by r.done";
        TypedQuery<InspectionRecordRow> query =
                query(statement, InspectionRecordRow.class).setParameter("code", code);
        job.ifPresent(wanted -> query.setParameter("job", wanted.toUpperCase(Locale.ROOT)));
        return many(query).stream().map(InspectionRepository::toDomain).toList();
    }

    /**
     * The last time a job was done on a machine.
     *
     * @param code the machine
     * @param job the job's code, in either case
     * @return the most recent record, or empty when it was never done
     */
    public Optional<InspectionRecord> lastDone(LiftCode code, String job) {
        List<InspectionRecord> found = forLift(code, job);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(found.size() - 1));
    }

    /**
     * Every defect still open across the hill, oldest first.
     *
     * @return the defects
     */
    public List<Defect> openDefects() {
        return openDefects(Optional.empty());
    }

    /**
     * The defects still open on one machine, oldest first.
     *
     * @param code the machine
     * @return the defects
     */
    public List<Defect> openDefects(LiftCode code) {
        return openDefects(Optional.of(code));
    }

    private List<Defect> openDefects(Optional<LiftCode> code) {
        String statement = "select d from DefectRow d where d.cleared is null"
                + code.map(any -> " and d.record.liftCode = :code").orElse("")
                + " order by d.raised";
        TypedQuery<DefectRow> query = query(statement, DefectRow.class);
        code.ifPresent(wanted -> query.setParameter("code", wanted));
        return many(query).stream().map(InspectionRepository::defect).toList();
    }

    /**
     * Clear a defect, found by where it is and the day it was raised, and write the clearance back.
     *
     * @param where where it is
     * @param raised the day it was raised
     * @param on the day it was cleared
     * @param by who cleared it
     * @return the cleared defect
     * @throws StorageError when there is no such defect
     * @throws io.github.rkaushikdev.sheaveline.upkeep.UpkeepError when it is already cleared, or
     *     nobody is named
     */
    public Defect clearDefect(String where, LocalDate raised, LocalDate on, String by) {
        DefectRow row = one(
                query("select d from DefectRow d where d.where = :where and d.raised = :raised", DefectRow.class)
                        .setParameter("where", where)
                        .setParameter("raised", raised),
                "there is no open defect at " + where + " raised " + raised);
        Defect cleared = defect(row).clear(on, by);
        row.setCleared(cleared.cleared().orElse(null));
        row.setClearedBy(cleared.clearedBy().orElse(null));
        return cleared;
    }

    private static Defect defect(DefectRow row) {
        return new Defect(
                row.getWhere(), row.getLevel(), row.getDetail(), row.getRaised(), row.getCleared(), row.getClearedBy());
    }

    private static InspectionRecord toDomain(InspectionRecordRow row) {
        return InspectionRecord.of(
                row.getLiftCode(),
                row.getCode(),
                row.getDone(),
                row.getBy(),
                row.getHours(),
                row.getCycles().map(Integer::longValue),
                row.getDefects().stream().map(InspectionRepository::defect).toList());
    }
}
