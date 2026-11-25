package com.reclizer.csgobox.forge_1_20_1.gui;

import com.reclizer.csgobox.forge_1_20_1.compat.TaczInspectViewport;
import com.reclizer.csgobox.forge_1_20_1.event.FirstPersonInspectHandler;
import net.minecraft.util.FormattedCharSequence;
import com.reclizer.csgobox.forge_1_20_1.sounds.ModSounds;
import com.reclizer.csgobox.forge_1_20_1.utils.ButtonPalette;
import com.reclizer.csgobox.utils.ColorTools;
import com.reclizer.csgobox.utils.ItemDrag3D;
import com.reclizer.csgobox.forge_1_20_1.utils.AnimRenderOps;
import com.reclizer.csgobox.forge_1_20_1.utils.DiscInspect;
import com.reclizer.csgobox.forge_1_20_1.utils.EpicFightInspect;
import com.reclizer.csgobox.forge_1_20_1.utils.GuiItemMove;
import com.reclizer.csgobox.forge_1_20_1.utils.MaidSlabPreview;
import com.reclizer.csgobox.forge_1_20_1.utils.SlashBladePreview;
import com.reclizer.csgobox.forge_1_20_1.utils.SlashBladeComboInspect;
import com.reclizer.csgobox.forge_1_20_1.utils.RenderFontTool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

/**
 * The look screen: the big centred preview of one item (unboxing reward or
 * right-click inspect) over a live world backdrop. This class is only the
 * orchestrator — the pieces live in their own collaborators:
 * {@link LookLayout} (rects/easing), {@link LookToolbar} (icon row),
 * {@link LookWearCard} (shipment wear metadata), {@link LookSceneryRig}
 * (panorama camera), and the preview strategies in utils (EF battle
 * inspect, mob animation, SlashBlade rack, TACZ first-person, music).
 */
public class CsLookItemScreen extends Screen {
    private final Player player;
    private final ItemStack openItem;
    private final int grade;
    /** Screen to return to on close; {@code null} in the unboxing flow (the
     *  screen then closes to the world). Non-null when opened from the
     *  right-click "检视" context menu to inspect an item from a preview page. */
    private final Screen previousScreen;
    private final ItemDrag3D itemDrag = new ItemDrag3D(0, 0);

    /** Wear panel visibility, toggled by the info (ⓘ) toolbar button. */
    private boolean showInfoPanel = false;
    /** TACZ inspect viewport visibility; vestigial opt-in render path, exited
     *  by the inspect toolbar button when armed. */
    private boolean taczViewportActive = false;
    /** Skips the entry finish chime when the screen is reopened by
     *  {@link #openQuietly} right after a first-person inspect finishes. */
    private static boolean quietReopen = false;
    private final LookWearCard wearCard;

    /** Toolbar hover/glow state and drawing. */
    private final LookToolbar toolbar = new LookToolbar();
    /** Panorama camera rig behind the live world backdrop. */
    private final LookSceneryRig sceneryRig = new LookSceneryRig();
    private int screenTicks = 0;

    /** Inspect-button animation mode: cached living-entity previews (mob
     *  drops) walk in place while on; toggled by the inspect toolbar button
     *  (index 0, the single inspect entry), armed only when the current item
     *  renders as a living entity. */
    private boolean mobAnimMode = false;

    /** Inspect-button position in a blade's cycle (rack pose flip → combo
     *  motion → stop): set once the pose has been flipped, so the next
     *  click starts the combo motion instead. Reset whenever the screen
     *  (re)opens. */
    private boolean slashBladePoseFlipped = false;


    /** Displays the server-authoritative reward after the progress animation completes. */
    public CsLookItemScreen(ItemStack item, int grade) {
        this(item, grade, null);
    }

    /**
     * Same screen but opened from the right-click "检视" context menu to inspect
     * an item from a preview page: silent (no entry chime) and returns to
     * {@code previousScreen} on close instead of closing to the world.
     */
    public CsLookItemScreen(ItemStack item, int grade, Screen previousScreen) {
        super(Component.literal("look_item"));
        this.player = Minecraft.getInstance().player;
        this.openItem = item == null ? ItemStack.EMPTY : item.copy();
        this.grade = grade;
        this.previousScreen = previousScreen;
        this.wearCard = new LookWearCard(this.openItem, ThreadLocalRandom.current());
        // Inspect entry is silent: the unboxing chime belongs to the reward flow.
        boolean silent = quietReopen || previousScreen != null;
        quietReopen = false;
        if (this.player != null && !this.openItem.isEmpty() && !silent) {
            player.playSound(ModSounds.CS_FINSH.get(), 10F, 1F);
        }
    }

    /**
     * Reopen this screen without the entry finish chime, used after a
     * first-person inspect finishes so the return doesn't replay the chime.
     * {@code previousScreen} carries the inspect context through the session
     * (null in shipment flows) so the reopen shows the same toolbar.
     */
    public static void openQuietly(ItemStack item, int grade, Screen previousScreen, Minecraft mc) {
        quietReopen = true;
        mc.setScreen(new CsLookItemScreen(item, grade, previousScreen));
    }

    /** True when this screen presents a confirmed shipment (unboxing result or
     *  terminal post-buy showcase): only then is wear viewable — the wear
     *  panel's skin/pattern/wear card is the drop's own generated metadata.
     *  The right-click inspect flow ({@code previousScreen} set) confirms
     *  nothing about an item's wear, so the wear button is hidden there. */
    private boolean showsShipment() {
        return this.previousScreen == null;
    }

    // Thin layout wrappers (the math lives in LookLayout; these keep the
    // call sites in this file short).

    private int backButtonWidth() {
        return LookLayout.backButtonWidth(this.width);
    }

    private int backButtonX() {
        return LookLayout.backButtonX(this.width);
    }

    private int backButtonY() {
        return LookLayout.backButtonY(this.height);
    }

    private int toolbarButtonSize() {
        return LookLayout.toolbarButtonSize(this.height);
    }

    private int toolbarButtonX(int index) {
        return LookLayout.toolbarButtonX(index, this.height);
    }

    private int toolbarButtonY() {
        return LookLayout.toolbarButtonY(this.height);
    }

    private int toolbarEnterRise(int index) {
        return LookLayout.toolbarEnterRise(this.screenTicks, index);
    }

    private int previewTextureSize() {
        return LookLayout.previewTextureSize(this.width, this.height);
    }

    private int previewPixelX() {
        return LookLayout.previewPixelX(this.width, this.height);
    }

    private int previewPixelY() {
        return LookLayout.previewPixelY(this.width, this.height);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        this.itemDrag.tick();
        renderLookBackground(guiGraphics);
        // renderBg before renderLabels: button rectangle must be drawn first,
        // then the centered button text on top. Reversed order (the previous
        // behaviour) caused the text to be hidden behind the rectangle.
        // Title (y=5%) and grade label (y=11%) sit in their own band, so
        // swapping the two render calls has no other z-order side-effects.
        renderBg(guiGraphics, mouseX, mouseY, partialTicks);
        renderLabels(guiGraphics, mouseX, mouseY);
    }

    private void renderLookBackground(GuiGraphics guiGraphics) {
        if (this.minecraft != null && this.minecraft.level != null) {
            int fill = UiBackdrop.fill();
            AnimRenderOps.fillGradient(guiGraphics, 0, 0, this.width, this.height, fill, fill);
        }
    }

    private void renderBg(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        if (this.minecraft != null) {
            this.minecraft.options.hideGui = true;
        }
        if (openItem.isEmpty()) return;

        // Enter transition: the strip screen's dark backdrop fades out while
        // the item scales up from 0.9 to 1.0 over 300ms (6 ticks).
        int fadeAlpha = (int) (0xFF * (1F - LookLayout.enterEase(this.screenTicks))) & 0xFF;
        if (fadeAlpha > 0) {
            AnimRenderOps.fill(guiGraphics, 0, 0, this.width, this.height,
                    (fadeAlpha << 24) | 0x000000);
        }

        float scale = (previewTextureSize() / 16F) * (0.9F + 0.1F * LookLayout.enterEase(this.screenTicks));
        int dividerY = LookLayout.dividerY(this.height);
        AnimRenderOps.fill(guiGraphics, this.width * 25 / 100, dividerY,
                this.width * 75 / 100, dividerY + 1, 0xFFD3D3D3);
        AnimRenderOps.fill(guiGraphics, this.width * 33 / 100, LookLayout.gradeBarY(this.height),
                this.width * 67 / 100, LookLayout.gradeBarY(this.height) + 4, ColorTools.colorItems(grade));
        // Centre the result preview in the available band; mouseDragged below
        // uses the same previewPixelX/Y/size so drag-detection matches what
        // the user sees.
        // TACZ guns default to our own 3D render path (AnimRenderOps.renderItem3D
        // -> renderGunModel3D), which supports drag rotation. TACZ's native
        // first-person inspect animation is opt-in via the inspect toolbar
        // button's mode cycle (see mouseClicked).
        boolean viewportRendered = false;
        if (this.taczViewportActive && this.minecraft != null && this.minecraft.player != null) {
            viewportRendered = TaczInspectViewport.renderViewport(guiGraphics, openItem,
                    this.minecraft.player, partialTicks,
                    previewPixelX() + (int) (8 * scale), previewPixelY() + (int) (8 * scale),
                    scale);
            if (!viewportRendered) {
                // Viewport lost (e.g. TACZ error mid-frame): fall back to 2D for good.
                this.taczViewportActive = false;
            }
        }
        if (!viewportRendered) {
            if (EpicFightInspect.isActive()) {
                // Epic Fight battle inspect owns the preview area: its own
                // rigged-figure widget (with camera controls) replaces the
                // flat item render for EF weapons.
                EpicFightInspect.render(guiGraphics, mouseX, mouseY, partialTicks);
            } else {
                // 检视页方块特效（BE 动画 + 粒子，2026-11-20）：begin/end 只包住
                // 本页的 3D 渲染调用——拖拽页/网格/终端机路径不设标志，行为不变。
                GuiItemMove.beginInspectEffects(openItem);
                try {
                    GuiItemMove.renderItemInInventoryFollowsMouse(guiGraphics,
                            previewPixelX(), previewPixelY(),
                            this.itemDrag.rotation(), openItem, this.player, scale);
                } finally {
                    GuiItemMove.endInspectEffects();
                }
            }
        }

        int btnX = backButtonX();
        int btnY = backButtonY();
        int btnW = backButtonWidth();
        int btnH = LookLayout.backButtonHeight(this.height);
        boolean hoverButton = ButtonPalette.isInside(mouseX, mouseY, btnX, btnY, btnW, btnH);
        ButtonPalette.drawButton(guiGraphics, ButtonPalette.DANGER, btnX, btnY, btnW, btnH, hoverButton);

        renderInfoPanel(guiGraphics);
        renderToolbar(guiGraphics, mouseX, mouseY);
    }

    private void renderToolbar(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        boolean specialInspect = this.mobAnimMode || EpicFightInspect.isActive()
                || SlashBladeComboInspect.isActive() || DiscInspect.isPlaying();
        this.toolbar.render(guiGraphics, this.font, this.width, this.height,
                mouseX, mouseY, this.screenTicks, showsShipment(), specialInspect, this.showInfoPanel);
    }

    private void renderInfoPanel(GuiGraphics guiGraphics) {
        if (!this.showInfoPanel || openItem.isEmpty()) return;
        float scale = 0.7F;
        int rowH = 13;
        String[] labelKeys = {
                "gui.csgobox.csgo_box.info.skin_style",
                "gui.csgobox.csgo_box.info.skin_id",
                "gui.csgobox.csgo_box.info.pattern",
                "gui.csgobox.csgo_box.info.wear_rating",
                "gui.csgobox.csgo_box.info.exterior"
        };
        Component[] valueTexts = {
                Component.translatable(this.wearCard.skinStyleKey()),
                Component.literal(String.valueOf(this.wearCard.skinId())),
                Component.literal(String.valueOf(this.wearCard.patternSeed())),
                Component.literal(this.wearCard.formatWear()),
                Component.translatable(this.wearCard.wearTierKey())
        };
        int gap = 8;
        int padX = 8;
        int padY = 6;
        int maxRowW = 0;
        for (int i = 0; i < labelKeys.length; i++) {
            maxRowW = Math.max(maxRowW,
                    (int) (this.font.width(Component.translatable(labelKeys[i]).getVisualOrderText()) * scale)
                            + gap + (int) (this.font.width(valueTexts[i].getVisualOrderText()) * scale));
        }
        int cardW = maxRowW + padX * 2;
        int cardH = labelKeys.length * rowH + padY * 2;
        int anchorX = toolbarButtonX(1) + toolbarButtonSize() / 2;
        int cardX = anchorX - cardW / 2;
        cardX = Math.max(2, Math.min(cardX, this.width - cardW - 2));
        int cardBottom = toolbarButtonY() - 8;
        int cardY = cardBottom - cardH;
        renderRoundedRect(guiGraphics, cardX, cardY, cardW, cardH, 4, 0xE0101014);
        int textY = cardY + padY;
        for (int i = 0; i < labelKeys.length; i++) {
            drawInfoRow(guiGraphics, cardX + padX, cardX + cardW - padX, textY + i * rowH, scale,
                    labelKeys[i], valueTexts[i]);
        }
    }

    private void renderRoundedRect(GuiGraphics guiGraphics, int x, int y, int w, int h, int r, int color) {
        for (int dy = 0; dy < h; dy++) {
            int cut = 0;
            if (dy < r) {
                cut = r - (int) Math.sqrt((double) (r * r - (r - 1 - dy) * (r - 1 - dy)));
            } else if (dy > h - r - 1) {
                int t = h - 1 - dy;
                cut = r - (int) Math.sqrt((double) (r * r - (r - 1 - t) * (r - 1 - t)));
            }
            guiGraphics.fill(x + cut, y + dy, x + w - cut, y + dy + 1, color);
        }
    }

    private void drawInfoRow(GuiGraphics guiGraphics, int labelX, int valueRight, int y, float scale,
                             String labelKey, Component value) {
        renderText(guiGraphics, Component.translatable(labelKey).getVisualOrderText(), labelX, y, scale,
                0xFF9A9A9A);
        float valueWidth = this.font.width(value.getVisualOrderText()) * scale;
        RenderFontTool.drawString(guiGraphics, this.font, value.getVisualOrderText(),
                valueRight - valueWidth, y, 0, 0, scale, 0xFFFFFFFF);
    }


    private void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (openItem.isEmpty()) return;

        Style style = Style.EMPTY.withBold(true);
        int titleMaxWidth = Math.round(this.width * 54F / 100F);
        float titleScale = 1.6F;
        float titleX = centeredTextX(openItem.getItem().getName(openItem).getString(), titleScale, titleMaxWidth);
        RenderFontTool.drawStringClamped(guiGraphics, this.font, openItem.getItem().getName(openItem),
                titleX, this.height * 5F / 100F, 0, 0, titleScale,
                titleMaxWidth, 0xFFFFFFFF);
        float gradeX = centeredTextX(Component.translatable("gui.csgobox.csgo_box.grade" + grade).getString(), 1.0F,
                Math.round(this.width * 24F / 100F));
        renderText(guiGraphics,
                (grade >= 1 && grade <= 5
                        ? Component.translatable("gui.csgobox.csgo_box.grade" + grade)
                        : Component.translatable("gui.csgobox.csgo_box.grade_n", grade)).getVisualOrderText(),
                gradeX, this.height * 11.5F / 100F, 1F);
        // Button text colour tracks the panel painted in renderBg: hovered
        // button -> textColorHover, otherwise -> textColor. The previous
        // implementation used a constant white that clashed with the warm
        // danger fill.
        int bx = backButtonX();
        int by = backButtonY();
        int bw = backButtonWidth();
        int bh = LookLayout.backButtonHeight(this.height);
        boolean backHover = ButtonPalette.isInside(mouseX, mouseY, bx, by, bw, bh);
        int backText = backHover ? ButtonPalette.DANGER.textColorHover() : ButtonPalette.DANGER.textColor();
        renderCenteredText(guiGraphics,
                Component.translatable("gui.csgobox.csgo_box.close").withStyle(style).getVisualOrderText(),
                bx, by, bw, bh, 0.8F, backText);
    }

    private void renderText(GuiGraphics guiGraphics, FormattedCharSequence text, float x, float y, float scale) {
        renderText(guiGraphics, text, x, y, scale, 0xFFFFFFFF);
    }

    private void renderText(GuiGraphics guiGraphics, FormattedCharSequence text, float x, float y,
                            float scale, int color) {
        RenderFontTool.drawString(guiGraphics, this.font, text, x, y, 0, 0, scale, color);
    }

    private float centeredTextX(String text, float scale, int maxWidth) {
        float renderedWidth = Math.min(this.font.width(text) * scale, maxWidth);
        return (this.width - renderedWidth) / 2.0F;
    }

    private void renderCenteredText(GuiGraphics guiGraphics, FormattedCharSequence text,
                                    int x, int y, int w, int h, float scale, int color) {
        float textX = x + (w - this.font.width(text) * scale) / 2.0F;
        float textY = y + (h - this.font.lineHeight * scale) / 2.0F + 1;
        RenderFontTool.drawString(guiGraphics, this.font, text, textX, textY, 0, 0, scale, color);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        // EF battle inspect: the widget handles its own camera drag inside
        // its rect; the item drag stays parked while it owns the preview.
        if (EpicFightInspect.isActive() && EpicFightInspect.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        // Match the actual rendered crate rectangle (centred horizontally
        // and vertically inside the 16%–92% container). The previous code
        // used width*37% + frameWidth as the centre of an upper-left
        // rectangle, which no longer matched the centred draw site.
        int size = previewTextureSize();
        int x = previewPixelX();
        int y = previewPixelY();
        boolean isInRange = mouseX >= x && mouseX <= x + size
                && mouseY >= y && mouseY <= y + size;
        if (button == 0 && isInRange) {
            this.itemDrag.accumulate(dragX, dragY);
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // EF battle inspect: let the widget finish its own camera drags.
        if (EpicFightInspect.isActive() && EpicFightInspect.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        // EF battle inspect zoom lives inside the widget; outside stays vanilla.
        if (EpicFightInspect.isActive() && EpicFightInspect.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (EpicFightInspect.isActive() && EpicFightInspect.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int btnX = backButtonX();
        int btnY = backButtonY();
        int btnW = backButtonWidth();
        int btnH = LookLayout.backButtonHeight(this.height);
        if (button == 0 && ButtonPalette.isInside(mouseX, mouseY, btnX, btnY, btnW, btnH)) {
            this.onClose();
            return true;
        }
        int infoSize = toolbarButtonSize();
        // Inspect button (index 0) is the single inspect entry: it cycles the
        // inspect modes available for this item — Epic Fight battle inspect,
        // mob animation, SlashBlade rack pose / combo motion, TACZ
        // first-person inspect — falling back to our own 3D drag preview
        // once nothing special is on.
        int inspectX = toolbarButtonX(0);
        int inspectY = toolbarButtonY() + toolbarEnterRise(0);
        if (button == 0 && ButtonPalette.isInside(mouseX, mouseY, inspectX, inspectY, infoSize, infoSize)) {
            // Audio items (records, plus third-party inspect-audio provider
            // matches): the inspect button doubles as the music switch
            // (play/stop). They carry no special inspect mode, so none of the
            // branches below would ever fire — the audio check runs first.
            if (DiscInspect.isAudioItem(this.openItem)) {
                DiscInspect.toggle(this.openItem);
                return true;
            }
            if (this.taczViewportActive) {
                TaczInspectViewport.exit(this.openItem);
                this.taczViewportActive = false;
                return true;
            }
            if (EpicFightInspect.availableFor(this.openItem)) {
                this.toggleEpicFightInspect();
            } else if (AnimRenderOps.hasPreviewMobInstance()) {
                this.mobAnimMode = !this.mobAnimMode;
            } else if (SlashBladePreview.hasPreviewBlade()) {
                // Blade cycle: flip the rack pose, then play the blade's own
                // combo motion on the preview player, then stop it again.
                if (SlashBladeComboInspect.isActive()) {
                    SlashBladeComboInspect.stop();
                } else if (!this.slashBladePoseFlipped) {
                    this.slashBladePoseFlipped = true;
                    SlashBladePreview.toggleStyle();
                } else if (!SlashBladeComboInspect.start()) {
                    // Combo motion unavailable (SlashBlade's surface moved):
                    // keep the button useful as the pose flip.
                    this.slashBladePoseFlipped = false;
                    SlashBladePreview.toggleStyle();
                }
            } else if (this.minecraft != null && this.minecraft.player != null
                    && TaczInspectViewport.isAvailable(this.openItem)) {
                // First-person inspect: close the GUI, then let TACZ play its
                // native first-person inspect behind a transparent input-lock
                // screen; FirstPersonInspectHandler reopens this screen when
                // the animation finishes or the player presses Esc. TACZ's
                // native draw needs the vanilla first-person hand, so the
                // handler takes Epic Fight out of its player mode for the
                // session and restores it afterwards.
                this.onClose();
                FirstPersonInspectHandler.start(this.minecraft.player, this.openItem, this.grade,
                        null, this.previousScreen);
            }
            return true;
        }
        // Wear (ⓘ, slot 1) exists in shipment flows only: the inspect flow
        // (previousScreen set) confirms nothing, so wear stays unviewable.
        if (this.showsShipment()) {
            int infoX = toolbarButtonX(1);
            int infoY = toolbarButtonY() + toolbarEnterRise(1);
            if (button == 0 && ButtonPalette.isInside(mouseX, mouseY, infoX, infoY, infoSize, infoSize)) {
                this.showInfoPanel = !this.showInfoPanel;
                return true;
            }
        }
        int stickerSlot = this.showsShipment() ? 2 : 1;
        int stickerX = toolbarButtonX(stickerSlot);
        int stickerY = toolbarButtonY() + toolbarEnterRise(stickerSlot);
        if (button == 0 && ButtonPalette.isInside(mouseX, mouseY, stickerX, stickerY, infoSize, infoSize)) {
            this.sceneryRig.randomize(this.minecraft, this.player);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Screen-open camera mount: the background rides the scenery rig like
     *  the main-menu panorama (continuous slow spin; the position never
     *  changes until a "change scenery" click). */
    @Override
    public void init() {
        super.init();
        AnimRenderOps.clearPreviewMobInstance();
        SlashBladePreview.clearPreviewBlade();
        this.slashBladePoseFlipped = false;
        MaidSlabPreview.clearPreviewPick();
        EpicFightInspect.clear();
        this.sceneryRig.beginPanorama(this.minecraft, this.player);
    }

    /** Toggles the Epic Fight battle inspect: starts their own previewer
     *  widget over the room above the toolbar (figure wearing the player's
     *  skin, holding the weapon) or tears it down again. Reflection failures
     *  leave the screen untouched. */
    private void toggleEpicFightInspect() {
        if (EpicFightInspect.isActive()) {
            EpicFightInspect.stop();
            return;
        }
        ResourceLocation skin = null;
        // 1.20.1: Minecraft.player is already a LocalPlayer (an
        // AbstractClientPlayer); a Java 17 instanceof pattern may not
        // widen, so use the player reference directly.
        if (this.minecraft != null && this.minecraft.player != null
                && this.minecraft.getSkinManager() != null) {
            skin = this.minecraft.getSkinManager()
                    .getInsecureSkinLocation(this.minecraft.player.getGameProfile());
        }
        // The figure takes the whole room above the toolbar instead of the
        // item square: its framing has to hold the weapon's whole swing
        // (see EpicFightInspect). The item square's size is passed along as
        // the size the figure must not exceed — the size it had before.
        EpicFightInspect.start(0, 0, this.width, LookLayout.battleInspectHeight(this.height),
                this.openItem, skin, previewTextureSize());
    }

    /** True while the scenery rig owns the render camera (panorama mount);
     *  read by the render-cancel handler in the event package. */
    public boolean isSceneryRigMounted() {
        return this.sceneryRig.mounted();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            this.onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.options.hideGui = false;
        }
        if (this.previousScreen != null && this.minecraft != null) {
            this.minecraft.setScreen(this.previousScreen);
        } else {
            super.onClose();
        }
    }

    @Override
    public void removed() {
        // Same protection as onClose(): death/respawn replaces this screen via
        // setScreen() -> Screen.removed(), which would otherwise leave
        // hideGui=true and hide the HUD permanently.
        if (this.minecraft != null) {
            this.minecraft.options.hideGui = false;
        }
        this.sceneryRig.dismount(this.minecraft, this.player);
        AnimRenderOps.resetPreviewMobAnimation();
        DiscInspect.stop();
        this.mobAnimMode = false;
        EpicFightInspect.clear();
        SlashBladeComboInspect.stop();
        this.slashBladePoseFlipped = false;
        if (this.taczViewportActive) {
            TaczInspectViewport.exit(this.openItem);
            this.taczViewportActive = false;
        }
        super.removed();
    }

    @Override
    public final void tick() {
        super.tick();
        this.screenTicks++;
        this.toolbar.tickGlow();
        if (this.minecraft == null || this.minecraft.player == null) return;
        this.sceneryRig.advance();
        AnimRenderOps.tickPreviewMobAnimations(this.mobAnimMode);
        EpicFightInspect.tick();
        SlashBladeComboInspect.tick();
        if (!this.minecraft.player.isAlive() || this.minecraft.player.isRemoved()) {
            this.onClose();
        }
    }
}
