package com.reclizer.csgobox.v1_21_11.gui;

import com.reclizer.csgobox.v1_21_11.CsgoBox;
import com.reclizer.csgobox.v1_21_11.sounds.ModSounds;
import com.reclizer.csgobox.v1_21_11.utils.ButtonPalette;
import com.reclizer.csgobox.utils.ColorTools;
import com.reclizer.csgobox.v1_21_11.utils.GuiItemMove;
import com.reclizer.csgobox.utils.OverlayColor;
import com.reclizer.csgobox.v1_21_11.utils.RenderFontTool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
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
    private final float wearValue;
    private final int patternSeed;
    private final int skinId;
    private final int skinStyleIndex;
    private final boolean statTrak;
    private final int statTrakKills;

    private static final Identifier ICON_INSPECT =
            Identifier.parse("csgobox:textures/gui/toolbar/inspect.png");
    private static final Identifier ICON_GLOVES =
            Identifier.parse("csgobox:textures/gui/toolbar/gloves.png");
    private static final Identifier ICON_MODEL =
            Identifier.parse("csgobox:textures/gui/toolbar/model.png");
    private static final Identifier ICON_INFO =
            Identifier.parse("csgobox:textures/gui/toolbar/info.png");
    private static final Identifier ICON_STICKER =
            Identifier.parse("csgobox:textures/gui/toolbar/sticker.png");
    private static final Identifier ICON_MORE =
            Identifier.parse("csgobox:textures/gui/toolbar/more.png");

    /** Decorative finish styles, mirrored in lang (style.custom_paint etc). */
    private static final String[] SKIN_STYLES = {
            "custom_paint", "gunsmith", "patina", "hydrographic", "spray_paint", "anodized"
    };

    /** Displays the server-authoritative reward after the progress animation completes. */
    public CsLookItemScreen(ItemStack item, int grade) {
        super(Minecraft.getInstance(), Minecraft.getInstance().font, Component.literal("look_item"));
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

    // Preview geometry shared by renderBg (positions the PIP render state)
    // and mouseDragged (keeps the drag-detection rectangle in lock-step with
    // what the user actually sees). Mirrors CsboxScreen's helper trio so the
    // result screen and the box-opening screen render the same held item at
    // the same on-screen pixel size.
    private int previewTextureSize() {
        int containerTop = this.height * 22 / 100;
        int containerBottom = this.height * 88 / 100;
        int containerHeight = containerBottom - containerTop;
        return Math.max(144, Math.min(this.width * 28 / 100, containerHeight * 72 / 100));
    }

    private int previewPixelX() {
        return (this.width - previewTextureSize()) / 2;
    }

    private int previewPixelY() {
        // Container = vertical band between the grade bar and the divider
        // above the button. Centre the crate within it so the result focus
        // sits in the visual middle of the screen.
        int containerTop = this.height * 22 / 100;
        int containerBottom = this.height * 88 / 100;
        return (containerTop + containerBottom - previewTextureSize()) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        renderLookBackground(guiGraphics);
        // renderBg before renderLabels: button rectangle must be drawn first,
        // then the centered button text on top. Reversed order (the previous
        // behaviour) caused the text to be hidden behind the rectangle.
        // Title (y=5%) and grade label (y=11%) sit in their own band, so
        // swapping the two render calls has no other z-order side-effects.
        renderBg(guiGraphics, mouseX, mouseY);
        renderLabels(guiGraphics, mouseX, mouseY);
    }

    private void renderLookBackground(GuiGraphics guiGraphics) {
        if (this.minecraft != null && this.minecraft.level != null) {
            guiGraphics.fillGradient(0, 0, this.width, this.height,
                    OverlayColor.getBackgroundColor(), OverlayColor.getBackgroundColor());
        }
    }

    private void renderBg(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (this.minecraft != null) {
            this.minecraft.options.hideGui = true;
        }
        if (openItem.isEmpty()) return;

        float scale = previewTextureSize() / 16F;
        guiGraphics.fill(this.width * 25 / 100, this.height * 92 / 100,
                this.width * 75 / 100, this.height * 92 / 100 + 1, 0xFFD3D3D3);
        guiGraphics.fill(this.width * 33 / 100, this.height * 17 / 100,
                this.width * 67 / 100, this.height * 17 / 100 + 4, ColorTools.colorItems(grade));
        // Centre the result preview in the available band; mouseDragged below
        // uses the same previewPixelX/Y/size so drag-detection matches what
        // the user sees.
        GuiItemMove.renderItemInInventoryFollowsMouse(guiGraphics,
                previewPixelX(), previewPixelY(),
                this.rotX, this.rotY, openItem, this.player, scale);

        int btnX = backButtonX();
        int btnY = this.height * 94 / 100;
        int btnW = backButtonWidth();
        int btnH = this.height * 5 / 100;
        boolean hoverButton = ButtonPalette.isInside(mouseX, mouseY, btnX, btnY, btnW, btnH);
        ButtonPalette.drawButton(guiGraphics, ButtonPalette.DANGER, btnX, btnY, btnW, btnH, hoverButton);

        renderToolbar(guiGraphics, mouseX, mouseY);
        renderInfoPanel(guiGraphics);
    }

    private void renderToolbar(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int size = toolbarButtonSize();
        int y = toolbarButtonY();
        Identifier[] icons = {ICON_INSPECT, ICON_GLOVES, ICON_MODEL, ICON_INFO, ICON_STICKER, ICON_MORE};
        for (int i = 0; i < icons.length; i++) {
            int x = toolbarButtonX(i);
            boolean hover = ButtonPalette.isInside(mouseX, mouseY, x, y, size, size);
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
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, icons[i],
                    iconX, iconY, 0F, 0F, iconSize, iconSize, 16, 16);
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
                Component.translatable("gui.csgobox.csgo_box.grade" + grade).getVisualOrderText(),
                gradeX, this.height * 11.5F / 100F, 1F);
        // Button text colour tracks the panel painted in renderBg: hovered
        // button -> textColorHover, otherwise -> textColor. The previous
        // implementation used a constant white that clashed with the warm
        // danger fill.
        int bx = backButtonX();
        int by = this.height * 94 / 100;
        int bw = backButtonWidth();
        int bh = this.height * 5 / 100;
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
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        double mouseX = event.x();
        double mouseY = event.y();
        // Match the actual rendered crate rectangle (centred horizontally
        // and vertically inside the 16%–92% container). The previous code
        // used width*37% + frameWidth as the centre of an upper-left
        // rectangle, which no longer matched the centred draw site.
        int size = previewTextureSize();
        int x = previewPixelX();
        int y = previewPixelY();
        boolean isInRange = mouseX >= x && mouseX <= x + size
                && mouseY >= y && mouseY <= y + size;
        if (event.button() == 0 && isInRange) {
            this.rotX = GuiItemMove.renderRotAngleX(dragX, this.rotX);
            this.rotY = GuiItemMove.renderRotAngleY(dragY, this.rotY);
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int btnX = backButtonX();
        int btnY = this.height * 94 / 100;
        int btnW = backButtonWidth();
        int btnH = this.height * 5 / 100;
        if (event.button() == 0 && ButtonPalette.isInside(event.x(), event.y(), btnX, btnY, btnW, btnH)) {
            this.onClose();
            return true;
        }
        int infoSize = toolbarButtonSize();
        int infoX = toolbarButtonX(3);
        int infoY = toolbarButtonY();
        if (event.button() == 0 && ButtonPalette.isInside(event.x(), event.y(), infoX, infoY, infoSize, infoSize)) {
            this.showInfoPanel = !this.showInfoPanel;
            return true;
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
