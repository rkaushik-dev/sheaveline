package io.github.rkaushikdev.sheaveline.api.routes;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.sheaveline.Hill;
import io.github.rkaushikdev.sheaveline.Sheaveline;
import io.github.rkaushikdev.sheaveline.api.Http;
import io.github.rkaushikdev.sheaveline.api.Role;
import io.github.rkaushikdev.sheaveline.api.Settings;
import io.github.rkaushikdev.sheaveline.api.Wire;
import io.github.rkaushikdev.sheaveline.persistence.Database;
import io.github.rkaushikdev.sheaveline.persistence.UnitOfWork;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@Import(Wire.Service.class)
class RouteServiceTest {

    /** A database that is not there, and that nothing may create by connecting to it. */
    static final String NOWHERE = "jdbc:h2:file:/nowhere/at/all;IFEXISTS=TRUE";

    @Autowired
    private MockMvc mvc;

    private Http client;

    @BeforeEach
    void putAMachineOnFile() {
        Settings built = Wire.settings();
        try (UnitOfWork work = UnitOfWork.on(built.database()).open()) {
            work.lifts().add(Hill.johnPaul());
            work.commit();
        }
        client = new Http(mvc);
    }

    /** Settings over a database of the test's own, with the suite's keys and its today. */
    static Settings over(Database database) {
        return Settings.of(database).withKeys(Wire.RING).withToday(() -> Wire.TODAY);
    }

    static Map<String, Object> team() {
        return team("alpha", 24, 200);
    }

    static Map<String, Object> team(String name) {
        return team(name, 24, 200);
    }

    static Map<String, Object> team(String name, Integer ladder, Integer rope) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", name);
        body.put("ladder_feet", ladder);
        body.put("rope_feet", rope);
        return body;
    }

    static Map<String, Object> plan(List<Map<String, Object>> teams) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("teams", teams);
        return body;
    }

    @Test
    void healthAnswersWithoutAKey() {
        Http.Answer reply = client.get("/health");
        assertThat(reply.status()).isEqualTo(200);
        assertThat(reply.json().get("service").asText()).isEqualTo("sheaveline");
        assertThat(reply.json().get("version").asText()).isEqualTo(Sheaveline.VERSION);
    }

    @Test
    void healthCarriesTheResortItWasBuiltFor() {
        try (Wire.Running app = Wire.freshClient(Wire.settings().withResort("Snowbasin"))) {
            assertThat(app.client().get("/health").json().get("resort").asText())
                    .isEqualTo("Snowbasin");
        }
    }

    /** A health check that opens a connection takes the service down when the disk is slow. */
    @Test
    void healthDoesNotTouchTheDatabase() {
        try (Database nowhere = Database.open(NOWHERE);
                Wire.Running app = Wire.freshClient(over(nowhere))) {
            assertThat(app.client().get("/health").status()).isEqualTo(200);
        }
    }

    @Test
    void readySaysWhatItFound() {
        Http.Answer reply = client.get("/ready");
        assertThat(reply.status()).isEqualTo(200);
        JsonNode body = reply.json();
        assertThat(body.get("ready")).isEqualTo(Http.TRUE);
        assertThat(body.get("tables").asInt()).isEqualTo(Database.tableNames().size());
        assertThat(body.get("missing")).isEqualTo(Http.tree("[]"));
        assertThat(body.get("lifts").asInt()).isEqualTo(1);
        assertThat(body.get("today").asText()).isEqualTo(Wire.TODAY.toString());
        assertThat(body.get("detail").asText()).isEqualTo("ready");
    }

    @Test
    void readyNeedsNoKeyEither() {
        assertThat(client.get("/ready").status()).isEqualTo(200);
    }

    @Test
    void aDatabaseWithNoTablesIsNotReady() {
        Http.Answer reply;
        try (Database empty = Database.inMemory();
                Wire.Running app = Wire.freshClient(over(empty))) {
            reply = app.client().get("/ready");
        }
        assertThat(reply.status()).isEqualTo(503);
        JsonNode body = reply.json();
        assertThat(body.get("ready")).isEqualTo(Http.FALSE);
        assertThat(body.get("missing")).isNotEmpty();
        assertThat(body.get("detail").asText()).contains("tables are not built yet");
    }

    @Test
    void theTwoChecksAnswerDifferentlyOnTheSameBrokenDatabase() {
        try (Database empty = Database.inMemory();
                Wire.Running app = Wire.freshClient(over(empty))) {
            assertThat(app.client().get("/health").status()).isEqualTo(200);
            assertThat(app.client().get("/ready").status()).isEqualTo(503);
        }
    }

    @Test
    void aStagingPlanComesBackAsABeatSheet() {
        Map<String, Object> body = plan(List.of(team("alpha"), team("bravo")));
        body.put("seats_filled", 3);
        Http.Answer reply = client.postJson("/lifts/JP/evacuation", body, Wire.headers(Role.OPERATOR));
        assertThat(reply.status()).isEqualTo(200);
        JsonNode sheet = reply.json();
        assertThat(sheet.get("workable")).isEqualTo(Http.TRUE);
        assertThat(sheet.get("carriers").asInt()).isEqualTo(84);
        assertThat(sheet.get("beats")).hasSize(2);
        assertThat(StreamSupport.stream(sheet.get("beats").spliterator(), false)
                        .mapToInt(one -> one.get("carriers").asInt())
                        .sum())
                .isEqualTo(84);
        assertThat(sheet.get("clears_in")).isNotEqualTo(Http.NULL);
    }

    @Test
    void aSecondTeamCutsTheClearingTime() {
        assertThat(clears(1)).isGreaterThan(clears(2));
    }

    String clears(int count) {
        List<Map<String, Object>> teams = new ArrayList<>();
        IntStream.range(0, count).forEach(one -> teams.add(team("team" + one)));
        return client.postJson("/lifts/JP/evacuation", plan(teams), Wire.headers(Role.OPERATOR))
                .json()
                .get("clears_in")
                .asText();
    }

    @Test
    void aTeamThatCannotReachTheLineIsRefusedRatherThanHidden() {
        Http.Answer reply = client.postJson(
                "/lifts/JP/evacuation", plan(List.of(team("short", 8, null))), Wire.headers(Role.OPERATOR));
        assertThat(reply.status()).isEqualTo(200);
        JsonNode body = reply.json();
        assertThat(body.get("workable")).isEqualTo(Http.FALSE);
        assertThat(body.get("stranded").asInt()).isPositive();
        assertThat(body.get("clears_in")).isEqualTo(Http.NULL);
        assertThat(StreamSupport.stream(body.get("refusals").spliterator(), false)
                        .map(JsonNode::asText))
                .anyMatch(one -> one.contains("nobody reaches it"));
    }

    @Test
    void aReaderMayNotAskForAPlan() {
        Http.Answer reply = client.postJson("/lifts/JP/evacuation", plan(List.of(team())), Wire.headers());
        assertThat(reply.status()).isEqualTo(401);
    }

    @Test
    void aPlanWithNoTeamsInIt() {
        Http.Answer reply = client.postJson("/lifts/JP/evacuation", plan(List.of()), Wire.headers(Role.OPERATOR));
        assertThat(reply.status()).isEqualTo(422);
    }

    @Test
    void aMachineThatIsNotOnTheRegister() {
        Http.Answer reply = client.postJson("/lifts/ZZ/evacuation", plan(List.of(team())), Wire.headers(Role.OPERATOR));
        assertThat(reply.status()).isEqualTo(404);
    }
}
