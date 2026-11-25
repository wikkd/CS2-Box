package com.reclizer.csgobox.v1_21_1.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.reclizer.csgobox.v1_21_1.compat.TaczInspectViewport;
import com.reclizer.csgobox.v1_21_1.event.FirstPersonInspectHandler;
import com.reclizer.csgobox.v1_21_1.sounds.ModSounds;
import com.reclizer.csgobox.utils.ColorTools;
import com.reclizer.csgobox.utils.Easing;
import com.reclizer.csgobox.utils.ItemDrag3D;
import com.reclizer.csgobox.v1_21_1.utils.AnimRenderOps;
import com.reclizer.csgobox.v1_21_1.utils.MaidSlabPreview;
import com.reclizer.csgobox.v1_21_1.utils.RenderFontTool;
import com.reclizer.csgobox.v1_21_1.utils.EpicFightInspect;
import com.reclizer.csgobox.v1_21_1.utils.SlashBladeComboInspect;
import com.reclizer.csgobox.v1_21_1.utils.SlashBladePreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
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
    /** Info panel open/close animation: 0 = closed, 1 = open. */
    private float infoPanelAnim = 0F;
    /** TACZ inspect viewport visibility; vestigial opt-in render path, exited
     *  by the inspect toolbar button when armed. */
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
    private static final ResourceLocation ICON_INFO =
            ResourceLocation.parse("csgobox:textures/gui/toolbar/info.png");
    private static final ResourceLocation ICON_STICKER =
            ResourceLocation.parse("csgobox:textures/gui/toolbar/sticker.png");
    private static final ResourceLocation ICON_MORE =
            ResourceLocation.parse("csgobox:textures/gui/toolbar/more.png");


    /** Content bounding boxes (px in the 32x32 texture) per toolbar slot,
     *  kept in sync with the artwork: inspect 30x29, info 26x26,
     *  sticker 30x23, more 5x21. One row per layout — the compact layout
     *  holds different icons than shipment at the same slot (sticker is
     *  slot 1 compact but slot 2 shipment), so the layouts cannot share
     *  positional arrays. */
    private static final int[][] SHIPMENT_CONTENT = {{30, 29}, {26, 26}, {30, 23}, {5, 21}};
    private static final int[][] COMPACT_CONTENT = {{30, 29}, {30, 23}, {5, 21}};

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

    /** Inspect-button position in a blade's cycle (rack pose flip → combo
     *  motion → stop). Reset whenever the screen opens or closes. */
    private boolean slashBladePoseFlipped = false;



    /** Displays the server-authoritative reward after the progress animation completes. */
    public CsLookItemScreen(ItemStack item, int grade) {
        this(item, grade, null, -1F);
    }

    /**
     * Same screen but opened from the right-click "检视" context menu to inspect
     * an item from a preview page: silent (no entry chime) and returns to
     * {@code previousScreen} on close instead of closing to the world.
     */
    public CsLookItemScreen(ItemStack item, int grade, Screen previousScreen) {
        this(item, grade, previousScreen, -1F);
    }

    /** Post-buy showcase: shows the bought item with the terminal offer's exact
     *  wear value (works for items without a durability bar too) and closes to
     *  the world on Esc / the close button. */
    public CsLookItemScreen(ItemStack item, int grade, float wearValue) {
        this(item, grade, null, wearValue);
    }

    private CsLookItemScreen(ItemStack item, int grade, Screen previousScreen, float forcedWear) {
        super(Component.literal("look_item"));
        this.player = Minecraft.getInstance().player;
        this.openItem = item == null ? ItemStack.EMPTY : item.copy();
        this.grade = grade;
        this.previousScreen = previousScreen;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        if (forcedWear >= 0F) {
            this.wearValue = Math.max(0F, Math.min(1F, forcedWear));
        } else if (!this.openItem.isEmpty() && this.openItem.isDamageableItem() && this.openItem.getDamageValue() > 0) {
            int maxDamage = this.openItem.getMaxDamage();
            this.wearValue = maxDamage > 0 ? (float) this.openItem.getDamageValue() / maxDamage : rnd.nextFloat();
        } else {
            this.wearValue = rnd.nextFloat();
        }
        this.patternSeed = rnd.nextInt(1000);
        this.skinId = rnd.nextInt(100, 1301);
        this.skinStyleIndex = rnd.nextInt(SKIN_STYLES.length);
        // Inspect entry is silent: the unboxing chime belongs to the reward flow.
        boolean silent = quietReopen || previousScreen != null;
        quietReopen = false;
        if (this.player != null && !this.openItem.isEmpty() && !silent) {
            float vol = 1.0F;
            if (vol > 0) {
                player.playSound(ModSounds.CS_FINSH.get(), vol * 10F, 1F);
            }
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
        // -> renderGunModel3D), which supports drag rotation. TACZ's native
        // first-person inspect animation is opt-in via the inspect toolbar
        // button's mode cycle (see mouseClicked).
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
            if (EpicFightInspect.isActive()) {
                // Epic Fight battle inspect owns the preview area: its own
                // rigged-figure widget (with camera controls) replaces the
                // flat item render for EF weapons.
                EpicFightInspect.render(guiGraphics, mouseX, mouseY, partialTicks);
            } else {
                AnimRenderOps.renderItem3D(guiGraphics, openItem, this.player,
                        this.width * 37 / 100, this.height * 30 / 100, this.itemDrag.rotation(), scale);
            }
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
        // The wear (ⓘ) button ships only in shipment flows; the inspect
        // toolbar is compact [inspect, sticker, more] with no dead slot.
        boolean shipment = this.showsShipment();
        ResourceLocation[] icons = shipment
                ? new ResourceLocation[]{ICON_INSPECT, ICON_INFO, ICON_STICKER, ICON_MORE}
                : new ResourceLocation[]{ICON_INSPECT, ICON_STICKER, ICON_MORE};
        String[] tooltips = shipment ? TOOLTIP_KEYS : TOOLTIP_KEYS_INSPECT;
        int[][] contents = shipment ? SHIPMENT_CONTENT : COMPACT_CONTENT;
        this.hoveredButton = -1;
        for (int i = 0; i < icons.length; i++) {
            int x = toolbarButtonX(i);
            int by = y + toolbarEnterRise(i);
            int alpha = (int) (0xFF * toolbarEnterEase(i));

            boolean hover = isInside(mouseX, mouseY, x, by, size, size);
            if (hover) {
                this.hoveredButton = i;
            }
            // Inspect (index 0) is the single inspect entry: always on as the
            // default mode (own 3D drag preview); its underline turns green
            // while a special inspect mode (mob animation / EF battle inspect /
            // blade combo motion) is active.
            boolean specialInspect = this.mobAnimMode || EpicFightInspect.isActive()
                    || SlashBladeComboInspect.isActive();
            boolean active = i == 0 || (i == 1 && this.showInfoPanel);
            int outer = 0xFF2B2B31;
            int inner = active ? 0xFF33333B : 0xFF232328;
            AnimRenderOps.fill(guiGraphics, x, by, x + size, by + size, (alpha << 24) | (outer & 0xFFFFFF));
            AnimRenderOps.fill(guiGraphics, x + 1, by + 1, x + size - 1, by + size - 1, (alpha << 24) | (inner & 0xFFFFFF));
            if (active) {
                int underlineAlpha = i == 1 ? (int) (0xFF * anim) : 0xFF;
                AnimRenderOps.fill(guiGraphics, x + 2, by + size - 2, x + size - 2, by + size,
                        (underlineAlpha << 24)
                                | (i == 0 && specialInspect ? 0x54FF54 : 0xFFFFFF));
            }
            int iconSize = Math.max(12, Math.min(64, size * 3 / 4));
            int maxEdge = Math.max(contents[i][0], contents[i][1]);
            int screenW = iconSize * contents[i][0] / maxEdge;
            int screenH = iconSize * contents[i][1] / maxEdge;
            int iconX = x + (size - screenW) / 2;
            int iconY = by + (size - screenH) / 2;
            int u = (32 - contents[i][0]) / 2;
            int v = (32 - contents[i][1]) / 2;
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
            AnimRenderOps.blitTextured(guiGraphics, icons[i], iconX, iconY, screenW, screenH, u, v,
                    contents[i][0], contents[i][1], 32, 32, iconColor);

            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        }
        renderToolbarTooltip(guiGraphics, mouseX, mouseY, tooltips);
    }


    private void renderToolbarTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY, String[] tooltips) {
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
        AnimRenderOps.fill(guiGraphics, tipX, tipY, tipX + tooltipW, tipY + tooltipH, (alpha << 24) | 0x101014);
        int textAlpha = (int) (0xFF * this.toolbarGlow * toolbarEnterEase(this.hoveredButton));
        renderText(guiGraphics,
                Component.translatable(tooltips[this.hoveredButton]).getVisualOrderText(),
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
        int anchorX = toolbarButtonX(1) + toolbarButtonSize() / 2;
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
                (grade >= 1 && grade <= 5
                        ? Component.translatable("gui.csgobox.csgo_box.grade" + grade)
                        : Component.translatable("gui.csgobox.csgo_box.grade_n", grade)).getVisualOrderText(),
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
        // EF battle inspect: the widget handles its own camera drag inside
        // its rect; the item drag stays parked while it owns the preview.
        if (EpicFightInspect.isActive() && EpicFightInspect.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
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
        if (EpicFightInspect.isActive() && EpicFightInspect.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        this.itemDrag.release();
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // EF battle inspect zoom lives inside the widget; outside stays vanilla.
        if (EpicFightInspect.isActive() && EpicFightInspect.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (EpicFightInspect.isActive() && EpicFightInspect.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        int btnX = backButtonX();
        int btnY = backButtonY();
        int btnW = backButtonWidth();
        int btnH = this.height * 5 / 100;
        if (button == 0 && isInside(mouseX, mouseY, btnX, btnY, btnW, btnH)) {
            this.onClose();
            return true;
        }
        int infoSize = toolbarButtonSize();
        // Inspect button (index 0) is the single inspect entry: it cycles the
        // inspect modes available for this item — Epic Fight battle inspect,
        // mob animation, SlashBlade rack pose / own combo motion, TACZ
        // first-person inspect — falling back to our own 3D drag preview once
        // nothing special is on.
        int inspectX = toolbarButtonX(0);
        int inspectY = toolbarButtonY() + toolbarEnterRise(0);
        if (button == 0 && isInside(mouseX, mouseY, inspectX, inspectY, infoSize, infoSize)) {
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
                if (SlashBladeComboInspect.isActive()) {
                    SlashBladeComboInspect.stop();
                } else if (!this.slashBladePoseFlipped) {
                    this.slashBladePoseFlipped = true;
                    SlashBladePreview.toggleStyle();
                } else if (!SlashBladeComboInspect.start()) {
                    // Combo motion unavailable (SlashBlade's surface moved):
                    // keep the button a pose flip.
                    this.slashBladePoseFlipped = false;
                    SlashBladePreview.toggleStyle();
                }
            } else if (this.minecraft != null && this.minecraft.player != null
                    && TaczInspectViewport.isAvailable(this.openItem)) {
                // First-person inspect: close the GUI first, then let TACZ play
                // its native first-person inspect behind a transparent
                // input-lock screen; FirstPersonInspectHandler reopens this
                // screen when the animation finishes or the player presses Esc.
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
            if (button == 0 && isInside(mouseX, mouseY, infoX, infoY, infoSize, infoSize)) {
                this.showInfoPanel = !this.showInfoPanel;
                return true;
            }
        }
        int stickerSlot = this.showsShipment() ? 2 : 1;
        int stickerX = toolbarButtonX(stickerSlot);
        int stickerY = toolbarButtonY() + toolbarEnterRise(stickerSlot);
        if (button == 0 && isInside(mouseX, mouseY, stickerX, stickerY, infoSize, infoSize)) {
            this.randomizeScenery();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** Screen-open camera mount: the background rides the scenery rig like
     *  the main-menu panorama (continuous slow spin; the position never
     *  changes until a "change scenery" click). */
    /** Toggles the Epic Fight battle inspect: starts their own previewer
     *  widget over the preview area (figure wearing the player's skin,
     *  holding the weapon) or tears it down again. Reflection failures leave
     *  the screen untouched. */
    private void toggleEpicFightInspect() {
        if (EpicFightInspect.isActive()) {
            EpicFightInspect.stop();
            return;
        }
        ResourceLocation skin = null;
        if (this.minecraft != null && this.minecraft.player instanceof AbstractClientPlayer viewer) {
            skin = viewer.getSkin().texture();
        }
        EpicFightInspect.start(this.width * 32 / 100, this.height * 15 / 100,
                this.width * 36 / 100, this.height * 56 / 100, this.openItem, skin);
    }

    @Override
    public void init() {
        super.init();
        AnimRenderOps.clearPreviewMobInstance();
        SlashBladeComboInspect.stop();
        this.slashBladePoseFlipped = false;
        SlashBladePreview.clearPreviewBlade();
        MaidSlabPreview.clearPreviewPick();
        EpicFightInspect.clear();
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

    /** Creates the client-side-only camera rig (a marker entity, which never
     *  renders and has no AI) and registers it into the client level. */
    private Entity createSceneryRig() {
        ClientLevel level = this.minecraft.level;
        Entity rig = EntityType.MARKER.create(level);
        if (rig == null) {
            return null;
        }
        rig.setId(-1_900_000_000 - sceneryRigIdCounter++);
        rig.moveTo(this.player.getX(), this.player.getEyeY(), this.player.getZ(),
                this.player.getYRot(), this.player.getXRot());
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

    /** Surface height at the target block, with an unloaded-chunk fallback
     *  (heightmaps answer the world floor for missing chunks). */
    private double scenerySurfaceY(double x, double z) {
        ClientLevel level = this.minecraft.level;
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, (int) Math.floor(x), (int) Math.floor(z));
        if (surface <= level.getMinBuildHeight()) {
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
        SlashBladeComboInspect.stop();
        this.slashBladePoseFlipped = false;
        EpicFightInspect.clear();
        if (this.sceneryPlayerHidden) {
            if (this.player != null) {
                this.player.setInvisible(false);
            }
            this.sceneryPlayerHidden = false;
        }
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
        this.advanceSceneryGlide();
        AnimRenderOps.tickPreviewMobAnimations(this.mobAnimMode);
        EpicFightInspect.tick();
        SlashBladeComboInspect.tick();
        if (!this.minecraft.player.isAlive() || this.minecraft.player.isRemoved()) {
            this.onClose();
        }
    }
}
