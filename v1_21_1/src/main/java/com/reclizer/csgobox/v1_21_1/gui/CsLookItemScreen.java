package com.reclizer.csgobox.v1_21_1.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.reclizer.csgobox.v1_21_1.CsgoBox;
import com.reclizer.csgobox.v1_21_1.compat.TaczInspectViewport;
import com.reclizer.csgobox.v1_21_1.event.FirstPersonInspectHandler;
import com.reclizer.csgobox.v1_21_1.sounds.ModSounds;
import com.reclizer.csgobox.utils.ColorTools;
import com.reclizer.csgobox.utils.Easing;
import com.reclizer.csgobox.utils.ItemDrag3D;
import com.reclizer.csgobox.v1_21_1.utils.AnimRenderOps;
import com.reclizer.csgobox.v1_21_1.utils.RenderFontTool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public class CsLookItemScreen extends Screen {
    private final Player player;
    private final ItemStack openItem;
    private final int grade;
    private final ItemDrag3D itemDrag = new ItemDrag3D(0, 0);

    /** Wear panel visibility, toggled by the info (ⓘ) toolbar button. */
    private boolean showInfoPanel = false;
    /** Info panel open/close animation: 0 = closed, 1 = open. */
    private float infoPanelAnim = 0F;
    /** TACZ inspect viewport visibility, toggled by the gloves toolbar button. */
    private boolean taczViewportActive = false;
    /** Skips the entry finish chime when the screen is reopened by
     *  {@link #openQuietly} right after a first-person inspect finishes. */
    private static boolean quietReopen = false;
    private final float wearValue;
    private final int patternSeed;
    private final int skinId;
    private final int skinStyleIndex;

    private static final ResourceLocation ICON_INSPECT =
            ResourceLocation.parse("csgobox:textures/gui/toolbar/inspect.png");
    private static final ResourceLocation ICON_GLOVES =
            ResourceLocation.parse("csgobox:textures/gui/toolbar/gloves.png");
    private static final ResourceLocation ICON_MODEL =
            ResourceLocation.parse("csgobox:textures/gui/toolbar/model.png");
    private static final ResourceLocation ICON_INFO =
            ResourceLocation.parse("csgobox:textures/gui/toolbar/info.png");
    private static final ResourceLocation ICON_STICKER =
            ResourceLocation.parse("csgobox:textures/gui/toolbar/sticker.png");
    private static final ResourceLocation ICON_MORE =
            ResourceLocation.parse("csgobox:textures/gui/toolbar/more.png");


    /** Content bounding box (px in the 32x32 texture) per icon, kept in
     *  sync with the artwork: inspect 30x29, gloves 30x20, model 28x30,
     *  info 26x26, sticker 30x23, more 7x27. */
    private static final int[] ICON_CONTENT_W = {30, 30, 28, 26, 30, 7};
    private static final int[] ICON_CONTENT_H = {29, 20, 30, 26, 23, 27};

    /** Tooltip lang keys per toolbar button. */
    private static final String[] TOOLTIP_KEYS = {
            "gui.csgobox.csgo_box.toolbar.inspect",
            "gui.csgobox.csgo_box.toolbar.gloves",
            "gui.csgobox.csgo_box.toolbar.model",
            "gui.csgobox.csgo_box.toolbar.info",
            "gui.csgobox.csgo_box.toolbar.sticker",
            "gui.csgobox.csgo_box.toolbar.more"
    };

    /** Toolbar hover state: index of hovered button (-1 = none) and 0..1 glow. */
    private int hoveredButton = -1;
    private float toolbarGlow = 0F;
    private int screenTicks = 0;

    /** Info panel label keys (translated once per screen). */
    private static final String[] INFO_LABEL_KEYS = {
            "gui.csgobox.csgo_box.info.skin_style",
            "gui.csgobox.csgo_box.info.skin_id",
            "gui.csgobox.csgo_box.info.pattern",
            "gui.csgobox.csgo_box.info.wear_rating",
            "gui.csgobox.csgo_box.info.exterior"
    };
    /** Lazily laid-out info panel rows: skin/pattern/wear are fixed for the
     *  screen's lifetime, so translate + measure them once. */
    private FormattedCharSequence[] infoLabelTexts;
    private FormattedCharSequence[] infoValueTexts;
    private int infoMaxRowW = -1;

    /** Decorative finish styles, mirrored in lang (style.custom_paint etc). */
    private static final String[] SKIN_STYLES = {
            "custom_paint", "gunsmith", "patina", "hydrographic", "spray_paint", "anodized"
    };

    /** Displays the server-authoritative reward after the progress animation completes. */
    public CsLookItemScreen(ItemStack item, int grade) {
        super(Component.literal("look_item"));
        this.player = Minecraft.getInstance().player;
        this.openItem = item == null ? ItemStack.EMPTY : item.copy();
        this.grade = grade;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (!this.openItem.isEmpty() && this.openItem.isDamageableItem() && this.openItem.getDamageValue() > 0) {
            int maxDamage = this.openItem.getMaxDamage();
            this.wearValue = maxDamage > 0 ? (float) this.openItem.getDamageValue() / maxDamage : rnd.nextFloat();
        } else {
            this.wearValue = rnd.nextFloat();
        }
        this.patternSeed = rnd.nextInt(1000);
        this.skinId = rnd.nextInt(100, 1301);
        this.skinStyleIndex = rnd.nextInt(SKIN_STYLES.length);
        boolean silent = quietReopen;
        quietReopen = false;
        if (this.player != null && !this.openItem.isEmpty() && !silent) {
            float vol = CsgoBox.CONFIG.finishSoundVolume() / 100F;
            if (vol > 0) {
                player.playSound(ModSounds.CS_FINSH.get(), vol * 10F, 1F);
            }
        }
    }

    /**
     * Reopen this screen without the entry finish chime, used after a
     * first-person inspect finishes so the return doesn't replay the chime.
     */
    public static void openQuietly(ItemStack item, int grade, Minecraft mc) {
        quietReopen = true;
        mc.setScreen(new CsLookItemScreen(item, grade));
    }

    private int backButtonWidth() {
        return Math.max(64, this.width * 7 / 100);
    }

    private int backButtonX() {
        return Math.max(8, this.width - backButtonWidth() - 16);
    }

    private int backButtonY() {
        return this.height - 8 - this.height * 5 / 100;
    }

    private int toolbarButtonSize() {
        return Math.max(17, this.height * 35 / 1000);
    }

    private int toolbarButtonX(int index) {
        return Math.max(8, 8 + index * (toolbarButtonSize() + 6));
    }

    private int toolbarButtonY() {
        return this.height - 8 - toolbarButtonSize();
    }

    private float toolbarEnterEase(int index) {
        float enterT = Math.max(0F, Math.min(1F, (this.screenTicks - index * 0.8F) / 3F));
        return 1F - (1F - enterT) * (1F - enterT);
    }

    private int toolbarEnterRise(int index) {
        return (int) (8F * (1F - toolbarEnterEase(index)));
    }

    /** Enter transition progress: 0 at screen open, 1 after 300ms (6 ticks). */
    private float enterE() {
        return Easing.easeOutCubic(Math.min(1F, this.screenTicks / 6F));
    }

    private String wearTierKey() {
        float w = this.wearValue;
        if (w < 0.07F) return "gui.csgobox.csgo_box.wear_fn";
        if (w < 0.15F) return "gui.csgobox.csgo_box.wear_mw";
        if (w < 0.38F) return "gui.csgobox.csgo_box.wear_ft";
        if (w < 0.45F) return "gui.csgobox.csgo_box.wear_ww";
        return "gui.csgobox.csgo_box.wear_bs";
    }

    private String formatWear() {
        return String.format(Locale.ROOT, "%.9f", this.wearValue);
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
        renderBg(guiGraphics, mouseX, mouseY, partialTicks);
        renderLabels(guiGraphics);
        // Keep the pre-swap end-of-frame state: blend must stay enabled while
        // renderLabels draws (RenderFontTool.drawString does not manage blend),
        // so the disable that used to end renderBg now ends render() instead.
        RenderSystem.disableBlend();
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
        int fadeAlpha = (int) (0xFF * (1F - enterE())) & 0xFF;
        if (fadeAlpha > 0) {
            AnimRenderOps.fill(guiGraphics, 0, 0, this.width, this.height,
                    (fadeAlpha << 24) | 0x000000);
        }

        int frameWidth = width * 26 / 100;
        float scale = (frameWidth / 16F) * (0.9F + 0.1F * enterE());
        int dividerY = this.height - 18 - toolbarButtonSize();
        AnimRenderOps.fill(guiGraphics, this.width * 25 / 100, dividerY,
                this.width * 75 / 100, dividerY + 1, 0xFFD3D3D3);
        AnimRenderOps.fill(guiGraphics, this.width * 37 / 100, this.height * 16 / 100,
                this.width * 63 / 100, this.height * 16 / 100 + 4, ColorTools.colorItems(grade));
        // TACZ guns default to our own 3D render path (AnimRenderOps.renderItem3D
        // -> renderGunModel3D), which supports drag rotation. The TACZ inspect
        // viewport is opt-in via the gloves toolbar button ("First-Person
        // Inspect Animation"); it uses TACZ's fixed-pose renderer and does
        // not follow mouse drag.
        boolean viewportRendered = false;
        if (this.taczViewportActive && this.minecraft != null && this.minecraft.player != null) {
            viewportRendered = TaczInspectViewport.renderViewport(guiGraphics, openItem,
                    this.minecraft.player, partialTicks,
                    this.width * 37 / 100 + (int) (8 * scale), this.height * 30 / 100 + (int) (8 * scale),
                    scale);
            if (!viewportRendered) {
                // Viewport lost (e.g. TACZ error mid-frame): fall back to 2D for good.
                this.taczViewportActive = false;
            }
        }
        if (!viewportRendered) {
            AnimRenderOps.renderItem3D(guiGraphics, openItem, this.player,
                    this.width * 37 / 100, this.height * 30 / 100, this.itemDrag.rotation(), scale);
        }

        int btnX = backButtonX();
        int btnY = backButtonY();
        int btnW = backButtonWidth();
        int btnH = this.height * 5 / 100;
        boolean hoverButton = isInside(mouseX, mouseY, btnX, btnY, btnW, btnH);
        int outerColor = hoverButton ? 0xFFFF4444 : 0xFFFF0000;
        int innerColor = hoverButton ? 0xFFCC4444 : 0xFFAA0000;
        AnimRenderOps.fill(guiGraphics, btnX, btnY, btnX + btnW, btnY + btnH, outerColor);
        AnimRenderOps.fill(guiGraphics, btnX + 1, btnY + 1, btnX + btnW - 1, btnY + btnH - 1, innerColor);

        renderInfoPanel(guiGraphics);
        renderToolbar(guiGraphics, mouseX, mouseY);
    }

    private void renderToolbar(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int size = toolbarButtonSize();
        int y = toolbarButtonY();
        float anim = this.infoPanelAnim;
        ResourceLocation[] icons = {ICON_INSPECT, ICON_GLOVES, ICON_MODEL, ICON_INFO, ICON_STICKER, ICON_MORE};
        this.hoveredButton = -1;
        for (int i = 0; i < icons.length; i++) {
            int x = toolbarButtonX(i);
            int by = y + toolbarEnterRise(i);
            int alpha = (int) (0xFF * toolbarEnterEase(i));

            boolean hover = isInside(mouseX, mouseY, x, by, size, size);
            if (hover) {
                this.hoveredButton = i;
            }
            // Inspect (index 0) is the default mode (own 3D drag preview);
            // gloves (index 1) is active only while the TACZ viewport is on.
            boolean active = (i == 0 && !this.taczViewportActive) || (i == 1 && this.taczViewportActive) || (i == 3 && this.showInfoPanel);
            int outer = 0xFF2B2B31;
            int inner = active ? 0xFF33333B : 0xFF232328;
            AnimRenderOps.fill(guiGraphics, x, by, x + size, by + size, (alpha << 24) | (outer & 0xFFFFFF));
            AnimRenderOps.fill(guiGraphics, x + 1, by + 1, x + size - 1, by + size - 1, (alpha << 24) | (inner & 0xFFFFFF));
            if (active) {
                int underlineAlpha = i == 3 ? (int) (0xFF * anim) : 0xFF;
                AnimRenderOps.fill(guiGraphics, x + 2, by + size - 2, x + size - 2, by + size,
                        (underlineAlpha << 24) | 0xFFFFFF);
            }
            int iconSize = Math.max(12, Math.min(64, size * 3 / 4));
            int maxEdge = Math.max(ICON_CONTENT_W[i], ICON_CONTENT_H[i]);
            int screenW = iconSize * ICON_CONTENT_W[i] / maxEdge;
            int screenH = iconSize * ICON_CONTENT_H[i] / maxEdge;
            int iconX = x + (size - screenW) / 2;
            int iconY = by + (size - screenH) / 2;
            int u = (32 - ICON_CONTENT_W[i]) / 2;
            int v = (32 - ICON_CONTENT_H[i]) / 2;
            int iconColor;
            if (active) {
                iconColor = 0xFFFFFFFF;
            } else {
                float b = i == this.hoveredButton ? 0.55F + 0.45F * this.toolbarGlow : 0.55F;
                iconColor = 0xFF000000 | ((int) (0xFF * b) << 16) | ((int) (0xFF * b) << 8) | (int) (0xFF * b);
            }
            iconColor = (alpha << 24) | (iconColor & 0xFFFFFF);
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(((iconColor >> 16) & 0xFF) / 255F,
                    ((iconColor >> 8) & 0xFF) / 255F, (iconColor & 0xFF) / 255F, ((iconColor >> 24) & 0xFF) / 255F);
            if (i == 5) {
                renderMoreDots(guiGraphics, x, by, size, iconColor);
            } else {
                AnimRenderOps.blitTextured(guiGraphics, icons[i], iconX, iconY, screenW, screenH, u, v,
                        ICON_CONTENT_W[i], ICON_CONTENT_H[i], 32, 32, iconColor);
            }
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        }
        renderToolbarTooltip(guiGraphics, mouseX, mouseY);
    }

    private void renderMoreDots(GuiGraphics guiGraphics, int btnX, int btnY, int size, int color) {
        int r = Math.max(2, size / 5);
        int cx = btnX + size / 2;
        int cy = btnY + size / 2;
        int spacing = Math.max(3, r * 2);
        for (int k = -1; k <= 1; k++) {
            int ccy = cy + k * spacing;
            for (int dy = -r; dy <= r; dy++) {
                int half = (int) Math.sqrt((double) (r * r - dy * dy));
                AnimRenderOps.fill(guiGraphics, cx - half, ccy + dy, cx + half + 1, ccy + dy + 1, color);
            }
        }
    }


    private void renderToolbarTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (this.hoveredButton < 0 || this.toolbarGlow < 0.05F) return;
        int size = toolbarButtonSize();
        int x = toolbarButtonX(this.hoveredButton);
        int y = toolbarButtonY() + toolbarEnterRise(this.hoveredButton);
        int textW = this.font.width(Component.translatable(TOOLTIP_KEYS[this.hoveredButton]).getVisualOrderText());
        int tooltipW = (int) (textW * 0.7F) + 8;
        int tooltipH = (int) (this.font.lineHeight * 0.7F) + 4;
        int tipX = x + (size - tooltipW) / 2;
        int tipY = y - tooltipH - 6;
        tipX = Math.max(2, Math.min(tipX, this.width - tooltipW - 2));
        tipY = Math.max(2, tipY);
        int alpha = (int) (0xCC * this.toolbarGlow * toolbarEnterEase(this.hoveredButton));
        AnimRenderOps.fill(guiGraphics, tipX, tipY, tipX + tooltipW, tipY + tooltipH, (alpha << 24) | 0x101014);
        int textAlpha = (int) (0xFF * this.toolbarGlow * toolbarEnterEase(this.hoveredButton));
        renderText(guiGraphics,
                Component.translatable(TOOLTIP_KEYS[this.hoveredButton]).getVisualOrderText(),
                tipX + 4, tipY + 2, 0.7F, (textAlpha << 24) | 0xCCCCCC);
    }

    private void renderInfoPanel(GuiGraphics guiGraphics) {
        if (this.infoPanelAnim < 0.02F || openItem.isEmpty()) return;
        float anim = this.infoPanelAnim;
        int textAlpha = (int) (0xFF * anim) & 0xFF;
        int cardAlpha = (int) (0xE0 * anim) & 0xFF;
        float scale = 0.7F;
        int rowH = 13;
        if (this.infoLabelTexts == null) {
            Component[] labels = new Component[INFO_LABEL_KEYS.length];
            for (int i = 0; i < INFO_LABEL_KEYS.length; i++) {
                labels[i] = Component.translatable(INFO_LABEL_KEYS[i]);
            }
            Component[] values = {
                    Component.translatable("gui.csgobox.csgo_box.style." + SKIN_STYLES[this.skinStyleIndex]),
                    Component.literal(String.valueOf(this.skinId)),
                    Component.literal(String.valueOf(this.patternSeed)),
                    Component.literal(formatWear()),
                    Component.translatable(wearTierKey())
            };
            int gap = 8;
            this.infoLabelTexts = new FormattedCharSequence[labels.length];
            this.infoValueTexts = new FormattedCharSequence[values.length];
            int maxRowW = 0;
            for (int i = 0; i < labels.length; i++) {
                this.infoLabelTexts[i] = labels[i].getVisualOrderText();
                this.infoValueTexts[i] = values[i].getVisualOrderText();
                maxRowW = Math.max(maxRowW,
                        (int) (this.font.width(this.infoLabelTexts[i]) * scale)
                                + gap + (int) (this.font.width(this.infoValueTexts[i]) * scale));
            }
            this.infoMaxRowW = maxRowW;
        }
        int padX = 8;
        int padY = 6;
        int cardW = this.infoMaxRowW + padX * 2;
        int cardH = INFO_LABEL_KEYS.length * rowH + padY * 2;
        int anchorX = toolbarButtonX(3) + toolbarButtonSize() / 2;
        int cardX = anchorX - cardW / 2;
        cardX = Math.max(2, Math.min(cardX, this.width - cardW - 2));
        int cardBottom = toolbarButtonY() - 8;
        int cardY = cardBottom - cardH + Math.round(8F * (1F - anim));
        renderRoundedRect(guiGraphics, cardX, cardY, cardW, cardH, 4, (cardAlpha << 24) | 0x101014);
        int textY = cardY + padY;
        for (int i = 0; i < INFO_LABEL_KEYS.length; i++) {
            drawInfoRow(guiGraphics, cardX + padX, cardX + cardW - padX, textY + i * rowH, scale,
                    this.infoLabelTexts[i], this.infoValueTexts[i], textAlpha);
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
            AnimRenderOps.fill(guiGraphics, x + cut, y + dy, x + w - cut, y + dy + 1, color);
        }
    }

    private void drawInfoRow(GuiGraphics guiGraphics, int labelX, int valueRight, int y, float scale,
                             FormattedCharSequence labelText, FormattedCharSequence valueText, int textAlpha) {
        renderText(guiGraphics, labelText, labelX, y, scale,
                (textAlpha << 24) | 0x9A9A9A);
        float valueWidth = this.font.width(valueText) * scale;
        RenderFontTool.drawString(guiGraphics, this.font, valueText,
                valueRight - valueWidth, y, 0, 0, scale, (textAlpha << 24) | 0xFFFFFF);
    }


    private void renderLabels(GuiGraphics guiGraphics) {
        if (openItem.isEmpty()) return;

        Style style = Style.EMPTY.withBold(true);
        int titleAlpha = (int) (0xFF * enterE()) & 0xFF;
        renderText(guiGraphics, openItem.getItem().getName(openItem).getVisualOrderText(),
                this.width * 45F / 100F, this.height * 5F / 100F, 1.8F, (titleAlpha << 24) | 0xFFFFFFFF);
        renderText(guiGraphics,
                Component.translatable("gui.csgobox.csgo_box.grade" + grade).getVisualOrderText(),
                this.width * 45F / 100F, this.height * 11F / 100F, 1F, (titleAlpha << 24) | 0xFFFFFFFF);
        renderCenteredText(guiGraphics,
                Component.translatable("gui.csgobox.csgo_box.close").withStyle(style).getVisualOrderText(),
                backButtonX(), backButtonY(), backButtonWidth(), this.height * 5 / 100, 0.8F);
    }

    private void renderText(GuiGraphics guiGraphics, FormattedCharSequence text, float x, float y, float scale) {
        renderText(guiGraphics, text, x, y, scale, 0xFFFFFFFF);
    }

    private void renderText(GuiGraphics guiGraphics, FormattedCharSequence text, float x, float y,
                            float scale, int color) {
        RenderFontTool.drawString(guiGraphics, this.font, text, x, y, 0, 0, scale, color);
    }

    private void renderCenteredText(GuiGraphics guiGraphics, FormattedCharSequence text,
                                    int x, int y, int w, int h, float scale) {
        float textX = x + (w - this.font.width(text) * scale) / 2.0F;
        float textY = y + (h - this.font.lineHeight * scale) / 2.0F + 1;
        RenderFontTool.drawString(guiGraphics, this.font, text, textX, textY, 0, 0, scale, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        float frameWidth = this.width * 26F / 100F;
        float itemCenterX = this.width * 37F / 100F + frameWidth;
        float itemCenterY = this.height * 30F / 100F;
        float range = frameWidth * 0.7F;
        boolean isInRange = mouseX >= itemCenterX - range && mouseX <= itemCenterX + range
                && mouseY >= itemCenterY - range && mouseY <= itemCenterY + range;
        if (button == 0 && isInRange) {
            this.itemDrag.accumulate(dragX, dragY);
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        this.itemDrag.release();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int btnX = backButtonX();
        int btnY = backButtonY();
        int btnW = backButtonWidth();
        int btnH = this.height * 5 / 100;
        if (button == 0 && isInside(mouseX, mouseY, btnX, btnY, btnW, btnH)) {
            this.onClose();
            return true;
        }
        int infoSize = toolbarButtonSize();
        // Inspect button (index 0) is the default mode: our own 3D drag
        // preview. Clicking it while the TACZ viewport is active returns to
        // the default preview (exits the first-person inspect viewport).
        int inspectX = toolbarButtonX(0);
        int inspectY = toolbarButtonY() + toolbarEnterRise(0);
        if (button == 0 && isInside(mouseX, mouseY, inspectX, inspectY, infoSize, infoSize)) {
            if (this.taczViewportActive) {
                TaczInspectViewport.exit(this.openItem);
                this.taczViewportActive = false;
            }
            return true;
        }
        int glovesX = toolbarButtonX(1);
        int glovesY = toolbarButtonY() + toolbarEnterRise(1);
        if (button == 0 && isInside(mouseX, mouseY, glovesX, glovesY, infoSize, infoSize)) {
            // First-person inspect: close the GUI first, then let TACZ play its
            // native first-person inspect behind a transparent input-lock
            // screen; FirstPersonInspectHandler reopens this screen when the
            // animation finishes or the player presses Esc.
            if (this.minecraft != null && this.minecraft.player != null
                    && TaczInspectViewport.isAvailable(this.openItem)) {
                this.onClose();
                FirstPersonInspectHandler.start(this.minecraft.player, this.openItem, this.grade);
            }
            return true;
        }
        int infoX = toolbarButtonX(3);
        int infoY = toolbarButtonY() + toolbarEnterRise(3);
        if (button == 0 && isInside(mouseX, mouseY, infoX, infoY, infoSize, infoSize)) {
            this.showInfoPanel = !this.showInfoPanel;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static boolean isInside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    @Override
    public boolean keyPressed(int key, int b, int c) {
        if (key == 256) {
            this.onClose();
            return true;
        }
        return super.keyPressed(key, b, c);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.options.hideGui = false;
        }
        super.onClose();
    }

    @Override
    public void removed() {
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
        float glowTarget = this.hoveredButton >= 0 ? 1F : 0F;
        this.toolbarGlow += (glowTarget - this.toolbarGlow) * 0.5F;
        float panelTarget = this.showInfoPanel ? 1F : 0F;
        this.infoPanelAnim += (panelTarget - this.infoPanelAnim) * 0.35F;
        if (Math.abs(this.infoPanelAnim - panelTarget) < 0.005F) {
            this.infoPanelAnim = panelTarget;
        }
        if (this.minecraft == null || this.minecraft.player == null) return;
        if (!this.minecraft.player.isAlive() || this.minecraft.player.isRemoved()) {
            this.onClose();
        }
    }
}
