package com.reclizer.csgobox.v1_21_1.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.reclizer.csgobox.v1_21_1.CsgoBox;
import com.reclizer.csgobox.v1_21_1.compat.TaczInspectViewport;
import com.reclizer.csgobox.v1_21_1.sounds.ModSounds;
import com.reclizer.csgobox.utils.ColorTools;
import com.reclizer.csgobox.v1_21_1.utils.GuiItemMove;
import com.reclizer.csgobox.utils.OverlayColor;
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
    private float rotX = 0;
    private float rotY = 0;

    /** Wear panel visibility, toggled by the info (ⓘ) toolbar button. */
    private boolean showInfoPanel = false;
    /** TACZ inspect viewport visibility, toggled by the gloves toolbar button. */
    private boolean taczViewportActive = false;
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

    /** Per-icon content edge (px in the 32x32 texture): inspect 30x29, gloves 30x20, model 28x30,
     *  info 26x26, sticker 30x23, more 7x27 (vertical, edge = height). Keeps visible content
     *  visually uniform: iconSize = size * 12 / edge, so content edge renders at ~size*3/8. */
    private static final int[] ICON_CONTENT_EDGE = {29, 20, 28, 26, 23, 27};

    /** Tooltip lang keys per toolbar button, aligned with ICON_CONTENT_EDGE. */
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
        if (this.player != null && !this.openItem.isEmpty()) {
            float vol = CsgoBox.CONFIG.finishSoundVolume() / 100F;
            if (vol > 0) {
                player.playSound(ModSounds.CS_FINSH.get(), vol * 10F, 1F);
            }
        }
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
        return Math.max(24, this.height * 5 / 100);
    }

    private int toolbarButtonX(int index) {
        return Math.max(8, 8 + index * (toolbarButtonSize() + 6));
    }

    private int toolbarButtonY() {
        return this.height - 8 - toolbarButtonSize();
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
        renderLookBackground(guiGraphics);
        renderLabels(guiGraphics);
        renderBg(guiGraphics, mouseX, mouseY, partialTicks);
    }

    private void renderLookBackground(GuiGraphics guiGraphics) {
        if (this.minecraft != null && this.minecraft.level != null) {
            guiGraphics.fillGradient(0, 0, this.width, this.height,
                    OverlayColor.getBackgroundColor(), OverlayColor.getBackgroundColor());
        }
    }

    private void renderBg(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (this.minecraft != null) {
            this.minecraft.options.hideGui = true;
        }
        if (openItem.isEmpty()) return;

        int frameWidth = width * 26 / 100;
        float scale = frameWidth / 16F;
        int dividerY = this.height - 18 - toolbarButtonSize();
        guiGraphics.fill(this.width * 25 / 100, dividerY,
                this.width * 75 / 100, dividerY + 1, 0xFFD3D3D3);
        guiGraphics.fill(this.width * 37 / 100, this.height * 16 / 100,
                this.width * 63 / 100, this.height * 16 / 100 + 4, ColorTools.colorItems(grade));
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
            GuiItemMove.renderItemInInventoryFollowsMouse(guiGraphics, this.width * 37 / 100, this.height * 30 / 100,
                    this.rotX, this.rotY, openItem, this.player, scale);
        }

        int btnX = backButtonX();
        int btnY = backButtonY();
        int btnW = backButtonWidth();
        int btnH = this.height * 5 / 100;
        boolean hoverButton = isInside(mouseX, mouseY, btnX, btnY, btnW, btnH);
        int outerColor = hoverButton ? 0xFFFF4444 : 0xFFFF0000;
        int innerColor = hoverButton ? 0xFFCC4444 : 0xFFAA0000;
        guiGraphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, outerColor);
        guiGraphics.fill(btnX + 1, btnY + 1, btnX + btnW - 1, btnY + btnH - 1, innerColor);

        renderToolbar(guiGraphics, mouseX, mouseY);
        renderInfoPanel(guiGraphics);
        RenderSystem.disableBlend();
    }

    private void renderToolbar(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int size = toolbarButtonSize();
        int y = toolbarButtonY();
        ResourceLocation[] icons = {ICON_INSPECT, ICON_GLOVES, ICON_MODEL, ICON_INFO, ICON_STICKER, ICON_MORE};
        this.hoveredButton = -1;
        for (int i = 0; i < icons.length; i++) {
            int x = toolbarButtonX(i);
            boolean hover = isInside(mouseX, mouseY, x, y, size, size);
            if (hover) {
                this.hoveredButton = i;
            }
            boolean active = (i == 3 && this.showInfoPanel) || (i == 1 && this.taczViewportActive);
            int outer = 0xFF2B2B31;
            int inner = active ? 0xFF33333B : 0xFF232328;
            guiGraphics.fill(x, y, x + size, y + size, outer);
            guiGraphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, inner);
            if (active) {
                guiGraphics.fill(x + 2, y + size - 2, x + size - 2, y + size, 0xFFFFFFFF);
            }
            int iconSize = Math.max(12, Math.min(64, size * 12 / ICON_CONTENT_EDGE[i]));
            int iconX = x + (size - iconSize) / 2;
            int iconY = y + (size - iconSize) / 2;
            int iconColor;
            if (active) {
                iconColor = 0xFFF2C980;
            } else {
                float b = i == this.hoveredButton ? 0.55F + 0.45F * this.toolbarGlow : 0.55F;
                iconColor = 0xFF000000 | ((int) (0xF2 * b) << 16) | ((int) (0xC9 * b) << 8) | (int) (0x80 * b);
            }
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(((iconColor >> 16) & 0xFF) / 255F,
                    ((iconColor >> 8) & 0xFF) / 255F, (iconColor & 0xFF) / 255F, 1F);
            guiGraphics.blit(icons[i], iconX, iconY, 0, 0, iconSize, iconSize, 32, 32);
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        }
        renderToolbarTooltip(guiGraphics, mouseX, mouseY);
    }

    private void renderToolbarTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (this.hoveredButton < 0 || this.toolbarGlow < 0.05F) return;
        int size = toolbarButtonSize();
        int x = toolbarButtonX(this.hoveredButton);
        int y = toolbarButtonY();
        int textW = this.font.width(Component.translatable(TOOLTIP_KEYS[this.hoveredButton]).getVisualOrderText());
        int tooltipW = (int) (textW * 0.7F) + 8;
        int tooltipH = (int) (this.font.lineHeight * 0.7F) + 4;
        int tipX = x + (size - tooltipW) / 2;
        int tipY = y - tooltipH - 6;
        tipX = Math.max(2, Math.min(tipX, this.width - tooltipW - 2));
        tipY = Math.max(2, tipY);
        int alpha = (int) (0xCC * this.toolbarGlow);
        guiGraphics.fill(tipX, tipY, tipX + tooltipW, tipY + tooltipH, (alpha << 24) | 0x101014);
        int textAlpha = (int) (0xFF * this.toolbarGlow);
        renderText(guiGraphics,
                Component.translatable(TOOLTIP_KEYS[this.hoveredButton]).getVisualOrderText(),
                tipX + 4, tipY + 2, 0.7F, (textAlpha << 24) | 0xCCCCCC);
    }

    private void renderInfoPanel(GuiGraphics guiGraphics) {
        if (!this.showInfoPanel || openItem.isEmpty()) return;
        int panelX = this.width * 8 / 100;
        int panelY = this.height * 20 / 100;
        int panelW = Math.max(200, this.width * 16 / 100);
        int rowH = 13;
        int lineCount = 5;
        int panelH = 12 + lineCount * rowH;
        int panelRight = panelX + panelW;
        int panelBottom = panelY + panelH;
        guiGraphics.fill(panelX, panelY, panelRight, panelBottom, 0xE0101014);

        int textX = panelX + 8;
        int y = panelY + 8;
        float scale = 0.7F;
        int rowIndex = 0;
        drawInfoRow(guiGraphics, textX, y + rowIndex++ * rowH, scale,
                "gui.csgobox.csgo_box.info.skin_style",
                Component.translatable("gui.csgobox.csgo_box.style." + SKIN_STYLES[this.skinStyleIndex]));
        drawInfoRow(guiGraphics, textX, y + rowIndex++ * rowH, scale,
                "gui.csgobox.csgo_box.info.skin_id",
                Component.literal(String.valueOf(this.skinId)));
        drawInfoRow(guiGraphics, textX, y + rowIndex++ * rowH, scale,
                "gui.csgobox.csgo_box.info.pattern",
                Component.literal(String.valueOf(this.patternSeed)));
        drawInfoRow(guiGraphics, textX, y + rowIndex++ * rowH, scale,
                "gui.csgobox.csgo_box.info.wear_rating",
                Component.literal(formatWear()));
        drawInfoRow(guiGraphics, textX, y + rowIndex * rowH, scale,
                "gui.csgobox.csgo_box.info.exterior",
                Component.translatable(wearTierKey()));
    }

    private void drawInfoRow(GuiGraphics guiGraphics, int x, int y, float scale,
                             String labelKey, Component value) {
        renderText(guiGraphics, Component.translatable(labelKey, value).getVisualOrderText(),
                x, y, scale, 0xFFCCCCCC);
    }

    private void renderLabels(GuiGraphics guiGraphics) {
        if (openItem.isEmpty()) return;

        Style style = Style.EMPTY.withBold(true);
        renderText(guiGraphics, openItem.getItem().getName(openItem).getVisualOrderText(),
                this.width * 45F / 100F, this.height * 5F / 100F, 1.8F);
        renderText(guiGraphics,
                Component.translatable("gui.csgobox.csgo_box.grade" + grade).getVisualOrderText(),
                this.width * 45F / 100F, this.height * 11F / 100F, 1F);
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
            this.rotX = GuiItemMove.renderRotAngleX(dragX, this.rotX);
            this.rotY = GuiItemMove.renderRotAngleY(dragY, this.rotY);
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
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
        int glovesX = toolbarButtonX(1);
        int glovesY = toolbarButtonY();
        if (button == 0 && isInside(mouseX, mouseY, glovesX, glovesY, infoSize, infoSize)) {
            if (this.taczViewportActive) {
                TaczInspectViewport.exit(this.openItem);
                this.taczViewportActive = false;
            } else if (TaczInspectViewport.isAvailable(this.openItem)
                    && this.minecraft != null && this.minecraft.player != null
                    && TaczInspectViewport.enter(this.openItem, this.minecraft.player)) {
                this.taczViewportActive = true;
            }
            return true;
        }
        int infoX = toolbarButtonX(3);
        int infoY = toolbarButtonY();
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
        float glowTarget = this.hoveredButton >= 0 ? 1F : 0F;
        this.toolbarGlow += (glowTarget - this.toolbarGlow) * 0.5F;
        if (this.minecraft == null || this.minecraft.player == null) return;
        if (!this.minecraft.player.isAlive() || this.minecraft.player.isRemoved()) {
            this.onClose();
        }
    }
}
