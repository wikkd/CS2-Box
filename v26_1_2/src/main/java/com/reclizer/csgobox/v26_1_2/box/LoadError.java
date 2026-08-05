package com.reclizer.csgobox.v26_1_2.box;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;

/**
 * One JSON load failure, retained so it can be shown to players in chat instead
 * of vanishing into the server log.
 *
 * <p>Line and column are 1-based when reported by Gson's {@code getLocation()};
 * a value of -1 means the source location was not available.</p>
 */
public record LoadError(
        Path file,
        String boxId,
        String reason,
        int line,
        int column
) {
    public Component toChatMessage() {
        String text;
        if (line > 0) {
            text = String.format(
                    "[CS2-Box] 箱子加载失败: %s — 第 %d 行第 %d 列: %s",
                    boxId, line, column, reason);
        } else {
            text = String.format(
                    "[CS2-Box] 箱子加载失败: %s — %s", boxId, reason);
        }
        return Component.literal(text).withStyle(s -> s.withColor(ChatFormatting.RED));
    }
}