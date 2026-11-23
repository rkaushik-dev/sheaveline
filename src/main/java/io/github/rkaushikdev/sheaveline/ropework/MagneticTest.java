package io.github.rkaushikdev.sheaveline.ropework;

import io.github.rkaushikdev.sheaveline.plant.HaulRope;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * One pass of the magnetic instrument over the whole rope.
 *
 * <p>A caliper reads the outside of the rope. The magnetic test reads the inside, and on a haul rope
 * the inside is where the trouble starts, because the core crushes and the inner wires break long
 * before anything shows on the crowns. The test comes back as a trace with two kinds of mark on it.
 * Loss of metallic area is a gradual thinning measured as a percentage. A local fault is a step in
 * the trace at one place, which is a broken wire or a group of them.
 *
 * @param rope the rope tested
 * @param taken the day of the test
 * @param contractor the outfit that ran it
 * @param instrument the instrument it was run with
 * @param indications the marks on the trace, in the order reported
 */
public record MagneticTest(
        HaulRope rope, LocalDate taken, String contractor, String instrument, List<Indication> indications) {

    /** A loss of metallic area above this percentage condemns the rope. */
    public static final BigDecimal LMA_CONDEMN = BigDecimal.valueOf(10);

    /** A loss of metallic area above this percentage puts the rope on watch. */
    public static final BigDecimal LMA_WATCH = BigDecimal.valueOf(6);

    /** A local fault of more wires than this condemns the rope. */
    public static final int LOCAL_FAULT_CONDEMN = 4;

    /** A local fault of more wires than this puts the rope on watch. */
    public static final int LOCAL_FAULT_WATCH = 2;

    /**
     * A test that could have been run.
     *
     * @throws RopeworkError when the outfit or the instrument is not named, the test predates the
     *     rope, or a mark sits past the end of it
     */
    public MagneticTest {
        Objects.requireNonNull(rope, "rope");
        Objects.requireNonNull(taken, "taken");
        Objects.requireNonNull(contractor, "contractor");
        Objects.requireNonNull(instrument, "instrument");
        indications = List.copyOf(indications);
        if (contractor.strip().isEmpty()) {
            throw new RopeworkError("a magnetic test names the outfit that ran it");
        }
        if (instrument.strip().isEmpty()) {
            throw new RopeworkError("a magnetic test names the instrument");
        }
        if (taken.isBefore(rope.installed())) {
            throw new RopeworkError("a test cannot predate the rope it is of");
        }
        for (Indication mark : indications) {
            if (mark.station().compareTo(rope.length()) > 0) {
                throw new RopeworkError(
                        "station " + Decimals.fixed(mark.station().feet(), 0) + " is past the end");
            }
        }
    }

    /**
     * A clean trace.
     *
     * @param rope the rope tested
     * @param taken the day of the test
     * @param contractor the outfit that ran it
     * @param instrument the instrument
     * @return the test
     */
    public static MagneticTest of(HaulRope rope, LocalDate taken, String contractor, String instrument) {
        return of(rope, taken, contractor, instrument, List.of());
    }

    /**
     * A trace with its marks.
     *
     * @param rope the rope tested
     * @param taken the day of the test
     * @param contractor the outfit that ran it
     * @param instrument the instrument
     * @param indications the marks
     * @return the test
     */
    public static MagneticTest of(
            HaulRope rope, LocalDate taken, String contractor, String instrument, List<Indication> indications) {
        return new MagneticTest(rope, taken, contractor, instrument, indications);
    }

    /**
     * The marks of one kind.
     *
     * @param kind the kind
     * @return those marks, in the order reported
     */
    public List<Indication> ofKind(IndicationKind kind) {
        return indications.stream().filter(mark -> mark.kind() == kind).toList();
    }

    /**
     * The largest loss of metallic area on the trace.
     *
     * @return the percentage, zero when there is none
     */
    public BigDecimal peakLoss() {
        return ofKind(IndicationKind.LOSS_OF_AREA).stream()
                .map(Indication::magnitude)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
    }

    /**
     * The largest local fault on the trace.
     *
     * @return the number of wires, zero when there is none
     */
    public int largestFault() {
        return ofKind(IndicationKind.LOCAL_FAULT).stream()
                .map(Indication::magnitude)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO)
                .intValue();
    }

    /**
     * The most broken wires the trace shows inside any one lay length, adding up local faults that
     * sit within a lay of each other.
     *
     * <p>The instrument writes a separate step for every group of breaks it can tell apart, so two
     * two wire faults six inches apart come back as two marks. The discard count is per lay, and on
     * the rope they are four wires gone from one lay.
     *
     * @return the number of wires, zero when the trace shows no local fault
     */
    public int wiresInOneLay() {
        List<Indication> faults = ofKind(IndicationKind.LOCAL_FAULT).stream()
                .sorted(Comparator.comparingLong(mark -> mark.station().mils()))
                .toList();
        long lay = rope.spec().layLength().mils();
        int worst = 0;
        for (int first = 0; first < faults.size(); first++) {
            long start = faults.get(first).station().mils();
            int wires = 0;
            for (int next = first; next < faults.size(); next++) {
                if (faults.get(next).station().mils() - start > lay) {
                    break;
                }
                wires += faults.get(next).magnitude().intValue();
            }
            worst = Math.max(worst, wires);
        }
        return worst;
    }

    /** Such as {@code FAT-2019-04412 tested 2025-01-15, 2 marks}. */
    @Override
    public String toString() {
        return rope.serial() + " tested " + taken + ", " + indications.size() + " marks";
    }
}
