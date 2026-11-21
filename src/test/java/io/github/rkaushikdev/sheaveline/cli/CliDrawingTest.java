package io.github.rkaushikdev.sheaveline.cli;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.rkaushikdev.sheaveline.Hill;
import io.github.rkaushikdev.sheaveline.Office;
import io.github.rkaushikdev.sheaveline.plant.Blueprint;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliDrawingTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path tmp;

    private final Shell shell = new Shell();

    private String database;
    private Path drawing;

    @BeforeEach
    void anEmptyRegisterAndADrawing() throws IOException {
        database = Office.empty(tmp);
        drawing = tmp.resolve("john-paul.json");
        Files.writeString(
                drawing,
                JSON.writerWithDefaultPrettyPrinter().writeValueAsString(Blueprint.writeLift(Hill.johnPaul())),
                StandardCharsets.UTF_8);
    }

    private Shell.Reply run(String... args) {
        return shell.on(database, args);
    }

    @Test
    void aDrawingThatReadsSaysWhatItMakes() {
        Shell.Reply reply = run("lift", "check", drawing.toString());
        assertThat(reply.code()).isZero();
        assertThat(reply.output())
                .contains("JP John Paul reads", "towers           7", "carriers         84", "rope length fits yes");
    }

    @Test
    void checkingADrawingWritesNothing() {
        run("lift", "check", drawing.toString());
        assertThat(run("lift", "list").output()).doesNotContain("John Paul");
    }

    @Test
    void loadingPutsTheMachineOnFile() {
        Shell.Reply reply = run("lift", "load", drawing.toString());
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("JP John Paul loaded", "7 towers, 84 carriers");
        assertThat(run("lift", "list").output()).contains("John Paul");
    }

    @Test
    void loadingTheSameMachineTwiceIsRefused() {
        run("lift", "load", drawing.toString());
        Shell.Reply reply = run("lift", "load", drawing.toString());
        assertThat(reply.code()).isEqualTo(1);
        assertThat(reply.output()).contains("already on file");
    }

    @Test
    void replaceTakesTheOldMachineOffFirst() {
        run("lift", "load", drawing.toString());
        Shell.Reply reply = run("lift", "load", drawing.toString(), "--replace");
        assertThat(reply.code()).isZero();
        assertThat(run("lift", "list").count("John Paul")).isEqualTo(1);
    }

    @Test
    void aDrawingWithABadTowerLoadsNothingAtAll() throws IOException {
        JsonNode document = JSON.readTree(drawing.toFile());
        document.withObject("/towers/2").remove("height");
        Path broken = tmp.resolve("broken.json");
        Files.writeString(broken, JSON.writeValueAsString(document), StandardCharsets.UTF_8);
        Shell.Reply reply = run("lift", "load", broken.toString());
        assertThat(reply.code()).isEqualTo(1);
        assertThat(reply.output()).contains("tower 3");
        assertThat(run("lift", "list").output()).doesNotContain("John Paul");
    }

    @Test
    void aFileThatIsNotJsonSaysWhereItStopped() throws IOException {
        Path path = tmp.resolve("not.json");
        Files.writeString(path, "{ this is not json", StandardCharsets.UTF_8);
        Shell.Reply reply = run("lift", "load", path.toString());
        assertThat(reply.code()).isEqualTo(1);
        assertThat(reply.output()).contains("is not json", "line");
    }

    @Test
    void aFileHoldingAListIsNotADrawing() throws IOException {
        Path path = tmp.resolve("list.json");
        Files.writeString(path, "[1, 2, 3]", StandardCharsets.UTF_8);
        Shell.Reply reply = run("lift", "load", path.toString());
        assertThat(reply.code()).isEqualTo(1);
        assertThat(reply.output()).contains("not a drawing");
    }

    @Test
    void aFileThatIsNotThereIsRefusedByTheOption() {
        Shell.Reply reply = run("lift", "load", tmp.resolve("nothing.json").toString());
        assertThat(reply.code()).isEqualTo(Main.MISUSED);
        assertThat(reply.err()).contains("does not exist");
    }

    @Test
    void exportPrintsTheDrawingToThePage() throws IOException {
        run("lift", "load", drawing.toString());
        Shell.Reply reply = run("lift", "export", "JP");
        assertThat(reply.code()).isZero();
        JsonNode document = JSON.readTree(reply.output());
        assertThat(document.get("code").asText()).isEqualTo("JP");
        assertThat(document.get("towers").size()).isEqualTo(7);
    }

    @Test
    void exportWritesAFileWhenAsked() throws IOException {
        run("lift", "load", drawing.toString());
        Path out = tmp.resolve("again.json");
        Shell.Reply reply = run("lift", "export", "JP", "--output", out.toString());
        assertThat(reply.code()).isZero();
        assertThat(reply.output()).contains("written to " + out);
        assertThat(JSON.readTree(out.toFile()).get("name").asText()).isEqualTo("John Paul");
    }

    @Test
    void aMachineExportedAndLoadedAgainIsTheSameMachine() {
        run("lift", "load", drawing.toString());
        Path out = tmp.resolve("again.json");
        run("lift", "export", "JP", "--output", out.toString());
        String first = run("lift", "show", "JP").output();
        run("lift", "load", out.toString(), "--replace");
        assertThat(run("lift", "show", "JP").output()).isEqualTo(first);
    }

    @Test
    void exportingAMachineThatIsNotThere() {
        Shell.Reply reply = run("lift", "export", "ZZ");
        assertThat(reply.code()).isEqualTo(1);
        assertThat(reply.output()).contains("there is no lift ZZ");
    }

    @Test
    void aSecondMachineLoadsAlongsideTheFirst() throws IOException {
        Path other = tmp.resolve("strawberry.json");
        Files.writeString(
                other, JSON.writeValueAsString(Blueprint.writeLift(Hill.strawberry())), StandardCharsets.UTF_8);
        run("lift", "load", drawing.toString());
        run("lift", "load", other.toString());
        assertThat(run("lift", "list").output()).contains("John Paul", "Strawberry");
    }
}
