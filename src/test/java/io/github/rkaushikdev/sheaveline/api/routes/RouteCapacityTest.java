package io.github.rkaushikdev.sheaveline.api.routes;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.sheaveline.Hill;
import io.github.rkaushikdev.sheaveline.api.Http;
import io.github.rkaushikdev.sheaveline.api.Settings;
import io.github.rkaushikdev.sheaveline.api.Wire;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.persistence.UnitOfWork;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@Import(Wire.Service.class)
class RouteCapacityTest {

    @Autowired
    private MockMvc mvc;

    private Http client;

    @BeforeEach
    void putTheMachinesOnFile() {
        Settings built = Wire.settings();
        try (UnitOfWork work = UnitOfWork.on(built.database()).open()) {
            work.lifts().add(Hill.johnPaul());
            work.lifts().add(Hill.strawberry());
            work.lifts().add(Hill.wildcat());
            work.commit();
        }
        client = new Http(mvc);
    }

    JsonNode body(String target) {
        return client.get(target, Wire.headers()).json();
    }

    static List<String> severities(JsonNode findings) {
        return StreamSupport.stream(findings.spliterator(), false)
                .map(one -> one.get("severity").asText())
                .toList();
    }

    @Test
    void theSpacingFallsOutOfTheMachineOnFile() {
        JsonNode body = body("/lifts/JP/spacing");
        assertThat(body.get("carriers").asInt()).isEqualTo(84);
        assertThat(body.get("speed").asText()).isEqualTo("1000 fpm");
        assertThat(body.get("spacing").asText()).endsWith(" ft");
        assertThat(body.get("meets_the_floor")).isEqualTo(Http.TRUE);
        assertThat(body.get("shortfall").asText()).isEqualTo("0:00:00");
    }

    @Test
    void takingCarriersOffOpensTheInterval() {
        JsonNode full = body("/lifts/JP/spacing");
        JsonNode thin = body("/lifts/JP/spacing?carriers=42");
        assertThat(thin.get("carriers").asInt()).isEqualTo(42);
        assertThat(thin.get("interval").asText())
                .isGreaterThan(full.get("interval").asText());
    }

    @Test
    void slowingTheRopeOpensItToo() {
        JsonNode fast = body("/lifts/JP/spacing");
        JsonNode slow = body("/lifts/JP/spacing?speed=500+fpm");
        assertThat(slow.get("speed").asText()).isEqualTo("500 fpm");
        assertThat(slow.get("interval").asText())
                .isGreaterThan(fast.get("interval").asText());
        assertThat(slow.get("spacing")).isEqualTo(fast.get("spacing"));
    }

    @Test
    void theClassFloorComesBackWithTheAnswer() {
        JsonNode quad = body("/lifts/JP/spacing");
        JsonNode triple = body("/lifts/SB/spacing");
        assertThat(quad.get("class_floor").asText()).isEqualTo("0:00:06");
        assertThat(triple.get("class_floor").asText()).isEqualTo("0:00:09");
    }

    @Test
    void aLineRunTooFullSaysHowFarUnderTheFloorItIs() {
        JsonNode body = body("/lifts/JP/spacing?carriers=2000");
        assertThat(body.get("meets_the_floor")).isEqualTo(Http.FALSE);
        assertThat(body.get("shortfall").asText()).isNotEqualTo("0:00:00");
        assertThat(body.get("most_carriers").asInt()).isLessThan(2000);
    }

    @Test
    void aSpeedThatDoesNotParseIsAFourHundred() {
        Http.Answer reply = client.get("/lifts/JP/spacing?speed=quite+fast", Wire.headers());
        assertThat(reply.status()).isEqualTo(400);
    }

    @Test
    void aCarrierCountOfNothingIsRefusedBeforeItReachesTheDomain() {
        Http.Answer reply = client.get("/lifts/JP/spacing?carriers=0", Wire.headers());
        assertThat(reply.status()).isEqualTo(422);
    }

    @Test
    void theCapacityReadsThreeWays() {
        JsonNode body = body("/lifts/JP/capacity");
        assertThat(body.get("seats_per_hour").asInt())
                .isGreaterThan(body.get("persons_per_hour").asInt());
        assertThat(body.get("carriers_per_hour").asInt() * 4)
                .isEqualTo(body.get("seats_per_hour").asInt());
        assertThat(body.get("vertical_feet_per_hour").asLong())
                .isGreaterThan(body.get("persons_per_hour").asLong());
    }

    @Test
    void aWorseLoadingEfficiencyMovesOnlyThePersonsFigure() {
        JsonNode good = body("/lifts/JP/capacity?occupancy=0.95");
        JsonNode poor = body("/lifts/JP/capacity?occupancy=0.60");
        assertThat(good.get("seats_per_hour")).isEqualTo(poor.get("seats_per_hour"));
        assertThat(good.get("persons_per_hour").asInt())
                .isGreaterThan(poor.get("persons_per_hour").asInt());
    }

    @Test
    void theCapacityAnswersAWhatIfWithoutStoringIt() {
        JsonNode thin = body("/lifts/JP/capacity?carriers=42");
        assertThat(thin.get("carriers").asInt()).isEqualTo(42);
        JsonNode again = body("/lifts/JP/capacity");
        assertThat(again.get("carriers").asInt()).isEqualTo(84);
    }

    @Test
    void anOccupancyOutsideTheRangeIsRefused() {
        Http.Answer reply = client.get("/lifts/JP/capacity?occupancy=1.4", Wire.headers());
        assertThat(reply.status()).isEqualTo(409);
        assertThat(reply.json().get("kind").asText()).isEqualTo("CapacityError");
    }

    @Test
    void theLineWalkComesBackFitInAThinYear() {
        JsonNode body = body("/lifts/JP/geometry?snow=2+ft");
        assertThat(body.get("verdict").asText()).isEqualTo("fit");
        assertThat(body.get("may_run")).isEqualTo(Http.TRUE);
        assertThat(body.get("worst_margin").asText()).endsWith(" ft");
        // A note about a tower reading off its design figure is information, not a reason to stop,
        // so a fit line may still carry some.
        assertThat(severities(body.get("findings"))).allMatch("note"::equals);
    }

    @Test
    void deepSnowTakesTheLineOutAndSaysWhere() {
        JsonNode body = body("/lifts/JP/geometry?snow=40+ft");
        assertThat(body.get("verdict").asText()).isEqualTo("unfit");
        assertThat(body.get("may_run")).isEqualTo(Http.FALSE);
        assertThat(body.get("findings")).isNotEmpty();
        assertThat(severities(body.get("findings"))).contains("stop");
    }

    @Test
    void droppingTheTensionLetsTheRopeDown() {
        JsonNode tight = body("/lifts/JP/geometry?snow=6+ft&tension=40000+lb");
        JsonNode slack = body("/lifts/JP/geometry?snow=6+ft&tension=12000+lb");
        assertThat(slack.get("worst_clearance").asText())
                .isLessThan(tight.get("worst_clearance").asText());
    }

    @Test
    void iceOnTheRopeBringsTheLineDown() {
        JsonNode bare = body("/lifts/JP/geometry?snow=6+ft");
        JsonNode iced = body("/lifts/JP/geometry?snow=6+ft&ice=1+in");
        assertThat(Length.parse(iced.get("worst_clearance").asText()))
                .isLessThan(Length.parse(bare.get("worst_clearance").asText()));
        assertThat(Length.parse(iced.get("ice").asText())).isEqualTo(Length.ofInches(1));
        assertThat(Length.parse(bare.get("ice").asText())).isEqualTo(Length.ZERO);
    }

    @Test
    void iceThinnerThanNothingIsRefused() {
        Http.Answer reply = client.get("/lifts/JP/geometry?ice=-1+in", Wire.headers());
        assertThat(reply.status()).isGreaterThanOrEqualTo(400).isLessThan(500);
    }

    @Test
    void theConditionsComeBackWithTheAnswer() {
        JsonNode body = body("/lifts/JP/geometry?snow=6+ft&tension=34500+lb");
        assertThat(body.get("snow").asText()).isEqualTo("6.00 ft");
        assertThat(body.get("tension").asText()).isEqualTo("34,500 lb");
    }

    @Test
    void aSnowDepthThatDoesNotParseIsAFourHundred() {
        Http.Answer reply = client.get("/lifts/JP/geometry?snow=deep", Wire.headers());
        assertThat(reply.status()).isEqualTo(400);
    }

    @Test
    void aMachineThatIsNotThereIsAFourOhFourOnEveryRoute() {
        for (String path : List.of("spacing", "capacity", "geometry")) {
            Http.Answer reply = client.get("/lifts/ZZ/" + path, Wire.headers());
            assertThat(reply.status()).as(path).isEqualTo(404);
        }
    }

    @Test
    void theSurfaceTowAnswersTheSameQuestions() {
        JsonNode body = body("/lifts/WC/spacing");
        assertThat(body.get("carriers").asInt()).isEqualTo(22);
        assertThat(body.get("class_floor").asText()).isEqualTo("0:00:05");
    }
}
