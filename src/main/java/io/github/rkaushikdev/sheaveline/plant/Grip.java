package io.github.rkaushikdev.sheaveline.plant;

import io.github.rkaushikdev.sheaveline.measures.Load;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * One grip, its spring pack, and the pull it is meant to let go at.
 *
 * <p>The grip is where the load actually goes into the rope. A fixed grip is torqued once and
 * checked; a detachable opens and closes twice a lap and is slip tested on a schedule. A surface
 * lift has a hanger and no grip at all.
 *
 * @param kind fixed or detachable
 * @param model the maker's model
 * @param jaws one or two
 * @param springPack the spring pack fitted
 * @param ratedSlip the pull it lets go above
 * @param installed the day it went on
 */
public record Grip(GripType kind, String model, int jaws, String springPack, Load ratedSlip, LocalDate installed) {

    /**
     * A grip that describes a real grip.
     *
     * @throws PlantError for a tow hanger, a jaw count other than one or two, no rated slip, or no
     *     model
     */
    public Grip {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(springPack, "springPack");
        Objects.requireNonNull(ratedSlip, "ratedSlip");
        Objects.requireNonNull(installed, "installed");
        if (kind == GripType.TOW) {
            throw new PlantError("a tow hanger is not a grip");
        }
        if (jaws != 1 && jaws != 2) {
            throw new PlantError("a grip has one or two jaws, not " + jaws);
        }
        if (ratedSlip.pounds() <= 0) {
            throw new PlantError("a grip has a rated slip force");
        }
        if (model.strip().isEmpty()) {
            throw new PlantError("a grip has a model");
        }
    }

    /**
     * Whether the grip goes on the slip test schedule.
     *
     * @return true for both grip kinds
     */
    public boolean isSlipTested() {
        return kind.isTestedForSlip();
    }

    /**
     * Days since the grip went on.
     *
     * @param on the day asked about
     * @return whole days
     * @throws PlantError when the day is before the grip went on
     */
    public long ageInDays(LocalDate on) {
        if (on.isBefore(installed)) {
            throw new PlantError(on + " is before the grip went on");
        }
        return ChronoUnit.DAYS.between(installed, on);
    }

    /**
     * Whether the grip holds a pull. It holds anything up to its rated slip and lets go above it.
     *
     * @param pull the pull along the rope
     * @return true up to and including the rated slip
     */
    public boolean holds(Load pull) {
        return pull.compareTo(ratedSlip) <= 0;
    }

    /** Such as {@code AK6 detachable grip}. */
    @Override
    public String toString() {
        return model + " " + kind.value() + " grip";
    }
}
