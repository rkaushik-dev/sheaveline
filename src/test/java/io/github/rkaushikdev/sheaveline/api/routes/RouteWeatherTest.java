package io.github.rkaushikdev.sheaveline.api.routes;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.sheaveline.Hill;
import io.github.rkaushikdev.sheaveline.api.Http;
import io.github.rkaushikdev.sheaveline.api.Role;
import io.github.rkaushikdev.sheaveline.api.Settings;
import io.github.rkaushikdev.sheaveline.api.Wire;
import io.github.rkaushikdev.sheaveline.persistence.UnitOfWork;
import io.github.rkaushikdev.sheaveline.weather.HoldReason;
import io.github.rkaushikdev.sheaveline.weather.HoldRecord;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@Import(Wire.Service.class)
class RouteWeatherTest {

    static final String NOON = "2025-01-15T12:00:00";

    @Autowired
    private MockMvc mvc;

    private Http client;

    private Settings built;

    @BeforeEach
    void putTheMachinesAndTheStationsOnFile() {
        built = Wire.settings();
        try (UnitOfWork work = UnitOfWork.on(built.database()).open()) {
            work.lifts().add(Hill.johnPaul());
            work.lifts().add(Hill.strawberry());
            work.lifts().add(Hill.wildcat());
            work.commit();
        }
        client = new Http(mvc);
        stations();
    }

    void stations() {
        Map<String, String> stations = new LinkedHashMap<>();
        stations.put("summit", "7981 ft");
        stations.put("mid", "7100 ft");
        stations.put("base", "6400 ft");
        stations.forEach((name, feet) -> client.postJson(
                "/weather/stations", Map.of("name", name, "elevation", feet), Wire.headers(Role.MANAGER)));
    }

    /** A quiet reading at the summit at noon. */
    static Reading reading() {
        return new Reading();
    }

    /** A reading body, changed field by field from a quiet one at the summit at noon. */
    static final class Reading {

        private final Map<String, Object> body = new LinkedHashMap<>();

        Reading() {
            body.put("station", "summit");
            body.put("taken", NOON);
            body.put("wind", "18 mph");
            body.put("bearing", "270");
            body.put("temperature", "8 F");
        }

        Reading with(String field, Object value) {
            body.put(field, value);
            return this;
        }

        Map<String, Object> body() {
            return body;
        }
    }

    Http.Answer record(Reading reading) {
        return client.postJson("/weather/observations", reading.body(), Wire.headers(Role.OPERATOR));
    }

    JsonNode hold(String code) {
        return client.get("/lifts/" + code + "/hold?now=" + NOON, Wire.headers())
                .json();
    }

    @Test
    void aManagerSetsUpAStation() {
        Http.Answer reply = client.postJson(
                "/weather/stations", Map.of("name", "cirque", "elevation", "8200 ft"), Wire.headers(Role.MANAGER));
        assertThat(reply.status()).isEqualTo(201);
        assertThat(reply.json().get("elevation").asText()).isEqualTo("8200.00 ft");
    }

    @Test
    void aStationGoesInOnce() {
        Http.Answer reply = client.postJson(
                "/weather/stations", Map.of("name", "summit", "elevation", "7981 ft"), Wire.headers(Role.MANAGER));
        assertThat(reply.status()).isEqualTo(404);
    }

    @Test
    void anOperatorKeyMayNotSetUpAStation() {
        Http.Answer reply = client.postJson(
                "/weather/stations", Map.of("name", "cirque", "elevation", "8200 ft"), Wire.headers(Role.OPERATOR));
        assertThat(reply.status()).isEqualTo(401);
    }

    @Test
    void theStationsComeBackInNameOrder() {
        JsonNode body = client.get("/weather/stations", Wire.headers()).json();
        assertThat(Http.texts(body, "name")).containsExactly("base", "mid", "summit");
    }

    @Test
    void anOperatorRecordsAReading() {
        Http.Answer reply =
                record(reading().with("gust", "38 mph").with("wind", "24 mph").with("bearing", "SW"));
        assertThat(reply.status()).isEqualTo(201);
        JsonNode body = reply.json();
        assertThat(body.get("wind").asText()).isEqualTo("24 mph");
        assertThat(body.get("gust").asText()).isEqualTo("38 mph");
        assertThat(body.get("peak").asText()).isEqualTo("38 mph");
        assertThat(body.get("bearing").asText()).isEqualTo("225 deg");
    }

    @Test
    void theChillComesBackWithTheReading() {
        JsonNode body = record(reading().with("wind", "20 mph").with("temperature", "5 F"))
                .json();
        assertThat(body.get("chill").asText()).isEqualTo("-15.4 F");
    }

    @Test
    void aReadingAgainstAStationThatIsNotThere() {
        Http.Answer reply = record(reading().with("station", "cirque"));
        assertThat(reply.status()).isEqualTo(404);
    }

    @Test
    void aGustBelowTheSustainedWindIsRefused() {
        Http.Answer reply = record(reading().with("wind", "30 mph").with("gust", "20 mph"));
        assertThat(reply.status()).isEqualTo(400);
    }

    @Test
    void theReadingsComeBackAndCanBeNarrowed() {
        record(reading());
        record(reading().with("station", "base").with("temperature", "24 F"));
        JsonNode every = client.get("/weather/observations", Wire.headers()).json();
        assertThat(every.get("total").asInt()).isEqualTo(2);
        JsonNode summit = client.get("/weather/observations?station=summit", Wire.headers())
                .json();
        assertThat(Http.texts(summit.get("items"), "station")).containsExactly("summit");
    }

    @Test
    void readingsCanBeNarrowedByTime() {
        for (String minute : new String[] {"05", "35"}) {
            record(reading().with("taken", "2025-01-15T12:" + minute + ":00"));
        }
        JsonNode body = client.get("/weather/observations?since=2025-01-15T12:20:00", Wire.headers())
                .json();
        assertThat(Http.texts(body.get("items"), "taken")).containsExactly("2025-01-15T12:35:00");
    }

    @Test
    void aQuietReadingLeavesTheMachineClear() {
        record(reading());
        JsonNode body = hold("JP");
        assertThat(body.get("held")).isEqualTo(Http.FALSE);
        assertThat(body.get("may_run")).isEqualTo(Http.TRUE);
        assertThat(body.get("reasons")).isEqualTo(Http.tree("[]"));
        assertThat(body.get("reading").get("station").asText()).isEqualTo("summit");
        assertThat(body.get("reading_age").asText()).isEqualTo("0:00:00");
    }

    @Test
    void theHoldReadsTheStationNearestTheTopTerminal() {
        record(reading().with("station", "summit").with("wind", "60 mph").with("bearing", "SSE"));
        record(reading().with("station", "base").with("wind", "2 mph"));
        JsonNode body = hold("JP");
        assertThat(body.get("reading").get("station").asText()).isEqualTo("summit");
        assertThat(body.get("held")).isEqualTo(Http.TRUE);
    }

    @Test
    void theHoldNamesWhichWeatherStoppedIt() {
        record(reading().with("wind", "30 mph").with("gust", "60 mph").with("bearing", "SSE"));
        JsonNode body = hold("JP");
        assertThat(Http.texts(body.get("reasons"), "reason")).contains("gust", "crosswind");
        assertThat(body.get("gust_limit").asText()).isEqualTo("45 mph");
    }

    @Test
    void aGustCloseToTheLimitRunsTheMachineSlowAndSaysWhy() {
        record(reading().with("wind", "20 mph").with("gust", "43 mph"));
        JsonNode body = hold("JP");
        assertThat(body.get("held")).isEqualTo(Http.FALSE);
        assertThat(body.get("run_slow")).isEqualTo(Http.TRUE);
        assertThat(body.get("slow_speed").asText()).isEqualTo("700 fpm");
        assertThat(Http.texts(body.get("slowing"), "reason")).containsExactly("gust");
        assertThat(body.get("slowing").get(0).get("detail").asText()).isEqualTo("43 mph against a limit of 45 mph");
    }

    @Test
    void aQuietReadingRunsAtFullSpeed() {
        record(reading());
        JsonNode body = hold("JP");
        assertThat(body.get("run_slow")).isEqualTo(Http.FALSE);
        assertThat(body.get("slow_speed")).isEqualTo(Http.NULL);
        assertThat(body.get("slowing")).isEqualTo(Http.tree("[]"));
    }

    @Test
    void aLightningHoldSaysItRunsOnAClock() {
        record(reading().with("lightning_within", "20000 ft"));
        JsonNode body = hold("JP");
        assertThat(body.get("needs_a_timer")).isEqualTo(Http.TRUE);
        JsonNode bolt = StreamSupport.stream(body.get("reasons").spliterator(), false)
                .filter(one -> one.get("reason").asText().equals("lightning"))
                .findFirst()
                .orElseThrow();
        assertThat(bolt.get("clears_on_reading")).isEqualTo(Http.FALSE);
    }

    void called(HoldReason reason, String started) {
        try (UnitOfWork work = UnitOfWork.on(built.database()).open()) {
            work.dayLogs().callHold(new HoldRecord(Hill.JOHN_PAUL, reason, LocalDateTime.parse(started), "K Ostler"));
            work.commit();
        }
    }

    @Test
    void anOpenLightningHoldRunsFromTheLastStrikeInTheLog() {
        called(HoldReason.LIGHTNING, "2025-01-15T11:00:00");
        record(reading().with("taken", "2025-01-15T11:20:00").with("lightning_within", "12000 ft"));
        record(reading().with("taken", "2025-01-15T11:45:00"));
        JsonNode open = client.get("/lifts/JP/hold?now=2025-01-15T11:46:00", Wire.headers())
                .json()
                .get("open_holds");
        assertThat(open).hasSize(1);
        assertThat(open.get(0).get("reason").asText()).isEqualTo("lightning");
        assertThat(open.get(0).get("may_clear")).isEqualTo(Http.FALSE);
        assertThat(open.get(0).get("clears_at").asText()).isEqualTo("2025-01-15T11:50:00");
        JsonNode later = client.get("/lifts/JP/hold?now=2025-01-15T11:55:00", Wire.headers())
                .json()
                .get("open_holds");
        assertThat(later.get(0).get("may_clear")).isEqualTo(Http.TRUE);
    }

    @Test
    void anOpenGustHoldWaitsForTheMarginOnTheRoute() {
        called(HoldReason.GUST, "2025-01-15T11:00:00");
        record(reading().with("wind", "20 mph").with("gust", "43 mph"));
        JsonNode body = hold("JP");
        assertThat(body.get("held")).isEqualTo(Http.FALSE);
        JsonNode gust = body.get("open_holds").get(0);
        assertThat(gust.get("reason").asText()).isEqualTo("gust");
        assertThat(gust.get("may_clear")).isEqualTo(Http.FALSE);
        assertThat(gust.get("clears_at")).isEqualTo(Http.NULL);
        assertThat(body.get("open_holds")).hasSize(1);
    }

    @Test
    void aSurfaceTowReadsTheBaseAndHasNoCrosswindLimit() {
        record(reading());
        record(reading().with("station", "base").with("temperature", "24 F"));
        JsonNode body = hold("WC");
        assertThat(body.get("reading").get("station").asText()).isEqualTo("base");
        assertThat(body.get("crosswind_limit")).isEqualTo(Http.NULL);
    }

    @Test
    void aStaleReadingIsRefusedRatherThanAnsweredFrom() {
        record(reading().with("taken", "2025-01-15T11:00:00"));
        Http.Answer reply = client.get("/lifts/JP/hold?now=" + NOON, Wire.headers());
        assertThat(reply.status()).isEqualTo(404);
        assertThat(reply.json().get("detail").asText()).contains("stale");
    }

    @Test
    void theStalenessWindowCanBeWidenedByTheCaller() {
        record(reading().with("taken", "2025-01-15T11:00:00"));
        Http.Answer reply = client.get("/lifts/JP/hold?now=" + NOON + "&stale_after_minutes=120", Wire.headers());
        assertThat(reply.status()).isEqualTo(200);
        assertThat(reply.json().get("reading_age").asText()).isEqualTo("1:00:00");
    }

    @Test
    void aMachineWithNoReadingAnywhereIsAFourOhFour() {
        Http.Answer reply = client.get("/lifts/JP/hold?now=" + NOON, Wire.headers());
        assertThat(reply.status()).isEqualTo(404);
    }
}
