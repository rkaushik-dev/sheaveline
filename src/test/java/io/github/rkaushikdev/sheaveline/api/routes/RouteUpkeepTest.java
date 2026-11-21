package io.github.rkaushikdev.sheaveline.api.routes;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.sheaveline.Hill;
import io.github.rkaushikdev.sheaveline.api.Http;
import io.github.rkaushikdev.sheaveline.api.Role;
import io.github.rkaushikdev.sheaveline.api.Settings;
import io.github.rkaushikdev.sheaveline.api.Wire;
import io.github.rkaushikdev.sheaveline.persistence.UnitOfWork;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
class RouteUpkeepTest {

    @Autowired
    private MockMvc mvc;

    private Http client;

    @BeforeEach
    void putTheMachinesOnFile() {
        Settings built = Wire.settings();
        try (UnitOfWork work = UnitOfWork.on(built.database()).open()) {
            work.lifts().add(Hill.johnPaul());
            work.lifts().add(Hill.strawberry());
            work.commit();
        }
        client = new Http(mvc);
    }

    static Map<String, Object> inspection() {
        return inspection("W1", "2025-01-15", List.of());
    }

    static Map<String, Object> inspection(String code) {
        return inspection(code, "2025-01-15", List.of());
    }

    static Map<String, Object> inspection(String code, String done, List<Map<String, Object>> defects) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("done", done);
        body.put("by", "M Vetter");
        body.put("defects", defects);
        return body;
    }

    static Map<String, Object> defect() {
        return defect("JP-T4", "sheave liner cracked");
    }

    static Map<String, Object> defect(String where, String detail) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("where", where);
        body.put("level", "defect");
        body.put("detail", detail);
        return body;
    }

    static Map<String, Object> orderBody() {
        return orderBody("defect", "2025-01-15");
    }

    static Map<String, Object> orderBody(String level, String raised) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("where", "JP-T4");
        body.put("detail", "sheave liner cracked");
        body.put("level", level);
        body.put("raised", raised);
        body.put("raised_by", "M Vetter");
        return body;
    }

    static Map<String, Object> closure() {
        return Map.of("closed", "2025-01-20", "closed_by", "M Vetter");
    }

    @Test
    void aMechanicSignsOffACleanWalk() {
        Http.Answer reply = client.postJson("/lifts/JP/inspections", inspection(), Wire.headers(Role.MECHANIC));
        assertThat(reply.status()).isEqualTo(201);
        JsonNode body = reply.json();
        assertThat(body.get("code").asText()).isEqualTo("W1");
        assertThat(body.get("clean")).isEqualTo(Http.TRUE);
        assertThat(body.get("defects")).isEqualTo(Http.tree("[]"));
    }

    @Test
    void aWalkThatFoundSomethingComesBackWithIt() {
        JsonNode body = client.postJson(
                        "/lifts/JP/inspections",
                        inspection("W1", "2025-01-15", List.of(defect())),
                        Wire.headers(Role.MECHANIC))
                .json();
        assertThat(body.get("clean")).isEqualTo(Http.FALSE);
        assertThat(body.get("defects").get(0).get("where").asText()).isEqualTo("JP-T4");
        assertThat(body.get("defects").get(0).get("open")).isEqualTo(Http.TRUE);
    }

    @Test
    void aJobThatRunsOnTheMeterNeedsAReading() {
        Http.Answer reply = client.postJson("/lifts/JP/inspections", inspection("H1"), Wire.headers(Role.MECHANIC));
        assertThat(reply.status()).isEqualTo(409);
        assertThat(reply.json().get("kind").asText()).isEqualTo("UpkeepError");
    }

    @Test
    void aJobThatIsNotInTheLibraryIsRefused() {
        Http.Answer reply = client.postJson("/lifts/JP/inspections", inspection("Z9"), Wire.headers(Role.MECHANIC));
        assertThat(reply.status()).isEqualTo(409);
    }

    @Test
    void aReaderKeyMayNotSignOffAWalk() {
        Http.Answer reply = client.postJson("/lifts/JP/inspections", inspection(), Wire.headers(Role.READER));
        assertThat(reply.status()).isEqualTo(401);
    }

    @Test
    void theRecordsOnOneMachineComeBackInOrder() {
        for (String day : List.of("2025-01-08", "2025-01-15")) {
            client.postJson("/lifts/JP/inspections", inspection("W1", day, List.of()), Wire.headers(Role.MECHANIC));
        }
        client.postJson("/lifts/JP/inspections", inspection("M1"), Wire.headers(Role.MECHANIC));
        JsonNode body =
                client.get("/lifts/JP/inspections", Wire.headers()).json().get("items");
        assertThat(body).hasSize(3);
        assertThat(Http.texts(body, "done").subList(0, 2)).containsExactly("2025-01-08", "2025-01-15");
    }

    @Test
    void theRecordsCanBeNarrowedToOneJob() {
        client.postJson("/lifts/JP/inspections", inspection(), Wire.headers(Role.MECHANIC));
        client.postJson("/lifts/JP/inspections", inspection("M1"), Wire.headers(Role.MECHANIC));
        JsonNode body = client.get("/lifts/JP/inspections?job=m1", Wire.headers())
                .json()
                .get("items");
        assertThat(Http.texts(body, "code")).containsExactly("M1");
    }

    @Test
    void theOpenDefectsOnAMachineComeBack() {
        client.postJson(
                "/lifts/JP/inspections",
                inspection("W1", "2025-01-15", List.of(defect(), defect("JP-T7", "bolt loose"))),
                Wire.headers(Role.MECHANIC));
        JsonNode body = client.get("/lifts/JP/defects", Wire.headers()).json();
        assertThat(Http.texts(body, "where")).containsExactlyInAnyOrder("JP-T4", "JP-T7");
        assertThat(StreamSupport.stream(body.spliterator(), false).map(one -> one.get("open")))
                .allMatch(Http.TRUE::equals);
    }

    @Test
    void defectsStayWithTheMachineTheyWereFoundOn() {
        client.postJson(
                "/lifts/JP/inspections",
                inspection("W1", "2025-01-15", List.of(defect())),
                Wire.headers(Role.MECHANIC));
        assertThat(client.get("/lifts/SB/defects", Wire.headers()).json()).isEqualTo(Http.tree("[]"));
    }

    @Test
    void anOrderTakesItsPriorityFromTheDefectAndNotTheRequest() {
        JsonNode body = client.postJson(
                        "/lifts/JP/work-orders", orderBody("serious", "2025-01-15"), Wire.headers(Role.MECHANIC))
                .json();
        assertThat(body.get("priority").asText()).isEqualTo("immediate");
        assertThat(body.get("due_by").asText()).isEqualTo("2025-01-15");
        assertThat(body.get("holds_the_machine")).isEqualTo(Http.TRUE);
    }

    @Test
    void anOrdinaryDefectGetsAMonth() {
        JsonNode body = client.postJson("/lifts/JP/work-orders", orderBody(), Wire.headers(Role.MECHANIC))
                .json();
        assertThat(body.get("priority").asText()).isEqualTo("scheduled");
        assertThat(body.get("due_by").asText()).isEqualTo("2025-02-14");
        assertThat(body.get("holds_the_machine")).isEqualTo(Http.FALSE);
    }

    @Test
    void ordersAreNumberedFromOneInSequence() {
        List<Integer> numbers = new ArrayList<>();
        for (int times = 0; times < 3; times++) {
            numbers.add(client.postJson("/lifts/JP/work-orders", orderBody(), Wire.headers(Role.MECHANIC))
                    .json()
                    .get("number")
                    .asInt());
        }
        assertThat(numbers).containsExactly(1, 2, 3);
    }

    @Test
    void theOpenOrdersCanBeNarrowedByMachineAndByPriority() {
        client.postJson("/lifts/JP/work-orders", orderBody(), Wire.headers(Role.MECHANIC));
        Map<String, Object> serious = orderBody("serious", "2025-01-15");
        serious.put("where", "SB-T2");
        client.postJson("/lifts/SB/work-orders", serious, Wire.headers(Role.MECHANIC));
        JsonNode every = client.get("/work-orders", Wire.headers()).json();
        assertThat(every.get("total").asInt()).isEqualTo(2);
        assertThat(every.get("next_offset")).isEqualTo(Http.NULL);
        JsonNode oneLift = client.get("/work-orders?lift=JP", Wire.headers()).json();
        assertThat(oneLift.get("total").asInt()).isEqualTo(1);
        JsonNode urgent =
                client.get("/work-orders?priority=immediate", Wire.headers()).json();
        assertThat(Http.texts(urgent.get("items"), "lift")).containsExactly("SB");
    }

    @Test
    void oneOrderComesBackByNumber() {
        client.postJson("/lifts/JP/work-orders", orderBody(), Wire.headers(Role.MECHANIC));
        JsonNode body = client.get("/work-orders/1", Wire.headers()).json();
        assertThat(body.get("detail").asText()).isEqualTo("sheave liner cracked");
        assertThat(client.get("/work-orders/99", Wire.headers()).status()).isEqualTo(404);
    }

    @Test
    void aManagerClosesAnOrder() {
        client.postJson("/lifts/JP/work-orders", orderBody(), Wire.headers(Role.MECHANIC));
        Http.Answer reply = client.postJson("/work-orders/1/closure", closure(), Wire.headers(Role.MANAGER));
        assertThat(reply.status()).isEqualTo(200);
        assertThat(reply.json().get("open")).isEqualTo(Http.FALSE);
        assertThat(client.get("/work-orders", Wire.headers()).json().get("items"))
                .isEqualTo(Http.tree("[]"));
    }

    @Test
    void aMechanicKeyMayNotCloseAnOrder() {
        client.postJson("/lifts/JP/work-orders", orderBody(), Wire.headers(Role.MECHANIC));
        Http.Answer reply = client.postJson("/work-orders/1/closure", closure(), Wire.headers(Role.MECHANIC));
        assertThat(reply.status()).isEqualTo(401);
    }

    @Test
    void closingAnOrderThatIsNotThere() {
        Http.Answer reply = client.postJson("/work-orders/99/closure", closure(), Wire.headers(Role.MANAGER));
        assertThat(reply.status()).isEqualTo(404);
    }

    @Test
    void theOverdueListReadsTheDayTheServiceThinksItIs() {
        client.postJson("/lifts/JP/work-orders", orderBody("serious", "2025-01-10"), Wire.headers(Role.MECHANIC));
        client.postJson("/lifts/JP/work-orders", orderBody(), Wire.headers(Role.MECHANIC));
        JsonNode body = client.get("/work-orders/overdue", Wire.headers()).json();
        assertThat(Http.texts(body, "priority")).containsExactly("immediate");
    }

    @Test
    void anOrderAgainstAMachineThatIsNotThere() {
        Http.Answer reply = client.postJson("/lifts/ZZ/work-orders", orderBody(), Wire.headers(Role.MECHANIC));
        assertThat(reply.status()).isEqualTo(404);
    }
}
