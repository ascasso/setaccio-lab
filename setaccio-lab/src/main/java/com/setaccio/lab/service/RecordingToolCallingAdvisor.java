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
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityChecker;
import org.springframework.ai.model.tool.ToolExecutionResult;

final class RecordingToolCallingAdvisor extends ToolCallingAdvisor {

    private final List<ToolCallObservation> selectedToolCalls = new ArrayList<>();
    private final List<ToolExecutionObservation> executedToolResponses = new ArrayList<>();
    private final Set<String> recordedToolResponseKeys = new HashSet<>();

    RecordingToolCallingAdvisor(ToolCallingManager toolCallingManager) {
        super(toolCallingManager, DEFAULT_TOOL_EXECUTION_ELIGIBILITY_CHECKER, DEFAULT_ORDER, true);
    }

    @Override
    protected ChatClientResponse doAfterCall(ChatClientResponse chatClientResponse, CallAdvisorChain callAdvisorChain) {
        if (chatClientResponse.chatResponse() == null) {
            return chatClientResponse;
        }
        chatClientResponse.chatResponse().getResults().stream()
                .map(generation -> generation.getOutput().getToolCalls())
                .flatMap(List::stream)
                .map(this::toObservation)
                .forEach(selectedToolCalls::add);
        return chatClientResponse;
    }

    @Override
    protected List<Message> doGetNextInstructionsForToolCall(
            ChatClientRequest chatClientRequest,
            ChatClientResponse chatClientResponse,
            ToolExecutionResult toolExecutionResult) {
        toolExecutionResult.conversationHistory().stream()
                .filter(ToolResponseMessage.class::isInstance)
                .map(ToolResponseMessage.class::cast)
                .flatMap(message -> message.getResponses().stream())
                .map(response -> new ToolExecutionObservation(
                        response.id(),
                        response.name(),
                        response.responseData()))
                .filter(this::notAlreadyRecorded)
                .forEach(executedToolResponses::add);
        return super.doGetNextInstructionsForToolCall(chatClientRequest, chatClientResponse, toolExecutionResult);
    }

    List<ToolCallObservation> selectedToolCalls() {
        return List.copyOf(selectedToolCalls);
    }

    List<ToolExecutionObservation> executedToolResponses() {
        return List.copyOf(executedToolResponses);
    }

    private ToolCallObservation toObservation(AssistantMessage.ToolCall toolCall) {
        return new ToolCallObservation(toolCall.id(), toolCall.type(), toolCall.name(), toolCall.arguments());
    }

    private boolean notAlreadyRecorded(ToolExecutionObservation observation) {
        return recordedToolResponseKeys.add(observation.id() + ":" + observation.name());
    }
}
