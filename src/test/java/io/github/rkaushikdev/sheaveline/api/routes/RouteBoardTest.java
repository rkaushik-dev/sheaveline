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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@Import(Wire.Service.class)
class RouteBoardTest {

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

    static Map<String, Object> deropement() {
        return deropement("2025-01-15T11:30:00");
    }

    static Map<String, Object> deropement(String at) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("at", at);
        body.put("kind", "deropement");
        body.put("injury", "none");
        body.put("people", 0);
        body.put("narrative", "chair 34 off the sheave train at T4");
        body.put("reported_by", "K Ostler");
        return body;
    }

    static Map<String, Object> sprain() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("at", "2025-01-15T10:00:00");
        body.put("kind", "loading");
        body.put("injury", "first aid");
        body.put("people", 1);
        body.put("narrative", "pole caught on the ramp");
        body.put("reported_by", "K Ostler");
        return body;
    }

    static Map<String, Object> filing(String filed) {
        return Map.of("filed", filed, "filed_by", "H Okafor");
    }

    int recorded(Map<String, Object> body) {
        return client.postJson("/lifts/JP/incidents", body, Wire.headers(Role.OPERATOR))
                .json()
                .get("id")
                .asInt();
    }

    @Test
    void anOperatorRecordsAnIncidentAndGetsTheDeadlineBack() {
        Http.Answer reply = client.postJson("/lifts/JP/incidents", deropement(), Wire.headers(Role.OPERATOR));
        assertThat(reply.status()).isEqualTo(201);
        JsonNode body = reply.json();
        assertThat(body.get("kind").asText()).isEqualTo("deropement");
        assertThat(body.get("deadline").asText()).isEqualTo("2:00:00");
        assertThat(body.get("deadline_driver").asText()).isEqualTo("kind");
        assertThat(body.get("due_by").asText()).isEqualTo("2025-01-15T13:30:00");
        assertThat(body.get("holds_the_machine")).isEqualTo(Http.TRUE);
        assertThat(body.get("filing")).isEqualTo(Http.NULL);
    }

    @Test
    void aGentleIncidentGoesInTheMonthlyReturn() {
        JsonNode body = client.postJson("/lifts/JP/incidents", sprain(), Wire.headers(Role.OPERATOR))
                .json();
        assertThat(body.get("deadline_driver").asText()).isEqualTo("both");
        assertThat(body.get("holds_the_machine")).isEqualTo(Http.FALSE);
    }

    @Test
    void aReaderKeyMayNotRecordAnIncident() {
        Http.Answer reply = client.postJson("/lifts/JP/incidents", deropement(), Wire.headers(Role.READER));
        assertThat(reply.status()).isEqualTo(401);
    }

    @Test
    void anIncidentAgainstAMachineThatIsNotThere() {
        Http.Answer reply = client.postJson("/lifts/ZZ/incidents", deropement(), Wire.headers(Role.OPERATOR));
        assertThat(reply.status()).isEqualTo(404);
    }

    @Test
    void somebodyHurtWithNobodyInvolvedIsRefused() {
        Map<String, Object> body = sprain();
        body.put("people", 0);
        Http.Answer reply = client.postJson("/lifts/JP/incidents", body, Wire.headers(Role.OPERATOR));
        assertThat(reply.status()).isEqualTo(409);
        assertThat(reply.json().get("kind").asText()).isEqualTo("BoardError");
    }

    @Test
    void anIncidentKindThatDoesNotExistIsRefused() {
        Map<String, Object> body = deropement();
        body.put("kind", "something else");
        Http.Answer reply = client.postJson("/lifts/JP/incidents", body, Wire.headers(Role.OPERATOR));
        assertThat(reply.status()).isEqualTo(422);
    }

    @Test
    void theIncidentsOnOneMachineComeBackInOrder() {
        client.postJson("/lifts/JP/incidents", deropement("2025-01-15T14:00:00"), Wire.headers(Role.OPERATOR));
        client.postJson("/lifts/JP/incidents", sprain(), Wire.headers(Role.OPERATOR));
        client.postJson("/lifts/SB/incidents", sprain(), Wire.headers(Role.OPERATOR));
        JsonNode body = client.get("/lifts/JP/incidents", Wire.headers()).json().get("items");
        assertThat(Http.texts(body, "at")).containsExactly("2025-01-15T10:00:00", "2025-01-15T14:00:00");
    }

    @Test
    void oneIncidentComesBackByItsId() {
        int made = recorded(deropement());
        JsonNode body = client.get("/incidents/" + made, Wire.headers()).json();
        assertThat(body.get("narrative").asText()).startsWith("chair 34");
    }

    @Test
    void anIncidentThatIsNotThere() {
        assertThat(client.get("/incidents/99", Wire.headers()).status()).isEqualTo(404);
    }

    @Test
    void theOutstandingListIsInDeadlineOrderNotEventOrder() {
        client.postJson("/lifts/JP/incidents", sprain(), Wire.headers(Role.OPERATOR));
        client.postJson("/lifts/JP/incidents", deropement("2025-01-15T14:00:00"), Wire.headers(Role.OPERATOR));
        JsonNode body = client.get("/incidents/outstanding?now=2025-01-15T17:00:00", Wire.headers())
                .json();
        assertThat(Http.texts(body, "kind")).containsExactly("deropement", "loading");
        assertThat(body.get(0).get("overdue")).isEqualTo(Http.TRUE);
        assertThat(body.get(0).get("outstanding_for").asText()).isEqualTo("3:00:00");
    }

    @Test
    void aManagerFilesAnIncidentAndTakesTheNextNumber() {
        int made = recorded(deropement());
        Http.Answer reply = client.postJson(
                "/incidents/" + made + "/filing", filing("2025-01-15T12:30:00"), Wire.headers(Role.MANAGER));
        assertThat(reply.status()).isEqualTo(201);
        JsonNode body = reply.json();
        assertThat(body.get("number").asText()).isEqualTo("UT-2025-0001");
        assertThat(body.get("late")).isEqualTo(Http.FALSE);
        assertThat(body.get("lateness").asText()).isEqualTo("0:00:00");
    }

    @Test
    void aLateFilingSaysHowLateItWas() {
        int made = recorded(deropement());
        JsonNode body = client.postJson(
                        "/incidents/" + made + "/filing", filing("2025-01-17T09:00:00"), Wire.headers(Role.MANAGER))
                .json();
        assertThat(body.get("late")).isEqualTo(Http.TRUE);
        assertThat(body.get("lateness").asText()).isEqualTo("43:30:00");
    }

    @Test
    void aFiledIncidentDropsOffTheOutstandingList() {
        int made = recorded(deropement());
        client.postJson("/incidents/" + made + "/filing", filing("2025-01-15T12:30:00"), Wire.headers(Role.MANAGER));
        assertThat(client.get("/incidents/outstanding", Wire.headers()).json()).isEqualTo(Http.tree("[]"));
        JsonNode body = client.get("/incidents/" + made, Wire.headers()).json();
        assertThat(body.get("filing").asText()).isEqualTo("UT-2025-0001");
    }

    @Test
    void anIncidentIsFiledOnce() {
        int made = recorded(deropement());
        Map<String, Object> payload = filing("2025-01-15T12:30:00");
        client.postJson("/incidents/" + made + "/filing", payload, Wire.headers(Role.MANAGER));
        Http.Answer again = client.postJson("/incidents/" + made + "/filing", payload, Wire.headers(Role.MANAGER));
        assertThat(again.status()).isEqualTo(404);
    }

    @Test
    void theNumbersRunInSequenceAcrossIncidents() {
        List<String> numbers = new ArrayList<>();
        for (int hour : new int[] {11, 14}) {
            int made = recorded(deropement("2025-01-15T" + hour + ":30:00"));
            numbers.add(client.postJson(
                            "/incidents/" + made + "/filing", filing("2025-01-15T16:00:00"), Wire.headers(Role.MANAGER))
                    .json()
                    .get("number")
                    .asText());
        }
        assertThat(numbers).containsExactly("UT-2025-0001", "UT-2025-0002");
    }

    @Test
    void anOperatorKeyMayNotFile() {
        int made = recorded(deropement());
        Http.Answer reply = client.postJson(
                "/incidents/" + made + "/filing", filing("2025-01-15T12:30:00"), Wire.headers(Role.OPERATOR));
        assertThat(reply.status()).isEqualTo(401);
    }
}
