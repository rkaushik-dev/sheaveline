package io.github.rkaushikdev.sheaveline.ops;

import static io.github.rkaushikdev.sheaveline.Hill.JOHN_PAUL;
import static io.github.rkaushikdev.sheaveline.Hill.WILDCAT;
import static io.github.rkaushikdev.sheaveline.Hill.johnPaul;
import static io.github.rkaushikdev.sheaveline.Hill.strawberry;
import static io.github.rkaushikdev.sheaveline.Hill.wildcat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.measures.Speed;
import io.github.rkaushikdev.sheaveline.plant.Lift;
import io.github.rkaushikdev.sheaveline.plant.LiftClass;
import io.github.rkaushikdev.sheaveline.plant.LiftCode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DownhillTest {

    private static final LocalDate ON = LocalDate.of(2025, 1, 15);

    private static DownhillAuthorisation authorisation(
            LiftCode lift, DownhillReason reason, LocalDate granted, LocalDate expires, Optional<String> escort) {
        return new DownhillAuthorisation(lift, reason, granted, "H Okafor", expires, escort);
    }

    private static DownhillAuthorisation authorisation(DownhillReason reason, Optional<String> escort) {
        return authorisation(JOHN_PAUL, reason, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 4, 20), escort);
    }

    private static DownhillAuthorisation authorisation(LocalDate granted, LocalDate expires) {
        return authorisation(JOHN_PAUL, DownhillReason.SIGHTSEEING, granted, expires, Optional.empty());
    }

    private static DownhillAuthorisation authorisation(LiftCode lift) {
        return authorisation(
                lift,
                DownhillReason.SIGHTSEEING,
                LocalDate.of(2025, 1, 1),
                LocalDate.of(2025, 4, 20),
                Optional.empty());
    }

    private static DownhillAuthorisation authorisation() {
        return authorisation(JOHN_PAUL);
    }

    private static DownhillCheck check(
            Lift lift, List<DownhillAuthorisation> authorisations, List<Post> posts, Optional<Long> fpm) {
        return DownhillCheck.of(lift, ON, authorisations, posts, fpm.map(Speed::ofFeetPerMinute));
    }

    private static DownhillCheck check() {
        return check(List.of(authorisation()));
    }

    private static DownhillCheck check(List<DownhillAuthorisation> authorisations) {
        return check(johnPaul(), authorisations, List.of(Post.TOP), Optional.of(400L));
    }

    private static DownhillCheck checkManned(List<Post> posts) {
        return check(johnPaul(), List.of(authorisation()), posts, Optional.of(400L));
    }

    private static DownhillCheck checkAt(Optional<Long> fpm) {
        return check(johnPaul(), List.of(authorisation()), List.of(Post.TOP), fpm);
    }

    @Test
    void everyClassHasADownhillPolicy() {
        for (LiftClass liftClass : LiftClass.values()) {
            assertThat(DownhillPolicy.forClass(liftClass)).isInstanceOf(DownhillPolicy.class);
        }
    }

    @Test
    void aSurfaceTowDoesNotCarryDownhillAtAll() {
        DownhillPolicy policy = DownhillPolicy.forClass(LiftClass.SURFACE_TOW);
        assertThat(policy.allowed()).isFalse();
        assertThat(policy.carriersAtOnce()).isZero();
    }

    @Test
    void aDetachableCarriesMoreAtOnceThanAFixedGrip() {
        DownhillPolicy quad = DownhillPolicy.forClass(LiftClass.DETACHABLE_CHAIR);
        DownhillPolicy triple = DownhillPolicy.forClass(LiftClass.FIXED_CHAIR);
        assertThat(quad.carriersAtOnce()).isGreaterThan(triple.carriersAtOnce());
        assertThat(quad.speed()).isPresent();
        assertThat(triple.speed()).isPresent();
        assertThat(quad.speed().get()).isGreaterThan(triple.speed().get());
    }

    @Test
    void everyAerialClassWantsSomebodyAtTheTop() {
        for (LiftClass liftClass : LiftClass.values()) {
            DownhillPolicy policy = DownhillPolicy.forClass(liftClass);
            if (policy.allowed()) {
                assertThat(policy.attendantAtTop()).isTrue();
            }
        }
    }

    @Test
    void anAuthorisationIsSignedAndRunsForwards() {
        assertThatThrownBy(() -> new DownhillAuthorisation(JOHN_PAUL, DownhillReason.STAFF, ON, "  ", ON))
                .isInstanceOf(OperationsError.class)
                .hasMessage("an authorisation is signed by whoever gave it");
        assertThatThrownBy(() -> authorisation(LocalDate.of(2025, 4, 1), LocalDate.of(2025, 1, 1)))
                .isInstanceOf(OperationsError.class)
                .hasMessage("an authorisation cannot expire before it is given");
    }

    @Test
    void aRideThatNeedsAnEscortCarriesOne() {
        assertThatThrownBy(() -> authorisation(DownhillReason.DISABILITY, Optional.empty()))
                .isInstanceOf(OperationsError.class)
                .hasMessage("a disability ride is escorted");
        assertThat(authorisation(DownhillReason.DISABILITY, Optional.of("K Ostler"))
                        .escort())
                .hasValue("K Ostler");
    }

    @Test
    void aRideThatDoesNotNeedAnEscortMustNotNameOne() {
        assertThatThrownBy(() -> authorisation(DownhillReason.SIGHTSEEING, Optional.of("K Ostler")))
                .isInstanceOf(OperationsError.class)
                .hasMessage("a sightseeing ride does not carry an escort");
    }

    @Test
    void theReasonsKnowWhichOfThemArePublic() {
        assertThat(DownhillReason.SIGHTSEEING.isPublic()).isTrue();
        assertThat(DownhillReason.STAFF.isPublic()).isFalse();
        assertThat(DownhillReason.EQUIPMENT.needsAnEscort()).isTrue();
        assertThat(DownhillReason.BEGINNER.needsAnEscort()).isFalse();
    }

    @Test
    void anAuthorisationIsCurrentBetweenItsDates() {
        DownhillAuthorisation held = authorisation(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 4, 20));
        assertThat(held.isCurrent(ON)).isTrue();
        assertThat(held.isCurrent(LocalDate.of(2024, 12, 1))).isFalse();
        assertThat(held.daysLeft(ON)).isEqualTo(95);
    }

    @Test
    void anAuthorisationPrintsItsExpiry() {
        assertThat(authorisation()).hasToString("JP downhill for sightseeing, to 2025-04-20");
    }

    @Test
    void aMachineWithEverythingInPlaceMayCarryDown() {
        DownhillCheck read = check();
        assertThat(read.mayRun()).isTrue();
        assertThat(read.refusals()).isEmpty();
        assertThat(read.carriersAtOnce()).isEqualTo(4);
        assertThat(read.toString()).contains("may carry down");
        assertThat(read).hasToString("JP may carry down, 4 at once");
    }

    @Test
    void aTowIsRefusedBeforeAnythingElseIsLookedAt() {
        DownhillCheck read = check(wildcat(), List.of(authorisation(WILDCAT)), List.of(), Optional.of(400L));
        assertThat(read.mayRun()).isFalse();
        assertThat(read.refusals()).containsExactly("a surface lift does not carry downhill");
        assertThat(read.carriersAtOnce()).isZero();
    }

    @Test
    void nothingAuthorisedTodayStopsIt() {
        DownhillCheck read = check(List.of());
        assertThat(read.refusals()).contains("nothing authorised for today");
    }

    @Test
    void anAuthorisationForAnotherMachineDoesNotCount() {
        DownhillCheck read = check(List.of(authorisation(WILDCAT)));
        assertThat(read.refusals()).contains("nothing authorised for today");
    }

    @Test
    void aLapsedAuthorisationDoesNotCount() {
        DownhillAuthorisation lapsed = authorisation(LocalDate.of(2024, 11, 1), LocalDate.of(2024, 12, 1));
        DownhillCheck read = check(List.of(lapsed));
        assertThat(read.refusals()).contains("nothing authorised for today");
    }

    @Test
    void nobodyAtTheTopStopsIt() {
        DownhillCheck read = checkManned(List.of(Post.BOTTOM));
        assertThat(read.refusals()).contains("nobody at the top terminal");
    }

    @Test
    void aRopeRunningTooFastStopsItAndSaysByHowMuch() {
        DownhillCheck read = checkAt(Optional.of(1000L));
        assertThat(read.refusals()).anySatisfy(one -> assertThat(one).contains("above the"));
        assertThat(read.refusals().get(0)).contains("1000 fpm");
        assertThat(read.refusals()).containsExactly("1000 fpm is above the 500 fpm downhill limit");
    }

    @Test
    void noRopeSpeedSetStopsItToo() {
        DownhillCheck read = checkAt(Optional.empty());
        assertThat(read.refusals()).contains("no rope speed set");
    }

    @Test
    void aFixedGripIsHeldToASlowerRopeThanADetachable() {
        Speed atFourHundred = Speed.ofFeetPerMinute(400);
        DownhillCheck quad = checkAt(Optional.of(400L));
        DownhillCheck triple = DownhillCheck.of(
                strawberry(),
                ON,
                List.of(authorisation(strawberry().code())),
                List.of(Post.TOP),
                Optional.of(atFourHundred));
        assertThat(quad.mayRun()).isTrue();
        assertThat(triple.mayRun()).isFalse();
    }

    @Test
    void severalThingsCanBeWrongAtOnce() {
        DownhillCheck read = check(johnPaul(), List.of(), List.of(), Optional.of(1000L));
        assertThat(read.refusals()).hasSize(3);
    }

    @Test
    void aRefusedMachineCarriesNobody() {
        assertThat(check(List.of()).carriersAtOnce()).isZero();
        assertThat(check(List.of()).toString()).contains("may not carry down");
    }
}
