package com.reclizer.csgobox.v26_2.gui;

import com.reclizer.csgobox.utils.ColorTools;
import com.reclizer.csgobox.utils.OverlayColor;
import com.reclizer.csgobox.v26_2.utils.IconListTools;
import com.reclizer.csgobox.v26_2.utils.RenderFontTool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bottom-up 2D waterfall display for the rewards of a bulk box open. Items
 * are pushed onto the feed on a fixed cadence (CS:GO style ticker feed),
 * fade in, hold, then fade out. After all entries have been shown and the
 * last one has aged past its lifetime, a "collect" button appears.
 */
public class CsboxBulkResultScreen extends Screen {
    private static final int MAX_VISIBLE = 8;
    private static final long LIFE_TICKS = 5000;
    private static final long TICKS_PER_ENTRY = 200;

    private final Player player;
    private final List<ItemStack> allItems;
    private final List<Integer> allGrades;
    private final Deque<Entry> visible = new ArrayDeque<>();
    private int cursor = 0;
    private long lastAddTick = 0;
    private long lastTickTime = 0;
    private boolean showAllItems = false;

    public CsboxBulkResultScreen(Player player, List<ItemStack> items, List<Integer> grades) {
        super(Minecraft.getInstance(), Minecraft.getInstance().font, Component.literal("csgo_bulk_result"));
        this.player = player;
        this.allItems = new ArrayList<>();
        for (ItemStack s : items) {
            this.allItems.add(s == null ? ItemStack.EMPTY : s.copy());
        }
        this.allGrades = new ArrayList<>();
        for (Integer g : grades) {
            this.allGrades.add(g == null ? 1 : g);
        }
    }

    private static final class Entry {
        final ItemStack stack;
        final int grade;
        final long appearTick;
        final int index;

        Entry(ItemStack stack, int grade, long appearTick, int index) {
            this.stack = stack.copy();
            this.grade = grade;
            this.appearTick = appearTick;
            this.index = index;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.minecraft == null || this.minecraft.level == null) {
            com.reclizer.csgobox.v26_2.CsgoBox.LOGGER.info("[csbox-bulk-result] tick skip level-null");
            return;
        }
        long now = System.currentTimeMillis();
        if (lastTickTime == 0) {
            lastTickTime = now;
            lastAddTick = now;
        }
        if (cursor < allItems.size() && now - lastAddTick >= TICKS_PER_ENTRY) {
            ItemStack s = allItems.get(cursor);
            int g = cursor < allGrades.size() ? allGrades.get(cursor) : 1;
            visible.addFirst(new Entry(s, g, now, cursor + 1));
            while (visible.size() > MAX_VISIBLE) {
                visible.pollLast();
            }
            cursor++;
            lastAddTick = now;
        }
        if (cursor >= allItems.size() && !visible.isEmpty()) {
            Entry oldest = ((ArrayDeque<Entry>) visible).getLast();
            if (now - oldest.appearTick > LIFE_TICKS) {
                visible.pollLast();
            }
        }
        lastTickTime = now;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);
        if (this.minecraft != null && this.minecraft.level != null) {
            guiGraphics.fillGradient(0, 0, this.width, this.height,
                    OverlayColor.getBackgroundColor(), OverlayColor.getBackgroundColor());
        }
        renderHeader(guiGraphics);
        renderEntries(guiGraphics, partialTicks);
        renderFooter(guiGraphics, mouseX, mouseY);
    }

    private void renderHeader(GuiGraphicsExtractor guiGraphics) {
        Style titleStyle = Style.EMPTY.withBold(true);
        Component title = Component.translatable("gui.csgobox.bulk.title").withStyle(titleStyle);
        RenderFontTool.drawString(guiGraphics, this.font, title.getVisualOrderText(),
                (this.width - this.font.width(title)) * 0.5F, this.height * 0.06F, 0, 0, 1.4F, 0xFFFFFFFF);
        int shown = cursor;
        int total = allItems.size();
        Component progress = Component.literal(shown + " / " + total);
        RenderFontTool.drawString(guiGraphics, this.font, progress.getVisualOrderText(),
                (this.width - this.font.width(progress)) * 0.5F, this.height * 0.13F, 0, 0, 0.9F, 0xFFAAAAAA);
    }

    private void renderEntries(GuiGraphicsExtractor guiGraphics, float partialTicks) {
        if (this.minecraft == null || this.minecraft.level == null) {
            return;
        }
        long now = System.currentTimeMillis();

        int rowH = this.height / 22;
        int baseY = this.height * 92 / 100;
        int colW = Math.min(this.width * 35 / 100, 360);
        int x = (this.width - colW) / 2;

        int index = 0;
        for (Entry e : visible) {
            float age = (float) (now - e.appearTick) / LIFE_TICKS;
            float alpha;
            if (age < 0.10F) {
                alpha = age / 0.10F;
            } else if (age > 0.85F) {
                alpha = Math.max(0F, (1F - age) / 0.15F);
            } else {
                alpha = 1F;
            }
            int y = baseY - index * rowH;
            int intAlpha = (int) (alpha * 255F) & 0xFF;
            int frameColor = (intAlpha << 24) | (ColorTools.colorItems(e.grade) & 0x00FFFFFF);
            int labelColor = (intAlpha << 24) | 0x00EFEFEF;

            int itemSize = Math.min(rowH - 4, 32);
            int itemX = x;
            int itemY = y - itemSize / 2;
            guiGraphics.fill(x - 1, y - itemSize / 2 - 1, x + colW + 1, y + itemSize / 2 + 1, (intAlpha << 24) | 0x101010);
            guiGraphics.fill(itemX, itemY, itemX + 2, itemY + itemSize, frameColor);
            if (e.stack.isEmpty()) {
                guiGraphics.fill(itemX + 2, itemY, itemX + itemSize + 2, itemY + itemSize, (intAlpha << 24) | OverlayColor.dividerDim());
            } else if (this.player != null) {
                IconListTools.renderItemFrame(this.player, guiGraphics, e.stack,
                        itemX + 2, itemY, colW, itemSize, e.grade);
            }
            String label = e.stack.isEmpty()
                    ? "(" + e.index + ")"
                    : e.stack.getHoverName().getString() + "  ×" + e.stack.getCount() + "   #" + e.index;
            FormattedCharSequence seq = Component.literal(label).getVisualOrderText();
            RenderFontTool.drawString(guiGraphics, this.font, seq,
                    itemX + itemSize + 12, y - this.font.lineHeight * 0.5F, 0, 0, 0.9F, labelColor);
            index++;
        }
    }

    private void renderFooter(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        if (showAllItems) {
            renderAllItemsGrid(guiGraphics, mouseX, mouseY);
            return;
        }
        if (cursor < allItems.size()) {
            Component waiting = Component.translatable("gui.csgobox.bulk.waterfall_empty");
            RenderFontTool.drawString(guiGraphics, this.font, waiting.getVisualOrderText(),
                    (this.width - this.font.width(waiting)) * 0.5F, this.height * 0.18F, 0, 0, 0.9F, 0xFFAAAAAA);
            return;
        }
        if (!visible.isEmpty()) {
            return;
        }
        int btnW = Math.max(120, this.width * 14 / 100);
        int btnH = this.height * 5 / 100;
        int btnSpacing = 16;
        int totalBtnWidth = btnW * 2 + btnSpacing;
        int btnY = this.height * 86 / 100;

        int showAllX = (this.width - totalBtnWidth) / 2;
        boolean showAllHover = isInside(mouseX, mouseY, showAllX, btnY, btnW, btnH);
        int showAllFill = showAllHover ? 0xFF00AACC : 0xFF0088AA;
        int showAllBorder = showAllHover ? 0xFF00DDFF : 0xFF00AACC;
        guiGraphics.fill(showAllX, btnY, showAllX + btnW, btnY + btnH, showAllBorder);
        guiGraphics.fill(showAllX + 1, btnY + 1, showAllX + btnW - 1, btnY + btnH - 1, showAllFill);
        Style style = Style.EMPTY.withBold(true);
        Component showAllText = Component.translatable("gui.csgobox.bulk.show_all").withStyle(style);
        FormattedCharSequence showAllSeq = showAllText.getVisualOrderText();
        float showAllTextW = this.font.width(showAllSeq) * 0.95F;
        float showAllTextX = showAllX + (btnW - showAllTextW) / 2.0F;
        float showAllTextY = btnY + (btnH - this.font.lineHeight * 0.95F) / 2.0F + 1;
        RenderFontTool.drawString(guiGraphics, this.font, showAllSeq, showAllTextX, showAllTextY, 0, 0, 0.95F, 0xFFFFFFFF);

        int collectX = showAllX + btnW + btnSpacing;
        boolean collectHover = isInside(mouseX, mouseY, collectX, btnY, btnW, btnH);
        int collectFill = collectHover ? 0xFF00CC00 : 0xFF008800;
        int collectBorder = collectHover ? 0xFF00FF00 : 0xFF00AA00;
        guiGraphics.fill(collectX, btnY, collectX + btnW, btnY + btnH, collectBorder);
        guiGraphics.fill(collectX + 1, btnY + 1, collectX + btnW - 1, btnY + btnH - 1, collectFill);
        Component collectText = Component.translatable("gui.csgobox.bulk.collect").withStyle(style);
        FormattedCharSequence collectSeq = collectText.getVisualOrderText();
        float collectTextW = this.font.width(collectSeq) * 0.95F;
        float collectTextX = collectX + (btnW - collectTextW) / 2.0F;
        float collectTextY = btnY + (btnH - this.font.lineHeight * 0.95F) / 2.0F + 1;
        RenderFontTool.drawString(guiGraphics, this.font, collectSeq, collectTextX, collectTextY, 0, 0, 0.95F, 0xFFFFFFFF);
    }

    private void renderAllItemsGrid(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        Map<ItemStack, Integer> consolidated = new LinkedHashMap<>();
        Map<ItemStack, Integer> gradeMap = new LinkedHashMap<>();
        for (int i = 0; i < allItems.size(); i++) {
            ItemStack stack = allItems.get(i);
            int grade = i < allGrades.size() ? allGrades.get(i) : 1;
            boolean found = false;
            for (Map.Entry<ItemStack, Integer> entry : consolidated.entrySet()) {
                if (ItemStack.isSameItemSameComponents(stack, entry.getKey())) {
                    entry.setValue(entry.getValue() + stack.getCount());
                    found = true;
                    break;
                }
            }
            if (!found && !stack.isEmpty()) {
                consolidated.put(stack.copy(), stack.getCount());
                gradeMap.put(stack.copy(), grade);
            }
        }

        int cols = Math.min(8, this.width / 80);
        int itemSize = Math.min(64, this.width / cols - 12);
        int rows = (int) Math.ceil((double) consolidated.size() / cols);
        int gridWidth = cols * (itemSize + 8);
        int startX = (this.width - gridWidth) / 2;
        int startY = this.height * 18 / 100;

        Component title = Component.translatable("gui.csgobox.bulk.all_rewards_title");
        RenderFontTool.drawString(guiGraphics, this.font, title.getVisualOrderText(),
                (this.width - this.font.width(title)) * 0.5F, this.height * 0.06F, 0, 0, 1.2F, 0xFFFFFFFF);

        int idx = 0;
        for (Map.Entry<ItemStack, Integer> entry : consolidated.entrySet()) {
            int col = idx % cols;
            int row = idx / cols;
            int x = startX + col * (itemSize + 8);
            int y = startY + row * (itemSize + 8);
            ItemStack stack = entry.getKey();
            int count = entry.getValue();
            int grade = gradeMap.getOrDefault(stack, 1);

            int bgColor = (0xCC << 24) | (ColorTools.colorItems(grade) & 0x00FFFFFF);
            guiGraphics.fillGradient(x, y, x + itemSize + 4, y + itemSize + 4, bgColor, bgColor);
            guiGraphics.fill(x, y, x + 3, y + itemSize + 4, ColorTools.colorItems(grade));

            if (this.player != null) {
                IconListTools.renderItemFrame(this.player, guiGraphics, stack,
                        x + 2, y + 2, itemSize + 4, itemSize + 4, grade);
            }

            if (count > 1) {
                Component countText = Component.literal("x" + count);
                int countW = this.font.width(countText);
                RenderFontTool.drawString(guiGraphics, this.font, countText.getVisualOrderText(),
                        x + itemSize + 4 - countW - 2, y + itemSize + 4 - this.font.lineHeight - 2, 0, 0, 0.8F, 0xFFFFFFFF);
            }
            idx++;
        }

        int btnW = Math.max(120, this.width * 14 / 100);
        int btnH = this.height * 5 / 100;
        int btnX = (this.width - btnW) / 2;
        int btnY = this.height * 92 / 100;
        boolean hover = isInside(mouseX, mouseY, btnX, btnY, btnW, btnH);
        int fill = hover ? 0xFF00CC00 : 0xFF008800;
        int border = hover ? 0xFF00FF00 : 0xFF00AA00;
        guiGraphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, border);
        guiGraphics.fill(btnX + 1, btnY + 1, btnX + btnW - 1, btnY + btnH - 1, fill);
        Style style = Style.EMPTY.withBold(true);
        Component text = Component.translatable("gui.csgobox.bulk.collect").withStyle(style);
        FormattedCharSequence seq = text.getVisualOrderText();
        float textW = this.font.width(seq) * 0.95F;
        float textX = btnX + (btnW - textW) / 2.0F;
        float textY = btnY + (btnH - this.font.lineHeight * 0.95F) / 2.0F + 1;
        RenderFontTool.drawString(guiGraphics, this.font, seq, textX, textY, 0, 0, 0.95F, 0xFFFFFFFF);
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && cursor >= allItems.size() && visible.isEmpty()) {
            double mouseX = event.x();
            double mouseY = event.y();
            int btnW = Math.max(120, this.width * 14 / 100);
            int btnH = this.height * 5 / 100;
            int btnY = this.height * 86 / 100;

            if (showAllItems) {
                int btnX = (this.width - btnW) / 2;
                if (isInside(mouseX, mouseY, btnX, btnY, btnW, btnH)) {
                    this.onClose();
                    return true;
                }
            } else {
                int btnSpacing = 16;
                int totalBtnWidth = btnW * 2 + btnSpacing;
                int showAllX = (this.width - totalBtnWidth) / 2;
                int collectX = showAllX + btnW + btnSpacing;

                if (isInside(mouseX, mouseY, showAllX, btnY, btnW, btnH)) {
                    showAllItems = true;
                    return true;
                }
                if (isInside(mouseX, mouseY, collectX, btnY, btnW, btnH)) {
                    this.onClose();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) {
            this.onClose();
            return true;
        }
        return super.keyPressed(event);
    }
}
