package com.reclizer.csgobox.gui.client;

import com.reclizer.csgobox.capability.CsboxPlayerData;
import com.reclizer.csgobox.capability.ModCapability;
import com.reclizer.csgobox.item.ItemCsgoBox;
import com.reclizer.csgobox.packet.PacketGiveItem;
import com.reclizer.csgobox.sounds.ModSounds;
import com.reclizer.csgobox.utils.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

public class CsboxProgressScreen extends Screen {
    private final Player player;
    private final Map<ItemStack, Integer> itemGroup;
    private long seed;
    private final Random rng;
    private final List<ItemStack> itemInput = new ArrayList<>();
    private final List<Integer> gradeInput = new ArrayList<>();

    private float startWidth;
    private boolean startSwitch = true;
    private int startTime = 0;
    private int openTime = 0;
    private float velocityLerp = 0;
    private float lastRenderWidth = 0F;
    private float renderWidthAdd = 0F;
    private final float randomWidth;
    private List<Float> velocityExport;
    private List<Float> renderExport;
    private static final int ANIMATION_TOTAL_TICKS = 145;

    private float soundWidthAdd = 0;
    private boolean soundSwitch = true;
    private static final Random randomForWidth = new Random();

    public CsboxProgressScreen(long seed, Map<ItemStack, Integer> itemGroup, Player player) {
        super(Component.literal("cs_progress"));
        this.seed = seed;
        this.itemGroup = itemGroup;
        this.player = player;
        this.rng = new Random(seed);
        this.randomWidth = randomForWidth.nextFloat() * (111F - 93.5F) + 93.5F;
    }

    @Override
    protected void init() {
        super.init();
        this.startWidth = this.width;
        this.velocityExport = renderCount();
    }

    private List<Float> renderCount() {
        List<Float> list = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            float time = i / 20F;
            float velocity = (1.6F * time + 0.8F) / (float) Math.pow(2, 1.5 * time - 5.2);
            if (velocity < 0.3) velocity = 0;
            list.add(velocity);
        }
        return list;
    }

    private List<Float> renderMove(List<Float> list) {
        List<Float> result = new ArrayList<>();
        float add = 0;
        for (int i = 0; i < list.size(); i++) {
            float velocity = list.get(i);
            add += startWidth / 173F * velocity;
            result.add(add);
        }
        return result;
    }

    private void renderGradeItems() {
        for (int i = 0; i < 50; i++) {
            int grade = RandomItem.randomItemsGrade(rng,
                    ItemCsgoBox.getRandom(player.getMainHandItem()), player);
            ItemStack itemStack = RandomItem.randomItems(rng, grade, itemGroup);
            gradeInput.add(grade);
            itemInput.add(itemStack);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        this.renderProgressBackground(guiGraphics);
        this.renderBg(guiGraphics, partialTicks);
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

        float progress = Math.min(1, partialTicks + velocityLerp);

        for (int i = 0; i < itemInput.size(); i++) {
            if (itemInput.get(i).isEmpty()) continue;
            ItemStack itemStack = itemInput.get(i);
            int grade = gradeInput.get(i);

            float itemX = this.width * randomWidth / 100F
                    + i * this.width * 20F / 100F
                    - Mth.lerp(progress, lastRenderWidth, widthNewAdd);

            IconListTools.renderItemProgress(player, guiGraphics, itemStack,
                    itemX, this.height * 37F / 100F,
                    this.width, this.height, grade);
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
        openTime++;

        if (openTime == 2) {
            renderGradeItems();
        }
        if (openTime < 2) return;

        if (startSwitch) {
            startSwitch = false;
            this.startWidth = this.width;
            this.renderExport = renderMove(this.velocityExport);

            PacketDistributor.sendToServer(new PacketGiveItem(seed));

            if (!itemInput.isEmpty() && !gradeInput.isEmpty()) {
                CsboxPlayerData data = player.getData(ModCapability.PLAYER_DATA);
                player.setData(ModCapability.PLAYER_DATA,
                        data.withItem(itemInput.get(45)).withGrade(gradeInput.get(45)));
            }
        }

        if (openTime < 5) return;

        if (startTime < ANIMATION_TOTAL_TICKS) {
            startTime++;
        }

        if (startTime == ANIMATION_TOTAL_TICKS) {
            if (!itemInput.isEmpty() && !gradeInput.isEmpty()) {
                Minecraft.getInstance().setScreen(new CsLookItemScreen());
            }
        }

        float velocity = velocityExport.get(startTime);
        velocityLerp = velocity / 35;
        renderWidthAdd = renderExport.get(startTime);

        float thresholdStart = startWidth * randomWidth / 100F - startWidth / 2;
        float thresholdEnd = thresholdStart + startWidth * 20F * 35 / 100F;

        if (renderWidthAdd > thresholdStart && renderWidthAdd < thresholdEnd) {
            soundWidthAdd += startWidth / 173F * velocity;
            if (soundWidthAdd > startWidth * 20F / 100F) {
                soundWidthAdd = 0;
                player.level().playSound(player, player.getX(), player.getY(), player.getZ(),
                        ModSounds.CS_DITA.get(), SoundSource.NEUTRAL, 10F, 1F);
            }
        } else if (renderWidthAdd >= thresholdEnd) {
            if (soundSwitch) {
                soundWidthAdd = 0;
                soundSwitch = false;
            }
            soundWidthAdd += startWidth / 173F * velocity;
            if (soundWidthAdd > startWidth * 20F / 100F) {
                soundWidthAdd = 0;
                player.level().playSound(player, player.getX(), player.getY(), player.getZ(),
                        ModSounds.CS_DITA.get(), SoundSource.NEUTRAL, 10F, 1F);
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
}
