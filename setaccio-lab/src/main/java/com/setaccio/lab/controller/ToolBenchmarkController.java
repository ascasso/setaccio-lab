package com.setaccio.lab.controller;

import com.setaccio.lab.model.AdvisorMode;
import com.setaccio.lab.model.ToolBenchmarkComparisonResult;
import com.setaccio.lab.model.ToolBenchmarkPrompt;
import com.setaccio.lab.model.ToolBenchmarkRequest;
import com.setaccio.lab.model.ToolBenchmarkResult;
import com.setaccio.lab.service.ToolBenchmarkService;
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
@RequestMapping("/api/lab/tools")
public class ToolBenchmarkController {

    private static final Logger logger = LoggerFactory.getLogger(ToolBenchmarkController.class);

    private final ToolBenchmarkService toolBenchmarkService;

    public ToolBenchmarkController(ToolBenchmarkService toolBenchmarkService) {
        this.toolBenchmarkService = toolBenchmarkService;
    }

    @PostMapping
    public ResponseEntity<?> run(@RequestBody ToolBenchmarkRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }
        List<String> models = parseCsv(request.models(), "models");
        AdvisorMode advisorMode = request.resolvedAdvisorMode();
        if (advisorMode == AdvisorMode.TOOL_SEARCH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Use advisorMode=compare for Tool Search comparison runs");
        }
        List<ToolBenchmarkPrompt> prompts = resolvePrompts(request);
        List<String> requestedTools = parseOptionalCsv(request.requestedTools());

        try {
            logger.info("Tool benchmark requested: {} models, {} prompts, advisorMode={}",
                    models.size(), prompts.size(), advisorMode.jsonValue());
            if (advisorMode == AdvisorMode.COMPARE) {
                ToolBenchmarkComparisonResult result = toolBenchmarkService.compare(models, prompts, requestedTools);
                return ResponseEntity.ok(result);
            }
            return ResponseEntity.ok(toolBenchmarkService.run(models, advisorMode, prompts, requestedTools));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    private List<ToolBenchmarkPrompt> resolvePrompts(ToolBenchmarkRequest request) {
        List<ToolBenchmarkPrompt> prompts = request.prompts();
        if ((prompts == null || prompts.isEmpty()) && request.resolvedUseDefaultPrompts()) {
            return ToolBenchmarkService.defaultPrompts();
        }
        if (prompts == null || prompts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Provide prompts or set useDefaultPrompts to true");
        }
        List<ToolBenchmarkPrompt> normalized = prompts.stream()
                .map(prompt -> new ToolBenchmarkPrompt(normalizePromptId(prompt), prompt.text() == null ? "" : prompt.text().trim()))
                .toList();
        if (normalized.stream().anyMatch(prompt -> prompt.text().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Prompt text must not be blank");
        }
        return normalized;
    }

    private String normalizePromptId(ToolBenchmarkPrompt prompt) {
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

    private List<String> parseOptionalCsv(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }
}
