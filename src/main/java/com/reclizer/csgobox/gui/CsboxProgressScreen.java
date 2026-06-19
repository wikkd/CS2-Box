package com.reclizer.csgobox.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.reclizer.csgobox.CsgoBox;
import com.reclizer.csgobox.packet.PacketBoxOpenResult;
import com.reclizer.csgobox.sounds.ModSounds;
import com.reclizer.csgobox.utils.ColorTools;
import com.reclizer.csgobox.utils.IconListTools;
import com.reclizer.csgobox.utils.OverlayColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
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
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        renderProgressBackground(guiGraphics);
        renderBg(guiGraphics, partialTicks);
    }

    private void renderProgressBackground(GuiGraphics guiGraphics) {
        if (this.minecraft != null && this.minecraft.level != null) {
            guiGraphics.fillGradient(0, 0, this.width, this.height,
                    OverlayColor.getBackgroundColor(), OverlayColor.getBackgroundColor());
        }
    }

    private void renderBg(GuiGraphics guiGraphics, float partialTicks) {
        if (this.minecraft == null) return;
        this.minecraft.options.hideGui = true;

        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        if (openTime < 5) return;

        float widthNewAdd = renderWidthAdd;
        if (this.width != startWidth) {
            widthNewAdd *= this.width / startWidth;
        }

        float progress = Mth.clamp(partialTicks, 0.0F, 1.0F);
        int count = Math.min(itemInput.size(), gradeInput.size());
        for (int i = 0; i < count; i++) {
            ItemStack itemStack = itemInput.get(i);
            if (itemStack.isEmpty()) continue;

            float itemX = this.width * randomWidth / 100F
                    + i * this.width * 20F / 100F
                    - Mth.lerp(progress, lastRenderWidth, widthNewAdd);

            IconListTools.renderItemProgress(player, guiGraphics, itemStack,
                    itemX, this.height * 37F / 100F,
                    this.width, this.height, gradeInput.get(i));
        }

        lastRenderWidth = widthNewAdd;

        int goldLineTop = this.height * 37 / 100;
        int goldLineBottom = goldLineTop + this.height * 25 / 100;
        guiGraphics.fill(this.width / 2, goldLineTop,
                this.width / 2 + 2, goldLineBottom,
                ColorTools.argbColor(128, 255, 215, 0));
        RenderSystem.disableBlend();

        RenderSystem.enableBlend();
        guiGraphics.blit(
                ResourceLocation.parse("csgobox:textures/screens/csgo_background.png"),
                0, 0, 0, 0, this.width, this.height, this.width, this.height
        );
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
            if (!resultItem.isEmpty()) {
                Minecraft.getInstance().setScreen(new CsLookItemScreen(resultItem, resultGrade));
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
            float tickVol = CsgoBox.CONFIG.sound.tickSoundVolume / 100F;
            if (tickVol > 0) {
                player.level().playSound(player, player.getX(), player.getY(), player.getZ(),
                        ModSounds.CS_DITA.get(), SoundSource.NEUTRAL, tickVol * 10F, 1F);
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
        if (CsgoBox.CONFIG == null) return 145;
        int base = CsgoBox.CONFIG.animation.totalAnimationTicks;
        int multiplier = CsgoBox.CONFIG.animation.animationSpeedMultiplier;
        int ticks = switch (CsgoBox.CONFIG.general.animationSpeed) {
            case SLOW -> base * 2;
            case FAST -> base / 2;
            default -> base;
        };
        return Math.clamp(ticks / Math.max(1, multiplier), 20, 500);
    }
}
