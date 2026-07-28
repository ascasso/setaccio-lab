package com.setaccio.lab.evidence;

import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class EvidenceRunDirectoryTest {

    @TempDir
    Path tempDir;

    @Test
    void allocatesUniqueNonOverwritingRunDirectories() {
        Instant startedAt = Instant.parse("2026-07-24T12:34:56.123456789Z");

        Path first = EvidenceRunDirectory.createUnique(tempDir, "vision", startedAt);
        Path second = EvidenceRunDirectory.createUnique(tempDir, "vision", startedAt);

        assertThat(first).isDirectory();
        assertThat(second).isDirectory();
        assertThat(first).isNotEqualTo(second);
        assertThat(first.getFileName().toString())
                .matches("20260724T123456\\.123456789Z-vision-[0-9a-f]{8}");
        assertThat(second.getFileName().toString())
                .matches("20260724T123456\\.123456789Z-vision-[0-9a-f]{8}");
    }

    @Test
    void refusesToReuseANamedRunDirectory() {
        EvidenceRunDirectory.createNamed(tempDir, "2026-07-24-vision");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> EvidenceRunDirectory.createNamed(tempDir, "2026-07-24-vision"))
                .withMessageContaining("already exists");
    }

    @Test
    void rejectsTraversalAndNestedRunIds() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EvidenceRunDirectory.createNamed(tempDir, "../outside"))
                .withMessageContaining("safe path segment");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> EvidenceRunDirectory.createNamed(tempDir, "nested/run"))
                .withMessageContaining("safe path segment");
    }
}
