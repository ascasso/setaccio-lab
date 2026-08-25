package com.setaccio.lab.toolcompat;

import java.util.List;

/** Read-only local runtime snapshot used to resolve a proposed cohort. */
record ToolCompatibilityCohortInventory(
        String ollamaRuntimeVersion,
        List<ToolCompatibilityCohortInventoryModel> models
) {

    ToolCompatibilityCohortInventory {
        models = List.copyOf(models == null ? List.of() : models);
    }
}
