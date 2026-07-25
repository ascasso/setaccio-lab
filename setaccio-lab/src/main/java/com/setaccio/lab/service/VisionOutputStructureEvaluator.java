package com.setaccio.lab.service;

import com.setaccio.lab.model.VisionStructuralCheck;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public final class VisionOutputStructureEvaluator {

    public List<VisionStructuralCheck> evaluate(String outputText, List<String> requiredSections) {
        Set<String> headings = outputText == null
                ? Set.of()
                : outputText.lines()
                        .map(String::strip)
                        .filter(line -> line.startsWith("## "))
                        .map(line -> line.substring(3).strip().toLowerCase(java.util.Locale.ROOT))
                        .collect(Collectors.toUnmodifiableSet());
        return requiredSections.stream()
                .map(section -> new VisionStructuralCheck(
                        section,
                        headings.contains(section.toLowerCase(java.util.Locale.ROOT))))
                .toList();
    }

    public boolean complete(List<VisionStructuralCheck> checks) {
        return checks != null && !checks.isEmpty() && checks.stream().allMatch(VisionStructuralCheck::present);
    }
}
