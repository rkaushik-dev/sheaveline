package io.github.rkaushikdev.sheaveline.api.schemas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.TokenBuffer;
import io.github.rkaushikdev.sheaveline.api.Json;
import io.github.rkaushikdev.sheaveline.measures.Angle;
import io.github.rkaushikdev.sheaveline.measures.Bearing;
import io.github.rkaushikdev.sheaveline.measures.Duration;
import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.measures.Load;
import io.github.rkaushikdev.sheaveline.measures.Speed;
import io.github.rkaushikdev.sheaveline.measures.Temperature;
import io.github.rkaushikdev.sheaveline.measures.Torque;
import io.github.rkaushikdev.sheaveline.plant.FilingNumber;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import io.github.rkaushikdev.sheaveline.plant.RopeSerial;
import io.github.rkaushikdev.sheaveline.plant.TowerRef;
import io.github.rkaushikdev.sheaveline.schedule.TimeOfDay;
import io.swagger.v3.core.util.Json31;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SchemaMeasuresTest {

    static final ObjectMapper JSON = Json.mapper();

    /** One field of every face a measure or an identity travels in. */
    record Everything(
            @LengthFeet Length station,
            @LengthInches Length diameter,
            @LoadText Load tension,
            @SpeedText Speed speed,
            @WindText Speed wind,
            @DurationText Duration ride,
            @AngleText Angle deviation,
            @BearingText Bearing axis,
            @TemperatureText Temperature reading,
            @TorqueText Torque torque,
            @ClockText TimeOfDay sweep,
            @LiftCodeText LiftCode lift,
            @TowerRefText TowerRef tower,
            @RopeSerialText RopeSerial rope,
            @FilingNumberText FilingNumber filing) {}

    static Map<String, Object> sample() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("station", "4800 ft");
        fields.put("diameter", "1.378 in");
        fields.put("tension", "34,500 lb");
        fields.put("speed", "1000 fpm");
        fields.put("wind", "24 mph");
        fields.put("ride", "8:30");
        fields.put("deviation", "1.75 deg");
        fields.put("axis", "72");
        fields.put("reading", "-12 F");
        fields.put("torque", "450 in lb");
        fields.put("sweep", "16:15");
        fields.put("lift", "JP");
        fields.put("tower", "JP-T4");
        fields.put("rope", "FAT-2019-04412");
        fields.put("filing", "UT-2025-0147");
        return fields;
    }

    static Map<String, Object> sample(String field, Object value) {
        Map<String, Object> fields = sample();
        fields.put(field, value);
        return fields;
    }

    /**
     * Read the fields the way a body is read: text as text, and anything else handed over already
     * built, as an object in the middle of the JSON.
     */
    static Everything read(Map<String, Object> fields) throws IOException {
        try (TokenBuffer buffer = new TokenBuffer(JSON, false)) {
            buffer.writeStartObject();
            for (Map.Entry<String, Object> field : fields.entrySet()) {
                buffer.writeFieldName(field.getKey());
                if (field.getValue() instanceof String text) {
                    buffer.writeString(text);
                } else {
                    buffer.writeEmbeddedObject(field.getValue());
                }
            }
            buffer.writeEndObject();
            return JSON.readValue(buffer.asParser(JSON), Everything.class);
        }
    }

    static JsonNode dump(Everything everything) {
        return JSON.valueToTree(everything);
    }

    static JsonNode schema() {
        return Json31.mapper().valueToTree(Json.schemaOf(Everything.class));
    }

    @Test
    void everyFieldParsesIntoTheMeasureItNames() throws IOException {
        Everything read = read(sample());
        assertThat(read.station()).isEqualTo(Length.ofFeet(4800));
        assertThat(read.diameter()).isEqualTo(Length.ofInches("1.378"));
        assertThat(read.tension()).isEqualTo(Load.ofPounds(34500));
        assertThat(read.speed()).isEqualTo(Speed.ofFeetPerMinute(1000));
        assertThat(read.wind()).isEqualTo(Speed.ofMilesPerHour(24));
        assertThat(read.ride()).isEqualTo(Duration.ofHms(0, 8, 30));
        assertThat(read.deviation()).isEqualTo(Angle.ofDegrees("1.75"));
        assertThat(read.axis()).isEqualTo(Bearing.ofDegrees(72));
        assertThat(read.reading()).isEqualTo(Temperature.ofDegreesF(-12));
        assertThat(read.torque()).isEqualTo(Torque.ofInchPounds(450));
        assertThat(read.sweep()).isEqualTo(TimeOfDay.at(16, 15));
        assertThat(read.lift()).isEqualTo(new LiftCode("JP"));
        assertThat(read.tower()).isEqualTo(TowerRef.parse("JP-T4"));
        assertThat(read.rope()).isEqualTo(RopeSerial.parse("FAT-2019-04412"));
        assertThat(read.filing()).isEqualTo(new FilingNumber("UT", 2025, 147));
    }

    @Test
    void aMeasureAlreadyBuiltPassesStraightThrough() throws IOException {
        Everything read = read(sample("station", Length.ofFeet(1200)));
        assertThat(read.station()).isEqualTo(Length.ofFeet(1200));
    }

    @Test
    void theJsonComesBackAsTheStringsAPersonWrites() throws IOException {
        JsonNode body = dump(read(sample()));
        assertThat(body.get("station").asText()).isEqualTo("4800.00 ft");
        assertThat(body.get("diameter").asText()).isEqualTo("1.378\"");
        assertThat(body.get("tension").asText()).isEqualTo("34,500 lb");
        assertThat(body.get("speed").asText()).isEqualTo("1000 fpm");
        assertThat(body.get("wind").asText()).isEqualTo("24 mph");
        assertThat(body.get("ride").asText()).isEqualTo("0:08:30");
        assertThat(body.get("axis").asText()).isEqualTo("72 deg");
        assertThat(body.get("sweep").asText()).isEqualTo("16:15");
        assertThat(body.get("tower").asText()).isEqualTo("JP-T4");
    }

    @Test
    void nothingOnTheWireIsEverAFloat() throws IOException {
        JsonNode body = dump(read(sample()));
        assertThat(body.properties()).hasSize(15);
        assertThat(body.properties())
                .allSatisfy(field -> assertThat(field.getValue().isTextual())
                        .as(field.getKey())
                        .isTrue());
    }

    @Test
    void aFieldThatDoesNotReadIsRefusedRatherThanBelieved() {
        assertThatThrownBy(() -> read(sample("station", "quite a long way"))).isInstanceOf(Refused.class);
        assertThatThrownBy(() -> read(sample("speed", "very fast"))).isInstanceOf(Refused.class);
        assertThatThrownBy(() -> read(sample("lift", "1A"))).isInstanceOf(Refused.class);
    }

    /** A bare number is inches, and the field says which face it prints. */
    @Test
    void aLengthSentInInchesWhereFeetWereWantedStillReads() throws IOException {
        Everything read = read(sample("station", "148 ft"));
        assertThat(read.station()).isEqualTo(Length.ofFeet(148));
    }

    @Test
    void theSchemaSaysTheFieldsAreStrings() {
        JsonNode properties = schema().get("properties");
        for (String name : new String[] {"station", "tension", "speed", "tower"}) {
            assertThat(properties.get(name).get("type").asText()).as(name).isEqualTo("string");
        }
    }

    @Test
    void theSchemaCarriesAnExampleAPersonCanCopy() {
        JsonNode properties = schema().get("properties");
        assertThat(properties.get("station").get("examples"))
                .isEqualTo(JSON.createArrayNode().add("4800.00 ft"));
        assertThat(properties.get("rope").get("examples"))
                .isEqualTo(JSON.createArrayNode().add("FAT-2019-04412"));
    }

    @Test
    void theRoundTripThroughJsonComesBackTheSame() throws IOException {
        Everything before = read(sample());
        Everything after = JSON.readValue(JSON.writeValueAsString(before), Everything.class);
        assertThat(after.station()).isEqualTo(before.station());
        assertThat(after.tension()).isEqualTo(before.tension());
        assertThat(after.rope()).isEqualTo(before.rope());
        assertThat(after.reading()).isEqualTo(before.reading());
    }

    @Test
    void aWindAndARopeSpeedAreTheSameTypeWithDifferentFaces() throws IOException {
        Everything read = read(sample("wind", "1000 fpm"));
        assertThat(read.wind()).isEqualTo(Speed.ofFeetPerMinute(1000));
        assertThat(dump(read).get("wind").asText()).isEqualTo("11 mph");
    }
}
