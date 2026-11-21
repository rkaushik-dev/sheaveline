package io.github.rkaushikdev.sheaveline.persistence.models;

import io.github.rkaushikdev.sheaveline.measures.Bearing;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.measures.LoadPerFoot;
import io.github.rkaushikdev.sheaveline.measures.Speed;
import io.github.rkaushikdev.sheaveline.plant.LayDirection;
import io.github.rkaushikdev.sheaveline.plant.LiftClass;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.plant.RopeConstruction;
import io.github.rkaushikdev.sheaveline.plant.RopeGrade;
import java.time.LocalDate;

/** The rows the model tests stand their own rows on. */
final class Rows {

    static final LiftCode JP = new LiftCode("JP");

    private Rows() {}

    /** John Paul, with nothing bolted to it. */
    static LiftRow johnPaul() {
        return new LiftRow(
                JP,
                "John Paul",
                LiftClass.DETACHABLE_CHAIR,
                Bearing.ofDegrees(72),
                Speed.ofFeetPerMinute(1000),
                LocalDate.of(2004, 12, 3));
    }

    /** The specification John Paul's rope was made to. */
    static RopeSpecRow spec() {
        return new RopeSpecRow(
                RopeConstruction.SIX_BY_TWENTY_FIVE,
                RopeGrade.EIP,
                LayDirection.RIGHT_REGULAR,
                Length.ofInches("1.378"),
                Load.ofPounds(196000),
                LoadPerFoot.ofPoundsPerFoot("3.12"));
    }
}
