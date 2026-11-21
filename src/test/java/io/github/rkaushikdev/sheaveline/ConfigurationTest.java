package io.github.rkaushikdev.sheaveline;

import static io.github.rkaushikdev.sheaveline.Configuration.DATABASE;
import static io.github.rkaushikdev.sheaveline.Configuration.DEFAULT_DATABASE;
import static io.github.rkaushikdev.sheaveline.Configuration.DEFAULT_HOST;
import static io.github.rkaushikdev.sheaveline.Configuration.DEFAULT_PORT;
import static io.github.rkaushikdev.sheaveline.Configuration.DEFAULT_RESORT;
import static io.github.rkaushikdev.sheaveline.Configuration.EVERY_NAME;
import static io.github.rkaushikdev.sheaveline.Configuration.HOST;
import static io.github.rkaushikdev.sheaveline.Configuration.JOURNAL;
import static io.github.rkaushikdev.sheaveline.Configuration.KEYS;
import static io.github.rkaushikdev.sheaveline.Configuration.PORT;
import static io.github.rkaushikdev.sheaveline.Configuration.RESORT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.journal.Level;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigurationTest {

    private static Configuration read(String... pairs) {
        Map<String, String> env = new HashMap<>();
        for (int at = 0; at < pairs.length; at += 2) {
            env.put(pairs[at], pairs[at + 1]);
        }
        return Configuration.fromEnvironment(env);
    }

    @Test
    void nothingSetStillStarts() {
        Configuration made = read();
        assertThat(made.database()).isEqualTo(DEFAULT_DATABASE);
        assertThat(made.resort()).isEqualTo(DEFAULT_RESORT);
        assertThat(made.journal()).isSameAs(Level.INFO);
        assertThat(made.host()).isEqualTo(DEFAULT_HOST);
        assertThat(made.port()).isEqualTo(DEFAULT_PORT);
        assertThat(made.keyFile()).isEmpty();
    }

    @Test
    void everySettingCanBeGiven() {
        Configuration made = read(
                DATABASE, "jdbc:h2:file:./office",
                RESORT, "Snowbasin",
                JOURNAL, "debug",
                KEYS, "/etc/sheaveline/keys.json",
                HOST, "0.0.0.0",
                PORT, "9000");
        assertThat(made.database()).isEqualTo("jdbc:h2:file:./office");
        assertThat(made.resort()).isEqualTo("Snowbasin");
        assertThat(made.journal()).isSameAs(Level.DEBUG);
        assertThat(made.keyFile()).contains(Path.of("/etc/sheaveline/keys.json"));
        assertThat(made.host()).isEqualTo("0.0.0.0");
        assertThat(made.port()).isEqualTo(9000);
    }

    @Test
    void anEmptyValueCountsAsNotSet() {
        Configuration made = read(RESORT, "   ", PORT, "", KEYS, "");
        assertThat(made.resort()).isEqualTo(DEFAULT_RESORT);
        assertThat(made.port()).isEqualTo(DEFAULT_PORT);
        assertThat(made.keyFile()).isEmpty();
    }

    @Test
    void aPortThatIsNotANumber() {
        assertThatThrownBy(() -> read(PORT, "eight thousand"))
                .isInstanceOf(ConfigurationError.class)
                .hasMessageContaining(PORT)
                .hasMessage(PORT + " is 'eight thousand', which is not a port");
    }

    @Test
    void aPortOutsideTheRange() {
        for (String wrong : new String[] {"0", "65536", "-1"}) {
            assertThatThrownBy(() -> read(PORT, wrong))
                    .isInstanceOf(ConfigurationError.class)
                    .hasMessageContaining(PORT);
        }
    }

    @Test
    void aJournalLevelNobodyHas() {
        assertThatThrownBy(() -> read(JOURNAL, "chatty"))
                .isInstanceOf(ConfigurationError.class)
                .hasMessageContaining("no such level");
    }

    @Test
    void theJournalCanBeSwitchedOffEntirely() {
        assertThat(read(JOURNAL, "off").journal()).isSameAs(Level.OFF);
    }

    @Test
    void anInMemoryDatabaseSaysSo() {
        assertThat(read(DATABASE, "jdbc:h2:mem:office").isInMemory()).isTrue();
        assertThat(read().isInMemory()).isFalse();
    }

    @Test
    void aServerWithNoKeyFileSaysSoRatherThanRefusingSilently() {
        Configuration made = read();
        assertThat(made.servesKeys()).isFalse();
        assertThatThrownBy(made::requireKeys)
                .isInstanceOf(ConfigurationError.class)
                .hasMessageContaining("refuses every request");
    }

    @Test
    void aKeyFileThatIsNotThere(@TempDir Path tmp) {
        Configuration made = read(KEYS, tmp.resolve("missing.json").toString());
        assertThat(made.servesKeys()).isTrue();
        assertThatThrownBy(made::requireKeys)
                .isInstanceOf(ConfigurationError.class)
                .hasMessageContaining("which is not there");
    }

    @Test
    void aKeyFileThatIsThere(@TempDir Path tmp) throws IOException {
        Path wanted = tmp.resolve("keys.json");
        Files.writeString(wanted, "[]");
        assertThat(read(KEYS, wanted.toString()).requireKeys()).isEqualTo(wanted);
    }

    @Test
    void whatWasReadCanBePrintedWithoutLeakingAnything(@TempDir Path tmp) throws IOException {
        Path wanted = tmp.resolve("keys.json");
        Files.writeString(wanted, "[{\"secret\": \"do not print me\"}]");
        Map<String, String> described = read(KEYS, wanted.toString()).described();
        assertThat(described).containsEntry(KEYS, wanted.toString());
        assertThat(String.join(" ", described.values())).doesNotContain("do not print me");
    }

    @Test
    void everyNameThisModuleReadsIsListed() {
        assertThat(EVERY_NAME).containsExactlyInAnyOrder(DATABASE, RESORT, JOURNAL, KEYS, HOST, PORT);
        assertThat(EVERY_NAME).allMatch(one -> one.startsWith("SHEAVELINE_"));
    }

    @Test
    void theDescriptionNamesEverySetting() {
        Map<String, String> described = read().described();
        assertThat(described.keySet()).containsExactlyInAnyOrderElementsOf(EVERY_NAME);
        assertThat(described).containsEntry(KEYS, "not set");
    }

    @Test
    void theOneLineSummarySaysWhereTheRecordsAre() {
        String printed = read(RESORT, "Snowbasin", PORT, "9000").toString();
        assertThat(printed).contains("Snowbasin").contains(":9000").contains(DEFAULT_DATABASE);
    }

    /** Off and unset are the same level and not the same answer. */
    @Test
    void theConfigurationSaysWhetherTheJournalWasActuallyNamed() {
        assertThat(read().journalGiven()).isFalse();
        assertThat(read(JOURNAL, "info").journalGiven()).isTrue();
        assertThat(read(JOURNAL, "off").journalGiven()).isTrue();
        assertThat(read(JOURNAL, "   ").journalGiven()).isFalse();
    }

    @Test
    void anUnsetJournalStillReadsAsTheServersDefault() {
        assertThat(read().journal()).isSameAs(Level.INFO);
        assertThat(read().journalGiven()).isFalse();
        assertThat(Configuration.defaults().keyFile()).isEqualTo(Optional.empty());
    }
}
