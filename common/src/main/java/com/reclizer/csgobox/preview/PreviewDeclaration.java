package com.reclizer.csgobox.preview;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * One parsed preview declaration: "render this item as this entity/block
 * instead of its own model" (2026-11-22 预览适配接口 v1). Third-party mod
 * jars ship {@code assets/<modid>/csgobox_preview/<name>.json}; the platform
 * layer scans them via the client resource manager and hands the JSON text to
 * {@link PreviewDeclarationParser}.
 *
 * <p>Pure data: item/entity/block ids stay as validated {@code "ns:path"}
 * strings, {@code spawnDataJson} is the raw JSON of the optional
 * {@code spawn_data} object (the platform side converts it into its era NBT
 * type), and the rest pose is plain degrees. The resolution order between
 * competing declarations is {@link #ORDER}: higher {@code priority} wins,
 * ties break by {@code source} path so the outcome is deterministic.</p>
 */
public record PreviewDeclaration(
        List<String> items,
        String mode,
        String entityId,
        String blockId,
        Map<String, String> blockProperties,
        String spawnDataJson,
        float yawDeg,
        float pitchDeg,
        float rollDeg,
        float fitBoost,
        int priority,
        String source
) {
    /** Resolution order: priority descending, then source path ascending. */
    public static final Comparator<PreviewDeclaration> ORDER =
            Comparator.comparingInt(PreviewDeclaration::priority).reversed()
                    .thenComparing(PreviewDeclaration::source);

    public PreviewDeclaration {
        items = List.copyOf(items);
        blockProperties = Map.copyOf(blockProperties);
    }

    /** True when this declaration routes the preview through an entity. */
    public boolean isEntity() {
        return "entity".equals(mode);
    }
}
