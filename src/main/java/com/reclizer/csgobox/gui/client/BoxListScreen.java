package com.reclizer.csgobox.gui.client;

import com.reclizer.csgobox.api.box.BoxDefinition;
import com.reclizer.csgobox.api.box.BoxRegistry;
import com.reclizer.csgobox.utils.OverlayColor;
import com.reclizer.csgobox.utils.RenderFontTool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

public class BoxListScreen extends Screen {

    private final Screen parent;
    private final List<BoxDefinition> boxes;
    private static final int ENTRY_HEIGHT = 32;
    private static final int VISIBLE_ENTRIES = 18;
    private int scrollOffset = 0;

    public BoxListScreen(Screen parent) {
        super(Component.translatable("gui.csgobox.box_manager.title"));
        this.parent = parent;
        this.boxes = new ArrayList<>(BoxRegistry.getAll());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (this.minecraft != null && this.minecraft.level != null) {
            guiGraphics.fillGradient(0, 0, this.width, this.height, OverlayColor.getBackgroundColor(), OverlayColor.getBackgroundColor());
        } else {
            super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        renderTitles(guiGraphics);
        renderBoxList(guiGraphics, mouseX, mouseY);
        renderNewButton(guiGraphics, mouseX, mouseY);
    }

    private void renderTitles(GuiGraphics guiGraphics) {
        float titleScale = 2.0F;
        Component title = Component.translatable("gui.csgobox.box_manager.title");
        FormattedCharSequence titleText = title.getVisualOrderText();
        float titleWidth = this.font.width(titleText) * titleScale;
        float titleX = (this.width - titleWidth) / 2.0F;
        float titleY = this.height * 3F / 100F;
        RenderFontTool.drawString(guiGraphics, this.font, titleText, titleX, titleY, 0, 0, titleScale, 0xFFD3D3D3);

        if (boxes.isEmpty()) {
            float subScale = 0.8F;
            Component empty = Component.translatable("gui.csgobox.box_manager.no_boxes");
            FormattedCharSequence emptyText = empty.getVisualOrderText();
            float emptyWidth = this.font.width(emptyText) * subScale;
            RenderFontTool.drawString(guiGraphics, this.font, emptyText,
                    (this.width - emptyWidth) / 2.0F, this.height * 45F / 100F, 0, 0, subScale, 0xFF888888);
        }
    }

    private void renderBoxList(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int listStartY = this.height * 12 / 100;
        int listWidth = this.width * 80 / 100;
        int listX = (this.width - listWidth) / 2;

        int maxVisible = Math.min(boxes.size(), VISIBLE_ENTRIES);
        if (scrollOffset > boxes.size() - maxVisible) {
            scrollOffset = Math.max(0, boxes.size() - maxVisible);
        }

        for (int i = 0; i < maxVisible; i++) {
            int idx = i + scrollOffset;
            if (idx >= boxes.size()) break;

            BoxDefinition def = boxes.get(idx);
            int entryY = listStartY + i * ENTRY_HEIGHT;
            int entryX = listX;

            boolean hovered = mouseX >= entryX && mouseX <= entryX + listWidth
                    && mouseY >= entryY && mouseY <= entryY + ENTRY_HEIGHT - 2;

            int bgColor = hovered ? 0x40FFFFFF : 0x20FFFFFF;
            guiGraphics.fill(entryX, entryY, entryX + listWidth, entryY + ENTRY_HEIGHT - 2, bgColor);

            int totalItems = 0;
            for (var grade : def.grades()) {
                totalItems += grade.items().size();
            }
            String entryStr = Component.translatable("gui.csgobox.box_manager.entry",
                    def.name().getString(), def.grades().size(), totalItems,
                    (int)(def.dropRate() * 100)).getString();
            FormattedCharSequence entryText = Component.literal(entryStr).getVisualOrderText();
            RenderFontTool.drawString(guiGraphics, this.font, entryText,
                    entryX + 8, entryY + 8, 0, 0, 0.7F, 0xFFD3D3D3);

            ResourceLocation id = def.id();
            String idStr = id.toString();
            FormattedCharSequence idText = Component.literal(idStr).getVisualOrderText();
            RenderFontTool.drawString(guiGraphics, this.font, idText,
                    entryX + 8, entryY + 20, 0, 0, 0.5F, 0xFF888888);
        }
    }

    private void renderNewButton(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int btnX = this.width * 40 / 100;
        int btnY = this.height * 92 / 100;
        int btnW = this.width * 20 / 100;
        int btnH = this.height * 5 / 100;

        boolean hovered = mouseX >= btnX && mouseX <= btnX + btnW
                && mouseY >= btnY && mouseY <= btnY + btnH;

        int fillColor = hovered ? 0xFF00CC00 : 0xFF00AA00;
        int borderColor = hovered ? 0xFF00FF00 : 0xFF00CC00;
        guiGraphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, borderColor);
        guiGraphics.fill(btnX + 1, btnY + 1, btnX + btnW - 1, btnY + btnH - 1, fillColor);

        Component newText = Component.translatable("gui.csgobox.box_manager.new_box");
        FormattedCharSequence newSeq = newText.getVisualOrderText();
        float textWidth = this.font.width(newSeq) * 0.8F;
        RenderFontTool.drawString(guiGraphics, this.font, newSeq,
                btnX + (btnW - textWidth) / 2.0F, btnY + btnH * 0.25F, 0, 0, 0.8F, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        int listStartY = this.height * 12 / 100;
        int listWidth = this.width * 80 / 100;
        int listX = (this.width - listWidth) / 2;

        int maxVisible = Math.min(boxes.size(), VISIBLE_ENTRIES);
        for (int i = 0; i < maxVisible; i++) {
            int idx = i + scrollOffset;
            if (idx >= boxes.size()) break;
            int entryY = listStartY + i * ENTRY_HEIGHT;
            if (mouseX >= listX && mouseX <= listX + listWidth
                    && mouseY >= entryY && mouseY <= entryY + ENTRY_HEIGHT - 2) {
                if (this.minecraft != null) {
                    this.minecraft.setScreen(new BoxEditScreen(this, boxes.get(idx)));
                }
                return true;
            }
        }

        int btnX = this.width * 40 / 100;
        int btnY = this.height * 92 / 100;
        int btnW = this.width * 20 / 100;
        int btnH = this.height * 5 / 100;
        if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + btnH) {
            if (this.minecraft != null) {
                this.minecraft.setScreen(new BoxEditScreen(this, null));
            }
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxVisible = Math.min(boxes.size(), VISIBLE_ENTRIES);
        int maxScroll = Math.max(0, boxes.size() - maxVisible);
        scrollOffset = Math.clamp(scrollOffset - (int)scrollY, 0, maxScroll);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == 256) {
            if (this.minecraft != null) {
                this.minecraft.setScreen(parent);
            }
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }
}