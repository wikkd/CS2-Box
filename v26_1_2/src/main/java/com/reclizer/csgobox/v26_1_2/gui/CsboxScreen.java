package com.reclizer.csgobox.v26_1_2.gui;

import com.reclizer.csgobox.v26_1_2.CsgoBox;
import com.reclizer.csgobox.v26_1_2.item.ItemCsgoBox;
import com.reclizer.csgobox.v26_1_2.packet.PacketCsgoProgress;
import com.reclizer.csgobox.v26_1_2.packet.PacketRequestBoxItems;
import com.reclizer.csgobox.v26_1_2.packet.PacketSyncBoxItems;
import com.reclizer.csgobox.v26_1_2.utils.ButtonPalette;
import com.reclizer.csgobox.utils.ColorTools;
import com.reclizer.csgobox.utils.Easing;
import com.reclizer.csgobox.utils.ItemDrag3D;
import com.reclizer.csgobox.utils.GuiRegion;
import com.reclizer.csgobox.utils.OverlayColor;
import com.reclizer.csgobox.v26_1_2.utils.GuiItemMove;
import com.reclizer.csgobox.v26_1_2.utils.AnimRenderOps;
import com.reclizer.csgobox.v26_1_2.utils.IconListTools;
import com.reclizer.csgobox.v26_1_2.utils.RenderFontTool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

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

    private Identifier keyRl;
    private final long syncRequestId;
    private Optional<Identifier> expectedBoxId = Optional.empty();

    public CsboxScreen() {
        super(Minecraft.getInstance(), Minecraft.getInstance().font, Component.literal("cs_screen"));
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
            ClientPacketListener conn = Minecraft.getInstance().getConnection();
            if (conn != null) {
                conn.send(new ServerboundCustomPayloadPacket(new PacketRequestBoxItems(this.syncRequestId)));
            }
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

    // Geometry for the main 3D preview crate, derived once and reused by both
    // renderBg (to position the PIP render state) and mouseDragged (to keep the
    // drag-detection rectangle in lock-step with what's actually drawn).
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
        // Container = vertical band between subtitle (≈12%) and the
        // horizontal "物品:" separator at 53%. Center the crate within it.
        int containerTop = this.height * 12 / 100;
        int containerBottom = this.height * 53 / 100;
        return (containerTop + containerBottom - previewTextureSize()) / 2;
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
    /** Armor + offhand slots walked when counting/consuming keys, all locations. */
    private static final EquipmentSlot[] LOCATION_SLOTS = new EquipmentSlot[]{
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.OFFHAND};
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
        int count = 0;
        for (int i = 0; i < itemsList.size(); i++) {
            if (gradeList.get(i) > 4) break;
            count++;
        }
        return count;
    }

    private int pageCount() {
        int n = renderableCount();
        return Math.max(1, (n + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
    }

    private int boxKeyCount;


    private int countKeys() {
        int total = 0;
        if (this.entity != null && this.entity.getAbilities().instabuild) {
            return Integer.MAX_VALUE;
        }
        if (keyRl != null && this.entity != null) {
            for (ItemStack stack : entity.getInventory().getNonEquipmentItems()) {
                if (isKey(stack, keyRl)) {
                    total += stack.getCount();
                }
            }
            for (EquipmentSlot slot : LOCATION_SLOTS) {
                ItemStack stack = entity.getItemBySlot(slot);
                if (isKey(stack, keyRl)) {
                    total += stack.getCount();
                }
            }
        }
        return total;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double pDragX, double pDragY) {
        double pMouseX = event.x();
        double pMouseY = event.y();
        // Use the actual rendered crate rectangle (centered horizontally and
        // vertically inside the 12%–53% container) so drag-detection matches
        // what the user sees — the old width*26% rectangle was for the
        // 100%-FrameWidth crate, not the current 60%-FrameWidth preview.
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
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);
        this.itemDrag.tick();
        this.renderBg(guiGraphics, partialTicks, mouseX, mouseY);
        this.renderLabels(guiGraphics, mouseX, mouseY, partialTicks);
        if (this.hint != null) {
            float scale = 0.9F;
            float w = this.font.width(this.hint) * scale;
            RenderFontTool.drawString(guiGraphics, this.font, this.hint.getVisualOrderText(),
                    (this.width - w) / 2.0F, this.height * 30 / 100, 0, 0, scale, 0xFFFF5555);
            this.hintTicks--;
            if (this.hintTicks <= 0) {
                this.hint = null;
            }
        }
    }

    protected void renderBg(GuiGraphicsExtractor guiGraphics, float partialTicks, int gx, int gy) {
        if (this.minecraft != null && this.minecraft.level != null) {
            int fill = UiBackdrop.fill();
            AnimRenderOps.fillGradient(guiGraphics, 0, 0, this.width, this.height, fill, fill);
        }
        GuiRegion.Region listArea = GuiRegion.list(this.width, this.height);
        AnimRenderOps.fill(guiGraphics, listArea.x(), listArea.y(), listArea.right(), listArea.y() + 1, OverlayColor.divider());
        GuiRegion.Region footer = GuiRegion.fullWidthRow(this.width, this.height, 92, 1);
        AnimRenderOps.fill(guiGraphics, footer.x(), footer.y(), footer.right(), footer.bottom(), OverlayColor.divider());

        float scale = previewTextureSize() / 16F;
        // Skip the 3D crate when the box has no configured items — the empty
        // state warning banner (drawn in renderLabels) takes that screen
        // region instead. Without this guard the banner overlapped the crate,
        // making both unreadable.
        if (!boxEmpty && this.entity != null) {
            GuiItemMove.renderItemInInventoryFollowsMouse(guiGraphics, previewPixelX(), previewPixelY(),
                    this.itemDrag.rotation(), itemMenu, this.entity, scale);
        }

        renderGridAnimated(guiGraphics, partialTicks);

        if (itemKey != null) {
            IconListTools.renderGuiItem(this.entity, guiGraphics, itemKey,
                    this.width * 25F / 100, this.height * 93F / 100, 1);
        }

        drawOpenButton(guiGraphics, gx, gy);
        drawBackButton(guiGraphics, gx, gy);
    }

    /** Draws the current page (or both pages during the page-turn
     *  transition). Outgoing page slides away and fades; the incoming page
     *  slides in from the wheel's direction with the frame fading in. */
    private void renderGridAnimated(GuiGraphicsExtractor guiGraphics, float partialTicks) {
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

    private void renderPageGrid(GuiGraphicsExtractor guiGraphics, int page, int offsetY, int alpha) {
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

    private void drawOpenButton(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        int x = openButtonX();
        int y = this.height * 94 / 100;
        int w = actionButtonWidth();
        int h = this.height * 5 / 100;
        boolean hover = ButtonPalette.isInside(mouseX, mouseY, x, y, w, h);
        ButtonPalette.drawButton(guiGraphics, ButtonPalette.OPEN, x, y, w, h, hover);
    }

    private void drawBackButton(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        int x = backButtonX();
        int y = this.height * 94 / 100;
        int w = actionButtonWidth();
        int h = this.height * 5 / 100;
        boolean hover = ButtonPalette.isInside(mouseX, mouseY, x, y, w, h);
        ButtonPalette.drawButton(guiGraphics, ButtonPalette.DANGER, x, y, w, h, hover);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256) {
            this.minecraft.player.closeContainer();
            this.minecraft.options.hideGui = false;
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0 && pageCount() > 1) {
            int target = this.page + (scrollY > 0 ? -1 : 1);
            if (target >= 0 && target < pageCount()) {
                changePage(target);
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    protected void renderLabels(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        Style style = Style.EMPTY.withBold(true);
        boolean showNames = CsgoBox.CONFIG.showItemNames();

        renderLabelsAnimated(guiGraphics, showNames, partialTicks);

        if (pageCount() > 1) {
            renderText(guiGraphics, Component.literal((this.page + 1) + "/" + pageCount()).getVisualOrderText(),
                    this.width * 90 / 100F, this.height * 54 / 100F, 0.6F);
        }

        // Box item name rendered as a centered title-style heading. Max width
        // caps a long localised name so it cannot bleed past the screen edge;
        // when truncation kicks in, the ellipsis stays centered because
        // centeredTextX clamps the rendered width to maxWidth.
        int boxNameMaxWidth = Math.round(this.width * 54F / 100F);
        float boxNameScale = 0.8F;
        Component boxName = itemMenu.getItem().getName(itemMenu);
        float boxNameX = centeredTextX(boxName.getString(),
                boxNameScale, boxNameMaxWidth);
        // Pick a title color from the box definition's name style when one was
        // configured (e.g. via JSON "#RRGGBB ..." prefix), otherwise fall back
        // to the original 0xFFD3D3D3 light gray so the visual stays identical
        // for boxes without an explicit color.
        int titleColor = 0xFFD3D3D3;
        net.minecraft.network.chat.TextColor tc = boxName.getStyle().getColor();
        if (tc != null) {
            titleColor = 0xFF000000 | (tc.getValue() & 0xFFFFFF);
        }
        RenderFontTool.drawStringClamped(guiGraphics, this.font, boxName,
                boxNameX, this.height * 13F / 100F, 0, 0, boxNameScale,
                boxNameMaxWidth, titleColor);

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
                renderText(guiGraphics, itemKey.getItem().getName(itemKey).getVisualOrderText(),
                        this.width * 35F / 100F, this.height * 94F / 100F, 0.8F);
            }
        }

        renderText(guiGraphics, Component.translatable("gui.csgobox.csgo_box.label_items").withStyle(style).getVisualOrderText(),
                this.width * 3F / 100F, this.height * 50.3F / 100F, 0.8F);

        renderText(guiGraphics, Component.translatable("gui.csgobox.csgo_box.title").withStyle(style).getVisualOrderText(),
                middleOf(I18n.get("gui.csgobox.csgo_box.title"), 2), this.height * 5.9F / 100F, 2F);

        if (boxEmpty) {
            Component warnText = Component.translatable("gui.csgobox.csgo_box.label_not_configured");
            FormattedCharSequence warnSeq = warnText.getVisualOrderText();
            float warnWidth = this.font.width(warnSeq) * 1.2F;
            int bgX0 = Math.max(8, (int) ((this.width - warnWidth) / 2.0F) - 8);
            int bgX1 = Math.min(this.width - 8, (int) ((this.width + warnWidth) / 2.0F) + 8);
            // Banner Y: sit in the same vertical band the 3D crate would have
            // occupied (the 12%–53% container, centre ≈ 32.5%). The 3D crate
            // is suppressed in renderBg when boxEmpty, so the banner is now
            // the sole occupant of that band and can take its full centre.
            int bgY0 = this.height * 32 / 100 - 6;
            int bgY1 = bgY0 + (int) (this.font.lineHeight * 1.2F) + 10;
            // Defense-in-depth: renderLabels is invoked after renderBg, so labels
            // already draw on top of any items in renderBg. The nextStratum() pair
            // guarantees the warning banner stays above future additions to
            // renderBg (e.g. additional textured overlays) without re-ordering.
            guiGraphics.nextStratum();
            AnimRenderOps.fill(guiGraphics, bgX0, bgY0, bgX1, bgY1, OverlayColor.panel());
            RenderFontTool.drawString(guiGraphics, this.font, warnSeq,
                    (this.width - warnWidth) / 2.0F, bgY0 + 5, 0, 0, 1.2F, 0xFFFF4444);
            guiGraphics.nextStratum();
        }

        renderCenteredText(guiGraphics, Component.translatable("gui.csgobox.csgo_box.open_box").withStyle(style).getVisualOrderText(),
                openButtonX(), this.height * 94 / 100, actionButtonWidth(), this.height * 5 / 100, 0.8F,
                buttonTextColor(mouseX, mouseY, openButtonX(), this.height * 94 / 100,
                        actionButtonWidth(), this.height * 5 / 100, ButtonPalette.OPEN));
        renderCenteredText(guiGraphics, Component.translatable("gui.csgobox.csgo_box.back_box").withStyle(style).getVisualOrderText(),
                backButtonX(), this.height * 94 / 100, actionButtonWidth(), this.height * 5 / 100, 0.8F,
                buttonTextColor(mouseX, mouseY, backButtonX(), this.height * 94 / 100,
                        actionButtonWidth(), this.height * 5 / 100, ButtonPalette.DANGER));
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

    private void renderText(GuiGraphicsExtractor guiGraphics, FormattedCharSequence pText, float px, float py, float scale) {
        renderText(guiGraphics, pText, px, py, scale, 255);
    }

    private void renderText(GuiGraphicsExtractor guiGraphics, FormattedCharSequence pText, float px, float py, float scale, int alpha) {
        RenderFontTool.drawString(guiGraphics, this.font, pText, px, py, 0, 0, scale,
                ColorTools.withAlpha(0xFFD3D3D3, alpha));
    }

    /** Page-turn labels: mirrors renderGridAnimated's slide/fade so the item
     *  names and the gold slot label travel with their frames. */
    private void renderLabelsAnimated(GuiGraphicsExtractor guiGraphics, boolean showNames, float partialTicks) {
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

    private void renderPageLabels(GuiGraphicsExtractor guiGraphics, int page, int offsetY, int alpha, boolean showNames) {
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
            ItemStack itemStack1 = itemsList.get(i);
            int grade = gradeList.get(i);
            x = px;
            y = py;
            if (grade > 4) break;
            if (showNames) {
                Component component = itemStack1.getItem().getName(itemStack1);
                // Clamp to one grid slot (9% of screen width). At scale 0.6,
                // a natural Chinese name like "下界合金剑" is short enough
                // to render untruncated, but longer localisation keys or
                // modded item names would otherwise bleed into the next slot.
                int slotVisualWidth = Math.round(this.width * 9F / 100F);
                RenderFontTool.drawStringClamped(guiGraphics, this.font, component,
                        this.width * 4F / 100 + px * this.width * 9F / 100,
                        this.height * py / 100F + offsetY, 0, 0, 0.6F,
                        slotVisualWidth, ColorTools.withAlpha(0xFFD3D3D3, alpha));
            }
        }
        if (showNames) {
            renderText(guiGraphics, Component.translatable("gui.csgobox.csgo_box.label_gold").getVisualOrderText(),
                    this.width * 4 / 100F + x * this.width * 9 / 100F,
                    this.height * y / 100F + offsetY, 0.6F, alpha);
        }
    }

    private void renderCenteredText(GuiGraphicsExtractor guiGraphics, FormattedCharSequence text,
                                    int x, int y, int w, int h, float scale, int color) {
        float textW = this.font.width(text) * scale;
        float textX = x + (w - textW) / 2.0F;
        float textY = y + (h - this.font.lineHeight * scale) / 2.0F + 1;
        RenderFontTool.drawString(guiGraphics, this.font, text, textX, textY, 0, 0, scale, color);
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
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            int openX = openButtonX();
            int openY = this.height * 94 / 100;
            int openW = actionButtonWidth();
            int openH = this.height * 5 / 100;
            if (event.x() >= openX && event.x() <= openX + openW && event.y() >= openY && event.y() <= openY + openH) {
                if (this.entity != null) {
                    if (!openClicked && entity.getMainHandItem().getItem() instanceof ItemCsgoBox) {
                        Identifier keyRl = this.keyRl;
                        boolean canOpen = true;
                        if (keyRl != null && !keyRl.equals(Identifier.parse("minecraft:air"))) {
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
                            ClientPacketListener openConn = Minecraft.getInstance().getConnection();
                            if (openConn != null) {
                                openConn.send(new ServerboundCustomPayloadPacket(new PacketCsgoProgress(openRequestId)));
                            }
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
            if (event.x() >= backX && event.x() <= backX + backW && event.y() >= backY && event.y() <= backY + backH) {
                if (this.minecraft != null && this.minecraft.player != null) {
                    this.minecraft.player.closeContainer();
                    this.minecraft.options.hideGui = false;
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    /**
     * Client-side key availability check mirroring the server's
     * {@code tryConsumeKeys} slot coverage (main inventory + armor + offhand)
     * so keys stashed in armor/offhand are not wrongly reported as missing.
     * Box instances are never keys, matching the server's consumption rule.
     */
    private static boolean hasKeyAnywhere(Player player, Identifier keyRl) {
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (isKey(stack, keyRl)) {
                return true;
            }
        }
        for (EquipmentSlot slot : LOCATION_SLOTS) {
            if (isKey(player.getItemBySlot(slot), keyRl)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isKey(ItemStack stack, Identifier keyRl) {
        return !stack.isEmpty()
                && !(stack.getItem() instanceof ItemCsgoBox)
                && keyRl.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    @Override
    public void onClose() {
        super.onClose();
    }
}
