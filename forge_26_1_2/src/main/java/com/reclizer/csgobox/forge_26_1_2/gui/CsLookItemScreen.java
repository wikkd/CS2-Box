package com.reclizer.csgobox.forge_26_1_2.gui;

import com.reclizer.csgobox.forge_26_1_2.sounds.ModSounds;
import com.reclizer.csgobox.forge_26_1_2.utils.ButtonPalette;
import com.reclizer.csgobox.utils.ColorTools;
import com.reclizer.csgobox.utils.Easing;
import com.reclizer.csgobox.utils.ItemDrag3D;
import com.reclizer.csgobox.forge_26_1_2.utils.AnimRenderOps;
import com.reclizer.csgobox.forge_26_1_2.utils.GuiItemMove;
import com.reclizer.csgobox.forge_26_1_2.utils.RenderFontTool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

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
    private final float wearValue;
    private final int patternSeed;
    private final int skinId;
    private final int skinStyleIndex;

    private static final Identifier ICON_INSPECT =
            Identifier.parse("csgobox:textures/gui/toolbar/inspect.png");
    private static final Identifier ICON_INFO =
            Identifier.parse("csgobox:textures/gui/toolbar/info.png");
    private static final Identifier ICON_STICKER =
            Identifier.parse("csgobox:textures/gui/toolbar/sticker.png");
    private static final Identifier ICON_MORE =
            Identifier.parse("csgobox:textures/gui/toolbar/more.png");


    /** Content bounding box (px in the 32x32 texture) per icon, kept in
     *  sync with the artwork: inspect 30x29, info 26x26, sticker 30x23,
     *  more 7x27. */
    private static final int[] ICON_CONTENT_W = {30, 26, 30, 7};
    private static final int[] ICON_CONTENT_H = {29, 26, 23, 27};

    /** Tooltip lang keys per toolbar button (shipment toolbar, with wear). */
    private static final String[] TOOLTIP_KEYS = {
            "gui.csgobox.csgo_box.toolbar.inspect",
            "gui.csgobox.csgo_box.toolbar.info",
            "gui.csgobox.csgo_box.toolbar.sticker",
            "gui.csgobox.csgo_box.toolbar.more"
    };
    /** Tooltip keys for the compact inspect toolbar (no wear button: the
     *  right-click inspect flow confirms nothing about an item's wear). */
    private static final String[] TOOLTIP_KEYS_INSPECT = {
            "gui.csgobox.csgo_box.toolbar.inspect",
            "gui.csgobox.csgo_box.toolbar.sticker",
            "gui.csgobox.csgo_box.toolbar.more"
    };

    /** Toolbar hover state: index of hovered button (-1 = none) and 0..1 glow. */
    private int hoveredButton = -1;
    private float toolbarGlow = 0F;
    private int screenTicks = 0;

    /** Decorative finish styles, mirrored in lang (style.custom_paint etc). */
    private static final String[] SKIN_STYLES = {
            "custom_paint", "gunsmith", "patina", "hydrographic", "spray_paint", "anodized"
    };

    /** Random scenery (picture toolbar button): pitch band around the horizon
     *  in degrees; yaw is a full random circle. */
    private static final float SCENERY_PITCH_RANGE_DEG = 30.0F;
    /** Flight duration in client ticks: a slow cinematic glide (instant-replay
     *  style camera move). */
    private static final int SCENERY_GLIDE_TICKS = 60;
    /** Background panorama spin: continuous slow yaw drift while the screen
     *  is open, in degrees per client tick — the vanilla main-menu pace
     *  (PanoramaRenderer adds deltaTicks * panoramaSpeed * 0.1, i.e. 2 deg/s
     *  at the default 100% panorama speed). */
    private static final float SCENERY_SPIN_DEG_PER_TICK = 0.1F;
    /** Mount parking spot: this far in front of the player's face (along the
     *  view direction), so the camera starts outside the head model. */
    private static final double SCENERY_MOUNT_FORWARD = 0.5;
    /** Scenery flight: distance range from the player (blocks) and the
     *  clearance above the surface heightmap the camera parks at. */
    private static final double SCENERY_DIST_MIN = 8.0;
    private static final double SCENERY_DIST_RANGE = 12.0;
    private static final double SCENERY_SURFACE_CLEARANCE = 2.0;
    /** Client-side rig entity id seed (negative: never collides with server
     *  entity ids, which count up from zero). */
    private static int sceneryRigIdCounter;

    /** Scenery camera rig: a client-side-only marker entity the render camera
     *  rides via {@link Minecraft#setCameraEntity} (the vanilla spectator
     *  camera mechanism, no mixins). Markers never render and have no AI, and
     *  the player's own view is never touched. */
    private Entity sceneryRig;
    private Entity sceneryOriginalCamera;
    /** Flight animation state: pose to fly from, target pose, and tick
     *  progress within SCENERY_GLIDE_TICKS (-1 = not flying). */
    private double sceneryFromX;
    private double sceneryFromY;
    private double sceneryFromZ;
    private float sceneryFromYaw;
    private float sceneryFromPitch;
    private double sceneryToX;
    private double sceneryToY;
    private double sceneryToZ;
    private float sceneryToYaw;
    private float sceneryToPitch;
    private int sceneryAnimTick = -1;
    /** Guards the screen-open camera mount so window resizes don't re-mount. */
    private boolean sceneryAutoStarted;
    /** True while we hid the local player model for the panorama mount
     *  (skipped when already invisible, e.g. an invisibility potion). */
    private boolean sceneryPlayerHidden;
    /** Inspect-button animation mode: cached living-entity previews (mob
     *  drops) walk in place while on; toggled by the inspect toolbar button
     *  (index 0, the single inspect entry), armed only when the current item
     *  renders as a living entity. */
    private boolean mobAnimMode = false;



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
        super(Minecraft.getInstance(), Minecraft.getInstance().font, Component.literal("look_item"));
        this.player = Minecraft.getInstance().player;
        this.openItem = item == null ? ItemStack.EMPTY : item.copy();
        this.grade = grade;
        this.previousScreen = previousScreen;
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
        // Inspect entry is silent: the unboxing chime belongs to the reward flow.
        if (previousScreen == null && this.player != null && !this.openItem.isEmpty()) {
            float vol = 1.0F;
            if (vol > 0) {
                player.playSound(ModSounds.CS_FINSH.get(), vol * 10F, 1F);
            }
        }
    }

    /** True when this screen presents a confirmed shipment (unboxing result
     *  or terminal post-buy showcase): only then is wear viewable — the wear
     *  panel's skin/pattern/wear card is the drop's own generated metadata.
     *  The right-click inspect flow ({@code previousScreen} set) confirms
     *  nothing about an item's wear, so the wear button is hidden there. */
    private boolean showsShipment() {
        return this.previousScreen == null;
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
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);
        this.itemDrag.tick();
        renderLookBackground(guiGraphics);
        // renderBg before renderLabels: button rectangle must be drawn first,
        // then the centered button text on top. Reversed order (the previous
        // behaviour) caused the text to be hidden behind the rectangle.
        // Title (y=5%) and grade label (y=11%) sit in their own band, so
        // swapping the two render calls has no other z-order side-effects.
        renderBg(guiGraphics, mouseX, mouseY);
        renderLabels(guiGraphics, mouseX, mouseY);
    }

    private void renderLookBackground(GuiGraphicsExtractor guiGraphics) {
        if (this.minecraft != null && this.minecraft.level != null) {
            int fill = UiBackdrop.fill();
            AnimRenderOps.fillGradient(guiGraphics, 0, 0, this.width, this.height, fill, fill);
        }
    }

    private void renderBg(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
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

        float scale = (previewTextureSize() / 16F) * (0.9F + 0.1F * enterE());
        int dividerY = this.height - 18 - toolbarButtonSize();
        AnimRenderOps.fill(guiGraphics, this.width * 25 / 100, dividerY,
                this.width * 75 / 100, dividerY + 1, 0xFFD3D3D3);
        AnimRenderOps.fill(guiGraphics, this.width * 33 / 100, this.height * 17 / 100,
                this.width * 67 / 100, this.height * 17 / 100 + 4, ColorTools.colorItems(grade));
        // Centre the result preview in the available band; mouseDragged below
        // uses the same previewPixelX/Y/size so drag-detection matches what
        // the user sees.
        GuiItemMove.renderItemInInventoryFollowsMouse(guiGraphics,
                previewPixelX(), previewPixelY(),
                this.itemDrag.rotation(), openItem, this.player, scale);

        int btnX = backButtonX();
        int btnY = backButtonY();
        int btnW = backButtonWidth();
        int btnH = this.height * 5 / 100;
        boolean hoverButton = ButtonPalette.isInside(mouseX, mouseY, btnX, btnY, btnW, btnH);
        ButtonPalette.drawButton(guiGraphics, ButtonPalette.DANGER, btnX, btnY, btnW, btnH, hoverButton);

        renderInfoPanel(guiGraphics);
        renderToolbar(guiGraphics, mouseX, mouseY);
    }

    private void renderToolbar(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
        int size = toolbarButtonSize();
        int y = toolbarButtonY();
        // The wear (ⓘ) button ships only in shipment flows; the inspect
        // toolbar is compact [inspect, sticker, more] with no dead slot.
        boolean shipment = this.showsShipment();
        Identifier[] icons = shipment
                ? new Identifier[]{ICON_INSPECT, ICON_INFO, ICON_STICKER, ICON_MORE}
                : new Identifier[]{ICON_INSPECT, ICON_STICKER, ICON_MORE};
        String[] tooltips = shipment ? TOOLTIP_KEYS : TOOLTIP_KEYS_INSPECT;
        this.hoveredButton = -1;
        for (int i = 0; i < icons.length; i++) {
            int x = toolbarButtonX(i);
            int by = y + toolbarEnterRise(i);
            int alpha = (int) (0xFF * toolbarEnterEase(i));

            boolean hover = ButtonPalette.isInside(mouseX, mouseY, x, by, size, size);
            if (hover) {
                this.hoveredButton = i;
            }
            // Inspect (index 0) is the single inspect entry: always on as the
            // default mode (own 3D drag preview); its underline turns green
            // while the mob-animation inspect mode is active.
            boolean specialInspect = this.mobAnimMode;
            boolean active = i == 0 || (i == 1 && this.showInfoPanel);
            int outer = 0xFF2B2B31;
            int inner = active ? 0xFF33333B : 0xFF232328;
            guiGraphics.fill(x, by, x + size, by + size, (alpha << 24) | (outer & 0xFFFFFF));
            guiGraphics.fill(x + 1, by + 1, x + size - 1, by + size - 1, (alpha << 24) | (inner & 0xFFFFFF));
            if (active) {
                guiGraphics.fill(x + 2, by + size - 2, x + size - 2, by + size,
                        (alpha << 24) | (i == 0 && specialInspect ? 0x54FF54 : 0xFFFFFF));
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
            if (i == icons.length - 1) {
                renderMoreDots(guiGraphics, x, by, size, iconColor);
            } else {
                guiGraphics.blit(RenderPipelines.GUI_TEXTURED, icons[i],
                    iconX, iconY, u, v, screenW, screenH,
                    ICON_CONTENT_W[i], ICON_CONTENT_H[i], 32, 32, iconColor);
            }        }
        renderToolbarTooltip(guiGraphics, mouseX, mouseY, tooltips);
    }

    private void renderMoreDots(GuiGraphicsExtractor guiGraphics, int btnX, int btnY, int size, int color) {
        int r = Math.max(2, size / 5);
        int cx = btnX + size / 2;
        int cy = btnY + size / 2;
        int spacing = Math.max(3, r * 2);
        for (int k = -1; k <= 1; k++) {
            int ccy = cy + k * spacing;
            for (int dy = -r; dy <= r; dy++) {
                int half = (int) Math.sqrt((double) (r * r - dy * dy));
                guiGraphics.fill(cx - half, ccy + dy, cx + half + 1, ccy + dy + 1, color);
            }
        }
    }


    private void renderToolbarTooltip(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, String[] tooltips) {
        if (this.hoveredButton < 0 || this.toolbarGlow < 0.05F) return;
        int size = toolbarButtonSize();
        int x = toolbarButtonX(this.hoveredButton);
        int y = toolbarButtonY() + toolbarEnterRise(this.hoveredButton);
        int textW = this.font.width(Component.translatable(tooltips[this.hoveredButton]).getVisualOrderText());
        int tooltipW = (int) (textW * 0.7F) + 8;
        int tooltipH = (int) (this.font.lineHeight * 0.7F) + 4;
        int tipX = x + (size - tooltipW) / 2;
        int tipY = y - tooltipH - 6;
        tipX = Math.max(2, Math.min(tipX, this.width - tooltipW - 2));
        tipY = Math.max(2, tipY);
        int alpha = (int) (0xCC * this.toolbarGlow * toolbarEnterEase(this.hoveredButton));
        guiGraphics.fill(tipX, tipY, tipX + tooltipW, tipY + tooltipH, (alpha << 24) | 0x101014);
        int textAlpha = (int) (0xFF * this.toolbarGlow * toolbarEnterEase(this.hoveredButton));
        renderText(guiGraphics,
                Component.translatable(tooltips[this.hoveredButton]).getVisualOrderText(),
                tipX + 4, tipY + 2, 0.7F, (textAlpha << 24) | 0xCCCCCC);
    }

    private void renderInfoPanel(GuiGraphicsExtractor guiGraphics) {
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
                Component.translatable("gui.csgobox.csgo_box.style." + SKIN_STYLES[this.skinStyleIndex]),
                Component.literal(String.valueOf(this.skinId)),
                Component.literal(String.valueOf(this.patternSeed)),
                Component.literal(formatWear()),
                Component.translatable(wearTierKey())
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

    private void renderRoundedRect(GuiGraphicsExtractor guiGraphics, int x, int y, int w, int h, int r, int color) {
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

    private void drawInfoRow(GuiGraphicsExtractor guiGraphics, int labelX, int valueRight, int y, float scale,
                             String labelKey, Component value) {
        renderText(guiGraphics, Component.translatable(labelKey).getVisualOrderText(), labelX, y, scale,
                0xFF9A9A9A);
        float valueWidth = this.font.width(value.getVisualOrderText()) * scale;
        RenderFontTool.drawString(guiGraphics, this.font, value.getVisualOrderText(),
                valueRight - valueWidth, y, 0, 0, scale, 0xFFFFFFFF);
    }


    private void renderLabels(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY) {
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
        int bh = this.height * 5 / 100;
        boolean backHover = ButtonPalette.isInside(mouseX, mouseY, bx, by, bw, bh);
        int backText = backHover ? ButtonPalette.DANGER.textColorHover() : ButtonPalette.DANGER.textColor();
        renderCenteredText(guiGraphics,
                Component.translatable("gui.csgobox.csgo_box.close").withStyle(style).getVisualOrderText(),
                bx, by, bw, bh, 0.8F, backText);
    }

    private void renderText(GuiGraphicsExtractor guiGraphics, FormattedCharSequence text, float x, float y, float scale) {
        renderText(guiGraphics, text, x, y, scale, 0xFFFFFFFF);
    }

    private void renderText(GuiGraphicsExtractor guiGraphics, FormattedCharSequence text, float x, float y,
                            float scale, int color) {
        RenderFontTool.drawString(guiGraphics, this.font, text, x, y, 0, 0, scale, color);
    }

    private float centeredTextX(String text, float scale, int maxWidth) {
        float renderedWidth = Math.min(this.font.width(text) * scale, maxWidth);
        return (this.width - renderedWidth) / 2.0F;
    }

    private void renderCenteredText(GuiGraphicsExtractor guiGraphics, FormattedCharSequence text,
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
            this.itemDrag.accumulate(dragX, dragY);
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int btnX = backButtonX();
        int btnY = backButtonY();
        int btnW = backButtonWidth();
        int btnH = this.height * 5 / 100;
        if (event.button() == 0 && ButtonPalette.isInside(event.x(), event.y(), btnX, btnY, btnW, btnH)) {
            this.onClose();
            return true;
        }
        int infoSize = toolbarButtonSize();
        // Inspect button (index 0) is the single inspect entry: it toggles the
        // mob-animation inspect mode for living-entity drops (dead placeholder
        // for other items, which keep our own 3D drag preview).
        int inspectX = toolbarButtonX(0);
        int inspectY = toolbarButtonY() + toolbarEnterRise(0);
        if (event.button() == 0 && ButtonPalette.isInside(event.x(), event.y(), inspectX, inspectY, infoSize, infoSize)) {
            if (AnimRenderOps.hasPreviewMobInstance()) {
                this.mobAnimMode = !this.mobAnimMode;
            }
            return true;
        }
        // Wear (ⓘ, slot 1) exists in shipment flows only: the inspect flow
        // (previousScreen set) confirms nothing, so wear stays unviewable.
        if (this.showsShipment()) {
            int infoX = toolbarButtonX(1);
            int infoY = toolbarButtonY() + toolbarEnterRise(1);
            if (event.button() == 0 && ButtonPalette.isInside(event.x(), event.y(), infoX, infoY, infoSize, infoSize)) {
                this.showInfoPanel = !this.showInfoPanel;
                return true;
            }
        }
        int stickerSlot = this.showsShipment() ? 2 : 1;
        int stickerX = toolbarButtonX(stickerSlot);
        int stickerY = toolbarButtonY() + toolbarEnterRise(stickerSlot);
        if (event.button() == 0 && ButtonPalette.isInside(event.x(), event.y(), stickerX, stickerY, infoSize, infoSize)) {
            this.randomizeScenery();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    /** Screen-open camera mount: the background rides the scenery rig like
     *  the main-menu panorama (continuous slow spin; the position never
     *  changes until a "change scenery" click). */
    @Override
    public void init() {
        super.init();
        AnimRenderOps.clearPreviewMobInstance();
        if (!this.sceneryAutoStarted) {
            this.sceneryAutoStarted = true;
            this.beginSceneryPanorama();
        }
    }

    /** Mounts the render camera onto a rig parked at the player's eye so the
     *  backdrop spins slowly from the player's own view (main-menu panorama
     *  style). No flight: the rig stays put until a "change scenery" click. */
    private void beginSceneryPanorama() {
        if (this.minecraft == null || this.minecraft.level == null || this.player == null) {
            return;
        }
        if (this.sceneryRig != null) {
            return;
        }
        this.sceneryRig = this.createSceneryRig();
        if (this.sceneryRig == null) {
            return;
        }
        this.sceneryOriginalCamera = this.minecraft.getCameraEntity();
        this.minecraft.setCameraEntity(this.sceneryRig);
        // The panorama shows the world, not the player: hide the local model
        // while mounted (mid-spin the head would otherwise sweep through the
        // close-up frame). Restored in removed().
        if (!this.player.isInvisible()) {
            this.player.setInvisible(true);
            this.sceneryPlayerHidden = true;
        }
    }

    /** True while the scenery rig owns the render camera (panorama mount);
     *  read by the render-cancel handler in the event package. */
    public boolean isSceneryRigMounted() {
        return this.sceneryRig != null;
    }

    /** Scenery toolbar button (index 4): fly the render camera to a random
     *  nearby spot so the live world backdrop behind the item changes, while
     *  the player's own view is never touched. The camera rides a client-side
     *  marker entity (vanilla spectator camera mechanism), mounted when the
     *  screen opens and later flying from wherever it currently is.
     *  {@link #removed()} hands the camera back and discards the rig. */
    private void randomizeScenery() {
        if (this.minecraft == null || this.minecraft.level == null || this.player == null) {
            return;
        }
        if (this.sceneryRig == null) {
            this.sceneryRig = this.createSceneryRig();
            if (this.sceneryRig == null) {
                return;
            }
            this.sceneryOriginalCamera = this.minecraft.getCameraEntity();
            this.minecraft.setCameraEntity(this.sceneryRig);
            this.sceneryFromX = this.sceneryRig.getX();
            this.sceneryFromY = this.sceneryRig.getY();
            this.sceneryFromZ = this.sceneryRig.getZ();
            this.sceneryFromYaw = this.sceneryRig.getYRot();
            this.sceneryFromPitch = this.sceneryRig.getXRot();
        } else {
            this.sceneryFromX = this.sceneryRig.getX();
            this.sceneryFromY = this.sceneryRig.getY();
            this.sceneryFromZ = this.sceneryRig.getZ();
            this.sceneryFromYaw = this.sceneryRig.getYRot();
            this.sceneryFromPitch = this.sceneryRig.getXRot();
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        double angle = rnd.nextDouble() * Math.PI * 2.0;
        double dist = SCENERY_DIST_MIN + rnd.nextDouble() * SCENERY_DIST_RANGE;
        // Glide the short way around the circle: wrap the delta, not the target.
        this.sceneryToX = this.player.getX() + Math.cos(angle) * dist;
        this.sceneryToY = this.scenerySurfaceY(this.sceneryToX, this.player.getZ() + Math.sin(angle) * dist);
        this.sceneryToZ = this.player.getZ() + Math.sin(angle) * dist;
        this.sceneryToYaw = this.sceneryFromYaw + wrapDegrees(rnd.nextFloat() * 360.0F - this.sceneryFromYaw);
        this.sceneryToPitch = (rnd.nextFloat() * 2.0F - 1.0F) * SCENERY_PITCH_RANGE_DEG;
        this.sceneryAnimTick = 0;

    }

    /** Surface height at the target block, with an unloaded-chunk fallback
     *  (heightmaps answer the world floor for missing chunks). */
    private double scenerySurfaceY(double x, double z) {
        ClientLevel level = this.minecraft.level;
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, (int) Math.floor(x), (int) Math.floor(z));
        if (surface <= level.getMinY()) {
            return this.player.getY() + 4.0;
        }
        return surface + SCENERY_SURFACE_CLEARANCE;
    }

    /** Advances the scenery camera: between flights it spins the rig yaw
     *  slowly and continuously (main-menu panorama pace); during a flight it
     *  eases the rig (and thus the render camera) from the pose the click
     *  started at toward the random target over SCENERY_GLIDE_TICKS client
     *  ticks. Old-position bookkeeping feeds the vanilla per-frame
     *  interpolation, so both stay smooth at frame rate. */
    private void advanceSceneryGlide() {
        if (this.sceneryRig == null) {
            return;
        }
        if (this.sceneryAnimTick < 0) {
            // Panorama spin: slow continuous yaw drift, position untouched.
            this.sceneryRig.yRotO = this.sceneryRig.getYRot();
            this.sceneryRig.setYRot(wrapDegrees(this.sceneryRig.getYRot() + SCENERY_SPIN_DEG_PER_TICK));
            return;
        }
        this.sceneryAnimTick++;
        float t = Math.min(1.0F, (float) this.sceneryAnimTick / (float) SCENERY_GLIDE_TICKS);
        float e = 1.0F - (1.0F - t) * (1.0F - t) * (1.0F - t); // easeOutCubic
        this.sceneryRig.xo = this.sceneryRig.getX();
        this.sceneryRig.yo = this.sceneryRig.getY();
        this.sceneryRig.zo = this.sceneryRig.getZ();
        this.sceneryRig.yRotO = this.sceneryRig.getYRot();
        this.sceneryRig.xRotO = this.sceneryRig.getXRot();
        this.sceneryRig.setPos(this.sceneryFromX + (this.sceneryToX - this.sceneryFromX) * e,
                this.sceneryFromY + (this.sceneryToY - this.sceneryFromY) * e,
                this.sceneryFromZ + (this.sceneryToZ - this.sceneryFromZ) * e);
        this.sceneryRig.setYRot(this.sceneryFromYaw + (this.sceneryToYaw - this.sceneryFromYaw) * e);
        this.sceneryRig.setXRot(this.sceneryFromPitch + (this.sceneryToPitch - this.sceneryFromPitch) * e);
        if (t >= 1.0F) {
            this.sceneryAnimTick = -1;
        }
    }
    /** Creates the client-side-only camera rig (a marker entity, which never
     *  renders and has no AI) and registers it into the client level. */
    private Entity createSceneryRig() {
        ClientLevel level = this.minecraft.level;
        Entity rig = EntityType.MARKER.create(level, EntitySpawnReason.COMMAND);
        if (rig == null) {
            return null;
        }
        rig.setId(-1_900_000_000 - sceneryRigIdCounter++);
        rig.setPos(this.player.getX(), this.player.getEyeY(), this.player.getZ());
        rig.setYRot(this.player.getYRot());
        rig.setXRot(this.player.getXRot());
        // Park in front of the face (along the view direction) so the camera
        // starts outside the head model instead of sitting inside it.
        double lookYaw = Math.toRadians(this.player.getYRot());
        double lookPitch = Math.toRadians(this.player.getXRot());
        rig.setPos(rig.getX() - Math.sin(lookYaw) * Math.cos(lookPitch) * SCENERY_MOUNT_FORWARD,
                rig.getY() - Math.sin(lookPitch) * SCENERY_MOUNT_FORWARD,
                rig.getZ() + Math.cos(lookYaw) * Math.cos(lookPitch) * SCENERY_MOUNT_FORWARD);
        rig.xo = rig.getX();
        rig.yo = rig.getY();
        rig.zo = rig.getZ();
        rig.yRotO = rig.getYRot();
        rig.xRotO = rig.getXRot();
        level.addEntity(rig);
        return rig;
    }

    /** Wraps an angle delta to [-180, 180). */
    private static float wrapDegrees(float deg) {
        deg %= 360.0F;
        if (deg >= 180.0F) {
            deg -= 360.0F;
        }
        if (deg < -180.0F) {
            deg += 360.0F;
        }
        return deg;
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
        if (this.sceneryRig != null) {
            // Hand the render camera back to the player and drop the rig.
            if (this.minecraft != null && this.minecraft.getCameraEntity() == this.sceneryRig) {
                this.minecraft.setCameraEntity(this.sceneryOriginalCamera != null
                        ? this.sceneryOriginalCamera : this.player);
            }
            this.sceneryRig.discard();
            this.sceneryRig = null;
            this.sceneryAnimTick = -1;
            this.sceneryAutoStarted = false;
        }
        AnimRenderOps.resetPreviewMobAnimation();
        this.mobAnimMode = false;
        if (this.sceneryPlayerHidden) {
            if (this.player != null) {
                this.player.setInvisible(false);
            }
            this.sceneryPlayerHidden = false;
        }
        super.removed();
    }

    @Override
    public final void tick() {
        super.tick();
        this.screenTicks++;
        float glowTarget = this.hoveredButton >= 0 ? 1F : 0F;
        this.toolbarGlow += (glowTarget - this.toolbarGlow) * 0.5F;
        if (this.minecraft == null || this.minecraft.player == null) return;
        this.advanceSceneryGlide();
        AnimRenderOps.tickPreviewMobAnimations(this.mobAnimMode);
        if (!this.minecraft.player.isAlive() || this.minecraft.player.isRemoved()) {
            this.onClose();
        }
    }
}
