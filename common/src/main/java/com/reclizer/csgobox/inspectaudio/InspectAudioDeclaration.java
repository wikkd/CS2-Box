package com.reclizer.csgobox.inspectaudio;

import java.util.Comparator;
import java.util.List;

/**
 * One parsed inspect-audio declaration: "play this sound when the look
 * screen's inspect button is clicked for this item" (2026-11-22 检视音频接口
 * JSON 声明层). Third-party mod jars and resource packs ship
 * {@code assets/<modid>/csgobox_inspect_audio/<name>.json}; the platform
 * layer scans them via the client resource manager and hands the JSON text
 * to {@link InspectAudioDeclarationParser}.
 *
 * <p>Pure data: item and sound ids stay as validated {@code "ns:path"}
 * strings. The resolution order between competing declarations is
 * {@link #ORDER}: higher {@code priority} wins, ties break by {@code source}
 * path so the outcome is deterministic.</p>
 */
public record InspectAudioDeclaration(
        List<String> items,
        String soundId,
        int priority,
        String source
) {
    /** Resolution order: priority descending, then source path ascending. */
    public static final Comparator<InspectAudioDeclaration> ORDER =
            Comparator.comparingInt(InspectAudioDeclaration::priority).reversed()
                    .thenComparing(InspectAudioDeclaration::source);

    public InspectAudioDeclaration {
        items = List.copyOf(items);
    }
}
