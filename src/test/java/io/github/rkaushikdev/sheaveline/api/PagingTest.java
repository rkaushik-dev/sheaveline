package io.github.rkaushikdev.sheaveline.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.rkaushikdev.sheaveline.Hill;
import io.github.rkaushikdev.sheaveline.ops.MeterReading;
import io.github.rkaushikdev.sheaveline.persistence.UnitOfWork;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@Import(Wire.Service.class)
class PagingTest {

    static final LocalDate ON = LocalDate.of(2025, 1, 15);
    static final List<String> LETTERS =
            IntStream.range('a', 'a' + 10).mapToObj(Character::toString).toList();

    @Autowired
    private MockMvc mvc;

    private Http client;

    @BeforeEach
    void readTheMetersForTwelveDays() {
        Settings built = Wire.settings();
        try (UnitOfWork work = UnitOfWork.on(built.database()).open()) {
            work.lifts().add(Hill.johnPaul());
            for (int offset = 0; offset < 12; offset++) {
                work.dayLogs()
                        .readMeter(new MeterReading(
                                Hill.johnPaul().code(),
                                ON.plusDays(offset),
                                new BigDecimal(40000 + offset),
                                1_400_000 + offset,
                                "R Skaggs"));
            }
            work.commit();
        }
        client = new Http(mvc);
    }

    @Test
    void aPageHandsBackTheSliceItWasAskedFor() {
        Paged<String> made = Paged.of(LETTERS, new Paging(3, 0));
        assertThat(made.items()).containsExactly("a", "b", "c");
        assertThat(made.total()).isEqualTo(10);
        assertThat(made.nextOffset()).isEqualTo(Optional.of(3));
        assertThat(made.isLast()).isFalse();
        assertThat(made.size()).isEqualTo(3);
    }

    @Test
    void theLastPageSaysItIsTheLast() {
        Paged<String> made = Paged.of(LETTERS, new Paging(3, 9));
        assertThat(made.items()).containsExactly("j");
        assertThat(made.nextOffset()).isEmpty();
        assertThat(made.isLast()).isTrue();
    }

    @Test
    void aPagePastTheEndIsEmptyRatherThanAnError() {
        Paged<String> made = Paged.of(LETTERS, new Paging(3, 99));
        assertThat(made.items()).isEmpty();
        assertThat(made.total()).isEqualTo(10);
        assertThat(made.isLast()).isTrue();
    }

    @Test
    void aLimitBiggerThanTheListTakesAllOfIt() {
        Paged<String> made = Paged.of(LETTERS, Paging.first(100));
        assertThat(made.items()).isEqualTo(LETTERS);
        assertThat(made.isLast()).isTrue();
    }

    @Test
    void anEmptyListPagesToNothing() {
        Paged<String> made = Paged.of(List.of(), Paging.defaults());
        assertThat(made.items()).isEmpty();
        assertThat(made.total()).isZero();
        assertThat(made.isLast()).isTrue();
    }

    @Test
    void walkingThePagesVisitsEverythingExactlyOnce() {
        List<String> seen = new ArrayList<>();
        Optional<Integer> offset = Optional.of(0);
        while (offset.isPresent()) {
            Paged<String> page = Paged.of(LETTERS, new Paging(4, offset.get()));
            seen.addAll(page.items());
            offset = page.nextOffset();
        }
        assertThat(seen).isEqualTo(LETTERS);
    }

    @Test
    void aListRouteHandsBackAPageWithATotalOnIt() {
        JsonNode body =
                client.get("/lifts/JP/meters", Wire.headers(Role.MECHANIC)).json();
        assertThat(body.get("total").asInt()).isEqualTo(12);
        assertThat(body.get("limit").asInt()).isEqualTo(Paging.DEFAULT_LIMIT);
        assertThat(body.get("offset").asInt()).isZero();
        assertThat(body.get("next_offset")).isEqualTo(Http.NULL);
        assertThat(body.get("items")).hasSize(12);
    }

    @Test
    void aSmallerLimitCutsThePageAndNamesTheNextOffset() {
        JsonNode body = client.get("/lifts/JP/meters?limit=5", Wire.headers(Role.MECHANIC))
                .json();
        assertThat(body.get("items")).hasSize(5);
        assertThat(body.get("total").asInt()).isEqualTo(12);
        assertThat(body.get("next_offset").asInt()).isEqualTo(5);
    }

    @Test
    void theOffsetMovesTheWindow() {
        JsonNode first = client.get("/lifts/JP/meters?limit=5", Wire.headers(Role.MECHANIC))
                .json();
        JsonNode second = client.get("/lifts/JP/meters?limit=5&offset=5", Wire.headers(Role.MECHANIC))
                .json();
        assertThat(first.get("items").get(0)).isNotEqualTo(second.get("items").get(0));
        assertThat(second.get("offset").asInt()).isEqualTo(5);
        assertThat(second.get("next_offset").asInt()).isEqualTo(10);
    }

    @Test
    void aLimitNobodyShouldBeAskingFor() {
        Http.Answer tooMany =
                client.get("/lifts/JP/meters?limit=" + (Paging.MOST_AT_ONCE + 1), Wire.headers(Role.MECHANIC));
        assertThat(tooMany.status()).isEqualTo(422);
        Http.Answer noneAtAll = client.get("/lifts/JP/meters?limit=0", Wire.headers(Role.MECHANIC));
        assertThat(noneAtAll.status()).isEqualTo(422);
    }

    @Test
    void anOffsetBeforeTheStart() {
        Http.Answer reply = client.get("/lifts/JP/meters?offset=-1", Wire.headers(Role.MECHANIC));
        assertThat(reply.status()).isEqualTo(422);
    }

    @Test
    void theDaysListIsPagedTheSameWay() {
        JsonNode body = client.get("/lifts/JP/days?limit=2", Wire.headers()).json();
        assertThat(body.get("total").asInt()).isZero();
        assertThat(body.get("items")).isEqualTo(Http.tree("[]"));
    }

    @Test
    void theMeterReadingsSurviveTheRoundTripThroughAPage() {
        JsonNode body = client.get("/lifts/JP/meters?limit=1", Wire.headers(Role.MECHANIC))
                .json();
        JsonNode one = body.get("items").get(0);
        assertThat(one.get("lift").asText()).isEqualTo("JP");
        assertThat(one.get("day").asText()).isEqualTo(ON.toString());
        assertThat(one.get("cycles").asLong()).isEqualTo(1_400_000);
        assertThat(one.get("read_by").asText()).isEqualTo("R Skaggs");
    }
}
