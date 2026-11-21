package io.github.rkaushikdev.sheaveline;

import io.github.rkaushikdev.sheaveline.measures.Angle;
import io.github.rkaushikdev.sheaveline.measures.Bearing;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.measures.LoadPerFoot;
import io.github.rkaushikdev.sheaveline.measures.Speed;
import io.github.rkaushikdev.sheaveline.measures.Torque;
import io.github.rkaushikdev.sheaveline.plant.AssemblyKind;
import io.github.rkaushikdev.sheaveline.plant.Brake;
import io.github.rkaushikdev.sheaveline.plant.BrakeKind;
import io.github.rkaushikdev.sheaveline.plant.Bullwheel;
import io.github.rkaushikdev.sheaveline.plant.Carrier;
import io.github.rkaushikdev.sheaveline.plant.CarrierSet;
import io.github.rkaushikdev.sheaveline.plant.CarrierType;
import io.github.rkaushikdev.sheaveline.plant.DriveTrain;
import io.github.rkaushikdev.sheaveline.plant.DriveType;
import io.github.rkaushikdev.sheaveline.plant.Grip;
import io.github.rkaushikdev.sheaveline.plant.GripType;
import io.github.rkaushikdev.sheaveline.plant.HaulRope;
import io.github.rkaushikdev.sheaveline.plant.LayDirection;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.github.rkaushikdev.sheaveline.plant.LiftClass;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.plant.LineProfile;
import io.github.rkaushikdev.sheaveline.plant.RopeConstruction;
import io.github.rkaushikdev.sheaveline.plant.RopeGrade;
import io.github.rkaushikdev.sheaveline.plant.RopeSerial;
import io.github.rkaushikdev.sheaveline.plant.RopeSpec;
import io.github.rkaushikdev.sheaveline.plant.SheaveAssembly;
import io.github.rkaushikdev.sheaveline.plant.Terminal;
import io.github.rkaushikdev.sheaveline.plant.TerminalRole;
import io.github.rkaushikdev.sheaveline.plant.Tower;
import io.github.rkaushikdev.sheaveline.plant.TowerLine;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * The hill the tests are written against.
 *
 * <p>One resort, built here so that every test that needs a whole machine does not build one of its
 * own. John Paul is the detachable quad on the north face, Strawberry is the fixed grip triple that
 * serves the beginner terrain, and Wildcat is the surface tow at the base. The site and the season
 * are in {@link HillCalendar}.
 *
 * <p>A test that needs one of these machines with one part changed takes the machine and replaces
 * that part with the lift's {@code with} methods, such as {@code johnPaul().withRope(...)}, so the
 * changed machine is checked as a whole exactly as a freshly built one is.
 */
public final class Hill {

    /** The detachable quad. */
    public static final LiftCode JOHN_PAUL = new LiftCode("JP");

    /** The fixed grip triple. */
    public static final LiftCode STRAWBERRY = new LiftCode("SB");

    /** The surface tow. */
    public static final LiftCode WILDCAT = new LiftCode("WC");

    /** John Paul's survey: station and ground elevation in feet. */
    public static final long[][] JP_SURVEY = {
        {0, 6400}, {300, 6448}, {600, 6496}, {900, 6580}, {1200, 6700}, {1600, 6830}, {2000, 6940},
        {2400, 7040}, {2800, 7190}, {3200, 7360}, {3600, 7520}, {4000, 7660}, {4400, 7800}, {4800, 7920}
    };

    /** Strawberry's survey. */
    public static final long[][] SB_SURVEY = {
        {0, 6400}, {400, 6460}, {800, 6530}, {1200, 6610}, {1600, 6700}, {2000, 6790}
    };

    /** Wildcat's survey. */
    public static final long[][] WC_SURVEY = {{0, 6390}, {200, 6420}, {400, 6455}, {600, 6492}};

    /**
     * John Paul's towers: number, station, base elevation, height, and the deviation designed into
     * each side of the crossarm. The two halves add up to what the rope actually does over that tower
     * with the design weight on the line.
     */
    private static final Object[][] JP_LAYOUT = {
        {1, 600, 6496, 71, "0.064", "0.064"},
        {2, 1200, 6700, 61, "3.913", "3.912"},
        {3, 1800, 6880, 56, "3.398", "3.397"},
        {4, 2400, 7040, 64, "0.085", "0.084"},
        {5, 3000, 7280, 62, "3.175", "3.175"},
        {6, 3600, 7520, 58, "4.268", "4.268"},
        {7, 4200, 7730, 56, "3.639", "3.639"},
    };

    private Hill() {}

    /**
     * A support assembly of four sheaves at 1.4 degrees.
     *
     * @return the assembly
     */
    public static SheaveAssembly supportAssembly() {
        return supportAssembly(4, "1.4");
    }

    /**
     * A support assembly.
     *
     * @param sheaves how many sheaves
     * @param degrees the deviation
     * @return the assembly
     */
    public static SheaveAssembly supportAssembly(int sheaves, String degrees) {
        return new SheaveAssembly(AssemblyKind.SUPPORT, sheaves, Angle.ofDegrees(degrees));
    }

    /**
     * A hold down assembly of four sheaves at minus 1.1 degrees.
     *
     * @return the assembly
     */
    public static SheaveAssembly holdDownAssembly() {
        return holdDownAssembly(4, "-1.1");
    }

    /**
     * A hold down assembly.
     *
     * @param sheaves how many sheaves
     * @param degrees the deviation, negative
     * @return the assembly
     */
    public static SheaveAssembly holdDownAssembly(int sheaves, String degrees) {
        return new SheaveAssembly(AssemblyKind.HOLD_DOWN, sheaves, Angle.ofDegrees(degrees));
    }

    /**
     * John Paul's drive: a 700 horsepower AC drive with service and emergency brakes and a diesel
     * auxiliary.
     *
     * @return the drive train
     */
    public static DriveTrain driveTrain() {
        return driveTrain(DriveType.AC_DRIVE, 700, defaultBrakes(), Optional.of(DriveType.DIESEL_HYDRAULIC));
    }

    /**
     * A drive train with the 18.5 to one gearbox.
     *
     * @param type the prime mover
     * @param horsepower the rating
     * @param brakes the brakes
     * @param auxiliary the standby drive
     * @return the drive train
     */
    public static DriveTrain driveTrain(
            DriveType type, int horsepower, List<Brake> brakes, Optional<DriveType> auxiliary) {
        return new DriveTrain(type, horsepower, new BigDecimal("18.5"), brakes, auxiliary);
    }

    /**
     * A service brake at 900 pound feet and an emergency brake at 2400.
     *
     * @return the two brakes
     */
    public static List<Brake> defaultBrakes() {
        return List.of(
                new Brake(BrakeKind.SERVICE, Torque.ofPoundFeet(900)),
                new Brake(BrakeKind.EMERGENCY, Torque.ofPoundFeet(2400)));
    }

    /**
     * An eleven foot six bullwheel.
     *
     * @return the bullwheel
     */
    public static Bullwheel bullwheel() {
        return bullwheel(11, 6);
    }

    /**
     * A polyurethane lined bullwheel.
     *
     * @param feet the diameter's feet
     * @param inches the diameter's inches
     * @return the bullwheel
     */
    public static Bullwheel bullwheel(long feet, long inches) {
        return new Bullwheel(Length.ofFeetInches(feet, inches), "polyurethane");
    }

    /**
     * John Paul's rope specification: 1.378 inch 6x25 filler wire.
     *
     * @return the specification
     */
    public static RopeSpec jpRopeSpec() {
        return new RopeSpec(
                RopeConstruction.SIX_BY_TWENTY_FIVE,
                RopeGrade.EIP,
                LayDirection.RIGHT_REGULAR,
                Length.ofInches("1.378"),
                Load.ofPounds(196000),
                LoadPerFoot.ofPoundsPerFoot("3.12"));
    }

    /**
     * John Paul's seven towers.
     *
     * @return the tower line
     */
    public static TowerLine jpTowers() {
        List<Tower> towers = new ArrayList<>();
        for (Object[] row : JP_LAYOUT) {
            int number = (Integer) row[0];
            int sheaves = number == 4 || number == 5 ? 6 : 4;
            towers.add(new Tower(
                    JOHN_PAUL.tower(number),
                    Length.ofFeet((Integer) row[1]),
                    Length.ofFeet((Integer) row[2]),
                    Length.ofFeet((Integer) row[3]),
                    supportAssembly(sheaves, (String) row[4]),
                    supportAssembly(4, (String) row[5])));
        }
        return new TowerLine(towers);
    }

    /**
     * John Paul's eighty four quads.
     *
     * @return the fleet
     */
    public static CarrierSet jpCarriers() {
        return jpCarriers(84);
    }

    /**
     * John Paul's quads, numbered from one.
     *
     * @param count how many
     * @return the fleet
     */
    public static CarrierSet jpCarriers(int count) {
        Grip grip = new Grip(GripType.DETACHABLE, "AK6", 1, "blue", Load.ofPounds(2600), LocalDate.of(2019, 10, 4));
        return fleet(
                count, CarrierType.CHAIR_4, Load.ofPounds(410), Length.ofFeetInches(6, 4), Optional.of(grip), true);
    }

    /**
     * The detachable quad on the north face.
     *
     * @return John Paul
     */
    public static Lift johnPaul() {
        return new Lift(
                JOHN_PAUL,
                "John Paul",
                LiftClass.DETACHABLE_CHAIR,
                Bearing.ofDegrees(72),
                new Terminal(
                        TerminalRole.DRIVE,
                        "John Paul base",
                        Length.ofFeet(0),
                        Length.ofFeet(6441),
                        bullwheel(),
                        Optional.of(driveTrain()),
                        Optional.empty()),
                new Terminal(
                        TerminalRole.RETURN_TENSION,
                        "John Paul summit",
                        Length.ofFeet(4800),
                        Length.ofFeet(7981),
                        bullwheel(),
                        Optional.empty(),
                        Optional.of(Length.ofFeet(16))),
                jpTowers(),
                jpCarriers(),
                new HaulRope(
                        RopeSerial.parse("FAT-2019-04412"),
                        jpRopeSpec(),
                        LocalDate.of(2019, 10, 4),
                        Length.ofFeet(10132),
                        1),
                LineProfile.fromSurvey(JP_SURVEY),
                Speed.ofFeetPerMinute(1000),
                LocalDate.of(2004, 12, 3));
    }

    /**
     * Strawberry's three towers.
     *
     * @return the tower line
     */
    public static TowerLine sbTowers() {
        long[][] layout = {{1, 400, 6460, 26}, {2, 900, 6545, 30}, {3, 1500, 6675, 24}};
        List<Tower> towers = new ArrayList<>();
        for (long[] row : layout) {
            towers.add(new Tower(
                    STRAWBERRY.tower((int) row[0]),
                    Length.ofFeet(row[1]),
                    Length.ofFeet(row[2]),
                    Length.ofFeet(row[3]),
                    supportAssembly(),
                    supportAssembly()));
        }
        return new TowerLine(towers);
    }

    /**
     * Strawberry's forty six triples.
     *
     * @return the fleet
     */
    public static CarrierSet sbCarriers() {
        return sbCarriers(46);
    }

    /**
     * Strawberry's triples, numbered from one.
     *
     * @param count how many
     * @return the fleet
     */
    public static CarrierSet sbCarriers(int count) {
        Grip grip = new Grip(GripType.FIXED, "TB41", 2, "green", Load.ofPounds(2100), LocalDate.of(2011, 9, 20));
        return fleet(
                count, CarrierType.CHAIR_3, Load.ofPounds(295), Length.ofFeetInches(5, 10), Optional.of(grip), true);
    }

    /**
     * The fixed grip triple over the beginner terrain.
     *
     * @return Strawberry
     */
    public static Lift strawberry() {
        return new Lift(
                STRAWBERRY,
                "Strawberry",
                LiftClass.FIXED_CHAIR,
                Bearing.ofDegrees(310),
                new Terminal(
                        TerminalRole.DRIVE_TENSION,
                        "Strawberry base",
                        Length.ofFeet(0),
                        Length.ofFeet(6418),
                        bullwheel(9, 0),
                        Optional.of(driveTrain(DriveType.AC_DRIVE, 310, defaultBrakes(), Optional.empty())),
                        Optional.of(Length.ofFeet(9))),
                new Terminal(
                        TerminalRole.RETURN,
                        "Strawberry top",
                        Length.ofFeet(2000),
                        Length.ofFeet(6812),
                        bullwheel(9, 0),
                        Optional.empty(),
                        Optional.empty()),
                sbTowers(),
                sbCarriers(),
                new HaulRope(
                        RopeSerial.parse("BRI-2011-01887"),
                        new RopeSpec(
                                RopeConstruction.SIX_BY_TWENTY_SIX,
                                RopeGrade.EIP,
                                LayDirection.RIGHT_REGULAR,
                                Length.ofInches("1.102"),
                                Load.ofPounds(124000),
                                LoadPerFoot.ofPoundsPerFoot("1.98")),
                        LocalDate.of(2011, 9, 20),
                        Length.ofFeet(4113),
                        1),
                LineProfile.fromSurvey(SB_SURVEY),
                Speed.ofFeetPerMinute(450),
                LocalDate.of(1988, 11, 18));
    }

    /**
     * Wildcat's two towers.
     *
     * @return the tower line
     */
    public static TowerLine wcTowers() {
        long[][] layout = {{1, 200, 6420, 18}, {2, 450, 6467, 18}};
        List<Tower> towers = new ArrayList<>();
        for (long[] row : layout) {
            towers.add(new Tower(
                    WILDCAT.tower((int) row[0]),
                    Length.ofFeet(row[1]),
                    Length.ofFeet(row[2]),
                    Length.ofFeet(row[3]),
                    supportAssembly(2, "0.9"),
                    supportAssembly(2, "0.9")));
        }
        return new TowerLine(towers);
    }

    /**
     * Wildcat's twenty two t bars.
     *
     * @return the fleet
     */
    public static CarrierSet wcCarriers() {
        return wcCarriers(22);
    }

    /**
     * Wildcat's t bars, numbered from one.
     *
     * @param count how many
     * @return the fleet
     */
    public static CarrierSet wcCarriers(int count) {
        return fleet(count, CarrierType.TBAR, Load.ofPounds(38), Length.ofFeetInches(11, 0), Optional.empty(), false);
    }

    /**
     * The surface tow at the base area.
     *
     * @return Wildcat
     */
    public static Lift wildcat() {
        return new Lift(
                WILDCAT,
                "Wildcat",
                LiftClass.SURFACE_TOW,
                Bearing.ofDegrees(145),
                new Terminal(
                        TerminalRole.DRIVE,
                        "Wildcat base",
                        Length.ofFeet(0),
                        Length.ofFeet(6404),
                        bullwheel(6, 6),
                        Optional.of(driveTrain(
                                DriveType.DIESEL_HYDRAULIC,
                                75,
                                List.of(new Brake(BrakeKind.SERVICE, Torque.ofPoundFeet(300))),
                                Optional.empty())),
                        Optional.empty()),
                new Terminal(
                        TerminalRole.RETURN_TENSION,
                        "Wildcat top",
                        Length.ofFeet(600),
                        Length.ofFeet(6506),
                        bullwheel(6, 6),
                        Optional.empty(),
                        Optional.of(Length.ofFeet(6))),
                wcTowers(),
                wcCarriers(),
                new HaulRope(
                        RopeSerial.parse("BRI-2016-00294"),
                        new RopeSpec(
                                RopeConstruction.EIGHT_BY_NINETEEN,
                                RopeGrade.IPS,
                                LayDirection.RIGHT_REGULAR,
                                Length.ofInches("0.625"),
                                Load.ofPounds(35000),
                                LoadPerFoot.ofPoundsPerFoot("0.63")),
                        LocalDate.of(2016, 10, 11),
                        Length.ofFeet(1238),
                        1),
                LineProfile.fromSurvey(WC_SURVEY),
                Speed.ofFeetPerMinute(560),
                LocalDate.of(1996, 12, 6));
    }

    private static CarrierSet fleet(
            int count, CarrierType kind, Load tare, Length hanger, Optional<Grip> grip, boolean bar) {
        return new CarrierSet(IntStream.rangeClosed(1, count)
                .mapToObj(number -> new Carrier(number, kind, tare, hanger, grip, bar, true))
                .toList());
    }
}
