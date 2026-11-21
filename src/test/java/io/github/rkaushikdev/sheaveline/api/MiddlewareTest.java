package io.github.rkaushikdev.sheaveline.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.sheaveline.Hill;
import io.github.rkaushikdev.sheaveline.journal.Journal;
import io.github.rkaushikdev.sheaveline.journal.Level;
import io.github.rkaushikdev.sheaveline.persistence.UnitOfWork;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@Import(MiddlewareTest.Journaled.class)
class MiddlewareTest {

    /** What the journal of this class's application has written. */
    static final StringBuilder WRITTEN = new StringBuilder();

    /** The suite's settings, with a journal writing every line into {@link #WRITTEN}. */
    static class Journaled {

        @Bean
        Settings settings() {
            return Wire.settings().withJournal(new Journal(WRITTEN, Level.INFO, "2025-01-15T07:00:00"));
        }
    }

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
        WRITTEN.setLength(0);
        client = new Http(mvc);
    }

    static List<String> lines() {
        return Arrays.stream(WRITTEN.toString().split("\n"))
                .filter(one -> !one.isEmpty())
                .toList();
    }

    @Test
    void aRequestWritesOneLineWithTheStatusOnIt() {
        Http.Answer reply = client.get("/lifts", Wire.headers());
        assertThat(reply.status()).isEqualTo(200);
        assertThat(lines()).hasSize(1);
        assertThat(lines().get(0)).contains("method=GET", "path=/lifts", "status=200", "ms=");
    }

    @Test
    void theIdComesBackOnTheResponseAndIsOnTheLine() {
        Http.Answer reply = client.get("/lifts", Wire.headers());
        String given = reply.header(RequestJournal.REQUEST_ID_HEADER);
        assertThat(given).hasSize(12);
        assertThat(lines().get(0)).contains("request=" + given);
    }

    @Test
    void anIdTheCallerSuppliesIsKept() {
        Map<String, String> headers = new HashMap<>(Wire.headers());
        headers.put(RequestJournal.REQUEST_ID_HEADER, "radio-call-41");
        Http.Answer reply = client.get("/lifts", headers);
        assertThat(reply.header(RequestJournal.REQUEST_ID_HEADER)).isEqualTo("radio-call-41");
        assertThat(lines().get(0)).contains("request=radio-call-41");
    }

    @Test
    void twoRequestsGetTwoDifferentIds() {
        String first = client.get("/lifts", Wire.headers()).header(RequestJournal.REQUEST_ID_HEADER);
        String second = client.get("/lifts", Wire.headers()).header(RequestJournal.REQUEST_ID_HEADER);
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void aRefusedRequestStillGetsItsLine() {
        Http.Answer reply = client.get("/lifts/ZZ", Wire.headers());
        assertThat(reply.status()).isEqualTo(404);
        assertThat(lines().get(0)).contains("status=404");
    }

    @Test
    void aRequestWithNoKeyStillGetsItsLine() {
        assertThat(client.get("/lifts").status()).isEqualTo(401);
        assertThat(lines().get(0)).contains("status=401");
    }

    @Test
    void aSilentJournalWritesNothingButStillHandsOutIds() {
        try (Wire.Running app = Wire.freshClient(Wire.settings())) {
            Http.Answer reply = app.client().get("/health");
            assertThat(reply.status()).isEqualTo(200);
            assertThat(reply.header(RequestJournal.REQUEST_ID_HEADER)).hasSize(12);
        }
        assertThat(WRITTEN).isEmpty();
    }
}
