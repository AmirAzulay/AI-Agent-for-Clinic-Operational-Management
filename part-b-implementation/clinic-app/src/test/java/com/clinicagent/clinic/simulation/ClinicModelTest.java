package com.clinicagent.clinic.simulation;

import com.clinicagent.clinic.model.DepartmentStatus;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ClinicModelTest {

    // General Medicine thresholds: warning 6, overload 12, max queue 20.
    private static DepartmentStatus status(int waiting, double maxWait, boolean freeRoom) {
        return ClinicModel.statusFor(waiting, maxWait, freeRoom, 6, 12, 20);
    }

    @Test
    void queueLengthAloneSetsTheStatus() {
        assertThat(status(5, 0, true)).isEqualTo(DepartmentStatus.NORMAL);
        assertThat(status(6, 0, true)).isEqualTo(DepartmentStatus.WARNING);
        assertThat(status(12, 0, true)).isEqualTo(DepartmentStatus.OVERLOADED);
        assertThat(status(20, 0, true)).isEqualTo(DepartmentStatus.CRITICAL);
    }

    @Test
    void fullRoomsWithLongWaitsRaiseTheStatusEvenWithAShortQueue() {
        assertThat(status(2, 20, true)).isEqualTo(DepartmentStatus.NORMAL);
        assertThat(status(2, 15, false)).isEqualTo(DepartmentStatus.WARNING);
        assertThat(status(2, 25, false)).isEqualTo(DepartmentStatus.OVERLOADED);
        assertThat(status(1, 45, true)).isEqualTo(DepartmentStatus.CRITICAL);
    }

    @Test
    void arrivalsPeakLateMorningAndDropOvernight() {
        assertThat(ClinicModel.hourlyLoad(11)).isCloseTo(1.75, within(1e-9));
        assertThat(ClinicModel.hourlyLoad(23)).isCloseTo(0.25, within(1e-9));
        assertThat(ClinicModel.hourOfDay(0)).isEqualTo(ClinicModel.START_HOUR);
        assertThat(ClinicModel.hourOfDay(16 * 60)).isEqualTo(0.0);
    }

    @Test
    void poissonCountsAverageToTheirMean() {
        Random random = new Random(1);
        double mean = IntStream.range(0, 20_000).map(i -> ClinicModel.poisson(0.3, random)).average().orElseThrow();
        assertThat(mean).isCloseTo(0.3, within(0.02));
    }
}
