package io.github.rkaushikdev.sheaveline.plant;

import static io.github.rkaushikdev.sheaveline.Hill.johnPaul;
import static io.github.rkaushikdev.sheaveline.Hill.strawberry;
import static io.github.rkaushikdev.sheaveline.Hill.wildcat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.rkaushikdev.sheaveline.ParseError;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class BlueprintTest {

    private static ObjectNode drawing(Lift lift) {
        return Blueprint.writeLift(lift);
    }

    private static ObjectNode drawing() {
        return drawing(johnPaul());
    }

    @Test
    void theDrawingCarriesEveryPartOfTheMachine() {
        ObjectNode document = drawing();
        assertThat(document.get("code").asText()).isEqualTo("JP");
        assertThat(document.get("lift_class").asText()).isEqualTo("DETACHABLE_CHAIR");
        assertThat(document.get("terminals")).hasSize(2);
        assertThat(document.get("towers")).hasSize(7);
        assertThat(document.get("fleet").get("count").asInt()).isEqualTo(84);
        assertThat(document.get("rope").get("serial").asText()).isEqualTo("FAT-2019-04412");
        assertThat(document.get("survey")).hasSize(14);
    }

    @Test
    void everyMeasureInTheDrawingIsWrittenAsText() {
        ObjectNode document = drawing();
        assertThat(document.get("design_speed").asText()).endsWith(" fpm");
        assertThat(document.get("towers").get(0).get("height").asText()).endsWith(" ft");
        assertThat(document.get("rope").get("spec").get("nominal_diameter").asText())
                .endsWith("\"");
        assertThat(document.get("survey").get(0).get("elevation").asText()).endsWith(" ft");
    }

    @Test
    void theDrawingIsPlainJson() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        String text = mapper.writeValueAsString(drawing());
        assertThat(mapper.readTree(text).get("code").asText()).isEqualTo("JP");
    }

    @Test
    void aMachineReadBackFromItsOwnDrawingIsTheSameMachine() {
        Lift before = johnPaul();
        Lift after = Blueprint.readLift(drawing(before));
        assertThat(after.code()).isEqualTo(before.code());
        assertThat(after.name()).isEqualTo(before.name());
        assertThat(after.liftClass()).isSameAs(before.liftClass());
        assertThat(after.bearing()).isEqualTo(before.bearing());
        assertThat(after.designSpeed()).isEqualTo(before.designSpeed());
        assertThat(after.commissioned()).isEqualTo(before.commissioned());
    }

    @Test
    void theLineReadsBackWithTheSameGeometry() {
        Lift before = johnPaul();
        Lift after = Blueprint.readLift(drawing(before));
        assertThat(after.horizontalLength()).isEqualTo(before.horizontalLength());
        assertThat(after.verticalRise()).isEqualTo(before.verticalRise());
        assertThat(after.slopeLength()).isEqualTo(before.slopeLength());
        assertThat(after.spans().size()).isEqualTo(before.spans().size());
    }

    @Test
    void everyTowerReadsBackWithBothAssemblies() {
        Lift before = johnPaul();
        Lift after = Blueprint.readLift(drawing(before));
        List<Tower> was = before.towers().towers();
        List<Tower> now = after.towers().towers();
        assertThat(now).hasSameSizeAs(was);
        for (int at = 0; at < was.size(); at++) {
            assertThat(now.get(at).ref()).isEqualTo(was.get(at).ref());
            assertThat(now.get(at).station()).isEqualTo(was.get(at).station());
            assertThat(now.get(at).height()).isEqualTo(was.get(at).height());
            assertThat(now.get(at).uphill()).isEqualTo(was.get(at).uphill());
            assertThat(now.get(at).downhill()).isEqualTo(was.get(at).downhill());
        }
    }

    @Test
    void theDriveReadsBackWithItsBrakes() {
        Lift before = johnPaul();
        Lift after = Blueprint.readLift(drawing(before));
        DriveTrain was = before.driveTerminal().driveTrain();
        DriveTrain now = after.driveTerminal().driveTrain();
        assertThat(now.ratedHorsepower()).isEqualTo(was.ratedHorsepower());
        assertThat(now.gearboxRatio()).isEqualByComparingTo(was.gearboxRatio());
        assertThat(now.auxiliary()).isEqualTo(was.auxiliary());
        assertThat(now.brakes().stream().map(Brake::kind))
                .containsExactlyInAnyOrderElementsOf(
                        was.brakes().stream().map(Brake::kind).toList());
    }

    @Test
    void theFleetReadsBackWithItsGrip() {
        Lift before = johnPaul();
        Lift after = Blueprint.readLift(drawing(before));
        assertThat(after.carriersOnLine()).isEqualTo(before.carriersOnLine());
        assertThat(after.carriers().carrier(1).gripOf().model())
                .isEqualTo(before.carriers().carrier(1).gripOf().model());
        assertThat(after.carriers().carrier(1).tare())
                .isEqualTo(before.carriers().carrier(1).tare());
    }

    @Test
    void theRopeReadsBackWithItsSpec() {
        Lift before = johnPaul();
        assertThat(Blueprint.readLift(drawing(before)).rope()).isEqualTo(before.rope());
    }

    @Test
    void theTripleAndTheTowRoundTripAsWell() {
        for (Lift lift : List.of(strawberry(), wildcat())) {
            Lift after = Blueprint.readLift(drawing(lift));
            assertThat(after.code()).isEqualTo(lift.code());
            assertThat(after.liftClass()).isSameAs(lift.liftClass());
            assertThat(after.carriersOnLine()).isEqualTo(lift.carriersOnLine());
        }
    }

    @Test
    void aSurfaceCarrierReadsBackWithNoGrip() {
        Lift after = Blueprint.readLift(drawing(wildcat()));
        assertThat(StreamSupport.stream(after.carriers().spliterator(), false))
                .allMatch(one -> one.grip().isEmpty());
    }

    @Test
    void theTerminalsAreReadTheRightWayUpWhicheverOrderTheyAreGiven() {
        ObjectNode document = drawing();
        ArrayNode reversed = document.putArray("terminals");
        ObjectNode original = drawing();
        reversed.add(original.get("terminals").get(1))
                .add(original.get("terminals").get(0));
        Lift after = Blueprint.readLift(document);
        assertThat(after.bottom().label()).isEqualTo("John Paul base");
        assertThat(after.top().label()).isEqualTo("John Paul summit");
    }

    @Test
    void aMissingFieldSaysWhichOneAndWhere() {
        ObjectNode document = drawing();
        document.remove("design_speed");
        assertThatThrownBy(() -> Blueprint.readLift(document))
                .isInstanceOf(PlantError.class)
                .hasMessageContaining("design_speed")
                .hasMessage("JP has no design_speed");
    }

    @Test
    void aMissingFieldInsideATowerSaysWhichTower() {
        ObjectNode document = drawing();
        ((ObjectNode) document.get("towers").get(2)).remove("height");
        assertThatThrownBy(() -> Blueprint.readLift(document))
                .isInstanceOf(PlantError.class)
                .hasMessageContaining("tower 3");
    }

    @Test
    void aNameThatIsNotAClassIsRefused() {
        ObjectNode document = drawing();
        document.put("lift_class", "MONORAIL");
        assertThatThrownBy(() -> Blueprint.readLift(document))
                .isInstanceOf(PlantError.class)
                .hasMessageContaining("MONORAIL")
                .hasMessage("JP names 'MONORAIL', which is not a LiftClass");
    }

    @Test
    void aDateThatIsNotADateIsRefused() {
        ObjectNode document = drawing();
        document.put("commissioned", "the winter of 2004");
        assertThatThrownBy(() -> Blueprint.readLift(document))
                .isInstanceOf(PlantError.class)
                .hasMessage("JP has 'the winter of 2004' where a date belongs");
    }

    @Test
    void aMeasureThatDoesNotParseIsRefused() {
        ObjectNode document = drawing();
        ((ObjectNode) document.get("towers").get(0)).put("height", "quite tall");
        assertThatThrownBy(() -> Blueprint.readLift(document)).isInstanceOf(ParseError.class);
    }

    @Test
    void aDrawingWithOneTerminalIsRefused() {
        ObjectNode document = drawing();
        JsonNode first = document.get("terminals").get(0);
        document.putArray("terminals").add(first);
        assertThatThrownBy(() -> Blueprint.readLift(document))
                .isInstanceOf(PlantError.class)
                .hasMessage("JP has 1 terminals, not two");
    }

    @Test
    void aFleetOfNothingIsRefused() {
        ObjectNode document = drawing();
        ((ObjectNode) document.get("fleet")).put("count", 0);
        assertThatThrownBy(() -> Blueprint.readLift(document)).isInstanceOf(PlantError.class);
    }

    @Test
    void aSurveyWithNoShotsIsRefused() {
        ObjectNode document = drawing();
        document.putArray("survey");
        assertThatThrownBy(() -> Blueprint.readLift(document))
                .isInstanceOf(PlantError.class)
                .hasMessage("the survey has no shots in it");
    }

    @Test
    void theDomainChecksStillApplyToADrawing() {
        ObjectNode document = drawing();
        document.put("design_speed", "1400 fpm");
        assertThatThrownBy(() -> Blueprint.readLift(document)).isInstanceOf(PlantError.class);
    }

    @Test
    void writingAMachineReadFromADrawingGivesTheSameDrawing() {
        ObjectNode once = drawing();
        ObjectNode twice = Blueprint.writeLift(Blueprint.readLift(once));
        assertThat(twice).isEqualTo(once);
        assertThat(twice.toString()).isEqualTo(once.toString());
    }
}
