package io.github.rkaushikdev.sheaveline.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.sheaveline.ParseError;
import io.github.rkaushikdev.sheaveline.RangeError;
import io.github.rkaushikdev.sheaveline.persistence.StorageError;
import io.github.rkaushikdev.sheaveline.persistence.UnitOfWork;
import io.github.rkaushikdev.sheaveline.plant.PlantError;
import io.github.rkaushikdev.sheaveline.scratch.ScratchRoutes;
import io.github.rkaushikdev.sheaveline.upkeep.UpkeepError;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;

/**
 * The application as a whole: started through its factory, with routes the tests add beside its
 * own.
 */
class ApiAppTest {

    private static Settings settings;
    private static ConfigurableApplicationContext app;

    @BeforeAll
    static void startTheApplication() {
        settings = Settings.inMemory();
        app = SheavelineApplication.run(settings, List.of(ScratchRoutes.class), Wire.RANDOM_PORT);
    }

    @AfterAll
    static void stopTheApplication() {
        app.close();
        settings.database().close();
    }

    private static Http client() {
        return Wire.clientOf(app);
    }

    @Test
    void theServiceAnswersForItself() {
        Http.Answer reply = client().get("/health");
        assertThat(reply.status()).isEqualTo(200);
        JsonNode body = reply.json();
        assertThat(body.get("service").asText()).isEqualTo("sheaveline");
        assertThat(body.get("resort").asText()).isEqualTo("Ogden Peak");
    }

    @Test
    void theFactoryMakesAFreshApplicationEveryTime() {
        Settings one = Settings.inMemory();
        Settings two = Settings.inMemory();
        try (ConfigurableApplicationContext first = SheavelineApplication.run(one, Wire.RANDOM_PORT);
                ConfigurableApplicationContext second = SheavelineApplication.run(two, Wire.RANDOM_PORT)) {
            assertThat(first).isNotSameAs(second);
            assertThat(SheavelineApplication.settingsOf(first).database())
                    .isNotSameAs(SheavelineApplication.settingsOf(second).database());
        } finally {
            one.database().close();
            two.database().close();
        }
    }

    @Test
    void theResortNameComesOffTheSettings() {
        Settings alta = Settings.inMemory().withResort("Alta View");
        try (Wire.Running running = Wire.freshClient(alta)) {
            assertThat(running.client().get("/health").json().get("resort").asText())
                    .isEqualTo("Alta View");
        } finally {
            alta.database().close();
        }
    }

    @Test
    void anApplicationBuiltWithoutSettingsSaysSo() {
        try (GenericApplicationContext bare = new GenericApplicationContext()) {
            bare.refresh();
            assertThatThrownBy(() -> SheavelineApplication.settingsOf(bare))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("this application was built without settings");
        }
    }

    @Test
    void aUnitOfWorkIsOpenedAndClosedAroundARequest() {
        List<UnitOfWork> opened = new ArrayList<>();
        SheavelineApplication.unitOfWork(app, work -> {
            assertThat(work.isOpen()).isTrue();
            opened.add(work);
        });
        assertThat(opened).hasSize(1);
        assertThat(opened.get(0).isOpen()).isFalse();
    }

    @Test
    void aParseFailureIsAFourHundred() {
        assertThat(Errors.statusFor(new ParseError("nine", "a length"))).isEqualTo(400);
    }

    @Test
    void aRangeFailureIsAFourHundred() {
        assertThat(Errors.statusFor(new RangeError("tower number", 0, 1, 99))).isEqualTo(400);
    }

    @Test
    void aDomainRefusalIsAConflict() {
        assertThat(Errors.statusFor(new PlantError("a quad does not take a six place")))
                .isEqualTo(409);
        assertThat(Errors.statusFor(new UpkeepError("no such job"))).isEqualTo(409);
    }

    @Test
    void somethingThatIsNotOnFileIsAFourOhFour() {
        assertThat(Errors.statusFor(new StorageError("there is no lift ZZ"))).isEqualTo(404);
        assertThat(Errors.statusFor(new NotFound("nothing here"))).isEqualTo(404);
    }

    /** A parse failure is a measure failure too, and the narrower row is the one that counts. */
    @Test
    void theMostSpecificRowInTheTableWins() {
        assertThat(Errors.statusFor(new ParseError("nine", "a length"))).isEqualTo(400);
    }

    @Test
    void anythingUnmappedIsAFiveHundred() {
        assertThat(Errors.statusFor(new RuntimeException("something else"))).isEqualTo(500);
    }

    @Test
    void theBodyNamesTheKindAndTheSentence() {
        ErrorBody body = Errors.bodyFor(new PlantError("a quad does not take a six place"));
        assertThat(body.kind()).isEqualTo("PlantError");
        assertThat(body.detail()).isEqualTo("a quad does not take a six place");
        assertThat(body.field()).isEmpty();
    }

    @Test
    void aRangeFailureSaysWhichFieldItWas() {
        ErrorBody body = Errors.bodyFor(new RangeError("tower number", 0, 1, 99));
        assertThat(body.field()).isEqualTo(Optional.of("tower number"));
    }

    @Test
    void aParseFailureSaysWhatItWanted() {
        ErrorBody body = Errors.bodyFor(new ParseError("nine", "a length"));
        assertThat(body.field()).isEqualTo(Optional.of("a length"));
    }

    @Test
    void aDomainFailureRaisedInARouteComesBackInTheOneShape() {
        Http.Answer reply = client().get("/scratch/refuse");
        assertThat(reply.status()).isEqualTo(409);
        assertThat(reply.json())
                .isEqualTo(
                        Http.tree(
                                """
                        {"kind": "PlantError", "detail": "a quad does not take a six place", "field": null}
                        """));
    }

    @Test
    void aMeasureFailureRaisedInARouteIsAFourHundred() {
        Http.Answer reply = client().get("/scratch/measure");
        assertThat(reply.status()).isEqualTo(400);
        assertThat(reply.json().get("field").asText()).isEqualTo("a length");
    }

    @Test
    void aBadRequestIsTheSameShapeAsEverythingElse() {
        Http.Answer reply = client().get("/scratch/needs?count=notanumber");
        assertThat(reply.status()).isEqualTo(422);
        JsonNode body = reply.json();
        assertThat(body.properties().stream().map(Map.Entry::getKey).toList())
                .containsExactlyInAnyOrder("kind", "detail", "field");
        assertThat(body.get("field").asText()).isEqualTo("count");
    }
}
