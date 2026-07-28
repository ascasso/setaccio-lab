package com.setaccio.lab.vision;

import java.util.List;

record VisionCorpusCatalog(
        int corpusVersion,
        List<VisionCorpusCase> cases
) {

    VisionCorpusCatalog {
        cases = cases == null ? List.of() : List.copyOf(cases);
    }
}
