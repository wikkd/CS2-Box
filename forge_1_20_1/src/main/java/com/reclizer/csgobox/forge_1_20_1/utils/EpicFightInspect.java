package com.reclizer.csgobox.forge_1_20_1.utils;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Epic Fight battle-inspect preview: embeds Epic Fight's own
 * {@code ModelPreviewer} widget (the rigged-figure viewer their datapack
 * editor ships for weapon/animation previews) into the look screen. It shows
 * an EF biped figure holding the previewed weapon and plays the weapon
 * category's battle hold + attack combo animations, with the widget's own
 * drag/zoom camera.
 *
 * <p>Everything goes through reflection — zero compile-time Epic Fight
 * references, exactly like the TACZ/SBW/Create strategies. Any probe or
 * invocation failure degrades silently to the normal 3D preview. This class
 * only exists on the two platforms Epic Fight ships for (v1_21_1 NeoForge /
 * forge_1_20_1 Forge); EF's own method names are stable across both jars
 * (their production jar is SRG-named only for vanilla members, which the
 * remap pipeline rewrites wholesale), only vanilla API differs here.</p>
 */
public final class EpicFightInspect {

    private EpicFightInspect() {
    }

    /** Lowercased EF WeaponCategories that carry an animation set we can
     *  play (the EF weapon families; vanilla-tool categories stay out). */
    private static final Set<String> INSPECTABLE = Set.of(
            "greatsword", "longsword", "tachi", "uchigatana", "dagger", "spear", "fist");

    /** Animation ids (relative to the {@code epicfight:} namespace) per
     *  category, tried in order; each is existence-checked via EF's own
     *  checkNull before being queued. Names from the EF jar's
     *  animmodels/animations/ tree. */
    private static final Map<String, List<String>> ANIMATIONS = Map.of(
            "greatsword", List.of("biped/living/hold_greatsword", "biped/combat/greatsword_auto1",
                    "biped/combat/greatsword_auto2", "biped/combat/greatsword_dash"),
            "longsword", List.of("biped/living/hold_longsword", "biped/combat/longsword_auto1",
                    "biped/combat/longsword_auto2", "biped/combat/longsword_dash"),
            "tachi", List.of("biped/living/hold_tachi", "biped/combat/tachi_auto1",
                    "biped/combat/tachi_auto2", "biped/combat/tachi_dash"),
            "uchigatana", List.of("biped/living/hold_uchigatana", "biped/combat/uchigatana_auto1",
                    "biped/combat/uchigatana_auto2", "biped/combat/uchigatana_dash"),
            "dagger", List.of("biped/combat/dagger_auto1", "biped/combat/dagger_auto2",
                    "biped/combat/dagger_auto3", "biped/combat/dagger_dash"),
            "spear", List.of("biped/living/hold_spear", "biped/combat/spear_onehand_auto",
                    "biped/combat/spear_twohand_auto1", "biped/combat/spear_twohand_auto2",
                    "biped/combat/spear_dash"),
            "fist", List.of("biped/combat/fist_auto1", "biped/combat/fist_auto2",
                    "biped/combat/fist_auto3", "biped/combat/fist_dash", "biped/combat/fist_airslash"));

    private static boolean probed;
    private static boolean available;
    private static Constructor<?> previewerCtor;
    private static Object armatureBiped;
    private static Object meshBiped;
    private static Object hSizing;
    private static Object vSizing;
    private static Object transparentClearColor;
    private static Method byKey;
    private static Method checkNull;
    private static Method itemCapability;
    private static Method weaponCategory;
    private static Method setItemToRender;
    private static Method addAnimationToPlay;
    private static Method enableCameraControl;
    private static Method setBackgroundClearColor;
    private static Method setFigureTexture;
    private static Method setActive;
    private static Method widgetTick;
    private static Method widgetDestroy;
    private static Object[] categoryConstants;

    private static AbstractWidget widget;
    private static boolean active;

    /** True when EF is loaded and the item is an EF weapon we can showcase
     *  (arms the look screen's inspect-character-model button). */
    public static boolean availableFor(ItemStack stack) {
        if (!ensureProbe() || stack.isEmpty()) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || !"epicfight".equals(id.getNamespace())) {
            return false;
        }
        return weaponCategoryName(stack) != null;
    }

    /** True while the EF battle inspect owns the preview area. */
    public static boolean isActive() {
        return active;
    }

    /** Builds and configures the EF previewer widget over the given rect.
     *  Returns false (and leaves nothing behind) when EF is absent or any
     *  reflection step fails. */
    public static boolean start(int x, int y, int width, int height, ItemStack weapon,
            ResourceLocation figureTexture) {
        if (!ensureProbe() || weapon.isEmpty()) {
            return false;
        }
        stop();
        try {
            String category = weaponCategoryName(weapon);
            if (category == null) {
                return false;
            }
            Object efWidget = previewerCtor.newInstance(x, y, width, height, hSizing, vSizing,
                    armatureBiped, meshBiped);
            // The EF biped mesh UV-maps the vanilla skin layout, so the
            // figure wears the inspecting player's own skin.
            if (figureTexture != null) {
                setFigureTexture.invoke(efWidget, figureTexture);
            }
            setBackgroundClearColor.invoke(efWidget, transparentClearColor);
            setActive.invoke(efWidget, true);
            boolean anyAnimation = false;
            for (String id : ANIMATIONS.get(category)) {
                Object accessor = byKey.invoke(null, "epicfight:" + id);
                if (accessor != null && (Boolean) checkNull.invoke(null, accessor)) {
                    addAnimationToPlay.invoke(efWidget, accessor);
                    anyAnimation = true;
                }
            }
            if (!anyAnimation) {
                widgetDestroy.invoke(efWidget);
                return false;
            }
            setItemToRender.invoke(efWidget, (Item) weapon.getItem());
            enableCameraControl.invoke(efWidget, true);
            widget = (AbstractWidget) efWidget;
            active = true;
            return true;
        } catch (Throwable t) {
            widget = null;
            active = false;
            return false;
        }
    }

    /** Tears the widget down (releases its offscreen target) and returns the
     *  screen to the normal 3D preview. */
    public static void stop() {
        if (widget != null) {
            try {
                widgetDestroy.invoke(widget);
            } catch (Throwable ignored) {
            }
            widget = null;
        }
        active = false;
    }

    /** Alias used on screen re-init / removal; same teardown. */
    public static void clear() {
        stop();
    }

    /** Drives the widget's animation ticker; a failing widget is torn down
     *  so the screen falls back to the normal preview. */
    public static void tick() {
        if (!active) {
            return;
        }
        try {
            widgetTick.invoke(widget);
        } catch (Throwable t) {
            stop();
        }
    }

    /** Renders the widget's offscreen figure into the preview area. */
    public static void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!active) {
            return;
        }
        try {
            widget.render(guiGraphics, mouseX, mouseY, partialTick);
        } catch (Throwable t) {
            stop();
        }
    }

    /** Mouse forwarding: clicks inside the widget rect go to its own camera
     *  controls, everything outside stays with the screen. */
    public static boolean mouseClicked(double mouseX, double mouseY, int button) {
        AbstractWidget w = liveWidget();
        if (w == null || !w.isMouseOver(mouseX, mouseY)) {
            return false;
        }
        try {
            return w.mouseClicked(mouseX, mouseY, button);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean mouseDragged(double mouseX, double mouseY, int button,
            double dragX, double dragY) {
        AbstractWidget w = liveWidget();
        if (w == null || !w.isMouseOver(mouseX, mouseY)) {
            return false;
        }
        try {
            return w.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean mouseReleased(double mouseX, double mouseY, int button) {
        AbstractWidget w = liveWidget();
        if (w == null || !w.isMouseOver(mouseX, mouseY)) {
            return false;
        }
        try {
            return w.mouseReleased(mouseX, mouseY, button);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 1.20.1 scroll signature (single scroll delta). */
    public static boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
        AbstractWidget w = liveWidget();
        if (w == null || !w.isMouseOver(mouseX, mouseY)) {
            return false;
        }
        try {
            return w.mouseScrolled(mouseX, mouseY, scrollY);
        } catch (Throwable t) {
            return false;
        }
    }

    private static AbstractWidget liveWidget() {
        if (!active) {
            return null;
        }
        return widget;
    }

    /** Resolves the item's EF WeaponCategory name, or null when the item is
     *  not an EF weapon from our set (custom extendable categories included). */
    private static String weaponCategoryName(ItemStack stack) {
        try {
            Object capability = itemCapability.invoke(null, stack);
            if (capability == null) {
                return null;
            }
            Object category = weaponCategory.invoke(capability);
            if (category == null) {
                return null;
            }
            for (Object constant : categoryConstants) {
                if (constant == category) {
                    String name = ((Enum<?>) constant).name().toLowerCase(Locale.ROOT);
                    return INSPECTABLE.contains(name) ? name : null;
                }
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object enumConstant(Class<?> cls, String name) {
        for (Object constant : cls.getEnumConstants()) {
            if (((Enum<?>) constant).name().equals(name)) {
                return constant;
            }
        }
        return null;
    }

    /** One-shot reflection probe; false when Epic Fight is not installed or
     *  its surface moved (silently unavailable either way). */
    private static boolean ensureProbe() {
        if (probed) {
            return available;
        }
        probed = true;
        try {
            Class<?> previewer = Class.forName(
                    "yesman.epicfight.client.gui.datapack.widgets.ModelPreviewer");
            Class<?> resizable = Class.forName(
                    "yesman.epicfight.client.gui.datapack.widgets.ResizableComponent");
            Class<?> hSizingCls = Class.forName(
                    "yesman.epicfight.client.gui.datapack.widgets.ResizableComponent$HorizontalSizing");
            Class<?> vSizingCls = Class.forName(
                    "yesman.epicfight.client.gui.datapack.widgets.ResizableComponent$VerticalSizing");
            Class<?> armatures = Class.forName("yesman.epicfight.gameasset.Armatures");
            Class<?> meshes = Class.forName("yesman.epicfight.api.client.model.Meshes");
            Class<?> animationManager = Class.forName(
                    "yesman.epicfight.api.animation.AnimationManager");
            Class<?> capabilities = Class.forName(
                    "yesman.epicfight.world.capabilities.EpicFightCapabilities");
            Class<?> capabilityItem = Class.forName(
                    "yesman.epicfight.world.capabilities.item.CapabilityItem");
            Class<?> accessor = Class.forName("yesman.epicfight.api.asset.AssetAccessor");
            Class<?> vec4f = Class.forName("yesman.epicfight.api.utils.math.Vec4f");

            previewerCtor = previewer.getConstructor(int.class, int.class, int.class, int.class,
                    hSizingCls, vSizingCls, accessor, accessor);
            hSizing = enumConstant(hSizingCls, "LEFT_WIDTH");
            vSizing = enumConstant(vSizingCls, "TOP_HEIGHT");
            armatureBiped = armatures.getField("BIPED").get(null);
            meshBiped = meshes.getField("BIPED").get(null);
            transparentClearColor = vec4f.getConstructor().newInstance();
            byKey = animationManager.getMethod("byKey", String.class);
            checkNull = animationManager.getMethod("checkNull", accessor);
            itemCapability = capabilities.getMethod("getItemStackCapability", ItemStack.class);
            weaponCategory = capabilityItem.getMethod("getWeaponCategory");
            setItemToRender = previewer.getMethod("setItemToRender", Item.class);
            addAnimationToPlay = previewer.getMethod("addAnimationToPlay", accessor);
            // Upstream spelling ("Controll") is part of the API.
            enableCameraControl = previewer.getMethod("enableCameraControll", boolean.class);
            setBackgroundClearColor = previewer.getMethod("setBackgroundClearColor", vec4f);
            setFigureTexture = previewer.getMethod("setFigureTexture", ResourceLocation.class);
            setActive = resizable.getMethod("_setActive", boolean.class);
            widgetTick = previewer.getMethod("_tick");
            widgetDestroy = previewer.getMethod("onDestroy");
            Class<?> categories = Class.forName(
                    "yesman.epicfight.world.capabilities.item.CapabilityItem$WeaponCategories");
            categoryConstants = categories.getEnumConstants();
            available = hSizing != null && vSizing != null
                    && armatureBiped != null && meshBiped != null
                    && categoryConstants != null;
        } catch (Throwable t) {
            available = false;
        }
        return available;
    }
}
