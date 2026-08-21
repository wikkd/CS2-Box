package com.reclizer.csgobox.v1_21_1.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.reclizer.csgobox.v1_21_1.CsgoBox;
import com.reclizer.csgobox.v1_21_1.item.ItemCsgoBox;
import com.reclizer.csgobox.v1_21_1.packet.PacketCsgoProgress;
import com.reclizer.csgobox.v1_21_1.packet.PacketRequestBoxItems;
import com.reclizer.csgobox.v1_21_1.packet.PacketSyncBoxItems;
import com.reclizer.csgobox.utils.ColorTools;
import com.reclizer.csgobox.utils.Easing;
import com.reclizer.csgobox.utils.ItemDrag3D;
import com.reclizer.csgobox.utils.GuiRegion;
import com.reclizer.csgobox.utils.OverlayColor;
import com.reclizer.csgobox.v1_21_1.utils.GuiItemMove;
import com.reclizer.csgobox.v1_21_1.utils.AnimRenderOps;
import com.reclizer.csgobox.v1_21_1.utils.IconListTools;
import com.reclizer.csgobox.v1_21_1.utils.RenderFontTool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class CsboxScreen extends Screen {
    private final Player entity;
    private final Level world;

    private boolean openClicked = false;
    private boolean boxEmpty = false;

    // Transient feedback for rejected open clicks (e.g. no key found).
    private Component hint = null;
    private int hintTicks = 0;



    private final ItemDrag3D itemDrag = new ItemDrag3D(0, 0);

    private Map<ItemStack, Integer> itemGroup;

    private List<ItemStack> itemsList;
    private List<Integer> gradeList;
    /** Pre-laid-out item name sequences (translation + bidi reordering),
     *  rebuilt on each sync; grid positions still compute per frame. */
    private List<FormattedCharSequence> itemLabels = List.of();
    private FormattedCharSequence goldLabel = FormattedCharSequence.EMPTY;
    /** Dirty-marked page counts: computed lazily, invalidated on sync. */
    private int cachedRenderableCount = -1;
    private int cachedPageCount = -1;

    private ResourceLocation keyRl;
    private final long syncRequestId;
    private Optional<ResourceLocation> expectedBoxId = Optional.empty();

    public CsboxScreen() {
        super(Component.literal("cs_screen"));
        this.minecraft = Minecraft.getInstance();
        this.itemGroup = new LinkedHashMap<>();
        this.itemsList = new ArrayList<>();
        this.gradeList = new ArrayList<>();
        this.openClicked = true;
        this.syncRequestId = ThreadLocalRandom.current().nextLong();

        if (this.minecraft.player != null) {
            this.entity = this.minecraft.player;
            this.world = entity.level();
            this.itemMenu = this.minecraft.player.getItemInHand(InteractionHand.MAIN_HAND);
            this.expectedBoxId = Optional.ofNullable(ItemCsgoBox.getBoxId(this.itemMenu));
            PacketDistributor.sendToServer(new PacketRequestBoxItems(this.syncRequestId));
        } else {
            this.entity = null;
            this.world = null;
            this.itemMenu = ItemStack.EMPTY;
        }
    }

    private ItemStack itemKey;
    private ItemStack itemMenu;

    private int actionButtonWidth() {
        return Math.max(64, this.width * 7 / 100);
    }

    private int openButtonX() {
        return Math.max(8, this.width - actionButtonWidth() * 2 - 20);
    }

    private int backButtonX() {
        return openButtonX() + actionButtonWidth() + 8;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static List<ItemStack> itemsListProgress(Map<ItemStack, Integer> itemList) {
        int maxGrade = itemList.values().stream().max(Integer::compareTo).orElse(5);
        List<ItemStack> itemStacks = new ArrayList<>();
        for (int i = 1; i <= maxGrade; i++) {
            for (Map.Entry<ItemStack, Integer> entry : itemList.entrySet()) {
                if (entry.getValue() == i) {
                    itemStacks.add(entry.getKey());
                }
            }
        }
        return itemStacks;
    }

    private static List<Integer> gradeListProgress(Map<ItemStack, Integer> itemList) {
        int maxGrade = itemList.values().stream().max(Integer::compareTo).orElse(5);
        List<Integer> itemStacks = new ArrayList<>();
        for (int i = 1; i <= maxGrade; i++) {
            for (Map.Entry<ItemStack, Integer> entry : itemList.entrySet()) {
                if (entry.getValue() == i) {
                    itemStacks.add(i);
                }
            }
        }
        return itemStacks;
    }

    private static final int ITEMS_PER_PAGE = 20;

    private int page;

    // Page-turn transition: outgoing page slides out and fades while the
    // incoming page slides in (direction matches the wheel). Ease-out cubic
    // (same curve as the opening strip's easedScroll), ~200ms, interruptible:
    // a new scroll restarts the transition from the page currently on screen.
    private static final int PAGE_ANIM_TICKS = 12;
    private static final float PAGE_ANIM_DIST_RATIO = 8F; // percent of height
    private int animFromPage = -1;
    private int animToPage;
    private int animTicks;
    private int animDir;

    private static final int ENTER_TICKS = 6;
    /** Grid fade-in on first server sync; 6 = settled (no enter anim). */
    private int enterTicks = ENTER_TICKS;

    private float pageAnimEased(float partialTicks) {
        if (animFromPage < 0) return 1.0F;
        float t = Math.min(1.0F, (animTicks + partialTicks) / (float) PAGE_ANIM_TICKS);
        float u = 1.0F - t;
        return 1.0F - u * u * u;
    }

    private void changePage(int target) {
        this.animFromPage = this.page;
        this.animToPage = target;
        this.animDir = target > this.page ? 1 : -1;
        this.animTicks = 0;
        this.page = target;
    }

    private int renderableCount() {
        if (cachedRenderableCount < 0) {
            int count = 0;
            for (int i = 0; i < itemsList.size(); i++) {
                if (gradeList.get(i) > 4) break;
                count++;
            }
            cachedRenderableCount = count;
        }
        return cachedRenderableCount;
    }

    private int pageCount() {
        if (cachedPageCount < 0) {
            int n = renderableCount();
            cachedPageCount = Math.max(1, (n + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
        }
        return cachedPageCount;
    }

    private static List<FormattedCharSequence> buildItemLabels(List<ItemStack> stacks) {
        List<FormattedCharSequence> labels = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            labels.add(stack.getItem().getName(stack).getVisualOrderText());
        }
        return labels;
    }

    private int boxKeyCount;

    private int countKeys() {
        int total = 0;
        if (this.entity != null && this.entity.getAbilities().instabuild) {
            return Integer.MAX_VALUE;
        }
        if (keyRl != null && this.entity != null) {
            for (ItemStack stack : entity.getInventory().items) {
                if (isKey(stack, keyRl)) total += stack.getCount();
            }
            for (ItemStack stack : entity.getInventory().armor) {
                if (isKey(stack, keyRl)) total += stack.getCount();
            }
            for (ItemStack stack : entity.getInventory().offhand) {
                if (isKey(stack, keyRl)) total += stack.getCount();
            }
        }
        return total;
    }

    @Override
    public boolean mouseDragged(double pMouseX, double pMouseY, int pButton, double pDragX, double pDragY) {
        boolean isInRange = (pMouseX >= this.width * 37F / 100 && pMouseX <= this.width * 37F / 100 + 200)
                && (pMouseY >= this.height * 12F / 100 && pMouseY <= this.height * 12F / 100 + 176);
        if (pButton == 0 && isInRange) {
            this.itemDrag.accumulate(pDragX, pDragY);
        }
        return super.mouseDragged(pMouseX, pMouseY, pButton, pDragX, pDragY);
    }

    @Override
    public boolean mouseReleased(double pMouseX, double pMouseY, int pButton) {
        this.itemDrag.release();
        return super.mouseReleased(pMouseX, pMouseY, pButton);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        this.itemDrag.tick();
        this.renderBg(guiGraphics, partialTicks, mouseX, mouseY);
        this.renderLabels(guiGraphics, mouseX, mouseY, partialTicks);
        if (this.hint != null) {
            float scale = 0.9F;
            float w = RenderFontTool.width(this.font, this.hint.getVisualOrderText(), scale);
            RenderFontTool.drawString(guiGraphics, this.font, this.hint.getVisualOrderText(),
                    (this.width - w) / 2.0F, this.height * 30 / 100, 0, 0, scale, 0xFFFF5555);
            this.hintTicks--;
            if (this.hintTicks <= 0) {
                this.hint = null;
            }
        }
    }

    protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int gx, int gy) {
        if (this.minecraft != null && this.minecraft.level != null) {
            int fill = UiBackdrop.fill();
            AnimRenderOps.fillGradient(guiGraphics, 0, 0, this.width, this.height, fill, fill);
        }
        GuiRegion.Region listArea = GuiRegion.list(this.width, this.height);
        AnimRenderOps.fill(guiGraphics, listArea.x(), listArea.y(), listArea.right(), listArea.y() + 1, OverlayColor.divider());
        GuiRegion.Region footer = GuiRegion.fullWidthRow(this.width, this.height, 92, 1);
        AnimRenderOps.fill(guiGraphics, footer.x(), footer.y(), footer.right(), footer.bottom(), OverlayColor.divider());

        int FrameWidth = width * 26 / 100;
        float scale = FrameWidth / 16F;
        if (this.entity != null) {
            GuiItemMove.renderItemInInventoryFollowsMouse(guiGraphics, this.width * 37 / 100, this.height * 12 / 100,
                    this.itemDrag.rotation(), itemMenu, this.entity, scale);
        }

        renderGridAnimated(guiGraphics, partialTicks);

        if (itemKey != null) {
            IconListTools.renderGuiItem(this.entity, this.world, guiGraphics, itemKey,
                    this.width * 25F / 100, this.height * 93F / 100, 1);
        }

        int openX = openButtonX();
        int openY = this.height * 94 / 100;
        int openW = actionButtonWidth();
        int openH = this.height * 5 / 100;
        boolean openHover = isInside(gx, gy, openX, openY, openW, openH);
        drawButton(guiGraphics, openX, openY, openW, openH, 0xFF00AA00, 0xFF00FF00,
                0xFF33DD55, 0xFF66FF88, openHover);

        int backX = backButtonX();
        int backY = this.height * 94 / 100;
        int backW = actionButtonWidth();
        int backH = this.height * 5 / 100;
        boolean backHover = isInside(gx, gy, backX, backY, backW, backH);
        drawButton(guiGraphics, backX, backY, backW, backH, 0xFFAA0000, 0xFFFF0000,
                0xFFCC4444, 0xFFFF6666, backHover);
    }

    /** Draws the current page (or both pages during the page-turn
     *  transition). Outgoing page slides away and fades; the incoming page
     *  slides in from the wheel's direction with the frame fading in. */
    private void renderGridAnimated(GuiGraphics guiGraphics, float partialTicks) {
        float e = pageAnimEased(partialTicks);
        if (animFromPage < 0) {
            float enterE = Easing.easeOutCubic(Math.min(1F, this.enterTicks / (float) ENTER_TICKS));
            renderPageGrid(guiGraphics, this.page, Math.round(8F * (1F - enterE)),
                    (int) (255F * enterE));
            return;
        }
        float dist = this.height * PAGE_ANIM_DIST_RATIO / 100F;
        renderPageGrid(guiGraphics, animFromPage, Math.round(-e * dist * animDir),
                (int) (255F * (1.0F - e)));
        renderPageGrid(guiGraphics, animToPage, Math.round((1.0F - e) * dist * animDir),
                (int) (255F * e));
    }

    private void renderPageGrid(GuiGraphics guiGraphics, int page, int offsetY, int alpha) {
        if (this.entity == null) return;
        GuiRegion.Region listArea = GuiRegion.list(this.width, this.height);
        int x = 0;
        int y = 0;
        int startIdx = page * ITEMS_PER_PAGE;
        for (int i = startIdx; i < Math.min(itemsList.size(), startIdx + ITEMS_PER_PAGE); i++) {
            int py = 55;
            int px = i - startIdx;
            if (px > 9) {
                py = 73;
                px -= 10;
            }
            ItemStack itemStack1 = itemsList.get(i);
            int grade = gradeList.get(i);
            x = px;
            y = py;
            if (grade > 4) break;
            IconListTools.renderItemFrame(this.entity, guiGraphics, itemStack1,
                    listArea.x() + px * GuiRegion.pctW(this.width, 9),
                    GuiRegion.pctH(this.height, py) + offsetY, this.width, this.height, grade, alpha);
        }
        if (!gradeList.isEmpty() && gradeList.get(gradeList.size() - 1) > 4
                && page == pageCount() - 1) {
            IconListTools.renderItemFrame(this.entity, guiGraphics, ItemStack.EMPTY,
                    listArea.x() + x * GuiRegion.pctW(this.width, 9),
                    GuiRegion.pctH(this.height, y) + offsetY, this.width, this.height, 5, alpha);
        }
    }

    private void drawButton(GuiGraphics guiGraphics, int x, int y, int w, int h,
                            int fillColor, int borderColor,
                            int fillHover, int borderHover, boolean hover) {
        int fill = hover ? fillHover : fillColor;
        int border = hover ? borderHover : borderColor;
        AnimRenderOps.fill(guiGraphics, x, y, x + w, y + h, border);
        AnimRenderOps.fill(guiGraphics, x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    @Override
    public boolean keyPressed(int key, int b, int c) {
        if (key == 256) {
            this.minecraft.player.closeContainer();
            this.minecraft.options.hideGui = false;
            return true;
        }
        return super.keyPressed(key, b, c);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0 && pageCount() > 1) {
            int target = this.page + (verticalAmount > 0 ? -1 : 1);
            if (target >= 0 && target < pageCount()) {
                changePage(target);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        Style style = Style.EMPTY.withBold(true);
        boolean showNames = CsgoBox.CONFIG.showItemNames();

        renderLabelsAnimated(guiGraphics, showNames, partialTicks);

        if (pageCount() > 1) {
            renderText(guiGraphics, Component.literal((this.page + 1) + "/" + pageCount()).getVisualOrderText(),
                    this.width * 90 / 100F, this.height * 54 / 100F, 0.6F);
        }

        renderText(guiGraphics, Component.translatable("gui.csgobox.csgo_box.label_box").getVisualOrderText(),
                this.width * 46F / 100F, this.height * 13F / 100F, 0.8F);
        // Box name rendered directly via RenderFontTool (not the local renderText
        // helper, which hardcodes a gray fallback) so that an optional 0xRRGGBB
        // color configured on the box's name style is honored. Without an
        // explicit color, the title falls back to 0xFFD3D3D3 — the previous
        // visual baseline.
        Component boxName = itemMenu.getItem().getName(itemMenu);
        int titleColor = 0xFFD3D3D3;
        net.minecraft.network.chat.TextColor tc = boxName.getStyle().getColor();
        if (tc != null) {
            titleColor = 0xFF000000 | (tc.getValue() & 0xFFFFFF);
        }
        RenderFontTool.drawStringVanilla(guiGraphics, this.font, boxName.getVisualOrderText(),
                this.width * 50F / 100F, this.height * 13F / 100F, 0, 0, 0.8F, titleColor);

        if (itemKey != null && !itemKey.isEmpty()) {
            if (boxKeyCount > 0) {
                String count = boxKeyCount == Integer.MAX_VALUE
                        ? " \u00D7 \u221E"
                        : " \u00D7 " + boxKeyCount;
                renderText(guiGraphics, Component.literal(count).getVisualOrderText(), this.width * 28F / 100F, this.height * 94F / 100F, 0.8F);
            } else {
                renderText(guiGraphics, Component.translatable("gui.csgobox.csgo_box.label_open").getVisualOrderText(),
                        this.width * 28F / 100F, this.height * 94F / 100F, 0.8F);
                renderText(guiGraphics, Component.translatable("gui.csgobox.csgo_box.label_open_1").getVisualOrderText(),
                        this.width * 40F / 100F, this.height * 94F / 100F, 0.8F);
                renderTextVanilla(guiGraphics, itemKey.getItem().getName(itemKey).getVisualOrderText(),
                        this.width * 35F / 100F, this.height * 94F / 100F, 0.8F, 255);
            }
        }

        renderText(guiGraphics, Component.translatable("gui.csgobox.csgo_box.label_items").withStyle(style).getVisualOrderText(),
                this.width * 3F / 100F, this.height * 50.3F / 100F, 0.8F);

        renderText(guiGraphics, Component.translatable("gui.csgobox.csgo_box.title").withStyle(style).getVisualOrderText(),
                middleOf(I18n.get("gui.csgobox.csgo_box.title"), 2), this.height * 5.9F / 100F, 2F);

        if (boxEmpty) {
            Component warnText = Component.translatable("gui.csgobox.csgo_box.label_not_configured");
            FormattedCharSequence warnSeq = warnText.getVisualOrderText();
            float warnWidth = RenderFontTool.width(this.font, warnSeq, 1.2F);
            int bgX0 = Math.max(8, (int) ((this.width - warnWidth) / 2.0F) - 8);
            int bgX1 = Math.min(this.width - 8, (int) ((this.width + warnWidth) / 2.0F) + 8);
            int bgY0 = this.height * 23 / 100 - 6;
            int bgY1 = bgY0 + (int) (this.font.lineHeight * 1.2F) + 10;
            RenderSystem.disableDepthTest();
            AnimRenderOps.fill(guiGraphics, bgX0, bgY0, bgX1, bgY1, 0xAA101010);
            RenderFontTool.drawString(guiGraphics, this.font, warnSeq,
                    (this.width - warnWidth) / 2.0F, bgY0 + 5, 0, 0, 1.2F, 0xFFFF4444);
            RenderSystem.enableDepthTest();
        }

        renderCenteredText(guiGraphics, Component.translatable("gui.csgobox.csgo_box.open_box").withStyle(style).getVisualOrderText(),
                openButtonX(), this.height * 94 / 100, actionButtonWidth(), this.height * 5 / 100, 0.8F);
        renderCenteredText(guiGraphics, Component.translatable("gui.csgobox.csgo_box.back_box").withStyle(style).getVisualOrderText(),
                backButtonX(), this.height * 94 / 100, actionButtonWidth(), this.height * 5 / 100, 0.8F);
    }

    private float middleOf(String text, float scale) {
        return (this.width - font.width(text) * scale) * 0.5F;
    }

    private void renderText(GuiGraphics guiGraphics, FormattedCharSequence pText, float px, float py, float scale) {
        renderText(guiGraphics, pText, px, py, scale, 255);
    }

    private void renderText(GuiGraphics guiGraphics, FormattedCharSequence pText, float px, float py, float scale, int alpha) {
        RenderFontTool.drawString(guiGraphics, this.font, pText, px, py, 0, 0, scale,
                ColorTools.withAlpha(0xFFD3D3D3, alpha));
    }

    /** Dynamic/external text (item names) keeps the default font. */
    private void renderTextVanilla(GuiGraphics guiGraphics, FormattedCharSequence pText,
                                   float px, float py, float scale, int alpha) {
        RenderFontTool.drawStringVanilla(guiGraphics, this.font, pText, px, py, 0, 0, scale,
                ColorTools.withAlpha(0xFFD3D3D3, alpha));
    }

    /** Page-turn labels: mirrors renderGridAnimated's slide/fade so the item
     *  names and the gold slot label travel with their frames. */
    private void renderLabelsAnimated(GuiGraphics guiGraphics, boolean showNames, float partialTicks) {
        float e = pageAnimEased(partialTicks);
        if (animFromPage < 0) {
            renderPageLabels(guiGraphics, this.page, 0, 255, showNames);
            return;
        }
        float dist = this.height * PAGE_ANIM_DIST_RATIO / 100F;
        renderPageLabels(guiGraphics, animFromPage, Math.round(-e * dist * animDir),
                (int) (255F * (1.0F - e)), showNames);
        renderPageLabels(guiGraphics, animToPage, Math.round((1.0F - e) * dist * animDir),
                (int) (255F * e), showNames);
    }

    private void renderPageLabels(GuiGraphics guiGraphics, int page, int offsetY, int alpha, boolean showNames) {
        if (!showNames) return;
        int x = 0;
        int y = 0;
        int startIdx = page * ITEMS_PER_PAGE;
        for (int i = startIdx; i < Math.min(itemsList.size(), startIdx + ITEMS_PER_PAGE); i++) {
            int py = 67;
            int px = i - startIdx;
            if (px > 9) {
                py = 85;
                px -= 10;
            }
            int grade = gradeList.get(i);
            x = px;
            y = py;
        }
        renderText(guiGraphics, goldLabel,
                this.width * 4 / 100F + x * this.width * 9 / 100F,
                this.height * y / 100F + offsetY, 0.6F, alpha);
    }

    private void renderCenteredText(GuiGraphics guiGraphics, FormattedCharSequence text,
                                    int x, int y, int w, int h, float scale) {
        float textW = RenderFontTool.width(this.font, text, scale);
        float textX = x + (w - textW) / 2.0F;
        float textY = y + (h - this.font.lineHeight * scale) / 2.0F + 1;
        RenderFontTool.drawString(guiGraphics, this.font, text, textX, textY, 0, 0, scale, 0xFFD3D3D3);
    }

    @Override
    public final void tick() {
        super.tick();
        if (this.minecraft == null) return;
        if (this.minecraft.player == null) return;
        if (this.minecraft.player.isAlive() && !this.minecraft.player.isRemoved()) {
            this.containerTick();
            if (this.enterTicks < ENTER_TICKS) {
                this.enterTicks++;
            }
        } else {
            this.minecraft.player.closeContainer();
        }
    }

    public void containerTick() {
        if (animFromPage >= 0 && ++animTicks >= PAGE_ANIM_TICKS) {
            animFromPage = -1;
        }
        var data = PacketSyncBoxItems.consumeMatching(this.syncRequestId, this.expectedBoxId);
        if (data != null) {
            this.itemGroup = buildItemGroup(data);
            this.itemsList = itemsListProgress(this.itemGroup);
            this.gradeList = gradeListProgress(this.itemGroup);
            this.itemLabels = buildItemLabels(this.itemsList);
            this.goldLabel = Component.translatable("gui.csgobox.csgo_box.label_gold").getVisualOrderText();
            this.cachedRenderableCount = -1;
            this.cachedPageCount = -1;
            this.itemKey = data.keyItem();
            if (data.keyItem() != null && !data.keyItem().isEmpty()) {
                this.keyRl = BuiltInRegistries.ITEM.getKey(data.keyItem().getItem());
            }
            this.openClicked = this.itemGroup.isEmpty();
            this.boxEmpty = this.itemGroup.isEmpty();
            this.boxKeyCount = countKeys();
            this.page = 0;
            this.animFromPage = -1;
            this.enterTicks = 0;
        }
    }

    private Map<ItemStack, Integer> buildItemGroup(PacketSyncBoxItems.BoxData data) {
        Map<ItemStack, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < data.items().size(); i++) {
            map.put(data.items().get(i), data.grades().get(i));
        }
        return map;
    }



    @Override
    public void init() {
        super.init();
    }

    @Override
    public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
        if (pButton == 0) {
            int openX = openButtonX();
            int openY = this.height * 94 / 100;
            int openW = actionButtonWidth();
            int openH = this.height * 5 / 100;
            if (pMouseX >= openX && pMouseX <= openX + openW && pMouseY >= openY && pMouseY <= openY + openH) {
                if (this.entity != null) {
                    if (!openClicked && entity.getMainHandItem().getItem() instanceof ItemCsgoBox) {
                        ResourceLocation keyRl = this.keyRl;
                        boolean canOpen = true;
                        if (keyRl != null && !keyRl.equals(ResourceLocation.parse("minecraft:air"))) {
                            canOpen = false;
                            if (entity.getAbilities().instabuild) {
                                canOpen = true;
                            } else {
                                canOpen = hasKeyAnywhere(entity, keyRl);
                            }
                        }
                        if (canOpen) {
                            long openRequestId = ThreadLocalRandom.current().nextLong();
                            // Request id only matches the later server result to this animation.
                            Minecraft.getInstance().setScreen(new CsboxProgressScreen(entity, openRequestId));
                            PacketDistributor.sendToServer(new PacketCsgoProgress(openRequestId));
                            openClicked = true;
                        } else {
                            this.hint = Component.translatable("gui.csgobox.box.no_key");
                            this.hintTicks = 200;
                        }
                    }
                }
                return true;
            }

            int backX = backButtonX();
            int backY = this.height * 94 / 100;
            int backW = actionButtonWidth();
            int backH = this.height * 5 / 100;
            if (pMouseX >= backX && pMouseX <= backX + backW && pMouseY >= backY && pMouseY <= backY + backH) {
                if (this.minecraft != null && this.minecraft.player != null) {
                    this.minecraft.player.closeContainer();
                    this.minecraft.options.hideGui = false;
                }
                return true;
            }
        }
        return super.mouseClicked(pMouseX, pMouseY, pButton);
    }

    /**
     * Client-side key availability check mirroring the server's
     * {@code tryConsumeKeys} slot coverage (main inventory + armor + offhand)
     * so keys stashed in armor/offhand are not wrongly reported as missing.
     * Box instances are never keys, matching the server's consumption rule.
     */
    private static boolean hasKeyAnywhere(Player player, ResourceLocation keyRl) {
        for (ItemStack stack : player.getInventory().items) {
            if (isKey(stack, keyRl)) return true;
        }
        for (ItemStack stack : player.getInventory().armor) {
            if (isKey(stack, keyRl)) return true;
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (isKey(stack, keyRl)) return true;
        }
        return false;
    }

    private static boolean isKey(ItemStack stack, ResourceLocation keyRl) {
        return !stack.isEmpty()
                && !(stack.getItem() instanceof ItemCsgoBox)
                && keyRl.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    @Override
    public void onClose() {
        super.onClose();
    }
}
