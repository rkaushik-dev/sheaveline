package io.github.rkaushikdev.sheaveline.api.routes;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.sheaveline.Hill;
import io.github.rkaushikdev.sheaveline.api.Http;
import io.github.rkaushikdev.sheaveline.api.Role;
import io.github.rkaushikdev.sheaveline.api.Settings;
import io.github.rkaushikdev.sheaveline.api.Wire;
import io.github.rkaushikdev.sheaveline.ops.CheckItem;
import io.github.rkaushikdev.sheaveline.ops.CheckOutcome;
import io.github.rkaushikdev.sheaveline.ops.CheckResult;
import io.github.rkaushikdev.sheaveline.ops.DayLog;
import io.github.rkaushikdev.sheaveline.ops.MeterReading;
import io.github.rkaushikdev.sheaveline.ops.PreOpSheet;
import io.github.rkaushikdev.sheaveline.persistence.UnitOfWork;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.github.rkaushikdev.sheaveline.schedule.OperatingWindow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
class RouteOpsTest {

    static final LocalDate ON = LocalDate.of(2025, 1, 15);
    static final OperatingWindow WINDOW = OperatingWindow.parse("09:00", "16:00", "16:30");
    static final String STOPPED = iso(LocalDateTime.of(2025, 1, 15, 11, 30));

    @Autowired
    private MockMvc mvc;

    private Http client;

    /** A moment as a client writes it, seconds and all. */
    static String iso(LocalDateTime moment) {
        return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(moment);
    }

    static PreOpSheet sheet(Lift machine) {
        List<CheckResult> results = new ArrayList<>();
        for (CheckItem item : PreOpSheet.STANDARD_SHEET) {
            if (item.appliesTo(machine)) {
                results.add(new CheckResult(item, CheckOutcome.PASS, "R Skaggs"));
            }
        }
        return new PreOpSheet(machine, ON, results, "R Skaggs", "D Chin");
    }

    @BeforeEach
    void writeUpTheMorning() {
        Settings built = Wire.settings();
        try (UnitOfWork work = UnitOfWork.on(built.database()).open()) {
            for (Lift machine : List.of(Hill.johnPaul(), Hill.strawberry())) {
                work.lifts().add(machine);
            }
            work.dayLogs().add(DayLog.start(Hill.johnPaul().code(), ON, WINDOW, sheet(Hill.johnPaul())));
            work.dayLogs()
                    .readMeter(new MeterReading(
                            Hill.johnPaul().code(), ON, new BigDecimal("40120.5"), 1_402_000, "R Skaggs"));
            work.commit();
        }
        client = new Http(mvc);
    }

    static Map<String, Object> stopBody() {
        return stopBody("operator stop");
    }

    static Map<String, Object> stopBody(String kind) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("kind", kind);
        body.put("stopped", STOPPED);
        body.put("stopped_by", "D Chin");
        body.put("note", "");
        return body;
    }

    static Map<String, Object> restartBody() {
        return restartBody(11, 45, false);
    }

    static Map<String, Object> restartBody(int hour, int minute, boolean lineChecked) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("stopped", STOPPED);
        body.put("restarted", iso(LocalDateTime.of(2025, 1, 15, hour, minute)));
        body.put("restarted_by", "D Chin");
        body.put("line_checked", lineChecked);
        return body;
    }

    @Test
    void oneDayComesBackWithTheAnswersWorkedOut() {
        Http.Answer reply = client.get("/lifts/JP/days/2025-01-15", Wire.headers());
        assertThat(reply.status()).isEqualTo(200);
        JsonNode body = reply.json();
        assertThat(body.get("sheet_walked")).isEqualTo(Http.TRUE);
        assertThat(body.get("may_open")).isEqualTo(Http.TRUE);
        assertThat(body.get("first_load").asText()).isEqualTo("9:00");
        assertThat(body.get("scheduled").asText()).isEqualTo("7:30:00");
        assertThat(body.get("meter").get("cycles").asLong()).isEqualTo(1_402_000);
    }

    @Test
    void aDayNobodyWroteUp() {
        Http.Answer reply = client.get("/lifts/JP/days/2025-01-16", Wire.headers());
        assertThat(reply.status()).isEqualTo(404);
    }

    @Test
    void aMachineThatIsNotOnTheRegister() {
        Http.Answer reply = client.get("/lifts/ZZ/days/2025-01-15", Wire.headers());
        assertThat(reply.status()).isEqualTo(404);
        assertThat(reply.json().get("detail").asText()).isEqualTo("there is no lift ZZ");
    }

    @Test
    void theDaysAMachineHasBeenWrittenUp() {
        assertThat(client.get("/lifts/JP/days", Wire.headers()).json().get("items"))
                .isEqualTo(Http.tree("[\"2025-01-15\"]"));
        assertThat(client.get("/lifts/SB/days", Wire.headers()).json().get("items"))
                .isEqualTo(Http.tree("[]"));
    }

    @Test
    void aStopGoesInAndSaysWhatFollowsFromItsKind() {
        Http.Answer reply = client.postJson("/lifts/JP/stops", stopBody(), Wire.headers(Role.OPERATOR));
        assertThat(reply.status()).isEqualTo(201);
        JsonNode body = reply.json();
        assertThat(body.get("open")).isEqualTo(Http.TRUE);
        assertThat(body.get("counts_against_the_machine")).isEqualTo(Http.TRUE);
        assertThat(body.get("reportable")).isEqualTo(Http.FALSE);
        assertThat(body.get("length")).isEqualTo(Http.NULL);
    }

    @Test
    void aPowerCutDoesNotCountAgainstTheMachine() {
        Http.Answer reply = client.postJson("/lifts/JP/stops", stopBody("power"), Wire.headers(Role.OPERATOR));
        assertThat(reply.json().get("counts_against_the_machine")).isEqualTo(Http.FALSE);
    }

    @Test
    void aReportableStopWillNotGoInWithoutAWriteUp() {
        Http.Answer reply = client.postJson("/lifts/JP/stops", stopBody("deropement"), Wire.headers(Role.OPERATOR));
        assertThat(reply.status()).isEqualTo(409);
        assertThat(reply.json().get("detail").asText()).contains("written up");
    }

    @Test
    void aReaderMayNotStopAMachine() {
        Http.Answer reply = client.postJson("/lifts/JP/stops", stopBody(), Wire.headers());
        assertThat(reply.status()).isEqualTo(401);
    }

    @Test
    void twoStopsCannotStartAtTheSameMoment() {
        client.postJson("/lifts/JP/stops", stopBody(), Wire.headers(Role.OPERATOR));
        Http.Answer again = client.postJson("/lifts/JP/stops", stopBody(), Wire.headers(Role.OPERATOR));
        assertThat(again.status()).isEqualTo(404);
        assertThat(again.json().get("detail").asText()).contains("already stopped");
    }

    @Test
    void aRestartClosesTheStopAndMeasuresIt() {
        client.postJson("/lifts/JP/stops", stopBody(), Wire.headers(Role.OPERATOR));
        Http.Answer reply = client.postJson("/lifts/JP/stops/restart", restartBody(), Wire.headers(Role.OPERATOR));
        assertThat(reply.status()).isEqualTo(200);
        assertThat(reply.json().get("length").asText()).isEqualTo("0:15:00");
        assertThat(reply.json().get("open")).isEqualTo(Http.FALSE);
    }

    @Test
    void aLongStopWillNotRestartUntilTheLineIsWalked() {
        client.postJson("/lifts/JP/stops", stopBody(), Wire.headers(Role.OPERATOR));
        Http.Answer refused =
                client.postJson("/lifts/JP/stops/restart", restartBody(12, 30, false), Wire.headers(Role.OPERATOR));
        assertThat(refused.status()).isEqualTo(409);
        assertThat(refused.json().get("detail").asText()).contains("line check");
        Http.Answer walked =
                client.postJson("/lifts/JP/stops/restart", restartBody(12, 30, true), Wire.headers(Role.OPERATOR));
        assertThat(walked.status()).isEqualTo(200);
    }

    @Test
    void restartingAStopNobodyCalled() {
        Http.Answer reply = client.postJson("/lifts/JP/stops/restart", restartBody(), Wire.headers(Role.OPERATOR));
        assertThat(reply.status()).isEqualTo(404);
    }

    @Test
    void theStopsOnADayAreTotalled() {
        client.postJson("/lifts/JP/stops", stopBody(), Wire.headers(Role.OPERATOR));
        client.postJson("/lifts/JP/stops/restart", restartBody(), Wire.headers(Role.OPERATOR));
        Http.Answer reply = client.get("/lifts/JP/days/2025-01-15/stops", Wire.headers());
        assertThat(reply.status()).isEqualTo(200);
        assertThat(reply.json().get("downtime").asText()).isEqualTo("0:15:00");
        assertThat(reply.json().get("chargeable").asText()).isEqualTo("0:15:00");
    }

    @Test
    void whatIsStoppedRightNow() {
        assertThat(client.get("/stops/open", Wire.headers()).json()).isEqualTo(Http.tree("[]"));
        client.postJson("/lifts/JP/stops", stopBody(), Wire.headers(Role.OPERATOR));
        Http.Answer reply = client.get("/stops/open", Wire.headers());
        assertThat(Http.texts(reply.json(), "lift")).containsExactly("JP");
        assertThat(client.get("/stops/open?lift=SB", Wire.headers()).json()).isEqualTo(Http.tree("[]"));
    }

    @Test
    void theMeterHistoryIsAMechanicsKey() {
        Http.Answer plain = client.get("/lifts/JP/meters", Wire.headers());
        assertThat(plain.status()).isEqualTo(401);
        Http.Answer reply = client.get("/lifts/JP/meters", Wire.headers(Role.MECHANIC));
        assertThat(reply.status()).isEqualTo(200);
        assertThat(reply.json().get("items").get(0).get("cycles").asLong()).isEqualTo(1_402_000);
        Http.Answer empty = client.get("/lifts/SB/meters", Wire.headers(Role.MECHANIC));
        assertThat(empty.json().get("items")).isEqualTo(Http.tree("[]"));
        assertThat(empty.json().get("total").asInt()).isZero();
    }
}
