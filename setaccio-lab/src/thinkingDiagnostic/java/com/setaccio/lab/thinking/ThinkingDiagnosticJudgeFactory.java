package com.setaccio.lab.thinking;

import com.setaccio.lab.evaluation.LocalFactCheckJudgeSettings;
import org.springframework.ai.chat.model.ChatModel;

/** Creates one no-pull, one-attempt judge model for a single arm's settings. */
@FunctionalInterface
public interface ThinkingDiagnosticJudgeFactory {

    ChatModel create(LocalFactCheckJudgeSettings settings);
}
