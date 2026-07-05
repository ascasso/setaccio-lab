package com.setaccio.lab.controller;

import com.setaccio.lab.model.AdvisorMode;
import com.setaccio.lab.model.ChatBenchmarkPrompt;
import com.setaccio.lab.model.ChatBenchmarkRequest;
import com.setaccio.lab.model.ChatBenchmarkResult;
import com.setaccio.lab.service.ChatBenchmarkService;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Profile("local")
@RestController
@RequestMapping("/api/lab/chat")
public class ChatBenchmarkController {

    private static final Logger logger = LoggerFactory.getLogger(ChatBenchmarkController.class);

    private final ChatBenchmarkService chatBenchmarkService;

    public ChatBenchmarkController(ChatBenchmarkService chatBenchmarkService) {
        this.chatBenchmarkService = chatBenchmarkService;
    }

    @PostMapping
    public ResponseEntity<ChatBenchmarkResult> run(@RequestBody ChatBenchmarkRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        List<String> models = parseCsv(request.models(), "models");
        AdvisorMode advisorMode = request.resolvedAdvisorMode();
        if (advisorMode != AdvisorMode.STANDARD) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only advisorMode=standard is implemented in this slice");
        }
        List<ChatBenchmarkPrompt> prompts = resolvePrompts(request);

        try {
            logger.info("Chat benchmark requested: {} models, {} prompts, advisorMode={}",
                    models.size(), prompts.size(), advisorMode.jsonValue());
            return ResponseEntity.ok(chatBenchmarkService.run(models, advisorMode, prompts));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    private List<ChatBenchmarkPrompt> resolvePrompts(ChatBenchmarkRequest request) {
        List<ChatBenchmarkPrompt> prompts = request.prompts();
        if ((prompts == null || prompts.isEmpty()) && request.resolvedUseDefaultPrompts()) {
            return ChatBenchmarkService.defaultPrompts();
        }
        if (prompts == null || prompts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Provide prompts or set useDefaultPrompts to true");
        }
        List<ChatBenchmarkPrompt> normalized = prompts.stream()
                .map(prompt -> new ChatBenchmarkPrompt(normalizePromptId(prompt),
                        prompt.text() == null ? "" : prompt.text().trim()))
                .toList();
        if (normalized.stream().anyMatch(prompt -> prompt.text().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Prompt text must not be blank");
        }
        return normalized;
    }

    private String normalizePromptId(ChatBenchmarkPrompt prompt) {
        if (prompt.id() != null && !prompt.id().isBlank()) {
            return prompt.id().trim();
        }
        return "prompt";
    }

    private List<String> parseCsv(String csv, String fieldName) {
        if (csv == null || csv.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Provide a comma-separated " + fieldName + " field");
        }
        List<String> values = Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (values.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Provide at least one " + fieldName.substring(0, fieldName.length() - 1));
        }
        return values;
    }
}
