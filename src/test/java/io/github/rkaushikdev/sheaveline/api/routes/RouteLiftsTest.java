package io.github.rkaushikdev.sheaveline.api.routes;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.sheaveline.Hill;
import io.github.rkaushikdev.sheaveline.api.Http;
import io.github.rkaushikdev.sheaveline.api.Role;
import io.github.rkaushikdev.sheaveline.api.Settings;
import io.github.rkaushikdev.sheaveline.api.Wire;
import io.github.rkaushikdev.sheaveline.persistence.UnitOfWork;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@Import(Wire.Service.class)
class RouteLiftsTest {

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

    @Test
    void theListComesBackInCodeOrder() {
        Http.Answer reply = client.get("/lifts", Wire.headers());
        assertThat(reply.status()).isEqualTo(200);
        assertThat(Http.texts(reply.json(), "code")).containsExactly("JP", "SB", "WC");
    }

    @Test
    void oneMachineComesBackInFull() {
        Http.Answer reply = client.get("/lifts/JP", Wire.headers());
        assertThat(reply.status()).isEqualTo(200);
        JsonNode body = reply.json();
        assertThat(body.get("name").asText()).isEqualTo("John Paul");
        assertThat(body.get("horizontal_length").asText()).isEqualTo("4800.00 ft");
        assertThat(body.get("towers")).hasSize(7);
        assertThat(body.get("spans")).hasSize(8);
    }

    @Test
    void aLowerCaseCodeIsReadTheSameWay() {
        assertThat(client.get("/lifts/jp", Wire.headers()).status()).isEqualTo(200);
    }

    @Test
    void aMachineThatIsNotThereIsAFourOhFour() {
        Http.Answer reply = client.get("/lifts/ZZ", Wire.headers());
        assertThat(reply.status()).isEqualTo(404);
        assertThat(reply.json().get("kind").asText()).isEqualTo("NotFound");
    }

    @Test
    void somethingThatIsNotACodeIsAFourHundred() {
        Http.Answer reply = client.get("/lifts/1A", Wire.headers());
        assertThat(reply.status()).isEqualTo(400);
        assertThat(reply.json().get("field").asText()).isEqualTo("a lift code");
    }

    @Test
    void theTowersComeBackOnTheirOwn() {
        JsonNode body = client.get("/lifts/JP/towers", Wire.headers()).json();
        assertThat(Http.numbers(body, "number")).containsExactly(1, 2, 3, 4, 5, 6, 7);
        assertThat(body.get(0).get("reference").asText()).isEqualTo("JP-T1");
    }

    @Test
    void theTerminalsComeBackOnTheirOwn() {
        JsonNode body = client.get("/lifts/JP/terminals", Wire.headers()).json();
        assertThat(Http.texts(body, "role")).containsExactly("drive", "return tension");
    }

    @Test
    void theSpansComeBackOnTheirOwn() {
        JsonNode body = client.get("/lifts/JP/spans", Wire.headers()).json();
        assertThat(Http.numbers(body, "index")).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
        assertThat(body.get(0).get("horizontal").asText()).endsWith(" ft");
    }

    @Test
    void aRequestWithNoKeyIsRefused() {
        Http.Answer reply = client.get("/lifts");
        assertThat(reply.status()).isEqualTo(401);
        assertThat(reply.json().get("kind").asText()).isEqualTo("AuthError");
    }

    @Test
    void aKeyThatIsNotOnTheRingIsRefused() {
        Http.Answer reply = client.get("/lifts", Map.of("x-api-key", "ffffffffffff.nonsense"));
        assertThat(reply.status()).isEqualTo(401);
    }

    @Test
    void aReaderKeyMayReadAndMayNotWithdraw() {
        assertThat(client.get("/lifts", Wire.headers(Role.READER)).status()).isEqualTo(200);
        Http.Answer reply = client.delete("/lifts/WC", Wire.headers(Role.READER));
        assertThat(reply.status()).isEqualTo(401);
    }

    @Test
    void aManagerKeyMayWithdrawAMachine() {
        Http.Answer reply = client.delete("/lifts/WC", Wire.headers(Role.MANAGER));
        assertThat(reply.status()).isEqualTo(204);
        assertThat(reply.text()).isEmpty();
        assertThat(Http.texts(client.get("/lifts", Wire.headers()).json(), "code"))
                .containsExactly("JP", "SB");
    }

    @Test
    void withdrawingAMachineThatIsNotThere() {
        Http.Answer reply = client.delete("/lifts/ZZ", Wire.headers(Role.MANAGER));
        assertThat(reply.status()).isEqualTo(404);
    }

    @Test
    void aHigherRoleCoversALowerOne() {
        assertThat(client.get("/lifts", Wire.headers(Role.MANAGER)).status()).isEqualTo(200);
        assertThat(client.get("/lifts", Wire.headers(Role.MECHANIC)).status()).isEqualTo(200);
    }

    @Test
    void theHealthRouteNeedsNoKey() {
        assertThat(client.get("/health").status()).isEqualTo(200);
    }

    @Test
    void aLapsedKeyIsRefusedEvenWithTheRightRole() {
        try (Wire.Running app = Wire.freshClient(Wire.settings(LocalDate.of(2026, 6, 1)))) {
            Http.Answer reply = app.client().get("/lifts", Wire.headers(Role.MANAGER));
            assertThat(reply.status()).isEqualTo(401);
        }
    }
}
