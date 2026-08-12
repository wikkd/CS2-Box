package com.reclizer.csgobox.v26_1_2.box;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;

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
        String label = boxId != null && !boxId.isBlank() ? boxId : file.getFileName().toString();
        Component text;
        if (line > 0) {
            text = Component.translatable(
                    "commands.csgobox.errors.entry", label, line, column, reason);
        } else {
            text = Component.translatable(
                    "commands.csgobox.errors.entry_plain", label, reason);
        }
        Component hover = Component.translatable("commands.csgobox.errors.file", file.toString())
                .append("\n")
                .append(Component.translatable("commands.csgobox.errors.click_copy"));
        String copyText = "[CS2-Box] " + file + " — "
                + (line > 0 ? "line " + line + " col " + column + ": " : "")
                + reason;
        return text.copy().withStyle(s -> s
                .withColor(ChatFormatting.RED)
                .withClickEvent(new ClickEvent.CopyToClipboard(copyText))
                .withHoverEvent(new HoverEvent.ShowText(hover)));
    }
}
