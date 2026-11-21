package io.github.rkaushikdev.sheaveline.persistence.mappers;

import io.github.rkaushikdev.sheaveline.persistence.StorageError;
import io.github.rkaushikdev.sheaveline.persistence.models.BreakObservationRow;
import io.github.rkaushikdev.sheaveline.persistence.models.BreakSurveyRow;
import io.github.rkaushikdev.sheaveline.persistence.models.DiameterReadingRow;
import io.github.rkaushikdev.sheaveline.persistence.models.IndicationRow;
import io.github.rkaushikdev.sheaveline.persistence.models.LayReadingRow;
import io.github.rkaushikdev.sheaveline.persistence.models.MagneticTestRow;
import io.github.rkaushikdev.sheaveline.persistence.models.SpliceRow;
import io.github.rkaushikdev.sheaveline.persistence.models.TensionReadingRow;
import io.github.rkaushikdev.sheaveline.persistence.models.TensionSystemRow;
import io.github.rkaushikdev.sheaveline.persistence.models.WearSurveyRow;
import io.github.rkaushikdev.sheaveline.plant.HaulRope;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.plant.RopeSerial;
import io.github.rkaushikdev.sheaveline.ropework.BreakObservation;
import io.github.rkaushikdev.sheaveline.ropework.BreakSurvey;
import io.github.rkaushikdev.sheaveline.ropework.DiameterReading;
import io.github.rkaushikdev.sheaveline.ropework.Indication;
import io.github.rkaushikdev.sheaveline.ropework.LayReading;
import io.github.rkaushikdev.sheaveline.ropework.MagneticTest;
import io.github.rkaushikdev.sheaveline.ropework.Splice;
import io.github.rkaushikdev.sheaveline.ropework.SpliceRegister;
import io.github.rkaushikdev.sheaveline.ropework.TensionReading;
import io.github.rkaushikdev.sheaveline.ropework.TensionSystem;
import io.github.rkaushikdev.sheaveline.ropework.WearSurvey;
import java.util.Comparator;
import java.util.List;

/**
 * Turning the rope file into rows and back again.
 *
 * <p>Every loader here takes the rope as an argument rather than reading it back through the row.
 * That is not laziness, it is the point: a survey row knows a serial, and the caller is the one that
 * decides which version of that rope it is talking about, because a rope that has been shortened
 * twice has three lengths in its history and the survey does not say which one it was taken against.
 */
public final class RopeworkMapper {

    private RopeworkMapper() {}

    /**
     * A rope walk as a row, with a row for each reading.
     *
     * @param survey the walk
     * @return a new row
     */
    public static WearSurveyRow wearToRow(WearSurvey survey) {
        WearSurveyRow row = new WearSurveyRow(survey.rope().serial(), survey.taken(), survey.inspector());
        for (DiameterReading reading : survey.diameters()) {
            row.addDiameter(new DiameterReadingRow(reading.station(), reading.acrossCrowns(), reading.acrossValleys()));
        }
        for (LayReading lay : survey.lays()) {
            row.addLay(new LayReadingRow(lay.station(), lay.measured()));
        }
        return row;
    }

    /**
     * A rope walk back out of its row, its readings in station order.
     *
     * @param row the row
     * @param rope the rope it was taken against
     * @return the walk
     * @throws StorageError when the row is against another rope
     */
    public static WearSurvey wearFromRow(WearSurveyRow row, HaulRope rope) {
        sameRope(row.getRopeSerial(), rope);
        return WearSurvey.of(
                rope,
                row.getTaken(),
                row.getInspector(),
                row.getDiameters().stream()
                        .sorted(Comparator.comparingLong(one -> one.getStation().mils()))
                        .map(one ->
                                new DiameterReading(one.getStation(), one.getAcrossCrowns(), one.getAcrossValleys()))
                        .toList(),
                row.getLays().stream()
                        .sorted(Comparator.comparingLong(one -> one.getStation().mils()))
                        .map(one -> new LayReading(one.getStation(), one.getMeasured()))
                        .toList());
    }

    /**
     * A broken wire count as a row, with a row for each station counted.
     *
     * @param survey the count
     * @return a new row
     */
    public static BreakSurveyRow breaksToRow(BreakSurvey survey) {
        BreakSurveyRow row = new BreakSurveyRow(survey.rope().serial(), survey.taken(), survey.inspector());
        for (BreakObservation seen : survey.observations()) {
            BreakObservationRow counted = new BreakObservationRow(seen.station(), seen.inOneLay(), seen.inSixLays());
            counted.setValleyBreaks(seen.valleyBreaks());
            counted.setAtSplice(seen.atSplice());
            row.addObservation(counted);
        }
        return row;
    }

    /**
     * A broken wire count back out of its row, its stations in order.
     *
     * @param row the row
     * @param rope the rope it was taken against
     * @return the count
     * @throws StorageError when the row is against another rope
     */
    public static BreakSurvey breaksFromRow(BreakSurveyRow row, HaulRope rope) {
        sameRope(row.getRopeSerial(), rope);
        return BreakSurvey.of(
                rope,
                row.getTaken(),
                row.getInspector(),
                row.getObservations().stream()
                        .sorted(Comparator.comparingLong(one -> one.getStation().mils()))
                        .map(one -> new BreakObservation(
                                one.getStation(),
                                one.getInOneLay(),
                                one.getInSixLays(),
                                one.getValleyBreaks(),
                                one.isAtSplice()))
                        .toList());
    }

    /**
     * A magnetic test as a row, with a row for each indication on the trace.
     *
     * @param test the test
     * @return a new row
     */
    public static MagneticTestRow magneticToRow(MagneticTest test) {
        MagneticTestRow row =
                new MagneticTestRow(test.rope().serial(), test.taken(), test.contractor(), test.instrument());
        for (Indication mark : test.indications()) {
            row.addIndication(new IndicationRow(mark.station(), mark.kind(), mark.magnitude()));
        }
        return row;
    }

    /**
     * A magnetic test back out of its row, its indications in station order.
     *
     * @param row the row
     * @param rope the rope it was taken against
     * @return the test
     * @throws StorageError when the row is against another rope
     */
    public static MagneticTest magneticFromRow(MagneticTestRow row, HaulRope rope) {
        sameRope(row.getRopeSerial(), rope);
        return MagneticTest.of(
                rope,
                row.getTaken(),
                row.getContractor(),
                row.getInstrument(),
                row.getIndications().stream()
                        .sorted(Comparator.comparingLong(one -> one.getStation().mils()))
                        .map(one -> new Indication(one.getStation(), one.getKind(), one.getMagnitude()))
                        .toList());
    }

    /**
     * A rope's splices as rows, one a splice.
     *
     * @param register the register
     * @return new rows
     */
    public static List<SpliceRow> splicesToRows(SpliceRegister register) {
        return register.splices().stream()
                .map(splice -> {
                    SpliceRow row = new SpliceRow(
                            register.rope().serial(),
                            splice.station(),
                            splice.made(),
                            splice.madeBy(),
                            splice.tucks(),
                            splice.length());
                    row.setMill(splice.mill());
                    return row;
                })
                .toList();
    }

    /**
     * A rope's splice register back out of its rows.
     *
     * @param rows the rows
     * @param rope the rope they are in
     * @return the register
     * @throws StorageError when a row is against another rope
     */
    public static SpliceRegister splicesFromRows(List<SpliceRow> rows, HaulRope rope) {
        for (SpliceRow row : rows) {
            sameRope(row.getRopeSerial(), rope);
        }
        return SpliceRegister.of(
                rope,
                rows.stream()
                        .map(row -> new Splice(
                                row.getStation(),
                                row.getMade(),
                                row.getMadeBy(),
                                row.getTucks(),
                                row.getLength(),
                                row.isMill()))
                        .toList());
    }

    /**
     * A machine's tension system as a row.
     *
     * @param system the system
     * @param liftCode the machine it tensions
     * @return a new row
     */
    public static TensionSystemRow tensionToRow(TensionSystem system, LiftCode liftCode) {
        return new TensionSystemRow(liftCode, system.kind(), system.nominal(), system.travel(), system.carriage());
    }

    /**
     * A tension system back out of its row.
     *
     * @param row the row
     * @return the system
     */
    public static TensionSystem tensionFromRow(TensionSystemRow row) {
        return new TensionSystem(row.getKind(), row.getNominal(), row.getTravel(), row.getCarriage());
    }

    /**
     * A tension reading as a row, against the system on a machine. A reading is taken on a day, and
     * the row holds it as the start of that day.
     *
     * @param reading the reading
     * @param liftCode the machine whose system was read
     * @return a new row
     */
    public static TensionReadingRow readingToRow(TensionReading reading, LiftCode liftCode) {
        return new TensionReadingRow(
                liftCode,
                reading.taken().atStartOfDay(),
                reading.carriage(),
                reading.measured(),
                reading.temperature());
    }

    /**
     * A tension reading back out of its row.
     *
     * @param row the row
     * @return the reading, on the day the row was taken
     */
    public static TensionReading readingFromRow(TensionReadingRow row) {
        return new TensionReading(
                row.getTaken().toLocalDate(), row.getCarriage(), row.getMeasured(), row.getTemperature());
    }

    private static void sameRope(RopeSerial serial, HaulRope rope) {
        if (!serial.equals(rope.serial())) {
            throw new StorageError("that row is against " + serial + ", not " + rope.serial());
        }
    }
}
