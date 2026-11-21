package io.github.rkaushikdev.sheaveline.persistence;

import io.github.rkaushikdev.sheaveline.board.FilingKind;
import io.github.rkaushikdev.sheaveline.board.IncidentKind;
import io.github.rkaushikdev.sheaveline.board.InjuryLevel;
import io.github.rkaushikdev.sheaveline.evac.DrillKind;
import io.github.rkaushikdev.sheaveline.ops.CheckOutcome;
import io.github.rkaushikdev.sheaveline.ops.EntryKind;
import io.github.rkaushikdev.sheaveline.ops.Post;
import io.github.rkaushikdev.sheaveline.ops.StopKind;
import io.github.rkaushikdev.sheaveline.people.Endorsement;
import io.github.rkaushikdev.sheaveline.plant.AssemblyKind;
import io.github.rkaushikdev.sheaveline.plant.BrakeKind;
import io.github.rkaushikdev.sheaveline.plant.CarrierType;
import io.github.rkaushikdev.sheaveline.plant.DriveType;
import io.github.rkaushikdev.sheaveline.plant.GripType;
import io.github.rkaushikdev.sheaveline.plant.LayDirection;
import io.github.rkaushikdev.sheaveline.plant.LiftClass;
import io.github.rkaushikdev.sheaveline.plant.RopeConstruction;
import io.github.rkaushikdev.sheaveline.plant.RopeGrade;
import io.github.rkaushikdev.sheaveline.plant.TerminalRole;
import io.github.rkaushikdev.sheaveline.ropework.IndicationKind;
import io.github.rkaushikdev.sheaveline.ropework.TensionKind;
import io.github.rkaushikdev.sheaveline.upkeep.DefectLevel;
import io.github.rkaushikdev.sheaveline.upkeep.Priority;
import io.github.rkaushikdev.sheaveline.weather.HoldReason;
import io.github.rkaushikdev.sheaveline.weather.Precipitation;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Objects;

/**
 * An enumeration stored by its constant's name, in a plain text column of {@value
 * ColumnTypes#ENUM_LENGTH} characters rather than a database enum type.
 *
 * <p>The name and not the words a person reads. The words get reworded, and a reworded value would
 * silently orphan every row written before the change. One converter per stored enumeration, each
 * applied wherever its type appears on a row.
 *
 * @param <E> the enumeration
 */
public abstract class NamedEnum<E extends Enum<E>> implements AttributeConverter<E, String> {

    private final Class<E> type;

    /**
     * A converter for one enumeration.
     *
     * @param type the enumeration
     */
    protected NamedEnum(Class<E> type) {
        this.type = Objects.requireNonNull(type, "type");
    }

    /**
     * The enumeration this converter stores.
     *
     * @return its class
     */
    public final Class<E> type() {
        return type;
    }

    @Override
    public final String convertToDatabaseColumn(E value) {
        return value == null ? null : value.name();
    }

    @Override
    public final E convertToEntityAttribute(String value) {
        return value == null ? null : Enum.valueOf(type, value);
    }

    /** A {@link LiftClass}. */
    @Converter(autoApply = true)
    public static final class LiftClasses extends NamedEnum<LiftClass> {
        /** The converter. */
        public LiftClasses() {
            super(LiftClass.class);
        }
    }

    /** A {@link TerminalRole}. */
    @Converter(autoApply = true)
    public static final class TerminalRoles extends NamedEnum<TerminalRole> {
        /** The converter. */
        public TerminalRoles() {
            super(TerminalRole.class);
        }
    }

    /** A {@link DriveType}, for the main drive and the auxiliary alike. */
    @Converter(autoApply = true)
    public static final class DriveTypes extends NamedEnum<DriveType> {
        /** The converter. */
        public DriveTypes() {
            super(DriveType.class);
        }
    }

    /** A {@link BrakeKind}. */
    @Converter(autoApply = true)
    public static final class BrakeKinds extends NamedEnum<BrakeKind> {
        /** The converter. */
        public BrakeKinds() {
            super(BrakeKind.class);
        }
    }

    /** An {@link AssemblyKind}, uphill and downhill alike. */
    @Converter(autoApply = true)
    public static final class AssemblyKinds extends NamedEnum<AssemblyKind> {
        /** The converter. */
        public AssemblyKinds() {
            super(AssemblyKind.class);
        }
    }

    /** A {@link CarrierType}. */
    @Converter(autoApply = true)
    public static final class CarrierTypes extends NamedEnum<CarrierType> {
        /** The converter. */
        public CarrierTypes() {
            super(CarrierType.class);
        }
    }

    /** A {@link GripType}. */
    @Converter(autoApply = true)
    public static final class GripTypes extends NamedEnum<GripType> {
        /** The converter. */
        public GripTypes() {
            super(GripType.class);
        }
    }

    /** A {@link RopeConstruction}. */
    @Converter(autoApply = true)
    public static final class RopeConstructions extends NamedEnum<RopeConstruction> {
        /** The converter. */
        public RopeConstructions() {
            super(RopeConstruction.class);
        }
    }

    /** A {@link RopeGrade}. */
    @Converter(autoApply = true)
    public static final class RopeGrades extends NamedEnum<RopeGrade> {
        /** The converter. */
        public RopeGrades() {
            super(RopeGrade.class);
        }
    }

    /** A {@link LayDirection}. */
    @Converter(autoApply = true)
    public static final class LayDirections extends NamedEnum<LayDirection> {
        /** The converter. */
        public LayDirections() {
            super(LayDirection.class);
        }
    }

    /** An {@link IndicationKind}. */
    @Converter(autoApply = true)
    public static final class IndicationKinds extends NamedEnum<IndicationKind> {
        /** The converter. */
        public IndicationKinds() {
            super(IndicationKind.class);
        }
    }

    /** A {@link TensionKind}. */
    @Converter(autoApply = true)
    public static final class TensionKinds extends NamedEnum<TensionKind> {
        /** The converter. */
        public TensionKinds() {
            super(TensionKind.class);
        }
    }

    /** A {@link Precipitation}. */
    @Converter(autoApply = true)
    public static final class Precipitations extends NamedEnum<Precipitation> {
        /** The converter. */
        public Precipitations() {
            super(Precipitation.class);
        }
    }

    /** A {@link HoldReason}. */
    @Converter(autoApply = true)
    public static final class HoldReasons extends NamedEnum<HoldReason> {
        /** The converter. */
        public HoldReasons() {
            super(HoldReason.class);
        }
    }

    /** A {@link FilingKind}. */
    @Converter(autoApply = true)
    public static final class FilingKinds extends NamedEnum<FilingKind> {
        /** The converter. */
        public FilingKinds() {
            super(FilingKind.class);
        }
    }

    /** An {@link IncidentKind}. */
    @Converter(autoApply = true)
    public static final class IncidentKinds extends NamedEnum<IncidentKind> {
        /** The converter. */
        public IncidentKinds() {
            super(IncidentKind.class);
        }
    }

    /** An {@link InjuryLevel}. */
    @Converter(autoApply = true)
    public static final class InjuryLevels extends NamedEnum<InjuryLevel> {
        /** The converter. */
        public InjuryLevels() {
            super(InjuryLevel.class);
        }
    }

    /** A {@link DrillKind}. */
    @Converter(autoApply = true)
    public static final class DrillKinds extends NamedEnum<DrillKind> {
        /** The converter. */
        public DrillKinds() {
            super(DrillKind.class);
        }
    }

    /** An {@link Endorsement}. */
    @Converter(autoApply = true)
    public static final class Endorsements extends NamedEnum<Endorsement> {
        /** The converter. */
        public Endorsements() {
            super(Endorsement.class);
        }
    }

    /** An {@link EntryKind}. */
    @Converter(autoApply = true)
    public static final class EntryKinds extends NamedEnum<EntryKind> {
        /** The converter. */
        public EntryKinds() {
            super(EntryKind.class);
        }
    }

    /** A {@link CheckOutcome}. */
    @Converter(autoApply = true)
    public static final class CheckOutcomes extends NamedEnum<CheckOutcome> {
        /** The converter. */
        public CheckOutcomes() {
            super(CheckOutcome.class);
        }
    }

    /** A {@link StopKind}. */
    @Converter(autoApply = true)
    public static final class StopKinds extends NamedEnum<StopKind> {
        /** The converter. */
        public StopKinds() {
            super(StopKind.class);
        }
    }

    /** A {@link Post}. */
    @Converter(autoApply = true)
    public static final class Posts extends NamedEnum<Post> {
        /** The converter. */
        public Posts() {
            super(Post.class);
        }
    }

    /** A {@link DefectLevel}. */
    @Converter(autoApply = true)
    public static final class DefectLevels extends NamedEnum<DefectLevel> {
        /** The converter. */
        public DefectLevels() {
            super(DefectLevel.class);
        }
    }

    /** A work order's {@link Priority}. */
    @Converter(autoApply = true)
    public static final class Priorities extends NamedEnum<Priority> {
        /** The converter. */
        public Priorities() {
            super(Priority.class);
        }
    }
}
