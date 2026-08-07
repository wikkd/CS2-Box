package com.reclizer.csgobox.v1_21_3.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.reclizer.csgobox.v1_21_3.CsgoBox;
import com.reclizer.csgobox.v1_21_3.packet.PacketBoxBulkResult;
import com.reclizer.csgobox.v1_21_3.packet.PacketBoxOpenResult;
import com.reclizer.csgobox.v1_21_3.sounds.ModSounds;
import com.reclizer.csgobox.utils.ColorTools;
import com.reclizer.csgobox.v1_21_3.utils.IconListTools;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Box opening animation screen. The displayed item strip is supplied by the
 * server result packet, so the animation and the final reward share the same
 * authoritative data.
 */
public class CsboxProgressScreen extends Screen {
    private static final int MAX_WAIT_TICKS = 200;

    private final Player player;
    private final long expectedRequestId;
    private final float randomWidth;
    private final int totalTicks;

    private final List<ItemStack> itemInput = new ArrayList<>();
    private final List<Integer> gradeInput = new ArrayList<>();

    private float startWidth;
    private boolean startSwitch = true;
    private int startTime = 0;
    private int openTime = 0;
    private float velocityLerp = 0;
    private float lastRenderWidth = 0F;
    private float renderWidthAdd = 0F;
    private float targetScroll = 0F;
    private float soundWidthAdd = 0;

    // Hard cap on tick-sound playback rate. The pixel-accumulator trigger
    // (soundWidthAdd > soundThreshold) gives the scroll its audible rhythm,
    // but at peak velocity it can fire every 1-2 ticks, which exceeds the
    // 8-channel OpenAL pool and floods the log with "Maximum sound pool
    // size reached" warnings. Wall-clock throttle (not game-time — game-time
    // is not monotonic across pause/unpause) keeps playback under ~8 Hz
    // while preserving the per-icon click the user hears.
    private static final long MIN_TICK_SOUND_INTERVAL_MS = 120L;
    private long lastTickSoundMs = 0L;

    private Integer serverWinningIndex = null;
    private ItemStack resultItem = ItemStack.EMPTY;
    private int resultGrade = 0;
    private int waitingTicks = 0;

    public CsboxProgressScreen(Player player, long requestId) {
        super(Component.literal("cs_progress"));
        this.player = player;
        this.expectedRequestId = requestId;
        this.randomWidth = ThreadLocalRandom.current().nextFloat() * (111F - 93.5F) + 93.5F;
        this.totalTicks = readAnimationTicks();
    }

    @Override
    protected void init() {
        super.init();
        this.startWidth = this.width;
    }

    private float easedScroll(float progress, float totalDistance) {
        float t = 1.0F - progress;
        return totalDistance * (1.0F - t * t * t);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        // NOTE: intentionally NOT calling super.render(). The base Screen.render
        // paints the menu background texture / panorama behind every screen
        // (renderBackground), which the decoupled 26.2 pipeline never draws -
        // a stale "background image" that must not appear here. This screen has
        // no widgets or tooltips, and renderBg paints its own blurred backdrop.
        renderBg(guiGraphics, partialTicks);
    }

    private void renderBg(GuiGraphics guiGraphics, float partialTicks) {
        if (this.minecraft == null) return;
        this.minecraft.options.hideGui = true;

        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // CS2-style backdrop: blur the live world and dim it, instead of an
        // opaque panel - mirrors the original case-opening depth-of-field look.
        this.renderBlurredBackground();
        guiGraphics.fill(0, 0, this.width, this.height, 0x8C000000);

        if (openTime < 5) return;

        float widthNewAdd = renderWidthAdd;
        if (this.width != startWidth) {
            widthNewAdd *= this.width / startWidth;
        }

        float progress = Mth.clamp(partialTicks, 0.0F, 1.0F);
        int count = Math.min(itemInput.size(), gradeInput.size());
        // CS2-style spotlight centred on the golden line: a soft lamp glow
        // behind the strip plus per-card brightness falloff (fully lit at the
        // line, smoothly dimmed outside the spotlight radius).
        float spacing = this.width * 20F / 100F;
        float lineX = this.width / 2F;
        float stripStartX = this.width * randomWidth / 100F;
        float lensTop = this.height * 37F / 100F;
        float cellWidth = this.width * 18F / 100F;
        float cellHeight = this.height * 25F / 100F;
        float scrollNow = Mth.lerp(progress, lastRenderWidth, widthNewAdd);
        float spotRadius = this.width * 30F / 100F;
        float spotCX = lineX;
        float spotCY = lensTop + cellHeight / 2F;

        // Soft lamp glow behind the strip - a clean radial gradient with a
        // transparent rim (the old lens_vignette.png baked in a black ring).
        int glowR = (int) (this.height * 45F / 100F);
        guiGraphics.blit(RenderType.GUI_TEXTURED,
                ResourceLocation.parse("csgobox:textures/screens/spot_glow.png"),
                (int) spotCX - glowR, (int) spotCY - glowR, 0F, 0F, glowR * 2, glowR * 2, glowR * 2, glowR * 2, 0xFFFFFFFF);

        // Strip pass: cards keep their raw size; brightness falls off with
        // distance from the golden line (smoothstep), like the original.
        for (int i = count - 1; i >= 0; i--) {
            ItemStack itemStack = itemInput.get(i);
            if (itemStack.isEmpty()) continue;
            float itemX = stripStartX + i * spacing - scrollNow;
            if (itemX > this.width + cellWidth || itemX + cellWidth < -cellWidth) continue;
            IconListTools.renderItemProgress(player, guiGraphics, itemStack,
                    itemX, lensTop, this.width, this.height, gradeInput.get(i));
            float nx = (itemX + cellWidth / 2F - spotCX) / spotRadius;
            float t = Math.min(nx * nx, 1.0F);
            float smooth = t * t * (3F - 2F * t);
            int dim = (int) (0x99 * smooth);
            if (dim > 0) {
                guiGraphics.fill((int) itemX, (int) lensTop, (int) (itemX + cellWidth),
                        (int) (lensTop + cellHeight) + 2, dim << 24);
            }
        }

        lastRenderWidth = widthNewAdd;

        // CS2Deck-style magnifier lens: a fixed circular viewport that the
        // passing strip zooms through (whole strip rendered again scaled about
        // the lens centre, so the same card stays aligned). The magnified
        // strip is clipped to the disc itself (slice scissors) - a true
        // magnifier bounded by the circular filter edge, not a single-card
        // "focus" animation.
        float lensCX = this.width / 2F;
        float lensCY = this.height / 2F;
        float lensScale = IconListTools.FOCUS_PEAK_SCALE;
        float lensR = this.width * 20F / 100F;
        int lensMinX = (int) (lensCX - lensR);
        int lensMinY = (int) (lensCY - lensR);
        int lensW = (int) (lensR * 2F);
        float magnifiedTop = lensCY + (lensTop - lensCY) * lensScale;
        float magnifiedBottom = magnifiedTop + cellHeight * lensScale;
        float cardScale = cellWidth * lensScale;
        // Only cards whose magnified rect reaches the lens bbox can ever show
        // in a slice - resolve that index window once instead of scanning the
        // whole strip for every slice.
        int iMin = count;
        int iMax = -1;
        for (int i = 0; i < count; i++) {
            float itemX = stripStartX + i * spacing - scrollNow;
            float pX = lensCX + (itemX - lensCX) * lensScale;
            if (pX + cardScale <= lensMinX) continue;
            if (pX >= lensMinX + lensW) break;
            if (iMin == count) iMin = i;
            iMax = i;
        }
        // Real-time magnification bounded by the circular lens edge. Scissor
        // only supports rectangles, so the magnified strip is drawn in
        // horizontal slices. Each slice's half-width is taken at the slice
        // edge FARTHEST from the lens centre, making the scissor rect
        // inscribed in the disc: magnified content never spills outside the
        // circle. Slice height adapts to the local slope of the arc - coarse
        // in the flat middle, 1-2px near the steep poles - so the inscribed
        // error stays well under a pixel.
        //
        // Adjacent slices whose clip rect rounds to the same (x0, x1) produce
        // bit-identical output, so they are merged into a single band first -
        // the card redraw (a 3D item render + buffer flush per card) then
        // runs once per band instead of once per slice. The merged rect stays
        // inscribed in the disc (same rounding, same far edge), so visual
        // output is unchanged.
        int yMin = Math.max(lensMinY, (int) Math.ceil(magnifiedTop));
        int yMax = Math.min(lensMinY + lensW, (int) Math.floor(magnifiedBottom));
        int maxBands = yMax - yMin + 1;
        int[] bands = new int[maxBands * 4];
        int bandCount = 0;
        int y = yMin;
        int curY = yMin, curH = 0, curX0 = 0, curX1 = 0;
        boolean curOpen = false;
        while (y <= yMax) {
            float farDist = Math.max(Math.abs(y - lensCY), Math.abs(y + 1 - lensCY));
            float slope = farDist / (float) Math.sqrt(Math.max(lensR * lensR - farDist * farDist, 1.0F));
            int h = Math.max(1, Math.min(16, (int) Math.ceil(0.5F / Math.max(slope, 0.03F))));
            h = Math.min(h, yMax - y + 1);
            float farDy = Math.max(Math.abs(y - lensCY), Math.abs(y + h - lensCY));
            float halfW2 = lensR * lensR - farDy * farDy;
            if (halfW2 > 0F) {
                float halfW = (float) Math.sqrt(halfW2);
                int x0 = (int) (lensCX - halfW);
                int x1 = (int) (lensCX + halfW) + 1;
                if (!curOpen || x0 != curX0 || x1 != curX1) {
                    if (curOpen) {
                        int o = bandCount++ * 4;
                        bands[o] = curY; bands[o + 1] = curH; bands[o + 2] = curX0; bands[o + 3] = curX1;
                    }
                    curY = y; curH = h; curX0 = x0; curX1 = x1; curOpen = true;
                } else {
                    curH += h;
                }
            } else if (curOpen) {
                int o = bandCount++ * 4;
                bands[o] = curY; bands[o + 1] = curH; bands[o + 2] = curX0; bands[o + 3] = curX1;
                curOpen = false;
            }
            y += h;
        }
        if (curOpen) {
            int o = bandCount++ * 4;
            bands[o] = curY; bands[o + 1] = curH; bands[o + 2] = curX0; bands[o + 3] = curX1;
        }
        for (int b = 0; b < bandCount; b++) {
            int o = b * 4;
            int by = bands[o], bh = bands[o + 1], x0 = bands[o + 2], x1 = bands[o + 3];
            // Opaque lens backing: the vignette's glass centre is fully
            // transparent, so without this the raw 1x strip drawn in the
            // strip pass would bleed through the disc and double up with
            // the magnified view. Fill the whole band first - the raw
            // residue is sealed under a neutral glass-colour plate.
            guiGraphics.fill(x0, by, x1, by + bh, 0xFF545454);
            guiGraphics.enableScissor(x0, by, x1, by + bh);
            for (int i = iMax; i >= iMin; i--) {
                ItemStack itemStack = itemInput.get(i);
                if (itemStack.isEmpty()) continue;
                float itemX = stripStartX + i * spacing - scrollNow;
                float pX = lensCX + (itemX - lensCX) * lensScale;
                if (pX > x1 + cardScale || pX + cardScale < x0) continue;
                IconListTools.renderItemProgressFocus(player, guiGraphics, itemStack,
                        pX, magnifiedTop, this.width, this.height, gradeInput.get(i), lensScale);
            }
            guiGraphics.disableScissor();
        }

        // Circular backing mask: transparent center (magnified strip shows
        // through) and transparent outside the disc too (the four corners of
        // the blit square stay see-through), only a soft rim shade around the
        // glass edge marks the lens silhouette.
        guiGraphics.blit(RenderType.GUI_TEXTURED,
                ResourceLocation.parse("csgobox:textures/screens/lens_vignette.png"),
                lensMinX, lensMinY, 0F, 0F, lensW, lensW, lensW, lensW, 0xFFFFFFFF);

        // Bright golden marker line, like the original.
        guiGraphics.fill((int) lineX, (int) lensTop, (int) lineX + 2, (int) (lensTop + cellHeight),
                ColorTools.argbColor(230, 255, 215, 0));
        RenderSystem.disableBlend();

    }

    @Override
    public void tick() {
        super.tick();

        if (serverWinningIndex == null) {
            waitingTicks++;
            if (waitingTicks > MAX_WAIT_TICKS) {
                this.onClose();
                return;
            }

            var result = PacketBoxOpenResult.consumeMatching(expectedRequestId);
            if (result == null) {
                return;
            }
            if (result.item().isEmpty()) {
                this.onClose();
                return;
            }

            this.serverWinningIndex = result.winningIndex();
            this.resultItem = result.item().copy();
            this.resultGrade = result.grade();
            this.itemInput.clear();
            this.itemInput.addAll(result.animationItems());
            this.gradeInput.clear();
            this.gradeInput.addAll(result.animationGrades());
            if (this.itemInput.isEmpty()) {
                this.itemInput.add(this.resultItem.copy());
                this.gradeInput.add(this.resultGrade);
                this.serverWinningIndex = 0;
            }
            return;
        }

        openTime++;
        if (openTime < 2) return;

        if (startSwitch) {
            startSwitch = false;
            this.startWidth = this.width;

            int winningIndex = Mth.clamp(serverWinningIndex, 0, Math.max(0, itemInput.size() - 1));
            float itemSpacing = startWidth * 20.0F / 100.0F;
            float startX = startWidth * randomWidth / 100.0F;
            float goldenLine = startWidth / 2.0F;
            this.targetScroll = startX + winningIndex * itemSpacing - goldenLine;
        }

        if (openTime < 5) return;

        if (startTime < totalTicks) {
            startTime++;
        }

        if (startTime == totalTicks) {
            PacketBoxBulkResult bulk = PacketBoxBulkResult.consumeMatching(this.expectedRequestId);
            // restore hideGui BEFORE setScreen — Minecraft.setScreen calls
            // Screen.removed() (not onClose()), so the onClose hideGui=false
            // reset below would never run otherwise, leaving the HUD hidden
            // after bulk open completes.
            if (this.minecraft != null) {
                this.minecraft.options.hideGui = false;
            }
            if (bulk != null && !bulk.items().isEmpty()) {
                List<ItemStack> allItems = new ArrayList<>();
                List<Integer> allGrades = new ArrayList<>();
                if (!this.resultItem.isEmpty()) {
                    allItems.add(this.resultItem.copy());
                    allGrades.add(this.resultGrade);
                }
                allItems.addAll(bulk.items());
                allGrades.addAll(bulk.grades());
                Minecraft.getInstance().setScreen(new CsboxBulkResultScreen(this.player, allItems, allGrades));
            } else if (!this.resultItem.isEmpty()) {
                Minecraft.getInstance().setScreen(new CsLookItemScreen(this.resultItem, this.resultGrade));
            } else {
                this.onClose();
            }
            return;
        }

        float progress = (float) startTime / Math.max(1, totalTicks - 1);
        renderWidthAdd = easedScroll(progress, targetScroll);

        float prevProgress = (float) Math.max(0, startTime - 1) / Math.max(1, totalTicks - 1);
        velocityLerp = (easedScroll(progress, targetScroll) - easedScroll(prevProgress, targetScroll)) / 35F;

        float thresholdStart = startWidth * randomWidth / 100F - startWidth / 2;
        float thresholdEnd = thresholdStart + startWidth * 20F * 35 / 100F;
        float soundThreshold = (renderWidthAdd >= thresholdEnd)
                ? startWidth * 10F / 100F
                : startWidth * 20F / 100F;

        float velocity = velocityLerp * 35F;
        soundWidthAdd += startWidth / 173F * velocity;
        if (soundWidthAdd > soundThreshold) {
            soundWidthAdd = 0;
            float tickVol = CsgoBox.CONFIG.tickSoundVolume() / 100F;
            if (tickVol > 0) {
                long nowMs = System.currentTimeMillis();
                if (nowMs - lastTickSoundMs >= MIN_TICK_SOUND_INTERVAL_MS) {
                    lastTickSoundMs = nowMs;
                    player.level().playSound(player, player.getX(), player.getY(), player.getZ(),
                            ModSounds.CS_DITA.get(), SoundSource.NEUTRAL, tickVol * 10F, 1F);
                }
            }
        }
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
    public boolean isPauseScreen() {
        return false;
    }

    private static int readAnimationTicks() {
        int base = CsgoBox.CONFIG.totalAnimationTicks();
        int multiplier = CsgoBox.CONFIG.animationSpeedMultiplier();
        int ticks = switch (CsgoBox.CONFIG.animationSpeed()) {
            case SLOW -> base * 2;
            case FAST -> base / 2;
            default -> base;
        };
        return Math.clamp(ticks / Math.max(1, multiplier), 20, 500);
    }
}
