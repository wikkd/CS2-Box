package com.reclizer.csgobox.v1_21_5.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.reclizer.csgobox.v1_21_5.CsgoBox;
import com.reclizer.csgobox.v1_21_5.sounds.ModSounds;
import com.reclizer.csgobox.utils.ColorTools;
import com.reclizer.csgobox.v1_21_5.utils.GuiItemMove;
import com.reclizer.csgobox.utils.OverlayColor;
import com.reclizer.csgobox.v1_21_5.utils.RenderFontTool;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.resources.ResourceLocation;
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
    private final float wearValue;
    private final int patternSeed;
    private final int skinId;
    private final int skinStyleIndex;
    private final boolean statTrak;
    private final int statTrakKills;

    private static final net.minecraft.resources.ResourceLocation ICON_INSPECT =
            ResourceLocation.parse("csgobox:textures/gui/toolbar/inspect.png");
    private static final net.minecraft.resources.ResourceLocation ICON_GLOVES =
            ResourceLocation.parse("csgobox:textures/gui/toolbar/gloves.png");
    private static final net.minecraft.resources.ResourceLocation ICON_MODEL =
            ResourceLocation.parse("csgobox:textures/gui/toolbar/model.png");
    private static final net.minecraft.resources.ResourceLocation ICON_INFO =
            ResourceLocation.parse("csgobox:textures/gui/toolbar/info.png");
    private static final net.minecraft.resources.ResourceLocation ICON_STICKER =
            ResourceLocation.parse("csgobox:textures/gui/toolbar/sticker.png");
    private static final net.minecraft.resources.ResourceLocation ICON_MORE =
            ResourceLocation.parse("csgobox:textures/gui/toolbar/more.png");

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
        this.statTrak = rnd.nextFloat() < 0.12F;
        this.statTrakKills = rnd.nextInt(1, 500);
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

    private int toolbarButtonSize() {
        return Math.max(24, this.height * 5 / 100);
    }

    private int toolbarButtonX(int index) {
        return Math.max(8, 8 + index * (toolbarButtonSize() + 6));
    }

    private int toolbarButtonY() {
        return this.height * 93 / 100;
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
        renderBg(guiGraphics, mouseX, mouseY);
    }

    private void renderLookBackground(GuiGraphics guiGraphics) {
        if (this.minecraft != null && this.minecraft.level != null) {
            guiGraphics.fillGradient(0, 0, this.width, this.height,
                    OverlayColor.getBackgroundColor(), OverlayColor.getBackgroundColor());
        }
    }

    private void renderBg(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1, 1, 1, 1);
        if (this.minecraft != null) {
            this.minecraft.options.hideGui = true;
        }
        if (openItem.isEmpty()) return;

        int frameWidth = width * 26 / 100;
        float scale = frameWidth / 16F;
        guiGraphics.fill(this.width * 25 / 100, this.height * 92 / 100,
                this.width * 75 / 100, this.height * 92 / 100 + 1, 0xFFD3D3D3);
        guiGraphics.fill(this.width * 37 / 100, this.height * 16 / 100,
                this.width * 63 / 100, this.height * 16 / 100 + 4, ColorTools.colorItems(grade));
        GuiItemMove.renderItemInInventoryFollowsMouse(guiGraphics, this.width * 37 / 100, this.height * 30 / 100,
                this.rotX, this.rotY, openItem, this.player, scale);

        int btnX = backButtonX();
        int btnY = this.height * 94 / 100;
        int btnW = backButtonWidth();
        int btnH = this.height * 5 / 100;
        boolean hoverButton = isInside(mouseX, mouseY, btnX, btnY, btnW, btnH);
        int outerColor = hoverButton ? 0xFFFF4444 : 0xFFFF0000;
        int innerColor = hoverButton ? 0xFFCC4444 : 0xFFAA0000;
        guiGraphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, outerColor);
        guiGraphics.fill(btnX + 1, btnY + 1, btnX + btnW - 1, btnY + btnH - 1, innerColor);

        renderToolbar(guiGraphics, mouseX, mouseY);
        renderInfoPanel(guiGraphics);
    }

    private void renderToolbar(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int size = toolbarButtonSize();
        int y = toolbarButtonY();
        net.minecraft.resources.ResourceLocation[] icons = {ICON_INSPECT, ICON_GLOVES, ICON_MODEL, ICON_INFO, ICON_STICKER, ICON_MORE};
        for (int i = 0; i < icons.length; i++) {
            int x = toolbarButtonX(i);
            boolean hover = isInside(mouseX, mouseY, x, y, size, size);
            boolean active = i == 3 && this.showInfoPanel;
            int outer = hover ? 0xFF3A3A42 : 0xFF2B2B31;
            int inner = active ? 0xFF33333B : (hover ? 0xFF2F2F36 : 0xFF232328);
            guiGraphics.fill(x, y, x + size, y + size, outer);
            guiGraphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, inner);
            if (active) {
                guiGraphics.fill(x + 2, y + size - 2, x + size - 2, y + size, 0xFFFFFFFF);
            }
            int iconSize = Math.max(12, size * 2 / 3);
            int iconX = x + (size - iconSize) / 2;
            int iconY = y + (size - iconSize) / 2;
            guiGraphics.blit(RenderType.GUI_TEXTURED, icons[i],
                    iconX, iconY, 0F, 0F, iconSize, iconSize, 16, 16, 0xFFFFFFFF);
        }
    }

    private void renderInfoPanel(GuiGraphics guiGraphics) {
        if (!this.showInfoPanel || openItem.isEmpty()) return;
        int panelX = this.width * 8 / 100;
        int panelY = this.height * 20 / 100;
        int panelW = Math.max(200, this.width * 16 / 100);
        int rowH = 13;
        int lineCount = this.statTrak ? 6 : 5;
        int panelH = 12 + lineCount * rowH;
        int panelRight = panelX + panelW;
        int panelBottom = panelY + panelH;
        guiGraphics.fill(panelX, panelY, panelRight, panelBottom, 0xE0101014);
        guiGraphics.fill(panelX, panelY, panelRight, panelY + 1, 0x40FFFFFF);
        guiGraphics.fill(panelX, panelBottom - 1, panelRight, panelBottom, 0x40FFFFFF);
        guiGraphics.fill(panelX, panelY, panelX + 1, panelBottom, 0x40FFFFFF);
        guiGraphics.fill(panelRight - 1, panelY, panelRight, panelBottom, 0x40FFFFFF);

        int textX = panelX + 8;
        int y = panelY + 8;
        float scale = 0.7F;
        int rowIndex = 0;
        drawInfoRow(guiGraphics, textX, panelRight - 8, y + rowIndex++ * rowH, scale,
                "gui.csgobox.csgo_box.info.skin_style",
                Component.translatable("gui.csgobox.csgo_box.style." + SKIN_STYLES[this.skinStyleIndex]));
        drawInfoRow(guiGraphics, textX, panelRight - 8, y + rowIndex++ * rowH, scale,
                "gui.csgobox.csgo_box.info.skin_id",
                Component.literal(String.valueOf(this.skinId)));
        drawInfoRow(guiGraphics, textX, panelRight - 8, y + rowIndex++ * rowH, scale,
                "gui.csgobox.csgo_box.info.pattern",
                Component.literal(String.valueOf(this.patternSeed)));
        drawInfoRow(guiGraphics, textX, panelRight - 8, y + rowIndex++ * rowH, scale,
                "gui.csgobox.csgo_box.info.wear_rating",
                Component.literal(formatWear()));
        drawInfoRow(guiGraphics, textX, panelRight - 8, y + rowIndex++ * rowH, scale,
                "gui.csgobox.csgo_box.info.exterior",
                Component.translatable(wearTierKey()));
        if (this.statTrak) {
            drawInfoRow(guiGraphics, textX, panelRight - 8, y + rowIndex * rowH, scale,
                    "gui.csgobox.csgo_box.info.stattrak",
                    Component.literal(String.valueOf(this.statTrakKills)), 0xFFFF6A00);
        }
    }

    private void drawInfoRow(GuiGraphics guiGraphics, int labelX, int valueRight, int y, float scale,
                             String labelKey, Component value) {
        drawInfoRow(guiGraphics, labelX, valueRight, y, scale, labelKey, value, 0xFFFFFFFF);
    }

    private void drawInfoRow(GuiGraphics guiGraphics, int labelX, int valueRight, int y, float scale,
                             String labelKey, Component value, int valueColor) {
        renderText(guiGraphics, Component.translatable(labelKey).getVisualOrderText(), labelX, y, scale,
                0xFF9A9A9A);
        float valueWidth = this.font.width(value.getVisualOrderText()) * scale;
        RenderFontTool.drawString(guiGraphics, this.font, value.getVisualOrderText(),
                valueRight - valueWidth, y, 0, 0, scale, valueColor);
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
                backButtonX(), this.height * 94 / 100, backButtonWidth(), this.height * 5 / 100, 0.8F);
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
        int btnY = this.height * 94 / 100;
        int btnW = backButtonWidth();
        int btnH = this.height * 5 / 100;
        if (button == 0 && isInside(mouseX, mouseY, btnX, btnY, btnW, btnH)) {
            this.onClose();
            return true;
        }
        int infoSize = toolbarButtonSize();
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
    public final void tick() {
        super.tick();
        if (this.minecraft == null || this.minecraft.player == null) return;
        if (!this.minecraft.player.isAlive() || this.minecraft.player.isRemoved()) {
            this.onClose();
        }
    }
}
