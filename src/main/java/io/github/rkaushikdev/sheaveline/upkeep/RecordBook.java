package io.github.rkaushikdev.sheaveline.upkeep;

import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Every inspection record on one machine.
 *
 * @param lift the machine
 * @param records its records, by day and then by job when built with {@link #of(LiftCode, List)}
 */
public record RecordBook(LiftCode lift, List<InspectionRecord> records) {

    private static final Comparator<InspectionRecord> ORDER =
            Comparator.comparing(InspectionRecord::done).thenComparing(InspectionRecord::code);

    /**
     * A book holding these records in the order given.
     *
     * @throws UpkeepError when a record is of another machine
     */
    public RecordBook {
        Objects.requireNonNull(lift, "lift");
        records = List.copyOf(records);
        for (InspectionRecord record : records) {
            if (!record.lift().equals(lift)) {
                throw new UpkeepError(record.lift() + " does not belong in a " + lift + " book");
            }
        }
    }

    /**
     * An empty book.
     *
     * @param lift the machine
     * @return a book with nothing in it
     */
    public static RecordBook of(LiftCode lift) {
        return new RecordBook(lift, List.of());
    }

    /**
     * A book with its records put in order by day and then by job.
     *
     * @param lift the machine
     * @param records the records, in any order
     * @return the book
     * @throws UpkeepError when a record is of another machine
     */
    public static RecordBook of(LiftCode lift, List<InspectionRecord> records) {
        return new RecordBook(lift, records.stream().sorted(ORDER).toList());
    }

    /**
     * Every record of one job.
     *
     * @param code the job, in either case
     * @return its records, in book order
     */
    public List<InspectionRecord> forCode(String code) {
        String wanted = code.toUpperCase(Locale.ROOT);
        return records.stream().filter(record -> record.code().equals(wanted)).toList();
    }

    /**
     * The latest record of one job.
     *
     * @param code the job, in either case
     * @return the last record of it in book order, or empty when it was never done
     */
    public Optional<InspectionRecord> lastDone(String code) {
        List<InspectionRecord> done = forCode(code);
        return done.isEmpty() ? Optional.empty() : Optional.of(done.get(done.size() - 1));
    }

    /**
     * Every defect in the book not yet cleared.
     *
     * @return those defects, record by record
     */
    public List<Defect> openDefects() {
        return records.stream()
                .flatMap(record -> record.defects().stream())
                .filter(Defect::isOpen)
                .toList();
    }

    /**
     * The open defects that stop the machine.
     *
     * @return those defects, record by record
     */
    public List<Defect> openSerious() {
        return openDefects().stream()
                .filter(defect -> defect.level().stopsTheMachine())
                .toList();
    }

    /**
     * The open defects at one place.
     *
     * @param where the place, such as {@code JP-T4}
     * @return those defects, record by record
     */
    public List<Defect> defectsAt(String where) {
        return openDefects().stream()
                .filter(defect -> defect.where().equals(where))
                .toList();
    }

    /**
     * The book with one more record in it.
     *
     * @param record the record
     * @return a new book in order; this one is unchanged
     * @throws UpkeepError when the record is of another machine
     */
    public RecordBook withRecord(InspectionRecord record) {
        List<InspectionRecord> all = new ArrayList<>(records);
        all.add(record);
        return of(lift, all);
    }

    /**
     * How many records are in the book.
     *
     * @return the count
     */
    public int size() {
        return records.size();
    }
}
