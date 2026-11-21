package io.github.rkaushikdev.sheaveline.plant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.sheaveline.measures.Speed;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ClassesTest {

    @Test
    void everyClassNamesItsGrip() {
        for (LiftClass liftClass : LiftClass.values()) {
            assertThat(liftClass.grip()).isNotNull();
        }
        assertThat(LiftClass.DETACHABLE_CHAIR.grip()).isSameAs(GripType.DETACHABLE);
        assertThat(LiftClass.FIXED_CHAIR.grip()).isSameAs(GripType.FIXED);
        assertThat(LiftClass.SURFACE_TOW.grip()).isSameAs(GripType.TOW);
    }

    @Test
    void onlyTheDetachablesDetach() {
        assertThat(LiftClass.DETACHABLE_CHAIR.isDetachable()).isTrue();
        assertThat(LiftClass.DETACHABLE_GONDOLA.isDetachable()).isTrue();
        assertThat(LiftClass.PULSE_GONDOLA.isDetachable()).isFalse();
        assertThat(LiftClass.AERIAL_TRAM.isDetachable()).isFalse();
    }

    @Test
    void aSurfaceLiftIsTheOnlyOneNotInTheAir() {
        assertThat(LiftClass.SURFACE_TOW.isAerial()).isFalse();
        assertThat(LiftClass.SURFACE_TOW.needsEvacuationPlan()).isFalse();
        for (LiftClass liftClass : LiftClass.values()) {
            if (liftClass != LiftClass.SURFACE_TOW) {
                assertThat(liftClass.isAerial()).isTrue();
                assertThat(liftClass.needsEvacuationPlan()).isTrue();
            }
        }
    }

    @Test
    void aTramRunsBackAndForthRatherThanRound() {
        assertThat(LiftClass.AERIAL_TRAM.isReversible()).isTrue();
        assertThat(LiftClass.DETACHABLE_CHAIR.isReversible()).isFalse();
    }

    @Test
    void theLineSpeedLimitIsAPropertyOfTheClass() {
        assertThat(LiftClass.FIXED_CHAIR.lineSpeedLimit()).isEqualTo(Speed.ofFeetPerMinute(500));
        assertThat(LiftClass.DETACHABLE_CHAIR.lineSpeedLimit()).isGreaterThan(LiftClass.FIXED_CHAIR.lineSpeedLimit());
        assertThat(LiftClass.AERIAL_TRAM.lineSpeedLimit()).isEqualTo(Speed.ofFeetPerMinute(2400));
    }

    @Test
    void everyClassHasAtLeastOneCarrierItTakes() {
        for (LiftClass liftClass : LiftClass.values()) {
            assertThat(liftClass.carriers()).isNotEmpty();
        }
    }

    @Test
    void aFixedGripChairDoesNotTakeASixPlace() {
        assertThat(LiftClass.FIXED_CHAIR.accepts(CarrierType.CHAIR_4)).isTrue();
        assertThat(LiftClass.FIXED_CHAIR.accepts(CarrierType.CHAIR_6)).isFalse();
        assertThatThrownBy(() -> LiftClass.FIXED_CHAIR.require(CarrierType.CHAIR_6))
                .isInstanceOf(PlantError.class)
                .hasMessage("a fixed grip chairlift does not carry a six place chair");
    }

    @Test
    void aGondolaTakesCabinsAndNothingElse() {
        assertThat(LiftClass.DETACHABLE_GONDOLA.carriers()).containsExactly(CarrierType.CABIN_8);
        assertThatThrownBy(() -> LiftClass.PULSE_GONDOLA.require(CarrierType.CHAIR_4))
                .isInstanceOf(PlantError.class);
    }

    @Test
    void aSurfaceLiftTakesNeitherChairsNorCabins() {
        for (CarrierType carrier : new CarrierType[] {CarrierType.CHAIR_2, CarrierType.CABIN_8, CarrierType.TRAM_CAR}) {
            assertThat(LiftClass.SURFACE_TOW.accepts(carrier)).isFalse();
        }
        assertThat(LiftClass.SURFACE_TOW.accepts(CarrierType.TBAR)).isTrue();
    }

    @Test
    void theRequireCheckPassesQuietlyWhenThePairingIsGood() {
        assertThatCode(() -> LiftClass.DETACHABLE_CHAIR.require(CarrierType.CHAIR_6))
                .doesNotThrowAnyException();
    }

    @Test
    void carriersReportHowManyTheySeat() {
        assertThat(CarrierType.CHAIR_2.seats()).isEqualTo(2);
        assertThat(CarrierType.CHAIR_6.seats()).isEqualTo(6);
        assertThat(CarrierType.CABIN_8.seats()).isEqualTo(8);
        assertThat(CarrierType.PLATTER.seats()).isEqualTo(1);
    }

    @Test
    void everyCarrierTypeHasASeatCount() {
        for (CarrierType carrier : CarrierType.values()) {
            assertThat(carrier.seats()).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    void enclosedAndSurfaceCarriersAreToldApart() {
        assertThat(CarrierType.CABIN_8.isEnclosed()).isTrue();
        assertThat(CarrierType.TRAM_CAR.isEnclosed()).isTrue();
        assertThat(CarrierType.CHAIR_4.isEnclosed()).isFalse();
        assertThat(CarrierType.TBAR.isSurface()).isTrue();
        assertThat(CarrierType.CABIN_8.isSurface()).isFalse();
    }

    @Test
    void aTowHangerIsNotSlipTested() {
        assertThat(GripType.DETACHABLE.isTestedForSlip()).isTrue();
        assertThat(GripType.FIXED.isTestedForSlip()).isTrue();
        assertThat(GripType.TOW.isTestedForSlip()).isFalse();
        assertThat(GripType.DETACHABLE.detaches()).isTrue();
        assertThat(GripType.FIXED.detaches()).isFalse();
    }

    @Test
    void aDieselIsItsOwnStandby() {
        assertThat(DriveType.DIESEL_HYDRAULIC.isPrimeMover()).isTrue();
        assertThat(DriveType.DIESEL_HYDRAULIC.hasVariableSpeed()).isFalse();
        assertThat(DriveType.AC_DRIVE.hasVariableSpeed()).isTrue();
        assertThat(DriveType.AC_DRIVE.isPrimeMover()).isFalse();
    }

    @Test
    void terminalRolesSeparateDrivingFromTensioning() {
        assertThat(TerminalRole.DRIVE.drives()).isTrue();
        assertThat(TerminalRole.DRIVE_TENSION.drives()).isTrue();
        assertThat(TerminalRole.RETURN.drives()).isFalse();
        assertThat(TerminalRole.RETURN_TENSION.tensions()).isTrue();
        assertThat(TerminalRole.DRIVE_TENSION.tensions()).isTrue();
        assertThat(TerminalRole.DRIVE.tensions()).isFalse();
    }

    @Test
    void exactlyOneTerminalRoleBothDrivesAndTensions() {
        assertThat(Arrays.stream(TerminalRole.values()).filter(role -> role.drives() && role.tensions()))
                .containsExactly(TerminalRole.DRIVE_TENSION);
    }
}
