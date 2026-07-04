package com.setaccio.lab.tool;

import java.math.BigDecimal;
import java.util.Objects;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

public class ArithmeticBenchmarkTools {

    public static final String ADD_TOOL_NAME = "lab_add_numbers";
    public static final String MULTIPLY_TOOL_NAME = "lab_multiply_numbers";

    @Tool(
            name = ADD_TOOL_NAME,
            description = "Add two decimal numbers for deterministic tool-calling benchmark prompts."
    )
    public ArithmeticResult addNumbers(
            @ToolParam(required = true, description = "Left decimal operand.") BigDecimal left,
            @ToolParam(required = true, description = "Right decimal operand.") BigDecimal right) {
        return new ArithmeticResult("add", require(left, "left"), require(right, "right"), left.add(right));
    }

    @Tool(
            name = MULTIPLY_TOOL_NAME,
            description = "Multiply two decimal numbers for deterministic tool-calling benchmark prompts."
    )
    public ArithmeticResult multiplyNumbers(
            @ToolParam(required = true, description = "Left decimal operand.") BigDecimal left,
            @ToolParam(required = true, description = "Right decimal operand.") BigDecimal right) {
        return new ArithmeticResult("multiply", require(left, "left"), require(right, "right"), left.multiply(right));
    }

    private BigDecimal require(BigDecimal value, String name) {
        return Objects.requireNonNull(value, name + " is required");
    }

    public record ArithmeticResult(
            String operation,
            BigDecimal left,
            BigDecimal right,
            BigDecimal result
    ) {}
}
