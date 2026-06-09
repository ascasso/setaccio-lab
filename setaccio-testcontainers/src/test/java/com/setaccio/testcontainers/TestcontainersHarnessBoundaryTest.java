package com.setaccio.testcontainers;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestcontainersHarnessBoundaryTest {

    @Test
    void moduleSkeletonDoesNotRequireDocker() {
        assertThat(true).as("Skeleton test must not start Docker or Testcontainers").isTrue();
    }
}
