package com.reclizer.csgobox.gui.client;

import com.reclizer.csgobox.api.box.BoxDefinition;
import com.reclizer.csgobox.api.box.BoxJsonLoader;
import com.reclizer.csgobox.api.box.BoxRegistry;
import com.reclizer.csgobox.api.box.GradeGroup;
import com.reclizer.csgobox.utils.ColorTools;
import com.reclizer.csgobox.utils.IconListTools;
import com.reclizer.csgobox.utils.OverlayColor;
import com.reclizer.csgobox.utils.RenderFontTool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class BoxEditScreen extends Screen {

    private final Screen parent;
    private final boolean isNew;
    private EditState state;
    private int selectedGradeIndex;
    private int entityScrollOffset;
    private int itemScrollOffset;
    private boolean dirty;
    private String boxIdInput;
    private boolean awaitingIdInput;
    private String itemIdInput;
    private boolean awaitingItemIdInput;

    private static final int ITEM_ROW_HEIGHT = 22;
    private static final int MAX_VISIBLE_ITEMS = 12;

    public BoxEditScreen(Screen parent, BoxDefinition def) {
        super(Component.translatable("gui.csgobox.edit.title"));
        this.parent = parent;
        this.isNew = def == null;
        this.selectedGradeIndex = 0;
        this.entityScrollOffset = 0;
        this.itemScrollOffset = 0;
        this.dirty = false;
        this.boxIdInput = "";
        this.awaitingIdInput = isNew;
        this.itemIdInput = "";
        this.awaitingItemIdInput = false;

        if (isNew) {
            this.state = new EditState();
            this.state.name = "New Box";
            this.state.dropRate = 0.12F;
            this.state.keyItem = ResourceLocation.parse("csgobox:csgo_key0");
            this.state.texture = Optional.empty();
            this.state.sound = Optional.empty();
            this.state.entities = new ArrayList<>();
            this.state.grades = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                this.state.grades.add(new GradeEditState(20, new ArrayList<>()));
            }
        } else {
            this.state = EditState.from(def);
        }
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

        if (awaitingIdInput) {
            renderIdInputDialog(guiGraphics, mouseX, mouseY);
            return;
        }
        if (awaitingItemIdInput) {
            renderItemIdInputDialog(guiGraphics, mouseX, mouseY);
            return;
        }

        renderHeader(guiGraphics);
        renderBasicInfo(guiGraphics, mouseX, mouseY);
        renderEntitySection(guiGraphics, mouseX, mouseY);
        renderWeightSection(guiGraphics, mouseX, mouseY);
        renderItemPoolSection(guiGraphics, mouseX, mouseY);
        renderBottomButtons(guiGraphics, mouseX, mouseY);
    }

    private void renderHeader(GuiGraphics guiGraphics) {
        float titleScale = 1.2F;
        String title = isNew ? "New Box" : state.name;
        FormattedCharSequence titleText = Component.literal(title).getVisualOrderText();
        float titleWidth = this.font.width(titleText) * titleScale;
        float titleX = (this.width - titleWidth) / 2.0F;
        RenderFontTool.drawString(guiGraphics, this.font, titleText, titleX, this.height * 2F / 100F, 0, 0, titleScale, 0xFFD3D3D3);

        float backScale = 0.7F;
        Component backText = Component.translatable("gui.csgobox.csgo_box.back_box");
        FormattedCharSequence backSeq = backText.getVisualOrderText();
        RenderFontTool.drawString(guiGraphics, this.font, backSeq, this.width * 3F / 100F, this.height * 2F / 100F, 0, 0, backScale, 0xFF888888);
    }

    private void renderBasicInfo(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int sectionX = this.width * 3 / 100;
        int sectionY = this.height * 7 / 100;
        int sectionW = this.width * 94 / 100;

        float labelScale = 0.6F;
        float valScale = 0.7F;

        Component sectionLabel = Component.translatable("gui.csgobox.edit.basic_info");
        RenderFontTool.drawString(guiGraphics, this.font, sectionLabel.getVisualOrderText(),
                sectionX, sectionY, 0, 0, labelScale, 0xFFAAAAAA);

        int lineY = sectionY + 12;
        String nameLine = Component.translatable("gui.csgobox.edit.name").getString() + ": " + state.name;
        RenderFontTool.drawString(guiGraphics, this.font, Component.literal(nameLine).getVisualOrderText(),
                sectionX + 4, lineY, 0, 0, valScale, 0xFFD3D3D3);

        String dropLine = Component.translatable("gui.csgobox.edit.drop_rate").getString()
                + ": " + String.format("%.1f%%", state.dropRate * 100);
        RenderFontTool.drawString(guiGraphics, this.font, Component.literal(dropLine).getVisualOrderText(),
                sectionX + 4, lineY + 12, 0, 0, valScale, 0xFFD3D3D3);

        String keyLine = Component.translatable("gui.csgobox.edit.key_item").getString() + ": " + state.keyItem;
        RenderFontTool.drawString(guiGraphics, this.font, Component.literal(keyLine).getVisualOrderText(),
                sectionX + 4, lineY + 24, 0, 0, valScale, 0xFFD3D3D3);

        String texLine = Component.translatable("gui.csgobox.edit.texture").getString() + ": "
                + state.texture.map(ResourceLocation::toString).orElse(Component.translatable("gui.csgobox.edit.default").getString());
        RenderFontTool.drawString(guiGraphics, this.font, Component.literal(texLine).getVisualOrderText(),
                sectionX + 4, lineY + 36, 0, 0, valScale, 0xFFD3D3D3);
    }

    private void renderEntitySection(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int sectionX = this.width * 3 / 100;
        int sectionY = this.height * 28 / 100;
        float labelScale = 0.6F;
        float valScale = 0.6F;

        Component sectionLabel = Component.translatable("gui.csgobox.edit.entities");
        RenderFontTool.drawString(guiGraphics, this.font, sectionLabel.getVisualOrderText(),
                sectionX, sectionY, 0, 0, labelScale, 0xFFAAAAAA);

        int lineY = sectionY + 12;
        int maxVisible = Math.min(state.entities.size(), 6);
        for (int i = 0; i < maxVisible; i++) {
            int idx = i + entityScrollOffset;
            if (idx >= state.entities.size()) break;
            EntityDropEntry entry = state.entities.get(idx);
            String entityLine = "  " + entry.entityId + "  " + String.format("%.0f%%", entry.rate * 100);
            RenderFontTool.drawString(guiGraphics, this.font, Component.literal(entityLine).getVisualOrderText(),
                    sectionX + 4, lineY + i * 10, 0, 0, valScale, 0xFFD3D3D3);
        }

        if (state.entities.size() > 6) {
            FormattedCharSequence moreText = Component.literal("... " + (state.entities.size() - 6) + " more").getVisualOrderText();
            RenderFontTool.drawString(guiGraphics, this.font, moreText, sectionX + 4, lineY + 6 * 10, 0, 0, 0.5F, 0xFF888888);
        }
    }

    private void renderWeightSection(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int sectionX = this.width * 3 / 100;
        int sectionY = this.height * 42 / 100;
        float labelScale = 0.6F;
        float valScale = 0.6F;

        Component sectionLabel = Component.translatable("gui.csgobox.edit.weights");
        RenderFontTool.drawString(guiGraphics, this.font, sectionLabel.getVisualOrderText(),
                sectionX, sectionY, 0, 0, labelScale, 0xFFAAAAAA);

        String[] gradeNames = {"classified", "restricted", "mil_spec", "industrial", "consumer"};
        int[] gradeColors = {0xFFD32CE6, 0xFF8847FF, 0xFF4B69FF, 0xFF4B69FF, 0xFF4B69FF};

        int totalWeight = 0;
        for (GradeEditState g : state.grades) totalWeight += g.weight;

        for (int i = 0; i < Math.min(5, state.grades.size()); i++) {
            int lineY = sectionY + 10 + i * 11;
            GradeEditState g = state.grades.get(i);
            float prob = totalWeight > 0 ? (float) g.weight / totalWeight * 100 : 0;

            guiGraphics.fill(sectionX + 4, lineY, sectionX + 6, lineY + 8, gradeColors[i]);

            String weightLine = gradeNames[i] + "  w:" + g.weight + "  " + String.format("%.1f%%", prob);
            RenderFontTool.drawString(guiGraphics, this.font, Component.literal(weightLine).getVisualOrderText(),
                    sectionX + 12, lineY + 1, 0, 0, valScale, 0xFFD3D3D3);
        }
    }

    private void renderItemPoolSection(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int sectionX = this.width * 3 / 100;
        int sectionY = this.height * 55 / 100;
        int sectionW = this.width * 94 / 100;
        float labelScale = 0.6F;
        float valScale = 0.6F;

        Component sectionLabel = Component.translatable("gui.csgobox.edit.items_pool");
        RenderFontTool.drawString(guiGraphics, this.font, sectionLabel.getVisualOrderText(),
                sectionX, sectionY, 0, 0, labelScale, 0xFFAAAAAA);

        int tabY = sectionY + 10;
        int tabW = sectionW / 5;
        for (int i = 0; i < Math.min(5, state.grades.size()); i++) {
            int tabX = sectionX + i * tabW;
            int tabColor = selectedGradeIndex == i ? 0x40FFFFFF : 0x15FFFFFF;
            guiGraphics.fill(tabX, tabY, tabX + tabW - 2, tabY + 14, tabColor);

            int gradeColor = ColorTools.colorItems(i + 1);
            guiGraphics.fill(tabX + 2, tabY + 2, tabX + 6, tabY + 12, gradeColor);

            String[] gradeNames = {"grade5", "grade4", "grade3", "grade2", "grade1"};
            String label = Component.translatable("gui.csgobox.csgo_box." + gradeNames[Math.min(i, 4)]).getString();
            FormattedCharSequence labelSeq = Component.literal(label).getVisualOrderText();
            RenderFontTool.drawString(guiGraphics, this.font, labelSeq,
                    tabX + 10, tabY + 3, 0, 0, 0.5F, 0xFFD3D3D3);
        }

        if (selectedGradeIndex < state.grades.size()) {
            GradeEditState grade = state.grades.get(selectedGradeIndex);
            int listY = tabY + 16;
            int maxVisible = Math.min(grade.items.size(), MAX_VISIBLE_ITEMS);

            for (int i = 0; i < maxVisible; i++) {
                int idx = i + itemScrollOffset;
                if (idx >= grade.items.size()) break;
                ItemStack item = grade.items.get(idx);
                int rowY = listY + i * ITEM_ROW_HEIGHT;

                int bgColor = (mouseY >= rowY && mouseY <= rowY + ITEM_ROW_HEIGHT) ? 0x30FFFFFF : 0x10FFFFFF;
                guiGraphics.fill(sectionX, rowY, sectionX + sectionW, rowY + ITEM_ROW_HEIGHT - 1, bgColor);

                if (this.minecraft != null && this.minecraft.player != null) {
                    IconListTools.renderGuiItem(this.minecraft.player, this.minecraft.player.level(), guiGraphics, item,
                            sectionX + 4, rowY + 3, 0.9F);
                }

                String itemName = item.getItem().getName(item).getString();
                String itemLine = itemName + "  x" + item.getCount();
                RenderFontTool.drawString(guiGraphics, this.font, Component.literal(itemLine).getVisualOrderText(),
                        sectionX + 24, rowY + 7, 0, 0, valScale, 0xFFD3D3D3);
            }

            if (grade.items.isEmpty()) {
                Component emptyText = Component.translatable("gui.csgobox.edit.empty_grade");
                RenderFontTool.drawString(guiGraphics, this.font, emptyText.getVisualOrderText(),
                        sectionX + 8, listY + 8, 0, 0, valScale, 0xFF888888);
            }

            int addBtnY = listY + maxVisible * ITEM_ROW_HEIGHT + 4;
            int btnW = sectionW / 2 - 4;
            boolean hoverInv = mouseX >= sectionX && mouseX <= sectionX + btnW
                    && mouseY >= addBtnY && mouseY <= addBtnY + 14;
            int invColor = hoverInv ? 0x40FFFFFF : 0x20FFFFFF;
            guiGraphics.fill(sectionX, addBtnY, sectionX + btnW, addBtnY + 14, invColor);
            Component addInvText = Component.translatable("gui.csgobox.edit.add_from_inventory");
            RenderFontTool.drawString(guiGraphics, this.font, addInvText.getVisualOrderText(),
                    sectionX + 4, addBtnY + 3, 0, 0, 0.5F, 0xFFAAAAAA);

            boolean hoverId = mouseX >= sectionX + btnW + 4 && mouseX <= sectionX + sectionW
                    && mouseY >= addBtnY && mouseY <= addBtnY + 14;
            int idColor = hoverId ? 0x40FFFFFF : 0x20FFFFFF;
            guiGraphics.fill(sectionX + btnW + 4, addBtnY, sectionX + sectionW, addBtnY + 14, idColor);
            Component addIdText = Component.translatable("gui.csgobox.edit.add_by_id");
            RenderFontTool.drawString(guiGraphics, this.font, addIdText.getVisualOrderText(),
                    sectionX + btnW + 8, addBtnY + 3, 0, 0, 0.5F, 0xFFAAAAAA);
        }
    }

    private void renderBottomButtons(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int btnY = this.height * 94 / 100;
        int btnH = this.height * 5 / 100;

        int saveX = this.width * 3 / 100;
        int saveW = this.width * 18 / 100;
        boolean saveHover = mouseX >= saveX && mouseX <= saveX + saveW && mouseY >= btnY && mouseY <= btnY + btnH;
        int saveFill = saveHover ? 0xFF00CC00 : 0xFF00AA00;
        int saveBorder = saveHover ? 0xFF00FF00 : 0xFF00CC00;
        guiGraphics.fill(saveX, btnY, saveX + saveW, btnY + btnH, saveBorder);
        guiGraphics.fill(saveX + 1, btnY + 1, saveX + saveW - 1, btnY + btnH - 1, saveFill);
        Component saveText = Component.translatable("gui.csgobox.edit.save");
        FormattedCharSequence saveSeq = saveText.getVisualOrderText();
        float saveTextW = this.font.width(saveSeq) * 0.7F;
        RenderFontTool.drawString(guiGraphics, this.font, saveSeq,
                saveX + (saveW - saveTextW) / 2.0F, btnY + btnH * 0.2F, 0, 0, 0.7F, 0xFFFFFFFF);

        int delX = saveX + saveW + 4;
        int delW = this.width * 18 / 100;
        boolean delHover = mouseX >= delX && mouseX <= delX + delW && mouseY >= btnY && mouseY <= btnY + btnH;
        int delFill = delHover ? 0xFFCC0000 : 0xFFAA0000;
        int delBorder = delHover ? 0xFFFF0000 : 0xFFCC0000;
        guiGraphics.fill(delX, btnY, delX + delW, btnY + btnH, delBorder);
        guiGraphics.fill(delX + 1, btnY + 1, delX + delW - 1, btnY + btnH - 1, delFill);
        Component delText = Component.translatable("gui.csgobox.edit.delete");
        FormattedCharSequence delSeq = delText.getVisualOrderText();
        float delTextW = this.font.width(delSeq) * 0.7F;
        RenderFontTool.drawString(guiGraphics, this.font, delSeq,
                delX + (delW - delTextW) / 2.0F, btnY + btnH * 0.2F, 0, 0, 0.7F, 0xFFFFFFFF);
    }

    private void renderIdInputDialog(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int dialogW = this.width * 50 / 100;
        int dialogH = this.height * 20 / 100;
        int dialogX = (this.width - dialogW) / 2;
        int dialogY = this.height * 35 / 100;

        guiGraphics.fill(dialogX, dialogY, dialogX + dialogW, dialogY + dialogH, 0xCC000000);
        guiGraphics.fill(dialogX, dialogY, dialogX + dialogW, dialogY + 1, 0xFF888888);
        guiGraphics.fill(dialogX, dialogY + dialogH - 1, dialogX + dialogW, dialogY + dialogH, 0xFF888888);

        Component prompt = Component.translatable("gui.csgobox.create.id_prompt");
        RenderFontTool.drawString(guiGraphics, this.font, prompt.getVisualOrderText(),
                dialogX + 8, dialogY + 8, 0, 0, 0.7F, 0xFFD3D3D3);

        String display = boxIdInput + (System.currentTimeMillis() % 1000 > 500 ? "_" : " ");
        RenderFontTool.drawString(guiGraphics, this.font, Component.literal(display).getVisualOrderText(),
                dialogX + 12, dialogY + 30, 0, 0, 0.8F, 0xFFFFFFFF);

        Component confirm = Component.translatable("gui.csgobox.edit.save");
        FormattedCharSequence confirmSeq = confirm.getVisualOrderText();
        float confirmW = this.font.width(confirmSeq) * 0.7F;
        RenderFontTool.drawString(guiGraphics, this.font, confirmSeq,
                dialogX + dialogW - confirmW - 12, dialogY + dialogH - 16, 0, 0, 0.7F, 0xFF00FF00);
    }

    private void renderItemIdInputDialog(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int dialogW = this.width * 50 / 100;
        int dialogH = this.height * 20 / 100;
        int dialogX = (this.width - dialogW) / 2;
        int dialogY = this.height * 35 / 100;

        guiGraphics.fill(dialogX, dialogY, dialogX + dialogW, dialogY + dialogH, 0xCC000000);
        guiGraphics.fill(dialogX, dialogY, dialogX + dialogW, dialogY + 1, 0xFF888888);
        guiGraphics.fill(dialogX, dialogY + dialogH - 1, dialogX + dialogW, dialogY + dialogH, 0xFF888888);

        Component prompt = Component.translatable("gui.csgobox.edit.item_id_prompt");
        RenderFontTool.drawString(guiGraphics, this.font, prompt.getVisualOrderText(),
                dialogX + 8, dialogY + 8, 0, 0, 0.7F, 0xFFD3D3D3);

        String display = itemIdInput + (System.currentTimeMillis() % 1000 > 500 ? "_" : " ");
        RenderFontTool.drawString(guiGraphics, this.font, Component.literal(display).getVisualOrderText(),
                dialogX + 12, dialogY + 30, 0, 0, 0.8F, 0xFFFFFFFF);

        Component confirm = Component.translatable("gui.csgobox.edit.save");
        FormattedCharSequence confirmSeq = confirm.getVisualOrderText();
        float confirmW = this.font.width(confirmSeq) * 0.7F;
        RenderFontTool.drawString(guiGraphics, this.font, confirmSeq,
                dialogX + dialogW - confirmW - 12, dialogY + dialogH - 16, 0, 0, 0.7F, 0xFF00FF00);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        if (awaitingIdInput) {
            handleIdInputClick(mouseX, mouseY);
            return true;
        }
        if (awaitingItemIdInput) {
            handleItemIdInputClick(mouseX, mouseY);
            return true;
        }

        float headerY = this.height * 2F / 100F;
        if (mouseY <= headerY + 16 && mouseX <= this.width * 10 / 100) {
            if (this.minecraft != null) {
                this.minecraft.setScreen(parent);
            }
            return true;
        }

        handleItemPoolClick(mouseX, mouseY);

        int btnY = this.height * 94 / 100;
        int btnH = this.height * 5 / 100;
        int saveX = this.width * 3 / 100;
        int saveW = this.width * 18 / 100;
        if (mouseX >= saveX && mouseX <= saveX + saveW && mouseY >= btnY && mouseY <= btnY + btnH) {
            saveBox();
            return true;
        }

        int delX = saveX + saveW + 4;
        int delW = this.width * 18 / 100;
        if (mouseX >= delX && mouseX <= delX + delW && mouseY >= btnY && mouseY <= btnY + btnH) {
            if (this.minecraft != null && !isNew) {
                String boxId = boxIdInput.isEmpty() ? "unknown" : boxIdInput;
                this.minecraft.setScreen(new ConfirmScreen(
                        confirmed -> {
                            if (confirmed) {
                                deleteBox();
                                this.minecraft.setScreen(parent);
                            } else {
                                this.minecraft.setScreen(this);
                            }
                        },
                        Component.translatable("gui.csgobox.edit.delete"),
                        Component.translatable("gui.csgobox.edit.delete_confirm", state.name)
                ));
            }
            return true;
        }

        handleTabClick(mouseX, mouseY);

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void handleTabClick(double mouseX, double mouseY) {
        int sectionX = this.width * 3 / 100;
        int sectionY = this.height * 55 / 100;
        int sectionW = this.width * 94 / 100;
        int tabY = sectionY + 10;
        int tabW = sectionW / 5;

        for (int i = 0; i < Math.min(5, state.grades.size()); i++) {
            int tabX = sectionX + i * tabW;
            if (mouseX >= tabX && mouseX <= tabX + tabW - 2 && mouseY >= tabY && mouseY <= tabY + 14) {
                selectedGradeIndex = i;
                itemScrollOffset = 0;
                return;
            }
        }
    }

    private void handleItemPoolClick(double mouseX, double mouseY) {
        int sectionX = this.width * 3 / 100;
        int sectionY = this.height * 55 / 100;
        int sectionW = this.width * 94 / 100;
        int tabY = sectionY + 10;
        int listY = tabY + 16;

        if (selectedGradeIndex >= state.grades.size()) return;
        GradeEditState grade = state.grades.get(selectedGradeIndex);
        int maxVisible = Math.min(grade.items.size(), MAX_VISIBLE_ITEMS);

        for (int i = 0; i < maxVisible; i++) {
            int idx = i + itemScrollOffset;
            if (idx >= grade.items.size()) break;
            int rowY = listY + i * ITEM_ROW_HEIGHT;
            if (mouseX >= sectionX && mouseX <= sectionX + sectionW && mouseY >= rowY && mouseY <= rowY + ITEM_ROW_HEIGHT) {
                grade.items.remove(idx);
                markDirty();
                return;
            }
        }

        int addBtnY = listY + maxVisible * ITEM_ROW_HEIGHT + 4;
        int btnW = sectionW / 2 - 4;
        if (mouseX >= sectionX && mouseX <= sectionX + btnW && mouseY >= addBtnY && mouseY <= addBtnY + 14) {
            addFromInventory();
            return;
        }
        if (mouseX >= sectionX + btnW + 4 && mouseX <= sectionX + sectionW && mouseY >= addBtnY && mouseY <= addBtnY + 14) {
            awaitingItemIdInput = true;
            itemIdInput = "";
            return;
        }
    }

    private void handleIdInputClick(double mouseX, double mouseY) {
        int dialogW = this.width * 50 / 100;
        int dialogH = this.height * 20 / 100;
        int dialogX = (this.width - dialogW) / 2;
        int dialogY = this.height * 35 / 100;

        Component confirm = Component.translatable("gui.csgobox.edit.save");
        float confirmW = this.font.width(confirm.getVisualOrderText()) * 0.7F;
        if (mouseX >= dialogX + dialogW - confirmW - 12 && mouseY >= dialogY + dialogH - 16 && mouseY <= dialogY + dialogH) {
            if (!boxIdInput.isEmpty()) {
                awaitingIdInput = false;
                markDirty();
            }
            return;
        }
    }

    private void handleItemIdInputClick(double mouseX, double mouseY) {
        int dialogW = this.width * 50 / 100;
        int dialogH = this.height * 20 / 100;
        int dialogX = (this.width - dialogW) / 2;
        int dialogY = this.height * 35 / 100;

        Component confirm = Component.translatable("gui.csgobox.edit.save");
        float confirmW = this.font.width(confirm.getVisualOrderText()) * 0.7F;
        if (mouseX >= dialogX + dialogW - confirmW - 12 && mouseY >= dialogY + dialogH - 16 && mouseY <= dialogY + dialogH) {
            if (!itemIdInput.isEmpty() && selectedGradeIndex < state.grades.size()) {
                addItemById(itemIdInput);
                awaitingItemIdInput = false;
                itemIdInput = "";
            }
            return;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int sectionY = this.height * 55 / 100;
        int tabY = sectionY + 10;
        int listY = tabY + 16;

        if (mouseY >= listY && selectedGradeIndex < state.grades.size()) {
            GradeEditState grade = state.grades.get(selectedGradeIndex);
            int maxScroll = Math.max(0, grade.items.size() - MAX_VISIBLE_ITEMS);
            itemScrollOffset = Math.clamp(itemScrollOffset - (int) scrollY, 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == 256) {
            if (awaitingIdInput) {
                awaitingIdInput = false;
                return true;
            }
            if (awaitingItemIdInput) {
                awaitingItemIdInput = false;
                return true;
            }
            if (this.minecraft != null) {
                this.minecraft.setScreen(parent);
            }
            return true;
        }

        if (awaitingIdInput) {
            if (key == 257 || key == 335) {
                if (!boxIdInput.isEmpty()) {
                    awaitingIdInput = false;
                    markDirty();
                }
                return true;
            }
            if (key == 259 && !boxIdInput.isEmpty()) {
                boxIdInput = boxIdInput.substring(0, boxIdInput.length() - 1);
                return true;
            }
            return true;
        }

        if (awaitingItemIdInput) {
            if (key == 257 || key == 335) {
                if (!itemIdInput.isEmpty() && selectedGradeIndex < state.grades.size()) {
                    addItemById(itemIdInput);
                    awaitingItemIdInput = false;
                    itemIdInput = "";
                }
                return true;
            }
            if (key == 259 && !itemIdInput.isEmpty()) {
                itemIdInput = itemIdInput.substring(0, itemIdInput.length() - 1);
                return true;
            }
            return true;
        }

        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (awaitingIdInput) {
            if (codePoint >= 32 && codePoint != 127) {
                boxIdInput += codePoint;
                return true;
            }
            return true;
        }

        if (awaitingItemIdInput) {
            if (codePoint >= 32 && codePoint != 127) {
                itemIdInput += codePoint;
                return true;
            }
            return true;
        }

        return super.charTyped(codePoint, modifiers);
    }

    private void addFromInventory() {
        if (this.minecraft == null || this.minecraft.player == null) return;
        if (selectedGradeIndex >= state.grades.size()) return;

        GradeEditState grade = state.grades.get(selectedGradeIndex);
        for (ItemStack stack : this.minecraft.player.getInventory().items) {
            if (!stack.isEmpty()) {
                boolean duplicate = false;
                for (ItemStack existing : grade.items) {
                    if (ItemStack.isSameItemSameComponents(existing, stack)) {
                        duplicate = true;
                        break;
                    }
                }
                if (!duplicate) {
                    grade.items.add(stack.copy());
                    markDirty();
                }
            }
        }
    }

    private void addItemById(String id) {
        if (selectedGradeIndex >= state.grades.size()) return;
        try {
            ResourceLocation rl = ResourceLocation.parse(id);
            var item = BuiltInRegistries.ITEM.get(rl);
            if (item != null) {
                ItemStack stack = new ItemStack(item);
                GradeEditState grade = state.grades.get(selectedGradeIndex);
                grade.items.add(stack);
                markDirty();
            }
        } catch (Exception ignored) {
        }
    }

    private void saveBox() {
        ResourceLocation id;
        if (isNew && !boxIdInput.isEmpty()) {
            id = ResourceLocation.parse(boxIdInput);
        } else if (isNew) {
            id = ResourceLocation.parse("csgobox:custom_box_" + System.currentTimeMillis() % 10000);
        } else {
            id = ResourceLocation.parse("csgobox:" + boxIdInput);
        }

        BoxDefinition def = state.toBoxDefinition(id);
        BoxRegistry.register(def);
        BoxJsonLoader.saveToFile(def);
        dirty = false;

        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    private void deleteBox() {
        if (isNew) return;
        ResourceLocation id = ResourceLocation.parse("csgobox:" + boxIdInput);
        BoxJsonLoader.deleteFile(id);
    }

    private void markDirty() {
        this.dirty = true;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    static class EditState {
        String name;
        float dropRate;
        ResourceLocation keyItem;
        Optional<ResourceLocation> texture;
        Optional<ResourceLocation> sound;
        List<EntityDropEntry> entities;
        List<GradeEditState> grades;

        EditState() {
            this.entities = new ArrayList<>();
            this.grades = new ArrayList<>();
        }

        static EditState from(BoxDefinition def) {
            EditState s = new EditState();
            s.name = def.name().getString();
            s.dropRate = def.dropRate();
            s.keyItem = def.keyItem();
            s.texture = def.texture();
            s.sound = def.sound();
            s.entities = new ArrayList<>();
            if (!def.entityDropRates().isEmpty()) {
                for (var entry : def.entityDropRates().entrySet()) {
                    s.entities.add(new EntityDropEntry(entry.getKey(), entry.getValue()));
                }
            } else {
                for (ResourceLocation entity : def.dropEntities()) {
                    s.entities.add(new EntityDropEntry(entity, def.dropRate()));
                }
            }
            s.grades = new ArrayList<>();
            for (GradeGroup g : def.grades()) {
                s.grades.add(GradeEditState.from(g));
            }
            return s;
        }

        BoxDefinition toBoxDefinition(ResourceLocation id) {
            BoxDefinition.Builder builder = BoxDefinition.builder(id, name);
            builder.key(keyItem);
            builder.dropRate(dropRate);
            texture.ifPresent(builder::texture);
            sound.ifPresent(builder::sound);
            for (EntityDropEntry entry : entities) {
                builder.dropFrom(entry.entityId.toString());
                builder.entityDropRate(entry.entityId.toString(), entry.rate);
            }
            String[] gradeIds = {"classified", "restricted", "mil_spec", "industrial", "consumer"};
            String[] gradeNames = {"保密", "受限", "军规级", "工业级", "消费级"};
            int[] gradeColors = {0xFFD32CE6, 0xFF8847FF, 0xFF4B69FF, 0xFF4B69FF, 0xFF4B69FF};
            for (int i = 0; i < grades.size(); i++) {
                GradeEditState g = grades.get(i);
                int idx = Math.min(i, 4);
                builder.addGrade(new GradeGroup(gradeIds[idx], gradeNames[idx], gradeColors[idx], g.weight, List.copyOf(g.items)));
            }
            return builder.build();
        }
    }

    static class GradeEditState {
        int weight;
        List<ItemStack> items;

        GradeEditState(int weight, List<ItemStack> items) {
            this.weight = weight;
            this.items = new ArrayList<>(items);
        }

        static GradeEditState from(GradeGroup g) {
            return new GradeEditState(g.weight(), new ArrayList<>(g.items()));
        }
    }

    static class EntityDropEntry {
        ResourceLocation entityId;
        float rate;

        EntityDropEntry(ResourceLocation entityId, float rate) {
            this.entityId = entityId;
            this.rate = rate;
        }
    }
}