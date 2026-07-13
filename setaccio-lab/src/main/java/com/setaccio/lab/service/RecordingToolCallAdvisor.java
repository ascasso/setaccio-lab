package com.setaccio.lab.service;

import com.setaccio.lab.model.ToolCallObservation;
import com.setaccio.lab.model.ToolExecutionObservation;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.Usage;

final class RecordingToolCallAdvisor implements BaseAdvisor {

    private static final int ORDER = ToolCallingAdvisor.DEFAULT_ORDER + 1;

    private final List<ToolCallObservation> selectedToolCalls = new ArrayList<>();
    private final List<ToolExecutionObservation> executedToolResponses = new ArrayList<>();
    private final Set<String> recordedToolResponseKeys = new HashSet<>();
    private int promptTokens;
    private int completionTokens;
    private boolean sawPromptTokens;
    private boolean sawCompletionTokens;

    @Override
    public ChatClientRequest before(ChatClientRequest chatClientRequest, AdvisorChain advisorChain) {
        chatClientRequest.prompt().getInstructions().stream()
                .filter(ToolResponseMessage.class::isInstance)
                .map(ToolResponseMessage.class::cast)
                .flatMap(message -> message.getResponses().stream())
                .map(response -> new ToolExecutionObservation(
                        response.id(),
                        response.name(),
                        response.responseData()))
                .filter(this::notAlreadyRecorded)
                .forEach(executedToolResponses::add);
        return chatClientRequest;
    }

    @Override
    public ChatClientResponse after(ChatClientResponse chatClientResponse, AdvisorChain advisorChain) {
        if (chatClientResponse.chatResponse() == null) {
            return chatClientResponse;
        }
        Usage usage = chatClientResponse.chatResponse().getMetadata() == null
                ? null
                : chatClientResponse.chatResponse().getMetadata().getUsage();
        accumulateUsage(usage);
        chatClientResponse.chatResponse().getResults().stream()
                .map(generation -> generation.getOutput().getToolCalls())
                .flatMap(List::stream)
                .map(this::toObservation)
                .forEach(selectedToolCalls::add);
        return chatClientResponse;
    }

    @Override
    public String getName() {
        return "Tool Benchmark Recording Advisor";
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    List<ToolCallObservation> selectedToolCalls() {
        return List.copyOf(selectedToolCalls);
    }

    List<ToolExecutionObservation> executedToolResponses() {
        return List.copyOf(executedToolResponses);
    }

    Integer promptTokens() {
        return sawPromptTokens ? promptTokens : null;
    }

    Integer completionTokens() {
        return sawCompletionTokens ? completionTokens : null;
    }

    private ToolCallObservation toObservation(AssistantMessage.ToolCall toolCall) {
        return new ToolCallObservation(toolCall.id(), toolCall.type(), toolCall.name(), toolCall.arguments());
    }

    private void accumulateUsage(Usage usage) {
        if (usage == null) {
            return;
        }
        if (usage.getPromptTokens() != null) {
            promptTokens += usage.getPromptTokens();
            sawPromptTokens = true;
        }
        if (usage.getCompletionTokens() != null) {
            completionTokens += usage.getCompletionTokens();
            sawCompletionTokens = true;
        }
    }

    private boolean notAlreadyRecorded(ToolExecutionObservation observation) {
        return recordedToolResponseKeys.add(observation.id() + ":" + observation.name());
    }
}
