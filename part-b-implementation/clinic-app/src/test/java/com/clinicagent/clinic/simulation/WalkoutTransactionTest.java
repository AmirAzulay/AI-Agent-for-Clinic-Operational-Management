package com.clinicagent.clinic.simulation;

import com.clinicagent.clinic.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Not @Transactional on purpose: walkouts must only ever run inside the simulation tick's transaction. */
class WalkoutTransactionTest extends PostgresIntegrationTest {

    @Autowired
    WalkoutService walkouts;

    @Test
    void walkoutsRefuseToRunOutsideATransaction() {
        assertThatThrownBy(() -> walkouts.processWalkouts(Instant.now()))
                .isInstanceOf(IllegalTransactionStateException.class);
    }
}
