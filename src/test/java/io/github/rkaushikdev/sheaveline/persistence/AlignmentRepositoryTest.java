package io.github.rkaushikdev.sheaveline.persistence;

import static io.github.rkaushikdev.sheaveline.Hill.JOHN_PAUL;
import static io.github.rkaushikdev.sheaveline.Hill.johnPaul;
import static io.github.rkaushikdev.sheaveline.Hill.strawberry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.measures.Length;
import io.github.rkaushikdev.sheaveline.persistence.mappers.PlantMapper;
import io.github.rkaushikdev.sheaveline.persistence.models.AlignmentSurveyRow;
import io.github.rkaushikdev.sheaveline.plant.AlignmentHistory;
import io.github.rkaushikdev.sheaveline.plant.AlignmentSurvey;
import io.github.rkaushikdev.sheaveline.plant.Tower;
import io.github.rkaushikdev.sheaveline.plant.TowerLine;
import io.github.rkaushikdev.sheaveline.plant.TowerShot;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The autumn tower shots, written and read back against the line they were shot on. */
class AlignmentRepositoryTest {

    private static final TowerLine LINE = johnPaul().towers();
    private static final LocalDate AUTUMN = LocalDate.of(2024, 10, 1);

    private UnitOfWork work;
    private AlignmentRepository alignment;

    @BeforeEach
    void johnPaulOnFile() {
        work = UnitOfWork.on(TestDatabase.fresh()).open();
        work.lifts().add(johnPaul());
        work.commit();
        alignment = work.alignment();
    }

    @AfterEach
    void closeIt() {
        work.close();
    }

    private static TowerShot shot(int number, String offset) {
        Tower tower = LINE.tower(number);
        return new TowerShot(tower.ref(), tower.station(), tower.crossarmElevation(), Length.parse(offset));
    }

    private static AlignmentSurvey survey(LocalDate taken, String offset) {
        return AlignmentSurvey.of(LINE, taken, "H Ogilvie", List.of(shot(5, offset), shot(4, "0 in")));
    }

    @Test
    void aSurveyComesBackShotForShot() {
        AlignmentSurvey before = survey(AUTUMN, "2 in");
        alignment.add(before);
        work.commit();
        List<AlignmentSurvey> found = alignment.surveys(JOHN_PAUL);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).shots()).isEqualTo(before.shots());
        assertThat(found.get(0).surveyor()).isEqualTo("H Ogilvie");
        assertThat(alignment.latest(JOHN_PAUL).taken()).isEqualTo(AUTUMN);
    }

    @Test
    void aMachineIsShotOnceADay() {
        alignment.add(survey(AUTUMN, "0 in"));
        work.commit();
        assertThatThrownBy(() -> alignment.add(survey(AUTUMN, "1 in")))
                .isInstanceOf(StorageError.class)
                .hasMessage("JP was already shot on 2024-10-01");
    }

    @Test
    void theHistoryReadsTheSurveysInOrder() {
        alignment.add(survey(AUTUMN.plusYears(1), "3 in"));
        alignment.add(survey(AUTUMN, "1 in"));
        work.commit();
        AlignmentHistory history = alignment.history(JOHN_PAUL);
        assertThat(history.size()).isEqualTo(2);
        assertThat(history.first().taken()).isEqualTo(AUTUMN);
        assertThat(history.latest().taken()).isEqualTo(AUTUMN.plusYears(1));
        assertThat(alignment.latest(JOHN_PAUL).taken()).isEqualTo(AUTUMN.plusYears(1));
    }

    @Test
    void aMachineNeverShotHasNoSurveyToRead() {
        assertThatThrownBy(() -> alignment.latest(JOHN_PAUL))
                .isInstanceOf(StorageError.class)
                .hasMessage("JP has never been shot");
        assertThatThrownBy(() -> alignment.history(JOHN_PAUL))
                .isInstanceOf(StorageError.class)
                .hasMessage("JP has never been shot");
    }

    @Test
    void aSurveyWillNotLoadAgainstAnotherMachinesLine() {
        AlignmentSurveyRow row = PlantMapper.alignmentToRow(survey(AUTUMN, "0 in"));
        assertThat(row.getShots()).hasSize(2);
        assertThatThrownBy(() -> PlantMapper.alignmentFromRow(row, strawberry().towers()))
                .isInstanceOf(StorageError.class)
                .hasMessage("that survey is on JP and not SB");
    }
}
