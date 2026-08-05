package com.reclizer.csgobox.v1_21_8.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.reclizer.csgobox.v1_21_8.CsgoBox;
import com.reclizer.csgobox.v1_21_8.item.ItemCsgoBox;
import com.reclizer.csgobox.v1_21_8.packet.PacketCsgoProgress;
import com.reclizer.csgobox.v1_21_8.packet.PacketRequestBoxItems;
import com.reclizer.csgobox.v1_21_8.packet.PacketSyncBoxItems;
import com.reclizer.csgobox.utils.GuiRegion;
import com.reclizer.csgobox.utils.OverlayColor;
import com.reclizer.csgobox.v1_21_8.utils.GuiItemMove;
import com.reclizer.csgobox.v1_21_8.utils.IconListTools;
import com.reclizer.csgobox.v1_21_8.utils.RenderFontTool;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class CsboxScreen extends Screen {
    private final Player entity;
    private final Level world;

    private boolean openClicked = false;
    private boolean boxEmpty = false;



    private float itemRotX;
    private float itemRotY;

    private Map<ItemStack, Integer> itemGroup;

    private List<ItemStack> itemsList;
    private List<Integer> gradeList;

    private ResourceLocation keyRl;
    private final long syncRequestId;
    private Optional<ResourceLocation> expectedBoxId = Optional.empty();

    public CsboxScreen() {
        super(Component.literal("cs_screen"));
        this.minecraft = Minecraft.getInstance();
        this.itemGroup = new LinkedHashMap<>();
        this.itemsList = new ArrayList<>();
        this.gradeList = new ArrayList<>();
        this.openClicked = true;
        this.syncRequestId = ThreadLocalRandom.current().nextLong();

        if (this.minecraft.player != null) {
            this.entity = this.minecraft.player;
            this.world = entity.level();
            this.itemMenu = this.minecraft.player.getItemInHand(InteractionHand.MAIN_HAND);
            this.expectedBoxId = Optional.ofNullable(ItemCsgoBox.getBoxId(this.itemMenu));
            ClientPacketDistributor.sendToServer(new PacketRequestBoxItems(this.syncRequestId));
        } else {
            this.entity = null;
            this.world = null;
            this.itemMenu = ItemStack.EMPTY;
        }
    }

    private ItemStack itemKey;
    private ItemStack itemMenu;

    private int actionButtonWidth() {
        return Math.max(64, this.width * 7 / 100);
    }

    private int openButtonX() {
        return Math.max(8, this.width - actionButtonWidth() * 2 - 20);
    }

    private int backButtonX() {
        return openButtonX() + actionButtonWidth() + 8;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static List<ItemStack> itemsListProgress(Map<ItemStack, Integer> itemList) {
        int maxGrade = itemList.values().stream().max(Integer::compareTo).orElse(5);
        List<ItemStack> itemStacks = new ArrayList<>();
        for (int i = 1; i <= maxGrade; i++) {
            for (Map.Entry<ItemStack, Integer> entry : itemList.entrySet()) {
                if (entry.getValue() == i) {
                    itemStacks.add(entry.getKey());
                }
            }
        }
        return itemStacks;
    }

    private static List<Integer> gradeListProgress(Map<ItemStack, Integer> itemList) {
        int maxGrade = itemList.values().stream().max(Integer::compareTo).orElse(5);
        List<Integer> itemStacks = new ArrayList<>();
        for (int i = 1; i <= maxGrade; i++) {
            for (Map.Entry<ItemStack, Integer> entry : itemList.entrySet()) {
                if (entry.getValue() == i) {
                    itemStacks.add(i);
                }
            }
        }
        return itemStacks;
    }

    private static final int ITEMS_PER_PAGE = 20;

    private int page;

    private int renderableCount() {
        int count = 0;
        for (int i = 0; i < itemsList.size(); i++) {
            if (gradeList.get(i) > 4) break;
            count++;
        }
        return count;
    }

    private int pageCount() {
        int n = renderableCount();
        return Math.max(1, (n + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE);
    }

    private int boxKeyCount;


    private int countKeys() {
        int total = 0;
        if (keyRl != null && this.entity != null) {
            for (int i = 0; i < 36; i++) {
                ItemStack stack = entity.getInventory().getItem(i);
                if (keyRl.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                    total += stack.getCount();
                }
            }
        }
        return total;
    }

    @Override
    public void renderBackground(GuiGraphics pGuiGraphics, int pMouseX, int pMouseY, float pPartialTick) {
        if (this.minecraft != null && this.minecraft.level != null) {
            pGuiGraphics.fillGradient(0, 0, this.width, this.height, OverlayColor.getBackgroundColor(), OverlayColor.getBackgroundColor());
        } else {
            super.renderBackground(pGuiGraphics, pMouseX, pMouseY, pPartialTick);
        }
    }

    @Override
    public boolean mouseDragged(double pMouseX, double pMouseY, int pButton, double pDragX, double pDragY) {
        boolean isInRange = (pMouseX >= this.width * 37F / 100 && pMouseX <= this.width * 37F / 100 + 200)
                && (pMouseY >= this.height * 12F / 100 && pMouseY <= this.height * 12F / 100 + 176);
        if (pButton == 0 && isInRange) {
            this.itemRotX = GuiItemMove.renderRotAngleX(pDragX, this.itemRotX);
            this.itemRotY = GuiItemMove.renderRotAngleY(pDragY, this.itemRotY);
        }
        return super.mouseDragged(pMouseX, pMouseY, pButton, pDragX, pDragY);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        this.renderBg(guiGraphics, partialTicks, mouseX, mouseY);
        this.renderLabels(guiGraphics, mouseX, mouseY);
    }

    protected void renderBg(GuiGraphics guiGraphics, float partialTicks, int gx, int gy) {
        GuiRegion.Region listArea = GuiRegion.list(this.width, this.height);
        guiGraphics.fill(listArea.x(), listArea.y(), listArea.right(), listArea.y() + 1, OverlayColor.divider());
        GuiRegion.Region footer = GuiRegion.fullWidthRow(this.width, this.height, 92, 1);
        guiGraphics.fill(footer.x(), footer.y(), footer.right(), footer.bottom(), OverlayColor.divider());

        int FrameWidth = width * 26 / 100;
        float scale = FrameWidth / 16F;
        if (this.entity != null) {
            GuiItemMove.renderItemInInventoryFollowsMouse(guiGraphics, this.width * 37 / 100, this.height * 12 / 100,
                    this.itemRotX, this.itemRotY, itemMenu, this.entity, scale);
        }

        int x = 0;
        int y = 0;

        if (this.entity != null) {
            int startIdx = this.page * ITEMS_PER_PAGE;
            for (int i = startIdx; i < Math.min(itemsList.size(), startIdx + ITEMS_PER_PAGE); i++) {
                int py = 55;
                int px = i - startIdx;
                if (px > 9) {
                    py = 73;
                    px -= 10;
                }
                ItemStack itemStack1 = itemsList.get(i);
                int grade = gradeList.get(i);
                x = px;
                y = py;
                if (grade > 4) break;
                IconListTools.renderItemFrame(this.entity, guiGraphics, itemStack1,
                        listArea.x() + px * GuiRegion.pctW(this.width, 9),
                        GuiRegion.pctH(this.height, py), this.width, this.height, grade);
            }
            if (!gradeList.isEmpty() && gradeList.get(gradeList.size() - 1) > 4
                    && this.page == pageCount() - 1) {
                IconListTools.renderItemFrame(this.entity, guiGraphics, ItemStack.EMPTY,
                        listArea.x() + x * GuiRegion.pctW(this.width, 9),
                        GuiRegion.pctH(this.height, y), this.width, this.height, 5);
            }
        }

        if (itemKey != null) {
            IconListTools.renderGuiItem(this.entity, guiGraphics, itemKey,
                    this.width * 25F / 100, this.height * 93F / 100, 1);
        }

        drawButton(guiGraphics, openButtonX(), this.height * 94 / 100,
                actionButtonWidth(), this.height * 5 / 100, 0xFF00AA00, 0xFF00FF00);
        drawButton(guiGraphics, backButtonX(), this.height * 94 / 100,
                actionButtonWidth(), this.height * 5 / 100, 0xFFAA0000, 0xFFFF0000);
    }

    private void drawButton(GuiGraphics guiGraphics, int x, int y, int w, int h, int fillColor, int borderColor) {
        guiGraphics.fill(x, y, x + w, y + h, borderColor);
        guiGraphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, fillColor);
    }

    @Override
    public boolean keyPressed(int key, int b, int c) {
        if (key == 256) {
            this.minecraft.player.closeContainer();
            this.minecraft.options.hideGui = false;
            return true;
        }
        return super.keyPressed(key, b, c);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (verticalAmount != 0 && pageCount() > 1) {
            int target = this.page + (verticalAmount > 0 ? -1 : 1);
            if (target >= 0 && target < pageCount()) {
                this.page = target;
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        Style style = Style.EMPTY.withBold(true);
        int x = 0;
        int y = 0;
        boolean showNames = CsgoBox.CONFIG.showItemNames();

        int startIdx = this.page * ITEMS_PER_PAGE;
        for (int i = startIdx; i < Math.min(itemsList.size(), startIdx + ITEMS_PER_PAGE); i++) {
            int py = 67;
            int px = i - startIdx;
            if (px > 9) {
                py = 85;
                px -= 10;
            }
            ItemStack itemStack1 = itemsList.get(i);
            int grade = gradeList.get(i);
            x = px;
            y = py;
            if (grade > 4) break;
            if (showNames) {
                Component component = itemStack1.getItem().getName(itemStack1);
                FormattedCharSequence pText = component.getVisualOrderText();
                renderText(guiGraphics, pText, this.width * 4F / 100 + px * this.width * 9F / 100, this.height * py / 100F, 0.6F);
            }
        }
        if (showNames) {
            renderText(guiGraphics, Component.translatable("gui.csgobox.csgo_box.label_gold").getVisualOrderText(),
                    this.width * 4 / 100F + x * this.width * 9 / 100F,
                    this.height * y / 100F, 0.6F);
        }
        if (pageCount() > 1) {
            renderText(guiGraphics, Component.literal((this.page + 1) + "/" + pageCount()).getVisualOrderText(),
                    this.width * 88 / 100F, this.height * 54 / 100F, 0.6F);
        }

        renderText(guiGraphics, Component.translatable("gui.csgobox.csgo_box.label_box").getVisualOrderText(),
                this.width * 46F / 100F, this.height * 13F / 100F, 0.8F);
        // Box name rendered directly via RenderFontTool (not the local renderText
        // helper, which hardcodes a gray fallback) so that an optional 0xRRGGBB
        // color configured on the box's name style is honored. Without an
        // explicit color, the title falls back to 0xFFD3D3D3 — the previous
        // visual baseline.
        Component boxName = itemMenu.getItem().getName(itemMenu);
        int titleColor = 0xFFD3D3D3;
        net.minecraft.network.chat.TextColor tc = boxName.getStyle().getColor();
        if (tc != null) {
            titleColor = 0xFF000000 | (tc.getValue() & 0xFFFFFF);
        }
        RenderFontTool.drawString(guiGraphics, this.font, boxName.getVisualOrderText(),
                this.width * 50F / 100F, this.height * 13F / 100F, 0, 0, 0.8F, titleColor);

        if (itemKey != null && !itemKey.isEmpty()) {
            if (boxKeyCount > 0) {
                String count = " \u00D7 " + boxKeyCount;
                renderText(guiGraphics, Component.literal(count).getVisualOrderText(), this.width * 28F / 100F, this.height * 94F / 100F, 0.8F);
            } else {
                renderText(guiGraphics, Component.translatable("gui.csgobox.csgo_box.label_open").getVisualOrderText(),
                        this.width * 28F / 100F, this.height * 94F / 100F, 0.8F);
                renderText(guiGraphics, Component.translatable("gui.csgobox.csgo_box.label_open_1").getVisualOrderText(),
                        this.width * 40F / 100F, this.height * 94F / 100F, 0.8F);
                renderText(guiGraphics, itemKey.getItem().getName(itemKey).getVisualOrderText(),
                        this.width * 35F / 100F, this.height * 94F / 100F, 0.8F);
            }
        }

        renderText(guiGraphics, Component.translatable("gui.csgobox.csgo_box.label_items").withStyle(style).getVisualOrderText(),
                this.width * 3F / 100F, this.height * 50.3F / 100F, 0.8F);

        renderText(guiGraphics, Component.translatable("gui.csgobox.csgo_box.title").withStyle(style).getVisualOrderText(),
                middleOf(I18n.get("gui.csgobox.csgo_box.title"), 2), this.height * 5.9F / 100F, 2F);

        if (boxEmpty) {
            Component warnText = Component.translatable("gui.csgobox.csgo_box.label_not_configured");
            FormattedCharSequence warnSeq = warnText.getVisualOrderText();
            float warnWidth = this.font.width(warnSeq) * 1.2F;
            int bgX0 = Math.max(8, (int) ((this.width - warnWidth) / 2.0F) - 8);
            int bgX1 = Math.min(this.width - 8, (int) ((this.width + warnWidth) / 2.0F) + 8);
            int bgY0 = this.height * 23 / 100 - 6;
            int bgY1 = bgY0 + (int) (this.font.lineHeight * 1.2F) + 10;
            guiGraphics.fill(bgX0, bgY0, bgX1, bgY1, 0xAA101010);
            RenderFontTool.drawString(guiGraphics, this.font, warnSeq,
                    (this.width - warnWidth) / 2.0F, bgY0 + 5, 0, 0, 1.2F, 0xFFFF4444);
        }

        renderCenteredText(guiGraphics, Component.translatable("gui.csgobox.csgo_box.open_box").withStyle(style).getVisualOrderText(),
                openButtonX(), this.height * 94 / 100, actionButtonWidth(), this.height * 5 / 100, 0.8F);
        renderCenteredText(guiGraphics, Component.translatable("gui.csgobox.csgo_box.back_box").withStyle(style).getVisualOrderText(),
                backButtonX(), this.height * 94 / 100, actionButtonWidth(), this.height * 5 / 100, 0.8F);
    }

    private float middleOf(String text, float scale) {
        return (this.width - font.width(text) * scale) * 0.5F;
    }

    private void renderText(GuiGraphics guiGraphics, FormattedCharSequence pText, float px, float py, float scale) {
        RenderFontTool.drawString(guiGraphics, this.font, pText, px, py, 0, 0, scale, 0xFFD3D3D3);
    }

    private void renderCenteredText(GuiGraphics guiGraphics, FormattedCharSequence text,
                                    int x, int y, int w, int h, float scale) {
        float textW = this.font.width(text) * scale;
        float textX = x + (w - textW) / 2.0F;
        float textY = y + (h - this.font.lineHeight * scale) / 2.0F + 1;
        RenderFontTool.drawString(guiGraphics, this.font, text, textX, textY, 0, 0, scale, 0xFFD3D3D3);
    }

    @Override
    public final void tick() {
        super.tick();
        if (this.minecraft == null) return;
        if (this.minecraft.player == null) return;
        if (this.minecraft.player.isAlive() && !this.minecraft.player.isRemoved()) {
            this.containerTick();
        } else {
            this.minecraft.player.closeContainer();
        }
    }

    public void containerTick() {
        var data = PacketSyncBoxItems.consumeMatching(this.syncRequestId, this.expectedBoxId);
        if (data != null) {
            this.itemGroup = buildItemGroup(data);
            this.itemsList = itemsListProgress(this.itemGroup);
            this.gradeList = gradeListProgress(this.itemGroup);
            this.itemKey = data.keyItem();
            if (data.keyItem() != null && !data.keyItem().isEmpty()) {
                this.keyRl = BuiltInRegistries.ITEM.getKey(data.keyItem().getItem());
            }
            this.openClicked = this.itemGroup.isEmpty();
            this.boxEmpty = this.itemGroup.isEmpty();
            this.boxKeyCount = countKeys();
            this.page = 0;
        }
    }

    private Map<ItemStack, Integer> buildItemGroup(PacketSyncBoxItems.BoxData data) {
        Map<ItemStack, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < data.items().size(); i++) {
            map.put(data.items().get(i), data.grades().get(i));
        }
        return map;
    }



    @Override
    public void init() {
        super.init();
    }

    @Override
    public boolean mouseClicked(double pMouseX, double pMouseY, int pButton) {
        if (pButton == 0) {
            int openX = openButtonX();
            int openY = this.height * 94 / 100;
            int openW = actionButtonWidth();
            int openH = this.height * 5 / 100;
            if (pMouseX >= openX && pMouseX <= openX + openW && pMouseY >= openY && pMouseY <= openY + openH) {
                if (this.entity != null) {
                    if (!openClicked && entity.getMainHandItem().getItem() instanceof ItemCsgoBox) {
                        ResourceLocation keyRl = this.keyRl;
                        boolean canOpen = true;
                        if (keyRl != null && !keyRl.equals(ResourceLocation.parse("minecraft:air"))) {
                            canOpen = false;
                            for (int i = 0; i < 36; i++) {
                                ItemStack stack = entity.getInventory().getItem(i);
                                if (keyRl.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                                    canOpen = true;
                                    break;
                                }
                            }
                        }
                        if (canOpen) {
                            long openRequestId = ThreadLocalRandom.current().nextLong();
                            // Request id only matches the later server result to this animation.
                            Minecraft.getInstance().setScreen(new CsboxProgressScreen(entity, openRequestId));
                            ClientPacketDistributor.sendToServer(new PacketCsgoProgress(openRequestId));
                            openClicked = true;
                        }
                    }
                }
                return true;
            }

            int backX = backButtonX();
            int backY = this.height * 94 / 100;
            int backW = actionButtonWidth();
            int backH = this.height * 5 / 100;
            if (pMouseX >= backX && pMouseX <= backX + backW && pMouseY >= backY && pMouseY <= backY + backH) {
                if (this.minecraft != null && this.minecraft.player != null) {
                    this.minecraft.player.closeContainer();
                    this.minecraft.options.hideGui = false;
                }
                return true;
            }
        }
        return super.mouseClicked(pMouseX, pMouseY, pButton);
    }

    @Override
    public void onClose() {
        super.onClose();
    }
}
