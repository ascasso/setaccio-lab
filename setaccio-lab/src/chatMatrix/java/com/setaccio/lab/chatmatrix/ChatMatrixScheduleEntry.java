package com.setaccio.lab.chatmatrix;

record ChatMatrixScheduleEntry(
        int sequence,
        int repetition,
        int seed,
        String promptId,
        String promptSha256
) {
    ChatMatrixScheduleEntry {
        if (sequence < 1 || repetition < 1 || seed < 0) {
            throw new IllegalArgumentException("Chat matrix schedule numbers must be positive");
        }
        if (promptId == null || promptId.isBlank()) {
            throw new IllegalArgumentException("Chat matrix schedule prompt ID must not be blank");
        }
        if (promptSha256 == null || !promptSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Chat matrix schedule prompt digest must be complete");
        }
    }
}
