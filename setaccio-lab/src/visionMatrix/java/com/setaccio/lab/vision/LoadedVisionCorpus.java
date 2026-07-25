package com.setaccio.lab.vision;

import java.nio.file.Path;
import java.util.List;

record LoadedVisionCorpus(
        int corpusVersion,
        List<LoadedVisionCase> cases
) {

    LoadedVisionCorpus {
        cases = cases == null ? List.of() : List.copyOf(cases);
    }

    record LoadedVisionCase(
            VisionCorpusCase metadata,
            Path imagePath
    ) {}
}
