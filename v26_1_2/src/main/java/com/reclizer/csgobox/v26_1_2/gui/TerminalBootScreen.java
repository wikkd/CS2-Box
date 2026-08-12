package com.reclizer.csgobox.v26_1_2.gui;

import com.reclizer.csgobox.utils.Easing;
import com.reclizer.csgobox.utils.ItemDrag3D;
import com.reclizer.csgobox.utils.GuiRegion;
import com.reclizer.csgobox.utils.OverlayColor;
import com.reclizer.csgobox.v26_1_2.CsgoBox;
import com.reclizer.csgobox.v26_1_2.box.BoxDefinition;
import com.reclizer.csgobox.v26_1_2.box.GradeGroup;
import com.reclizer.csgobox.v26_1_2.item.ItemCsgoBox;
import com.reclizer.csgobox.v26_1_2.utils.AnimRenderOps;
import com.reclizer.csgobox.v26_1_2.utils.ButtonPalette;
import com.reclizer.csgobox.v26_1_2.utils.GuiItemMove;
import com.reclizer.csgobox.v26_1_2.utils.IconListTools;
import com.reclizer.csgobox.v26_1_2.utils.RenderFontTool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Terminal boot screen — the pre-open step before the full terminal UI.
 * A pixel-for-pixel replica of the crate's pre-open screen (CsboxScreen):
 * big title, box name, item grade grid with names, gold slot, key counter,
 * page scroll and bottom action pills — with the 3D crate swapped for the
 * terminal model. Opening hands off to {@link TerminalScreen}.
 *
 * era: decoupled
 */
public class TerminalBootScreen extends Screen {

    private final ItemStack terminalStack;
    private final boolean boxEmpty;
    private final Component boxName;
    private final List<ItemStack> itemsList = new ArrayList<>();
    private final List<Integer> gradeList = new ArrayList<>();
    private int page;
    private int enterTicks = 0;

    private final ItemDrag3D itemDrag = new ItemDrag3D(-0.35F, 0.5F);

    private static final int ITEMS_PER_PAGE = 20;
    private static final int ENTER_TICKS = 6;

    public TerminalBootScreen(ItemStack terminalStack) {
        super(Component.translatable("gui.csgobox.terminal.title"));
        this.terminalStack = terminalStack;
        BoxDefinition def = ItemCsgoBox.getDefinition(terminalStack).orElse(null);
        if (def != null) {
            this.boxName = def.name();
            List<ItemStack> items = new ArrayList<>();
            List<Integer> grades = new ArrayList<>();
            for (GradeGroup grade : def.grades()) {
                int lvl = BoxDefinition.gradeLevel(grade.id());
                if (lvl <= 0) {
                    continue;
                }
                for (ItemStack stack : grade.items()) {
                    items.add(stack);
                    grades.add(lvl);
                }
            }
            // stable sort by grade ascending — same ordering the crate screen
            // derives from its server sync (grade 1 first, gold last).
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                order.add(i);
            }
            order.sort((a, b) -> Integer.compare(grades.get(a), grades.get(b)));
            for (int idx : order) {
                itemsList.add(items.get(idx));
                gradeList.add(grades.get(idx));
            }
            this.boxEmpty = itemsList.isEmpty();
        } else {
            this.boxName = terminalStack.getHoverName();
            this.boxEmpty = true;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- geometry (same preview band & buttons as CsboxScreen) ----
    private int previewTextureSize() {
        int containerTop = this.height * 12 / 100;
        int containerBottom = this.height * 53 / 100;
        int containerHeight = containerBottom - containerTop;
        return Math.max(128, Math.min(this.width * 24 / 100, containerHeight * 82 / 100));
    }

    private int previewPixelX() {
        return (this.width - previewTextureSize()) / 2;
    }

    private int previewPixelY() {
        int containerTop = this.height * 12 / 100;
        int containerBottom = this.height * 53 / 100;
        return (containerTop + containerBottom - previewTextureSize()) / 2;
    }

    private int actionButtonWidth() {
        return Math.max(72, this.width * 8 / 100);
    }

    private int openButtonX() {
        return Math.max(8, this.width - actionButtonWidth() * 2 - 20);
    }

    private int closeButtonX() {
        return openButtonX() + actionButtonWidth() + 8;
    }

    private int buttonY() {
        return this.height * 94 / 100;
    }

    private int buttonHeight() {
        return Math.max(18, this.height * 5 / 100);
    }

    // ---- pagination (mirrors CsboxScreen) ----
    private int renderableCount() {
        int count = 0;
        for (int i = 0; i < itemsList.size(); i++) {
            if (gradeList.get(i) > 4) {
                break;
            }
            count++;
        }
        return count;
    }

    private int pageCount() {
        int n = renderableCount();
        return Math.max(1, (n + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        if (this.enterTicks < ENTER_TICKS) {
            this.enterTicks++;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor gg, int mouseX, int mouseY, float partialTicks) {
        super.extractRenderState(gg, mouseX, mouseY, partialTicks);
        this.itemDrag.tick();
        renderBg(gg, partialTicks, mouseX, mouseY);
        renderLabels(gg, mouseX, mouseY);
    }

    protected void renderBg(GuiGraphicsExtractor gg, float partialTicks, int gx, int gy) {
        if (this.minecraft != null && this.minecraft.level != null) {
            int fill = UiBackdrop.fill();
            AnimRenderOps.fillGradient(gg, 0, 0, this.width, this.height, fill, fill);
        }
        GuiRegion.Region listArea = GuiRegion.list(this.width, this.height);
        AnimRenderOps.fill(gg, listArea.x(), listArea.y(), listArea.right(), listArea.y() + 1,
                OverlayColor.divider());
        GuiRegion.Region footer = GuiRegion.fullWidthRow(this.width, this.height, 92, 1);
        AnimRenderOps.fill(gg, footer.x(), footer.y(), footer.right(), footer.bottom(),
                OverlayColor.divider());

        Player player = this.minecraft != null ? this.minecraft.player : null;
        // Skip the 3D model when the terminal has no configured loot — the
        // warning banner (renderLabels) occupies that band instead.
        if (!boxEmpty && player != null) {
            float scale = previewTextureSize() / 16F;
            GuiItemMove.renderItemInInventoryFollowsMouse(gg, previewPixelX(), previewPixelY(),
                    this.itemDrag.rotation(), this.terminalStack, player, scale);
        }

        renderGrid(gg, player);

        drawOpenButton(gg, gx, gy);
        drawCloseButton(gg, gx, gy);
    }

    private void renderGrid(GuiGraphicsExtractor gg, Player player) {
        if (player == null) {
            return;
        }
        GuiRegion.Region listArea = GuiRegion.list(this.width, this.height);
        float enterE = Easing.easeOutCubic(Math.min(1F, this.enterTicks / (float) ENTER_TICKS));
        int gridOffsetY = Math.round(8F * (1F - enterE));
        int gridAlpha = (int) (255F * enterE);
        int x = 0;
        int y = 0;
        int startIdx = this.page * ITEMS_PER_PAGE;
        for (int i = startIdx; i < Math.min(itemsList.size(), startIdx + ITEMS_PER_PAGE); i++) {
            int py = 55;
            int px = i - startIdx;
            if (px > 9) {
                py = 73;
                px -= 10;
            }
            ItemStack stack = itemsList.get(i);
            int grade = gradeList.get(i);
            x = px;
            y = py;
            if (grade > 4) {
                break;
            }
            IconListTools.renderItemFrame(player, gg, stack,
                    listArea.x() + px * GuiRegion.pctW(this.width, 9),
                    GuiRegion.pctH(this.height, py) + gridOffsetY,
                    this.width, this.height, grade, gridAlpha);
        }
        if (!gradeList.isEmpty() && gradeList.get(gradeList.size() - 1) > 4
                && this.page == pageCount() - 1) {
            IconListTools.renderItemFrame(player, gg, ItemStack.EMPTY,
                    listArea.x() + x * GuiRegion.pctW(this.width, 9),
                    GuiRegion.pctH(this.height, y) + gridOffsetY,
                    this.width, this.height, 5, gridAlpha);
        }
    }

    protected void renderLabels(GuiGraphicsExtractor gg, int mouseX, int mouseY) {
        Style style = Style.EMPTY.withBold(true);
        boolean showNames = CsgoBox.CONFIG.showItemNames();

        int x = 0;
        int y = 0;
        int startIdx = this.page * ITEMS_PER_PAGE;
        for (int i = startIdx; i < Math.min(itemsList.size(), startIdx + ITEMS_PER_PAGE); i++) {
            int py = 67;
            int px = i - startIdx;
            if (px > 9) {
                py = 85;
                px -= 10;
            }
            ItemStack stack = itemsList.get(i);
            int grade = gradeList.get(i);
            x = px;
            y = py;
            if (grade > 4) {
                break;
            }
            if (showNames) {
                Component component = stack.getItem().getName(stack);
                int slotVisualWidth = Math.round(this.width * 9F / 100F);
                RenderFontTool.drawStringClamped(gg, this.font, component,
                        this.width * 4F / 100 + px * this.width * 9F / 100,
                        this.height * py / 100F, 0, 0, 0.6F,
                        slotVisualWidth, 0xFFD3D3D3);
            }
        }
        if (showNames) {
            renderText(gg, Component.translatable("gui.csgobox.csgo_box.label_gold").getVisualOrderText(),
                    this.width * 4 / 100F + x * this.width * 9 / 100F,
                    this.height * y / 100F, 0.6F);
        }
        if (pageCount() > 1) {
            renderText(gg, Component.literal((this.page + 1) + "/" + pageCount()).getVisualOrderText(),
                    this.width * 88 / 100F, this.height * 54 / 100F, 0.6F);
        }

        // terminal display name with its box-definition colour, centred title
        int boxNameMaxWidth = Math.round(this.width * 54F / 100F);
        float boxNameScale = 0.8F;
        float boxNameX = centeredTextX(boxName.getString(), boxNameScale, boxNameMaxWidth);
        int titleColor = 0xFFD3D3D3;
        TextColor tc = boxName.getStyle().getColor();
        if (tc != null) {
            titleColor = 0xFF000000 | (tc.getValue() & 0xFFFFFF);
        }
        RenderFontTool.drawStringClamped(gg, this.font, boxName,
                boxNameX, this.height * 13F / 100F, 0, 0, boxNameScale,
                boxNameMaxWidth, titleColor);

        renderText(gg, Component.translatable("gui.csgobox.csgo_box.label_items").withStyle(style).getVisualOrderText(),
                this.width * 3F / 100F, this.height * 50.3F / 100F, 0.8F);

        renderText(gg, Component.translatable("gui.csgobox.terminal.boot_title").withStyle(style).getVisualOrderText(),
                middleOf(I18n.get("gui.csgobox.terminal.boot_title"), 2),
                this.height * 5.9F / 100F, 2F);

        if (boxEmpty) {
            Component warnText = Component.translatable("gui.csgobox.csgo_box.label_not_configured");
            FormattedCharSequence warnSeq = warnText.getVisualOrderText();
            float warnWidth = this.font.width(warnSeq) * 1.2F;
            int bgX0 = Math.max(8, (int) ((this.width - warnWidth) / 2.0F) - 8);
            int bgX1 = Math.min(this.width - 8, (int) ((this.width + warnWidth) / 2.0F) + 8);
            int bgY0 = this.height * 32 / 100 - 6;
            int bgY1 = bgY0 + (int) (this.font.lineHeight * 1.2F) + 10;
            AnimRenderOps.fill(gg, bgX0, bgY0, bgX1, bgY1, OverlayColor.panel());
            RenderFontTool.drawString(gg, this.font, warnSeq,
                    (this.width - warnWidth) / 2.0F, bgY0 + 5, 0, 0, 1.2F, 0xFFFF4444);
        }

        renderCenteredText(gg, Component.translatable("gui.csgobox.terminal.open").withStyle(style).getVisualOrderText(),
                openButtonX(), buttonY(), actionButtonWidth(), buttonHeight(), 0.8F,
                buttonTextColor(mouseX, mouseY, openButtonX(), buttonY(),
                        actionButtonWidth(), buttonHeight(), ButtonPalette.OPEN));
        renderCenteredText(gg, Component.translatable("gui.csgobox.terminal.cancel").withStyle(style).getVisualOrderText(),
                closeButtonX(), buttonY(), actionButtonWidth(), buttonHeight(), 0.8F,
                buttonTextColor(mouseX, mouseY, closeButtonX(), buttonY(),
                        actionButtonWidth(), buttonHeight(), ButtonPalette.CLOSE));
    }

    private void drawOpenButton(GuiGraphicsExtractor gg, int mouseX, int mouseY) {
        boolean hover = ButtonPalette.isInside(mouseX, mouseY, openButtonX(), buttonY(),
                actionButtonWidth(), buttonHeight());
        ButtonPalette.drawButton(gg, ButtonPalette.OPEN, openButtonX(), buttonY(),
                actionButtonWidth(), buttonHeight(), hover);
    }

    private void drawCloseButton(GuiGraphicsExtractor gg, int mouseX, int mouseY) {
        boolean hover = ButtonPalette.isInside(mouseX, mouseY, closeButtonX(), buttonY(),
                actionButtonWidth(), buttonHeight());
        ButtonPalette.drawButton(gg, ButtonPalette.CLOSE, closeButtonX(), buttonY(),
                actionButtonWidth(), buttonHeight(), hover);
    }

    private int buttonTextColor(int mouseX, int mouseY, int x, int y, int w, int h, ButtonPalette.Style style) {
        boolean hover = ButtonPalette.isInside(mouseX, mouseY, x, y, w, h);
        return hover ? style.textColorHover() : style.textColor();
    }

    private float middleOf(String text, float scale) {
        return (this.width - font.width(text) * scale) * 0.5F;
    }

    private float centeredTextX(String text, float scale, int maxWidth) {
        float renderedWidth = Math.min(this.font.width(text) * scale, maxWidth);
        return (this.width - renderedWidth) / 2.0F;
    }

    private void renderText(GuiGraphicsExtractor gg, FormattedCharSequence text, float px, float py, float scale) {
        RenderFontTool.drawString(gg, this.font, text, px, py, 0, 0, scale, 0xFFD3D3D3);
    }

    private void renderCenteredText(GuiGraphicsExtractor gg, FormattedCharSequence text,
                                    int x, int y, int w, int h, float scale, int color) {
        float textW = this.font.width(text) * scale;
        float textX = x + (w - textW) / 2.0F;
        float textY = y + (h - this.font.lineHeight * scale) / 2.0F + 1;
        RenderFontTool.drawString(gg, this.font, text, textX, textY, 0, 0, scale, color);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double pDragX, double pDragY) {
        double pMouseX = event.x();
        double pMouseY = event.y();
        int size = previewTextureSize();
        int x = previewPixelX();
        int y = previewPixelY();
        boolean isInRange = (pMouseX >= x && pMouseX <= x + size)
                && (pMouseY >= y && pMouseY <= y + size);
        if (event.button() == 0 && isInRange) {
            this.itemDrag.accumulate(pDragX, pDragY);
        }
        return super.mouseDragged(event, pDragX, pDragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.itemDrag.release();
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0 && pageCount() > 1) {
            int target = this.page + (scrollY > 0 ? -1 : 1);
            if (target >= 0 && target < pageCount()) {
                this.page = target;
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            if (ButtonPalette.isInside(event.x(), event.y(), openButtonX(), buttonY(),
                    actionButtonWidth(), buttonHeight())) {
                Minecraft.getInstance().setScreenAndShow(new TerminalScreen(this.terminalStack));
                return true;
            }
            if (ButtonPalette.isInside(event.x(), event.y(), closeButtonX(), buttonY(),
                    actionButtonWidth(), buttonHeight())) {
                onClose();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.closeContainer();
        }
        super.onClose();
    }
}
