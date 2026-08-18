package com.reclizer.csgobox.forge_1_20_1.box;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;

import java.nio.file.Path;

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
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, copyText))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
    }
}
