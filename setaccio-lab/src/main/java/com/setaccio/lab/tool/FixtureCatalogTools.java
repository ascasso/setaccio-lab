package com.setaccio.lab.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

public class FixtureCatalogTools {

    public static final String LOOKUP_ITEM_TOOL_NAME = "lab_lookup_catalog_item";
    public static final String LIST_ITEMS_TOOL_NAME = "lab_list_catalog_items";

    private static final List<CatalogItem> DEFAULT_ITEMS = List.of(
            new CatalogItem(
                    "fixture-image-landscape",
                    "Landscape image fixture",
                    "image",
                    List.of("image", "outdoor", "classification"),
                    "Public-safe fixture representing an outdoor landscape image for vision benchmark prompts."
            ),
            new CatalogItem(
                    "fixture-invoice-sample",
                    "Sample invoice text fixture",
                    "document",
                    List.of("document", "invoice", "extraction"),
                    "Synthetic document fixture for structured extraction and summarization prompts."
            ),
            new CatalogItem(
                    "fixture-policy-faq",
                    "Policy FAQ fixture",
                    "reference",
                    List.of("reference", "faq", "lookup"),
                    "Small public-safe knowledge fixture for deterministic lookup and citation prompts."
            )
    );

    private final List<CatalogItem> items;
    private final Map<String, CatalogItem> itemsById;

    public FixtureCatalogTools() {
        this(DEFAULT_ITEMS);
    }

    FixtureCatalogTools(List<CatalogItem> items) {
        this.items = List.copyOf(items);
        this.itemsById = indexById(items);
    }

    @Tool(
            name = LOOKUP_ITEM_TOOL_NAME,
            description = "Look up one deterministic public-safe benchmark catalog fixture by item id."
    )
    public CatalogLookupResult lookupCatalogItem(
            @ToolParam(required = true, description = "Fixture item id, such as fixture-image-landscape.")
                    String itemId) {
        String normalizedId = normalize(itemId);
        CatalogItem item = itemsById.get(normalizedId);
        if (item == null) {
            return new CatalogLookupResult(normalizedId, false, null, "No catalog fixture matched the requested id.");
        }
        return new CatalogLookupResult(normalizedId, true, item, "Catalog fixture found.");
    }

    @Tool(
            name = LIST_ITEMS_TOOL_NAME,
            description = "List deterministic public-safe benchmark catalog fixtures, optionally filtered by category."
    )
    public CatalogListResult listCatalogItems(
            @ToolParam(required = false, description = "Optional category filter such as image, document, or reference.")
                    String category) {
        String normalizedCategory = normalize(category);
        List<CatalogItem> matches = normalizedCategory.isBlank() || "all".equals(normalizedCategory)
                ? items
                : items.stream().filter(item -> item.category().equals(normalizedCategory)).toList();
        return new CatalogListResult(normalizedCategory.isBlank() ? "all" : normalizedCategory, matches.size(), matches);
    }

    private Map<String, CatalogItem> indexById(List<CatalogItem> catalogItems) {
        Map<String, CatalogItem> index = new LinkedHashMap<>();
        for (CatalogItem item : catalogItems) {
            index.put(item.id(), item);
        }
        return Map.copyOf(index);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record CatalogItem(
            String id,
            String title,
            String category,
            List<String> tags,
            String summary
    ) {
        public CatalogItem {
            Objects.requireNonNull(id, "id is required");
            Objects.requireNonNull(title, "title is required");
            Objects.requireNonNull(category, "category is required");
            tags = List.copyOf(tags);
            Objects.requireNonNull(summary, "summary is required");
        }
    }

    public record CatalogLookupResult(
            String itemId,
            boolean found,
            CatalogItem item,
            String message
    ) {}

    public record CatalogListResult(
            String category,
            int count,
            List<CatalogItem> items
    ) {
        public CatalogListResult {
            items = List.copyOf(items);
        }
    }
}
