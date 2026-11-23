package io.github.rkaushikdev.sheaveline.api.routes;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.sheaveline.Hill;
import io.github.rkaushikdev.sheaveline.api.Http;
import io.github.rkaushikdev.sheaveline.api.Role;
import io.github.rkaushikdev.sheaveline.api.Settings;
import io.github.rkaushikdev.sheaveline.api.Wire;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.persistence.UnitOfWork;
import io.github.rkaushikdev.sheaveline.plant.HaulRope;
import io.github.rkaushikdev.sheaveline.ropework.BreakObservation;
import io.github.rkaushikdev.sheaveline.ropework.BreakSurvey;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@Import(Wire.Service.class)
class RouteRopesTest {

    static final String SERIAL = "FAT-2019-04412";

    @Autowired
    private MockMvc mvc;

    private Http client;

    private Settings built;

    @BeforeEach
    void putTheRopesOnFile() {
        built = Wire.settings();
        try (UnitOfWork work = UnitOfWork.on(built.database()).open()) {
            work.lifts().add(Hill.johnPaul());
            work.lifts().add(Hill.strawberry());
            work.ropes().add(Hill.wildcat().rope());
            work.commit();
        }
        client = new Http(mvc);
    }

    static Map<String, Object> surveyBody() {
        return surveyBody("1.376", "2025-01-15");
    }

    static Map<String, Object> surveyBody(String inches, String taken) {
        return surveyBody(taken, List.of(reading("0 ft", inches), reading("1200 ft", inches)));
    }

    static Map<String, Object> surveyBody(String taken, List<Map<String, Object>> diameters) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("taken", taken);
        body.put("inspector", "R Halvorsen");
        body.put("diameters", diameters);
        return body;
    }

    static Map<String, Object> reading(String station, String inches) {
        Map<String, Object> reading = new LinkedHashMap<>();
        reading.put("station", station);
        reading.put("across_crowns", inches + " in");
        reading.put("across_valleys", inches + " in");
        return reading;
    }

    @Test
    void everyRopeOnFileComesBack() {
        JsonNode body = client.get("/ropes", Wire.headers()).json();
        assertThat(body).hasSize(3);
        assertThat(Http.texts(body, "serial")).contains(SERIAL);
    }

    @Test
    void aRopeWithNoMachineUnderItIsASpare() {
        JsonNode body = client.get("/ropes/spares", Wire.headers()).json();
        assertThat(Http.texts(body, "serial")).containsExactly("BRI-2016-00294");
    }

    @Test
    void oneRopeComesBackWithTheFiguresOffItsSpec() {
        JsonNode body = client.get("/ropes/" + SERIAL, Wire.headers()).json();
        assertThat(body.get("construction").asText()).isEqualTo("6x25 filler wire");
        assertThat(body.get("diameter").asText()).isEqualTo("1.378\"");
        assertThat(body.get("condemning_diameter").asText()).isEqualTo("1.282\"");
        assertThat(body.get("maximum_tension").asText()).isEqualTo("49,000 lb");
        assertThat(body.get("endless")).isEqualTo(Http.TRUE);
    }

    @Test
    void aRopeThatIsNotOnFileIsAFourOhFour() {
        Http.Answer reply = client.get("/ropes/FAT-2019-99999", Wire.headers());
        assertThat(reply.status()).isEqualTo(404);
    }

    @Test
    void somethingThatIsNotASerialIsAFourHundred() {
        Http.Answer reply = client.get("/ropes/not-a-serial", Wire.headers());
        assertThat(reply.status()).isEqualTo(400);
    }

    @Test
    void aRopeStartsWithNoWalksAgainstIt() {
        assertThat(client.get("/ropes/" + SERIAL + "/surveys", Wire.headers())
                        .json()
                        .get("items"))
                .isEmpty();
    }

    @Test
    void aMechanicRecordsAWalkAndGetsTheVerdictBack() {
        Http.Answer reply = client.postJson("/ropes/" + SERIAL + "/surveys", surveyBody(), Wire.headers(Role.MECHANIC));
        assertThat(reply.status()).isEqualTo(201);
        JsonNode body = reply.json();
        assertThat(body.get("verdict").asText()).isEqualTo("serviceable");
        assertThat(body.get("inspector").asText()).isEqualTo("R Halvorsen");
        assertThat(body.get("readings")).hasSize(2);
    }

    @Test
    void aWornRopeComesBackCondemnedInTheSameReply() {
        Http.Answer reply = client.postJson(
                "/ropes/" + SERIAL + "/surveys", surveyBody("1.270", "2025-01-15"), Wire.headers(Role.MECHANIC));
        assertThat(reply.status()).isEqualTo(201);
        assertThat(reply.json().get("verdict").asText()).isEqualTo("condemned");
    }

    @Test
    void aRecordedWalkComesBackOnTheList() {
        client.postJson("/ropes/" + SERIAL + "/surveys", surveyBody(), Wire.headers(Role.MECHANIC));
        JsonNode body = client.get("/ropes/" + SERIAL + "/surveys", Wire.headers())
                .json()
                .get("items");
        assertThat(body).hasSize(1);
        assertThat(body.get(0).get("taken").asText()).isEqualTo("2025-01-15");
        assertThat(body.get(0).get("smallest").get("station").asText()).isEqualTo("0.00 ft");
    }

    @Test
    void aReaderKeyMayNotRecordAWalk() {
        Http.Answer reply = client.postJson("/ropes/" + SERIAL + "/surveys", surveyBody(), Wire.headers(Role.READER));
        assertThat(reply.status()).isEqualTo(401);
    }

    @Test
    void aWalkWithNoReadingsIsRefused() {
        Map<String, Object> body = surveyBody();
        body.put("diameters", List.of());
        Http.Answer reply = client.postJson("/ropes/" + SERIAL + "/surveys", body, Wire.headers(Role.MECHANIC));
        assertThat(reply.status()).isEqualTo(422);
        assertThat(reply.json().get("kind").asText()).isEqualTo("RequestValidationError");
    }

    @Test
    void aReadingThatDoesNotParseIsRefused() {
        List<Map<String, Object>> diameters =
                new ArrayList<>(List.of(reading("0 ft", "1.376"), reading("1200 ft", "1.376")));
        diameters.get(0).put("across_crowns", "about an inch and a bit");
        Map<String, Object> body = surveyBody("2025-01-15", diameters);
        Http.Answer reply = client.postJson("/ropes/" + SERIAL + "/surveys", body, Wire.headers(Role.MECHANIC));
        assertThat(reply.status()).isEqualTo(422);
    }

    @Test
    void aWalkTakenBeforeTheRopeWentOnIsRefused() {
        Http.Answer reply = client.postJson(
                "/ropes/" + SERIAL + "/surveys", surveyBody("1.376", "2015-01-15"), Wire.headers(Role.MECHANIC));
        assertThat(reply.status()).isEqualTo(409);
        assertThat(reply.json().get("kind").asText()).isEqualTo("RopeworkError");
    }

    @Test
    void twoWalksOnOneDayAreRefusedByTheDatabase() {
        client.postJson("/ropes/" + SERIAL + "/surveys", surveyBody(), Wire.headers(Role.MECHANIC));
        Http.Answer reply = client.postJson("/ropes/" + SERIAL + "/surveys", surveyBody(), Wire.headers(Role.MECHANIC));
        assertThat(reply.status()).isGreaterThanOrEqualTo(400);
    }

    @Test
    void theRopeFileReadsTheLatestWalk() {
        assertThat(client.get("/ropes/" + SERIAL + "/file", Wire.headers()).json())
                .isEqualTo(
                        Http.tree(
                                """
                        {"serial": "FAT-2019-04412", "verdict": "serviceable", "may_run": true,
                         "sources": [], "findings": []}
                        """));
        client.postJson(
                "/ropes/" + SERIAL + "/surveys", surveyBody("1.270", "2025-01-15"), Wire.headers(Role.MECHANIC));
        JsonNode body = client.get("/ropes/" + SERIAL + "/file", Wire.headers()).json();
        assertThat(body.get("verdict").asText()).isEqualTo("condemned");
        assertThat(body.get("may_run")).isEqualTo(Http.FALSE);
        assertThat(body.get("sources")).isEqualTo(Http.tree("[\"diameter\"]"));
        assertThat(body.get("findings").get(0).get("source").asText()).isEqualTo("diameter");
    }

    @Test
    void theRopeFileReadsTheLatestBrokenWireCountToo() {
        HaulRope rope = Hill.johnPaul().rope();
        try (UnitOfWork work = UnitOfWork.on(built.database()).open()) {
            work.breakSurveys()
                    .add(BreakSurvey.of(
                            rope,
                            LocalDate.of(2024, 12, 1),
                            "R Halvorsen",
                            List.of(new BreakObservation(Length.ofFeet(2400), 7, 9))));
            work.breakSurveys()
                    .add(BreakSurvey.of(
                            rope,
                            LocalDate.of(2025, 1, 15),
                            "R Halvorsen",
                            List.of(new BreakObservation(Length.ofFeet(2400), 4, 7))));
            work.commit();
        }
        JsonNode body = client.get("/ropes/" + SERIAL + "/file", Wire.headers()).json();
        assertThat(body.get("verdict").asText()).isEqualTo("monitor");
        assertThat(body.get("may_run")).isEqualTo(Http.TRUE);
        assertThat(body.get("sources")).isEqualTo(Http.tree("[\"broken wires\"]"));
        assertThat(body.get("findings").get(0).get("detail").asText())
                .isEqualTo("4 in one lay at 2400 ft, 4 in one lay against 3, 7 in six lays against 6");
    }
}
