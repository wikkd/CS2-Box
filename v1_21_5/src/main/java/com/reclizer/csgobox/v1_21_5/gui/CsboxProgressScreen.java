package com.reclizer.csgobox.v1_21_5.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.reclizer.csgobox.v1_21_5.CsgoBox;
import com.reclizer.csgobox.v1_21_5.packet.PacketBoxBulkResult;
import com.reclizer.csgobox.v1_21_5.packet.PacketBoxOpenResult;
import com.reclizer.csgobox.v1_21_5.sounds.ModSounds;
import com.reclizer.csgobox.utils.ColorTools;
import com.reclizer.csgobox.v1_21_5.utils.IconListTools;
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
import net.neoforged.neoforge.client.stencil.StencilFunction;
import net.neoforged.neoforge.client.stencil.StencilOperation;
import net.neoforged.neoforge.client.stencil.StencilPerFaceTest;
import net.neoforged.neoforge.client.stencil.StencilTest;

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
        // strip is clipped to the disc itself - a true magnifier bounded by
        // the circular filter edge.
        //
        // The circular clip is a stencil mask instead of scissor slicing. The
        // main render target carries a stencil attachment (switched on through
        // ConfigureMainRenderTargetEvent in CsgoBox), the lens disc is stamped
        // into it once, then every magnified card is drawn a single time with
        // "test equal 1" - no per-band re-draws, no per-band scissor, no
        // per-band buffer flush. neoforge's RenderSystem.enableStencil applies
        // the StencilTest to whatever Renderpass draws while it is active.
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
        // Only cards whose magnified rect can reach the lens bbox are worth
        // rendering - resolve that index window once for the stencil pass.
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

        // Flush everything accumulated before the stencil region first
        // (done strip + spotlight + backdrop). The stencil passes below must
        // not re-enqueue, clip or film these already-whole-screen draws.
        guiGraphics.flush();

        // Stencil pass 1: reset the lens bbox to 0 so stale values from a
        // previous frame (moved lens centre) never leak through. The stencil
        // test for this pass always passes and stamps ref 0 over the whole
        // bounding box; colour is not written (alpha 0).
        StencilTest maskWipe = new StencilTest(
                new StencilPerFaceTest(
                        StencilOperation.REPLACE, StencilOperation.REPLACE,
                        StencilOperation.REPLACE, StencilFunction.ALWAYS),
                StencilTest.DEFAULT_READ_MASK, StencilTest.DEFAULT_WRITE_MASK, 0);
        RenderSystem.enableStencil(maskWipe);
        guiGraphics.fill(lensMinX, lensMinY, lensMinX + lensW, lensMinY + lensW, 0x00000000);
        guiGraphics.flush();

        // Stencil pass 2: stamp the disc - only the pixels inside the lens
        // circle get a 1. Colour stays transparent so nothing is drawn.
        StencilTest maskStamp = new StencilTest(
                new StencilPerFaceTest(
                        StencilOperation.REPLACE, StencilOperation.REPLACE,
                        StencilOperation.REPLACE, StencilFunction.ALWAYS),
                StencilTest.DEFAULT_READ_MASK, StencilTest.DEFAULT_WRITE_MASK, 1);
        RenderSystem.enableStencil(maskStamp);
        this.drawLensDisc(guiGraphics, lensCX, lensCY, lensR);
        guiGraphics.flush();

        // Stencil pass 3: only where the stencil holds 1 the magnified strip
        // is drawn on top of an opaque disc backing (vignette centre is
        // transparent, so the raw 1x strip underneath would otherwise bleed
        // through). Each card is rendered exactly once.
        StencilTest maskRead = new StencilTest(
                new StencilPerFaceTest(
                        StencilOperation.KEEP, StencilOperation.KEEP,
                        StencilOperation.KEEP, StencilFunction.EQUAL),
                StencilTest.DEFAULT_READ_MASK, 0, 1);
        RenderSystem.enableStencil(maskRead);
        guiGraphics.fill(lensMinX, lensMinY, lensMinX + lensW, lensMinY + lensW, 0xFF545454);
        guiGraphics.flush();
        for (int i = iMax; i >= iMin; i--) {
            ItemStack itemStack = itemInput.get(i);
            if (itemStack.isEmpty()) continue;
            float itemX = stripStartX + i * spacing - scrollNow;
            float pX = lensCX + (itemX - lensCX) * lensScale;
            if (pX > lensMinX + lensW + cardScale || pX + cardScale < lensMinX) continue;
            IconListTools.renderItemProgressFocus(player, guiGraphics, itemStack,
                    pX, magnifiedTop, this.width, this.height, gradeInput.get(i), lensScale);
        }
        guiGraphics.flush();
        RenderSystem.disableStencil();

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

    }

    /**
     * Draws a solid disc as a fan of triangles for the stencil stamping pass.
     * 21.5 has no legacy immediate-mode circle, so the fan is built through
     * the same QUADS GUI pipeline as degenerate quads (centre + two rim
     * points, centre repeated). Colour is fully transparent - only the
     * stencil value matters.
     */
    private void drawLensDisc(GuiGraphics guiGraphics, float cx, float cy, float r) {
        final int segments = 96;
        guiGraphics.drawSpecial(multiBufferSource -> {
            var buffer = multiBufferSource.getBuffer(RenderType.gui());
            for (int i = 0; i < segments; i++) {
                double a0 = Math.PI * 2 * i / segments;
                double a1 = Math.PI * 2 * (i + 1) / segments;
                float x0 = cx + (float) (Math.cos(a0) * r);
                float y0 = cy + (float) (Math.sin(a0) * r);
                float x1 = cx + (float) (Math.cos(a1) * r);
                float y1 = cy + (float) (Math.sin(a1) * r);
                buffer.addVertex(cx, cy, 0.0F).setColor(0, 0, 0, 0);
                buffer.addVertex(x0, y0, 0.0F).setColor(0, 0, 0, 0);
                buffer.addVertex(x1, y1, 0.0F).setColor(0, 0, 0, 0);
                buffer.addVertex(cx, cy, 0.0F).setColor(0, 0, 0, 0);
            }
        });
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
