package com.setaccio.lab.smoke;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ToolSearchSmokeRunnerTest {

    @Test
    void selectsSemanticIdsAndOneBasedOrdinalsInRequestedOrder() {
        assertThat(ToolSearchSmokeRunner.selectCases("catalog-lookup,1,7"))
                .extracting(prompt -> prompt.id())
                .containsExactly("catalog-lookup", "arithmetic-add", "no-applicable-domain-tool");
    }

    @Test
    void usesAllDefaultsWhenNoCaseSelectorIsProvided() {
        assertThat(ToolSearchSmokeRunner.selectCases(null)).hasSize(8);
    }

    @Test
    void rejectsBlankUnknownOutOfRangeAndDuplicateSelectors() {
        List<String> invalid = List.of("", "unknown", "9", "1,arithmetic-add", "1,,2");

        for (String selectors : invalid) {
            assertThatIllegalArgumentException()
                    .as("selectors %s", selectors)
                    .isThrownBy(() -> ToolSearchSmokeRunner.selectCases(selectors));
        }
    }
}
