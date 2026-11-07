package com.clinicagent.clinic.simulation;

import com.clinicagent.clinic.model.Urgency;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class PatienceSamplerTest {

    private final PatienceSampler sampler = new PatienceSampler(new WalkoutProperties(null));

    @Test
    void highUrgencyPatientsNeverLeave() {
        assertThat(sampler.sample(Urgency.HIGH, new Random(1))).isNull();
    }

    @Test
    void patienceStaysInsideTheConfiguredRange() {
        Random random = new Random(42);
        IntStream.range(0, 1_000).forEach(i -> {
            assertThat(sampler.sample(Urgency.LOW, random)).isBetween(45, 90);
            assertThat(sampler.sample(Urgency.MEDIUM, random)).isBetween(60, 120);
        });
    }

    @Test
    void sameSeedGivesTheSamePatience() {
        assertThat(draw(new Random(7))).isEqualTo(draw(new Random(7)));
    }

    private List<Integer> draw(Random random) {
        return IntStream.range(0, 20).mapToObj(i -> sampler.sample(Urgency.LOW, random)).toList();
    }
}
