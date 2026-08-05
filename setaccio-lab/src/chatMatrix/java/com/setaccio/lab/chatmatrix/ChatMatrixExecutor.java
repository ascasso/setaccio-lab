package com.setaccio.lab.chatmatrix;

import com.setaccio.lab.chat.ChatGenerationSettings;
import com.setaccio.lab.chat.ChatInvocationOutcome;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class ChatMatrixExecutor {

    private final Clock clock;

    ChatMatrixExecutor() {
        this(Clock.systemUTC());
    }

    ChatMatrixExecutor(Clock clock) {
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        this.clock = clock;
    }

    ChatMatrixResult execute(ChatMatrixPreflight.Prepared prepared) {
        if (prepared == null) {
            throw new IllegalArgumentException("prepared preflight must not be null");
        }
        List<ChatMatrixScheduleEntry> schedule = ChatMatrixProtocol.schedule(prepared.catalog());
        List<ChatMatrixRow> rows = new ArrayList<>(schedule.size());
        Instant startedAt = clock.instant();
        for (ChatMatrixScheduleEntry entry : schedule) {
            ChatPromptCase prompt = prepared.catalog().require(entry.promptId());
            ChatGenerationSettings settings = prepared.settings().generationSettingsFor(entry.repetition());
            ChatInvocationOutcome outcome = prepared.session().invoke(prompt, prepared.modelIdentity(), settings);
            rows.add(ChatMatrixRow.from(entry, settings, prepared.modelIdentity(), outcome));
        }
        Instant finishedAt = clock.instant();
        return ChatMatrixProtocol.result(
                startedAt,
                finishedAt,
                prepared.settings(),
                prepared.modelIdentity(),
                prepared.catalog(),
                rows);
    }
}
