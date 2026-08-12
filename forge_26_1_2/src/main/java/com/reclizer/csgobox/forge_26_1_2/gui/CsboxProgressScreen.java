package com.reclizer.csgobox.forge_26_1_2.gui;

import com.reclizer.csgobox.forge_26_1_2.CsgoBox;
import com.reclizer.csgobox.forge_26_1_2.packet.PacketBoxBulkResult;
import com.reclizer.csgobox.forge_26_1_2.packet.PacketBoxOpenResult;
import com.reclizer.csgobox.forge_26_1_2.sounds.ModSounds;
import com.reclizer.csgobox.utils.ColorTools;
import com.reclizer.csgobox.forge_26_1_2.utils.AnimRenderOps;
import com.reclizer.csgobox.forge_26_1_2.utils.IconListTools;
import com.reclizer.csgobox.forge_26_1_2.utils.RenderFontTool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

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
    private static final int MAX_BULK_WAIT_TICKS = 100;

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

    /** Rejected opens (cooldown, missing key, dead player) show a red banner
     *  for REJECT_CLOSE_TICKS before closing instead of snapping away. */
    private static final int REJECT_CLOSE_TICKS = 40;
    private boolean rejected = false;
    private int rejectedTicks = 0;

    // Bulk-result aggregation state. waitingBulkTicks < 0 means we are not in
    // the drain phase yet; while draining, every chunk with our request id is
    // consumed and the finish fires after two quiet ticks or the hard cap.
    private int waitingBulkTicks = -1;
    private int quietBulkTicks = 0;
    private List<ItemStack> bulkItems = List.of();
    private List<Integer> bulkGrades = List.of();

    public CsboxProgressScreen(Player player, long requestId) {
        super(Minecraft.getInstance(), Minecraft.getInstance().font, Component.literal("cs_progress"));
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
    public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.extractRenderState(guiGraphics, mouseX, mouseY, partialTicks);
        renderBg(guiGraphics, partialTicks);
    }

    /**
     * CS2-style depth-of-field backdrop: always blur the live world regardless
     * of the menu-background-blurriness option. This framework hook runs exactly
     * once per frame; blurring again in renderBg would throw
     * "Can only blur once per frame" (GuiRenderState) and freeze the screen.
     */
    @Override
    protected void extractBlurredBackground(GuiGraphicsExtractor guiGraphics) {
        if (ModList.isLoaded("blur")) {
            super.extractBlurredBackground(guiGraphics);
        } else {
            AnimRenderOps.renderBlurredBackground(guiGraphics);
        }
    }

    private void renderBg(GuiGraphicsExtractor guiGraphics, float partialTicks) {
        if (this.minecraft == null) return;
        this.minecraft.options.hideGui = true;

        // CS2-style backdrop: the blur is applied by extractBlurredBackground;
        // here we only dim the blurred world.
        AnimRenderOps.fill(guiGraphics, 0, 0, this.width, this.height, 0x8C000000);

        if (rejected) {
            Component msg = Component.translatable("gui.csgobox.progress.rejected");
            float scale = 1.0F;
            float w = this.font.width(msg) * scale;
            RenderFontTool.drawString(guiGraphics, this.font, msg.getVisualOrderText(),
                    (this.width - w) / 2.0F, this.height * 40 / 100, 0, 0, scale, 0xFFFF5555);
        }

        if (openTime < 5) return;

        // Compute the resize-adjusted render position separately from the
        // lastRenderWidth we keep for next-tick interpolation. lastRenderWidth
        // must hold the raw tick value so a window resize mid-animation does
        // not produce a one-frame snap when the lerp runs on the next tick.
        float renderWidthNow = renderWidthAdd;
        if (this.width != startWidth) {
            renderWidthNow *= this.width / startWidth;
        }

        float progress = Mth.clamp(partialTicks, 0.0F, 1.0F);
        int count = Math.min(itemInput.size(), gradeInput.size());
        float scrollNow = Mth.lerp(progress, lastRenderWidth, renderWidthNow);
        // CS2-style spotlight centred on the golden line: a soft lamp glow
        // behind the strip plus per-card brightness falloff (fully lit at the
        // line, smoothly dimmed outside the spotlight radius).
        float spacing = this.width * 20F / 100F;
        float lineX = this.width / 2F;
        float stripStartX = this.width * randomWidth / 100F;
        float lensTop = this.height * 37F / 100F;
        float cellWidth = this.width * 18F / 100F;
        float cellHeight = this.height * 25F / 100F;
        float spotRadius = this.width * 30F / 100F;
        float spotCX = lineX;
        float spotCY = lensTop + cellHeight / 2F;

        // Soft lamp glow behind the strip - a clean radial gradient with a
        // transparent rim (the old lens_vignette.png baked in a black ring).
        int glowR = (int) (this.height * 45F / 100F);
        guiGraphics.blit(
                RenderPipelines.GUI_TEXTURED,
                Identifier.parse("csgobox:textures/screens/spot_glow.png"),
                (int) spotCX - glowR, (int) spotCY - glowR,
                0F, 0F,
                glowR * 2, glowR * 2,
                glowR * 2, glowR * 2
        );

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

        lastRenderWidth = renderWidthAdd;

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
            float backingDy = Math.min(Math.abs(by - lensCY), Math.abs(by + bh - lensCY));
            float backingHalfW2 = lensR * lensR - backingDy * backingDy;
            int backingX0 = x0;
            int backingX1 = x1;
            if (backingHalfW2 > 0F) {
                float backingHalfW = (float) Math.sqrt(backingHalfW2);
                backingX0 = (int) (lensCX - backingHalfW);
                backingX1 = (int) (lensCX + backingHalfW) + 1;
            }
            guiGraphics.fill(backingX0, by, backingX1, by + bh, 0xFF545454);
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
        guiGraphics.blit(
                RenderPipelines.GUI_TEXTURED,
                Identifier.parse("csgobox:textures/screens/lens_vignette.png"),
                lensMinX, lensMinY,
                0F, 0F,
                lensW, lensW,
                lensW, lensW
        );


        // Bright golden marker line, like the original.
        guiGraphics.fill((int) lineX, (int) lensTop, (int) lineX + 2, (int) (lensTop + cellHeight),
                ColorTools.argbColor(230, 255, 215, 0));

    }

    @Override
    public void tick() {
        super.tick();

        if (rejected) {
            rejectedTicks++;
            if (rejectedTicks >= REJECT_CLOSE_TICKS) {
                this.onClose();
            }
            return;
        }

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
            if (result.animationItems().isEmpty()) {
                this.rejected = true;
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

        if (startTime >= totalTicks) {
            if (waitingBulkTicks < 0) {
                waitingBulkTicks = 0;
                quietBulkTicks = 0;
                bulkItems = new ArrayList<>();
                bulkGrades = new ArrayList<>();
            }
            waitingBulkTicks++;
            drainBulkChunks();
            if (waitingBulkTicks >= MAX_BULK_WAIT_TICKS
                    || (waitingBulkTicks >= 10 && quietBulkTicks >= 2)) {
                finishAndShowResult();
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

    /**
     * Consumes every pending bulk chunk that matches this screen's request id.
     * Bulk open results travel in several small packets; this keeps draining
     * until the server-side burst is exhausted.
     */
    private void drainBulkChunks() {
        boolean got = false;
        PacketBoxBulkResult chunk;
        while ((chunk = PacketBoxBulkResult.consumeMatching(this.expectedRequestId)) != null) {
            this.bulkItems.addAll(chunk.items());
            this.bulkGrades.addAll(chunk.grades());
            got = true;
        }
        this.quietBulkTicks = got ? 0 : this.quietBulkTicks + 1;
    }

    /**
     * Shows the consolidated bulk result (or the single-item popup when no
     * bulk chunks arrived while draining).
     */
    private void finishAndShowResult() {
        // restore hideGui BEFORE setScreen — Minecraft.setScreen calls
        // Screen.removed() (not onClose()), so the onClose hideGui=false
        // reset below would never run otherwise, leaving the HUD hidden
        // after bulk open completes.
        if (this.minecraft != null) {
            this.minecraft.options.hideGui = false;
        }
        if (!this.bulkItems.isEmpty()) {
            List<ItemStack> allItems = new ArrayList<>();
            List<Integer> allGrades = new ArrayList<>();
            if (!this.resultItem.isEmpty()) {
                allItems.add(this.resultItem.copy());
                allGrades.add(this.resultGrade);
            }
            allItems.addAll(this.bulkItems);
            allGrades.addAll(this.bulkGrades);
            Minecraft.getInstance().setScreen(new CsboxBulkResultScreen(this.player, allItems, allGrades));
        } else if (!this.resultItem.isEmpty()) {
            Minecraft.getInstance().setScreen(new CsLookItemScreen(this.resultItem, this.resultGrade));
        } else {
            this.onClose();
        }
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
    public void removed() {
        // Death/respawn or any external setScreen() replacement only calls
        // Screen.removed() (never onClose()), so restore the HUD here too —
        // otherwise dying mid-animation leaves hideGui=true and the HUD
        // (health bar / hotbar / crosshair) disappears permanently after
        // respawn.
        if (this.minecraft != null) {
            this.minecraft.options.hideGui = false;
        }
        super.removed();
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
        return ticks / Math.max(1, multiplier);
    }
}
