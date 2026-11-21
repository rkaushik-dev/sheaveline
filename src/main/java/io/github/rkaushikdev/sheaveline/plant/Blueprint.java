package io.github.rkaushikdev.sheaveline.plant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.sheaveline.measures.Angle;
import io.github.rkaushikdev.sheaveline.measures.Bearing;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.measures.LoadPerFoot;
import io.github.rkaushikdev.sheaveline.measures.Speed;
import io.github.rkaushikdev.sheaveline.measures.Torque;
import io.github.rkaushikdev.sheaveline.util.Decimals;
import io.github.rkaushikdev.sheaveline.util.Quote;
import io.github.rkaushikdev.sheaveline.util.Valued;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Building a machine from the drawing, and writing one back out to it.
 *
 * <p>A lift arrives as a document. The surveyor sends elevations, the manufacturer sends a tower
 * schedule and a rope spec, and somebody types the rest. So there is one shape that all of it can be
 * written in, a JSON object, and one function that turns it into a {@link Lift} with every check in
 * the domain applied.
 *
 * <p>Both directions, because the office also has to hand the drawing back: when a machine is
 * modified the board wants the description it was accepted under, and reconstructing that from the
 * database by hand is how the wrong figure gets filed. The written document keeps a fixed key order,
 * so writing a machine read from a drawing gives the same drawing.
 *
 * <p>Every measure in the document is text. A tower height of 55 is ambiguous and a tower height of
 * {@code "55 ft"} is not, and the one place that matters is the one place somebody is typing.
 * Choices are written by their constant names ({@code DETACHABLE_CHAIR}) and read by either the name
 * or the words.
 */
public final class Blueprint {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private Blueprint() {}

    /**
     * Build a machine from the drawing, with every domain check applied.
     *
     * @param document the drawing
     * @return the lift
     * @throws PlantError naming the missing or wrong field and where it is
     * @throws io.github.rkaushikdev.sheaveline.ParseError when a measure does not read
     */
    public static Lift readLift(JsonNode document) {
        LiftCode code = LiftCode.parse(text(need(document, "code", "the drawing")));
        String where = code.toString();
        JsonNode terminals = need(document, "terminals", where);
        if (terminals.size() != 2) {
            throw new PlantError(where + " has " + terminals.size() + " terminals, not two");
        }
        Terminal bottom = readTerminal(terminals.get(0));
        Terminal top = readTerminal(terminals.get(1));
        if (bottom.station().compareTo(top.station()) > 0) {
            Terminal swap = bottom;
            bottom = top;
            top = swap;
        }
        return new Lift(
                code,
                text(need(document, "name", where)),
                named(LiftClass.class, need(document, "lift_class", where), where),
                Bearing.parse(text(need(document, "bearing", where))),
                bottom,
                top,
                readTowers(need(document, "towers", where), code),
                readCarriers(need(document, "fleet", where)),
                readRope(need(document, "rope", where)),
                readSurvey(need(document, "survey", where)),
                Speed.parse(text(need(document, "design_speed", where))),
                day(need(document, "commissioned", where), where));
    }

    /**
     * Hand the drawing back, in the shape it would be read from.
     *
     * @param lift the machine
     * @return the drawing
     */
    public static ObjectNode writeLift(Lift lift) {
        ObjectNode out = NODES.objectNode();
        out.put("code", lift.code().toString());
        out.put("name", lift.name());
        out.put("lift_class", lift.liftClass().name());
        out.put("bearing", lift.bearing().formatDegrees(3));
        out.put("design_speed", lift.designSpeed().formatFpm(2));
        out.put("commissioned", lift.commissioned().toString());
        out.putArray("terminals").add(writeTerminal(lift.bottom())).add(writeTerminal(lift.top()));
        ArrayNode towers = out.putArray("towers");
        lift.towers().forEach(one -> towers.add(writeTower(one)));
        out.set("fleet", writeCarriers(lift.carriers()));
        out.set("rope", writeRope(lift.rope()));
        out.set("survey", writeSurvey(lift.ground()));
        return out;
    }

    static SheaveAssembly readAssembly(JsonNode document, String where) {
        return new SheaveAssembly(
                named(AssemblyKind.class, need(document, "kind", where), where),
                whole(need(document, "sheaves", where), where),
                Angle.parse(text(need(document, "deviation", where))));
    }

    static ObjectNode writeAssembly(SheaveAssembly assembly) {
        ObjectNode out = NODES.objectNode();
        out.put("kind", assembly.kind().name());
        out.put("sheaves", assembly.sheaves());
        out.put("deviation", assembly.deviation().formatDegrees(3));
        return out;
    }

    private static TowerLine readTowers(JsonNode towers, LiftCode code) {
        List<Tower> read = new ArrayList<>();
        towers.forEach(one -> read.add(readTower(one, code)));
        return new TowerLine(read);
    }

    static Tower readTower(JsonNode document, LiftCode code) {
        int number = whole(need(document, "number", "a tower"), "a tower");
        String where = "tower " + number;
        return new Tower(
                code.tower(number),
                Length.parse(text(need(document, "station", where))),
                Length.parse(text(need(document, "base_elevation", where))),
                Length.parse(text(need(document, "height", where))),
                readAssembly(need(document, "uphill", where), where + " uphill"),
                readAssembly(need(document, "downhill", where), where + " downhill"));
    }

    static ObjectNode writeTower(Tower tower) {
        ObjectNode out = NODES.objectNode();
        out.put("number", tower.number());
        out.put("station", feet(tower.station()));
        out.put("base_elevation", feet(tower.baseElevation()));
        out.put("height", feet(tower.height()));
        out.set("uphill", writeAssembly(tower.uphill()));
        out.set("downhill", writeAssembly(tower.downhill()));
        return out;
    }

    static Terminal readTerminal(JsonNode document) {
        String label = text(need(document, "label", "a terminal"));
        TerminalRole role = named(TerminalRole.class, need(document, "role", label), label);
        Optional<DriveTrain> drive = present(document, "drive").map(one -> readDrive(one, label));
        Optional<Length> travel = present(document, "tension_travel").map(one -> Length.parse(text(one)));
        return new Terminal(
                role,
                label,
                Length.parse(text(need(document, "station", label))),
                Length.parse(text(need(document, "rope_elevation", label))),
                new Bullwheel(
                        Length.parse(text(need(document, "bullwheel_diameter", label))),
                        text(need(document, "bullwheel_liner", label))),
                drive,
                travel);
    }

    static DriveTrain readDrive(JsonNode document, String where) {
        List<Brake> brakes = new ArrayList<>();
        for (JsonNode one : need(document, "brakes", where)) {
            brakes.add(new Brake(
                    named(BrakeKind.class, need(one, "kind", where), where),
                    Torque.parse(text(need(one, "torque", where))),
                    !one.has("hydraulic") || one.get("hydraulic").asBoolean()));
        }
        return new DriveTrain(
                named(DriveType.class, need(document, "drive_type", where), where),
                whole(need(document, "rated_horsepower", where), where),
                new BigDecimal(text(need(document, "gearbox_ratio", where))),
                brakes,
                present(document, "auxiliary").map(one -> named(DriveType.class, one, where)));
    }

    static ObjectNode writeTerminal(Terminal terminal) {
        ObjectNode out = NODES.objectNode();
        out.put("label", terminal.label());
        out.put("role", terminal.role().name());
        out.put("station", feet(terminal.station()));
        out.put("rope_elevation", feet(terminal.ropeElevation()));
        out.put("bullwheel_diameter", terminal.bullwheel().diameter().formatInches(2));
        out.put("bullwheel_liner", terminal.bullwheel().liner());
        terminal.tensionTravel().ifPresent(travel -> out.put("tension_travel", feet(travel)));
        terminal.drive().ifPresent(drive -> {
            ObjectNode written = out.putObject("drive");
            written.put("drive_type", drive.driveType().name());
            written.put("rated_horsepower", drive.ratedHorsepower());
            written.put("gearbox_ratio", drive.gearboxRatio().toString());
            if (drive.auxiliary().isPresent()) {
                written.put("auxiliary", drive.auxiliary().get().name());
            } else {
                written.putNull("auxiliary");
            }
            ArrayNode brakes = written.putArray("brakes");
            for (Brake brake : drive.brakes()) {
                ObjectNode one = brakes.addObject();
                one.put("kind", brake.kind().name());
                one.put("torque", brake.torque().formatInchPounds());
                one.put("hydraulic", brake.hydraulic());
            }
        });
        return out;
    }

    static CarrierSet readCarriers(JsonNode document) {
        String where = "the fleet";
        CarrierType kind = named(CarrierType.class, need(document, "kind", where), where);
        int count = whole(need(document, "count", where), where);
        if (count < 1) {
            throw new PlantError("a fleet has carriers in it");
        }
        Optional<Grip> grip = present(document, "grip").map(Blueprint::readGrip);
        Load tare = Load.parse(text(need(document, "tare", where)));
        Length hanger = Length.parse(text(need(document, "hanger", where)));
        boolean bar = document.has("restraining_bar")
                && document.get("restraining_bar").asBoolean();
        return new CarrierSet(IntStream.rangeClosed(1, count)
                .mapToObj(number -> new Carrier(number, kind, tare, hanger, grip, bar, true))
                .toList());
    }

    private static Grip readGrip(JsonNode one) {
        String where = "the grip";
        return new Grip(
                named(GripType.class, need(one, "kind", where), where),
                text(need(one, "model", where)),
                whole(need(one, "jaws", where), where),
                text(need(one, "spring_pack", where)),
                Load.parse(text(need(one, "rated_slip", where))),
                day(need(one, "installed", where), where));
    }

    static ObjectNode writeCarriers(CarrierSet fleet) {
        Carrier first = fleet.carriers().get(0);
        ObjectNode out = NODES.objectNode();
        out.put("kind", fleet.kind().name());
        out.put("count", fleet.size());
        out.put("tare", first.tare().formatPounds());
        out.put("hanger", first.hanger().formatFeetInches());
        out.put("restraining_bar", first.restrainingBar());
        first.grip().ifPresent(grip -> {
            ObjectNode written = out.putObject("grip");
            written.put("kind", grip.kind().name());
            written.put("model", grip.model());
            written.put("jaws", grip.jaws());
            written.put("spring_pack", grip.springPack());
            written.put("rated_slip", grip.ratedSlip().formatPounds());
            written.put("installed", grip.installed().toString());
        });
        return out;
    }

    static HaulRope readRope(JsonNode document) {
        String where = "the rope";
        JsonNode spec = need(document, "spec", where);
        return new HaulRope(
                RopeSerial.parse(text(need(document, "serial", where))),
                new RopeSpec(
                        named(RopeConstruction.class, need(spec, "construction", where), where),
                        named(RopeGrade.class, need(spec, "grade", where), where),
                        named(LayDirection.class, need(spec, "lay", where), where),
                        Length.parse(text(need(spec, "nominal_diameter", where))),
                        Load.parse(text(need(spec, "breaking_strength", where))),
                        LoadPerFoot.ofPoundsPerFoot(text(need(spec, "weight_per_foot", where)))),
                day(need(document, "installed", where), where),
                Length.parse(text(need(document, "length", where))),
                document.has("splices") ? whole(document.get("splices"), where) : 1);
    }

    static ObjectNode writeRope(HaulRope rope) {
        ObjectNode out = NODES.objectNode();
        out.put("serial", rope.serial().toString());
        out.put("installed", rope.installed().toString());
        out.put("length", feet(rope.length()));
        out.put("splices", rope.splices());
        ObjectNode spec = out.putObject("spec");
        spec.put("construction", rope.spec().construction().name());
        spec.put("grade", rope.spec().grade().name());
        spec.put("lay", rope.spec().lay().name());
        spec.put("nominal_diameter", rope.spec().nominalDiameter().formatInches(3));
        spec.put("breaking_strength", rope.spec().breakingStrength().formatPounds());
        spec.put("weight_per_foot", rope.spec().weight().poundsPerFoot().toString());
        return out;
    }

    static LineProfile readSurvey(JsonNode shots) {
        if (shots.isEmpty()) {
            throw new PlantError("the survey has no shots in it");
        }
        List<GroundPoint> points = new ArrayList<>();
        for (JsonNode one : shots) {
            points.add(new GroundPoint(
                    Length.parse(text(need(one, "station", "a survey shot"))),
                    Length.parse(text(need(one, "elevation", "a survey shot")))));
        }
        return new LineProfile(points);
    }

    static ArrayNode writeSurvey(LineProfile ground) {
        ArrayNode out = NODES.arrayNode();
        for (GroundPoint one : ground.points()) {
            ObjectNode shot = out.addObject();
            shot.put("station", feet(one.station()));
            shot.put("elevation", feet(one.elevation()));
        }
        return out;
    }

    private static String feet(Length length) {
        return Decimals.fixed(length.feet(), 2) + " ft";
    }

    private static JsonNode need(JsonNode document, String key, String where) {
        if (!document.has(key)) {
            throw new PlantError(where + " has no " + key);
        }
        return document.get(key);
    }

    private static Optional<JsonNode> present(JsonNode document, String key) {
        JsonNode found = document.get(key);
        return found == null || found.isNull() ? Optional.empty() : Optional.of(found);
    }

    private static String text(JsonNode value) {
        return value.asText();
    }

    private static int whole(JsonNode value, String where) {
        if (value.isIntegralNumber()) {
            return value.intValue();
        }
        try {
            return Integer.parseInt(value.asText().strip());
        } catch (NumberFormatException wrong) {
            throw new PlantError(where + " has " + shown(value) + " where a whole number belongs", wrong);
        }
    }

    private static <E extends Enum<E> & Valued> E named(Class<E> kind, JsonNode value, String where) {
        String wanted = value.asText();
        for (E member : kind.getEnumConstants()) {
            if (value.isTextual()
                    && (member.name().equals(wanted) || member.value().equals(wanted))) {
                return member;
            }
        }
        throw new PlantError(where + " names " + shown(value) + ", which is not a " + kind.getSimpleName());
    }

    private static LocalDate day(JsonNode value, String where) {
        try {
            return LocalDate.parse(value.asText());
        } catch (DateTimeParseException bad) {
            throw new PlantError(where + " has " + shown(value) + " where a date belongs", bad);
        }
    }

    private static String shown(JsonNode value) {
        return value.isTextual() ? Quote.of(value.asText()) : value.toString();
    }
}
