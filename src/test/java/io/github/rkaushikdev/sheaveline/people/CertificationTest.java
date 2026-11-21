package io.github.rkaushikdev.sheaveline.people;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.ops.Post;
import io.github.rkaushikdev.sheaveline.plant.LiftClass;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CertificationTest {

    private static final LocalDate ON = LocalDate.of(2025, 1, 15);
    private static final LiftClass QUAD = LiftClass.DETACHABLE_CHAIR;
    private static final LiftClass TRIPLE = LiftClass.FIXED_CHAIR;
    private static final LocalDate ISSUED = LocalDate.of(2024, 11, 1);
    private static final LocalDate EXPIRES = LocalDate.of(2025, 11, 1);

    private static Certificate card(
            String person, Endorsement endorsement, LocalDate issued, LocalDate expires, Set<LiftClass> classes) {
        return new Certificate(person, endorsement, issued, expires, "Utah tramway board", classes);
    }

    private static Certificate card(String person, Endorsement endorsement, LocalDate issued, LocalDate expires) {
        return card(person, endorsement, issued, expires, EnumSet.allOf(LiftClass.class));
    }

    private static Certificate card(String person, Endorsement endorsement) {
        return card(person, endorsement, ISSUED, EXPIRES);
    }

    private static Certificate card(String person) {
        return card(person, Endorsement.OPERATOR);
    }

    private static Certificate card(Endorsement endorsement) {
        return card("R Diaz", endorsement);
    }

    private static Certificate card(LocalDate issued, LocalDate expires) {
        return card("R Diaz", Endorsement.OPERATOR, issued, expires);
    }

    private static Certificate card(String person, Set<LiftClass> classes) {
        return card(person, Endorsement.OPERATOR, ISSUED, EXPIRES, classes);
    }

    private static Certificate card() {
        return card("R Diaz");
    }

    private static PersonnelFile file(Certificate... cards) {
        return PersonnelFile.of(cards.length == 0 ? List.of(card()) : List.of(cards));
    }

    @Test
    void aCardNamesAPersonAndAnIssuerAndRunsForwards() {
        assertThatThrownBy(() -> card("  ")).isInstanceOf(PeopleError.class).hasMessage("a certificate names a person");
        assertThatThrownBy(() -> new Certificate(
                        "R Diaz", Endorsement.OPERATOR, LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1), " "))
                .isInstanceOf(PeopleError.class)
                .hasMessage("R Diaz has a card with nobody's name on it");
        assertThatThrownBy(() -> card(LocalDate.of(2025, 1, 1), LocalDate.of(2024, 1, 1)))
                .isInstanceOf(PeopleError.class)
                .hasMessage("R Diaz has a card that expires before it was issued");
        assertThatThrownBy(() -> card("R Diaz", Set.of()))
                .isInstanceOf(PeopleError.class)
                .hasMessage("R Diaz has a card that covers no machines");
    }

    @Test
    void aCardIsCurrentBetweenItsDates() {
        Certificate held = card(LocalDate.of(2024, 11, 1), LocalDate.of(2025, 11, 1));
        assertThat(held.isCurrent(ON)).isTrue();
        assertThat(held.isCurrent(LocalDate.of(2024, 10, 1))).isFalse();
        assertThat(held.isCurrent(LocalDate.of(2025, 12, 1))).isFalse();
        assertThat(held.daysLeft(ON)).isEqualTo(290);
    }

    @Test
    void anOperatorCardCoversTheBottomButNotTheTop() {
        Certificate held = card(Endorsement.OPERATOR);
        assertThat(held.coversPost(Post.BOTTOM)).isTrue();
        assertThat(held.coversPost(Post.MID)).isTrue();
        assertThat(held.coversPost(Post.TOP)).isFalse();
        assertThat(held.coversPost(Post.MECHANIC)).isFalse();
    }

    @Test
    void aLeadCardCoversTheTopAndEverythingUnderIt() {
        Certificate held = card(Endorsement.LEAD_OPERATOR);
        assertThat(held.coversPost(Post.TOP)).isTrue();
        assertThat(held.coversPost(Post.BOTTOM)).isTrue();
        assertThat(held.coversPost(Post.MECHANIC)).isFalse();
    }

    @Test
    void aMechanicCardCoversOnlyTheMechanicPost() {
        Certificate held = card(Endorsement.MECHANIC);
        assertThat(held.coversPost(Post.MECHANIC)).isTrue();
        assertThat(held.coversPost(Post.BOTTOM)).isFalse();
    }

    @Test
    void aCardCanBeLimitedToCertainMachines() {
        Certificate limited = card("R Diaz", Set.of(TRIPLE));
        assertThat(limited.coversClass(TRIPLE)).isTrue();
        assertThat(limited.coversClass(QUAD)).isFalse();
        assertThat(limited.allows(Post.BOTTOM, TRIPLE, ON)).isTrue();
        assertThat(limited.allows(Post.BOTTOM, QUAD, ON)).isFalse();
    }

    @Test
    void allThreeQuestionsHaveToPassAtOnce() {
        Certificate lapsed = card(LocalDate.of(2023, 1, 1), LocalDate.of(2024, 1, 1));
        assertThat(lapsed.allows(Post.BOTTOM, QUAD, ON)).isFalse();
        Certificate wrongPost = card(Endorsement.OPERATOR);
        assertThat(wrongPost.allows(Post.TOP, QUAD, ON)).isFalse();
        Certificate right = card(Endorsement.LEAD_OPERATOR);
        assertThat(right.allows(Post.TOP, QUAD, ON)).isTrue();
    }

    @Test
    void aRenewalStartsWhereTheOldCardEnded() {
        Certificate held = card(ISSUED, LocalDate.of(2025, 11, 1));
        Certificate renewed = held.renewedTo(LocalDate.of(2026, 11, 1), "Utah tramway board");
        assertThat(renewed.issued()).isEqualTo(LocalDate.of(2025, 11, 1));
        assertThat(renewed.expires()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(renewed.endorsement()).isSameAs(held.endorsement());
        assertThatThrownBy(() -> held.renewedTo(LocalDate.of(2025, 6, 1), "Utah tramway board"))
                .isInstanceOf(PeopleError.class)
                .hasMessage("R Diaz would be renewed to no later than they already are");
    }

    @Test
    void aCardPrintsItsExpiry() {
        assertThat(card()).hasToString("R Diaz, operator, to 2025-11-01");
    }

    @Test
    void theFileListsItsPeopleOnceEach() {
        PersonnelFile held = file(card("R Diaz"), card("R Diaz", Endorsement.LEAD_OPERATOR), card("T Woo"));
        assertThat(held.people()).containsExactly("R Diaz", "T Woo");
        assertThat(held.forPerson("R Diaz")).hasSize(2);
        assertThat(held.size()).isEqualTo(3);
    }

    @Test
    void theFileAnswersWhetherSomebodyMayHoldAPost() {
        PersonnelFile held = file(card("R Diaz", Endorsement.OPERATOR), card("T Woo", Endorsement.LEAD_OPERATOR));
        assertThat(held.mayHold("R Diaz", Post.BOTTOM, QUAD, ON)).isTrue();
        assertThat(held.mayHold("R Diaz", Post.TOP, QUAD, ON)).isFalse();
        assertThat(held.mayHold("T Woo", Post.TOP, QUAD, ON)).isTrue();
        assertThat(held.mayHold("J Marsh", Post.BOTTOM, QUAD, ON)).isFalse();
    }

    @Test
    void thePeopleQualifiedForAPostAreListed() {
        PersonnelFile held = file(
                card("R Diaz", Endorsement.OPERATOR),
                card("T Woo", Endorsement.LEAD_OPERATOR),
                card("J Marsh", Endorsement.MECHANIC));
        assertThat(held.qualifiedFor(Post.BOTTOM, QUAD, ON)).containsExactly("R Diaz", "T Woo");
        assertThat(held.qualifiedFor(Post.TOP, QUAD, ON)).containsExactly("T Woo");
        assertThat(held.qualifiedFor(Post.MECHANIC, QUAD, ON)).containsExactly("J Marsh");
    }

    @Test
    void aClassRestrictionNarrowsTheQualifiedList() {
        PersonnelFile held = file(card("R Diaz", Set.of(TRIPLE)), card("T Woo"));
        assertThat(held.qualifiedFor(Post.BOTTOM, TRIPLE, ON)).containsExactly("R Diaz", "T Woo");
        assertThat(held.qualifiedFor(Post.BOTTOM, QUAD, ON)).containsExactly("T Woo");
    }

    @Test
    void theCardsAboutToRunOutAreListed() {
        PersonnelFile held = file(
                card("R Diaz", Endorsement.OPERATOR, ISSUED, LocalDate.of(2025, 2, 1)),
                card("T Woo", Endorsement.OPERATOR, ISSUED, LocalDate.of(2025, 11, 1)));
        List<Certificate> soon = held.expiringWithin(ON, 30);
        assertThat(soon).extracting(Certificate::person).containsExactly("R Diaz");
        assertThatThrownBy(() -> held.expiringWithin(ON, -1))
                .isInstanceOf(PeopleError.class)
                .hasMessage("a look ahead is a positive number of days");
    }

    @Test
    void theCardsThatHaveAlreadyRunOutAreListedSeparately() {
        PersonnelFile held = file(
                card("R Diaz", Endorsement.OPERATOR, LocalDate.of(2023, 1, 1), LocalDate.of(2024, 1, 1)),
                card("T Woo"));
        assertThat(held.lapsed(ON)).extracting(Certificate::person).containsExactly("R Diaz");
        assertThat(held.currentFor("T Woo", ON)).isNotEmpty();
        assertThat(held.currentFor("R Diaz", ON)).isEmpty();
    }

    @Test
    void addingACardMakesANewFile() {
        PersonnelFile held = file();
        PersonnelFile longer = held.withCertificate(card("T Woo"));
        assertThat(held.size()).isEqualTo(1);
        assertThat(longer.size()).isEqualTo(2);
    }
}
