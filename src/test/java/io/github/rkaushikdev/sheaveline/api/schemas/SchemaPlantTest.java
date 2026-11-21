package io.github.rkaushikdev.sheaveline.api.schemas;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.sheaveline.Hill;
import io.github.rkaushikdev.sheaveline.api.Json;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.plant.CarrierSet;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.swagger.v3.core.util.Json31;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SchemaPlantTest {

    @Test
    void aLiftReadsBackEveryFigureATerminalTabletShows() {
        Lift lift = Hill.johnPaul();
        LiftOut out = LiftOut.of(lift);
        assertThat(out.code()).isEqualTo(lift.code());
        assertThat(out.name()).isEqualTo("John Paul");
        assertThat(out.liftClass()).isEqualTo("detachable chairlift");
        assertThat(out.horizontalLength()).isEqualTo(lift.horizontalLength());
        assertThat(out.verticalRise()).isEqualTo(lift.verticalRise());
        assertThat(out.rideTime()).isEqualTo(lift.rideTime());
        assertThat(out.ropeLengthAgrees()).isTrue();
    }

    @Test
    void theDerivedFiguresAreComputedRatherThanStored() {
        Lift lift = Hill.johnPaul();
        Lift faster = lift.withDesignSpeed(lift.designSpeed().scaled("0.5"));
        assertThat(LiftOut.of(faster).rideTime()).isGreaterThan(LiftOut.of(lift).rideTime());
    }

    @Test
    void bothTerminalsComeBackTheRightWayUp() {
        LiftOut out = LiftOut.of(Hill.johnPaul());
        assertThat(out.terminals().stream().map(TerminalOut::label).toList())
                .containsExactly("John Paul base", "John Paul summit");
        assertThat(out.terminals().get(0).role()).isEqualTo("drive");
        assertThat(out.terminals().get(1).tensionTravel()).isEqualTo(Optional.of(Length.ofFeet(16)));
    }

    @Test
    void theDriveTerminalCarriesItsBrakesAndTheOtherOneDoesNot() {
        LiftOut out = LiftOut.of(Hill.johnPaul());
        Optional<DriveOut> drive = out.terminals().get(0).drive();
        assertThat(drive).isPresent();
        assertThat(drive.get().brakes()).containsExactlyInAnyOrder("service", "emergency");
        assertThat(drive.get().hasStandbyPower()).isTrue();
        assertThat(out.terminals().get(1).drive()).isEmpty();
    }

    @Test
    void everyTowerComesBackWithBothAssemblies() {
        Lift lift = Hill.johnPaul();
        LiftOut out = LiftOut.of(lift);
        assertThat(out.towers()).hasSize(lift.towers().size());
        TowerOut first = out.towers().get(0);
        assertThat(first.number()).isEqualTo(1);
        assertThat(first.reference()).isEqualTo("JP-T1");
        assertThat(first.crossarmElevation()).isEqualTo(lift.towers().tower(1).crossarmElevation());
        assertThat(first.uphill().kind()).isEqualTo("support");
        assertThat(first.downhill().sheaves()).isEqualTo(4);
    }

    @Test
    void theFleetIsSummarisedRatherThanListed() {
        LiftOut out = LiftOut.of(Hill.johnPaul());
        assertThat(out.fleet().onLine()).isEqualTo(84);
        assertThat(out.fleet().seatsEach()).isEqualTo(4);
        assertThat(out.fleet().seatsOnLine()).isEqualTo(336);
        assertThat(out.fleet().withdrawn()).isZero();
        assertThat(out.fleet().missingNumbers()).isEmpty();
    }

    @Test
    void theRopeComesBackWithItsMillDetails() {
        Lift lift = Hill.johnPaul();
        LiftOut out = LiftOut.of(lift);
        assertThat(out.rope().serial()).isEqualTo(lift.rope().serial());
        assertThat(out.rope().construction()).isEqualTo("6x25 filler wire");
        assertThat(out.rope().splices()).isEqualTo(1);
    }

    @Test
    void theSpansComeBackInOrder() {
        Lift lift = Hill.johnPaul();
        LiftOut out = LiftOut.of(lift);
        assertThat(out.spans().stream().map(SpanOut::index).toList())
                .isEqualTo(IntStream.rangeClosed(1, lift.spans().size()).boxed().toList());
        assertThat(out.spans().get(0).label()).startsWith("John Paul base to");
    }

    @Test
    void aSummaryIsTheFiveThingsAListPageShows() {
        LiftSummaryOut summary = LiftOut.summaryOf(Hill.strawberry());
        assertThat(summary.name()).isEqualTo("Strawberry");
        assertThat(summary.liftClass()).isEqualTo("fixed grip chairlift");
        assertThat(summary.carriersOnLine()).isEqualTo(46);
    }

    @Test
    void everythingSerialisesToStringsAPersonCanRead() {
        JsonNode body = Json.mapper().valueToTree(LiftOut.of(Hill.johnPaul()));
        assertThat(body.get("horizontal_length").asText()).isEqualTo("4800.00 ft");
        assertThat(body.get("design_speed").asText()).isEqualTo("1000 fpm");
        assertThat(body.get("bearing").asText()).isEqualTo("72 deg");
        assertThat(body.get("rope").get("diameter").asText()).isEqualTo("1.378\"");
        assertThat(body.get("towers").get(0).get("uphill").get("deviation").asText())
                .endsWith(" deg");
    }

    @Test
    void theSurfaceTowComesBackWithoutGripsOrATensionDrive() {
        LiftOut out = LiftOut.of(Hill.wildcat());
        assertThat(out.fleet().kind()).isEqualTo("t bar");
        assertThat(out.terminals().get(1).tensionTravel()).isPresent();
        assertThat(out.terminals().get(0).drive()).isPresent();
    }

    @Test
    void aTerminalAndATowerCanBeRenderedOnTheirOwn() {
        Lift lift = Hill.johnPaul();
        assertThat(TerminalOut.of(lift.bottom()).label()).isEqualTo("John Paul base");
        assertThat(TowerOut.of(lift.towers().tower(3)).number()).isEqualTo(3);
        assertThat(SpanOut.of(lift.spans().span(1)).index()).isEqualTo(1);
    }

    @Test
    void theSchemaSaysTheMeasureFieldsAreStrings() {
        JsonNode properties =
                Json31.mapper().valueToTree(Json.schemaOf(LiftOut.class)).get("properties");
        assertThat(properties.get("horizontal_length").get("type").asText()).isEqualTo("string");
        assertThat(properties.get("design_speed").get("type").asText()).isEqualTo("string");
    }

    @Test
    void aWithdrawnCarrierShowsInTheFleetSummary() {
        Lift lift = Hill.johnPaul();
        CarrierSet thinned = new CarrierSet(lift.carriers().carriers().stream()
                .map(carrier -> carrier.number() == 3 ? carrier.withdrawn() : carrier)
                .toList());
        LiftOut out = LiftOut.of(lift.withCarriers(thinned));
        assertThat(out.fleet().onLine()).isEqualTo(83);
        assertThat(out.fleet().withdrawn()).isEqualTo(1);
    }
}
