package com.reclizer.csgobox.forge_1_20_1.utils;

import com.mojang.logging.LogUtils;
import com.reclizer.csgobox.utils.PreviewCameraFit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector4f;
import org.slf4j.Logger;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

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

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Lowercased EF WeaponCategories that carry an animation set we can
     *  play — every enum constant except {@code NOT_WEAPON} (2026-11-22:
     *  the vanilla/modded families joined the EF-own ones, so any weapon
     *  EF animates in game previews the same way here) and {@code FIST}
     *  (2026-11-25). FIST is what a mod declares to take its own weapon
     *  out of EF's weapon animation: SlashBlade Resharped ships
     *  {@code data/slashblade/capabilities/weapons/*.json} =
     *  {@code epicfight:fist} for every blade line (1.9.65 / 2.1.9 jars),
     *  the blades moving through their own MMD motion system instead. A
     *  battle inspect for such an item is bare-hand punches with the item
     *  glued to the hand — not the item's moves — and it takes the
     *  inspect button away from the item's own preview mode (SlashBlade:
     *  the rack pose toggle, the "inspect action incompatible with EF"
     *  report). */
    private static final Set<String> INSPECTABLE = Set.of(
            "greatsword", "longsword", "tachi", "uchigatana", "dagger", "spear",
            "sword", "axe", "pickaxe", "shovel", "hoe", "trident", "bow", "crossbow",
            "shield");

    /** Animation ids (relative to the {@code epicfight:} namespace) per
     *  category, tried in order; each is existence-checked via EF's own
     *  checkNull before being queued. Names from the EF jar's
     *  animmodels/animations/ tree. Sword/axe and the three tool families
     *  have no {@code hold_*} living pose — they open straight on their
     *  combat/dig animations ({@code dig_mainhand} is the mining swing,
     *  EF's own tool animation). */
    private static final Map<String, List<String>> ANIMATIONS = Map.ofEntries(
            Map.entry("greatsword", List.of("biped/living/hold_greatsword", "biped/combat/greatsword_auto1",
                    "biped/combat/greatsword_auto2", "biped/combat/greatsword_dash")),
            Map.entry("longsword", List.of("biped/living/hold_longsword", "biped/combat/longsword_auto1",
                    "biped/combat/longsword_auto2", "biped/combat/longsword_dash")),
            Map.entry("tachi", List.of("biped/living/hold_tachi", "biped/combat/tachi_auto1",
                    "biped/combat/tachi_auto2", "biped/combat/tachi_dash")),
            Map.entry("uchigatana", List.of("biped/living/hold_uchigatana", "biped/combat/uchigatana_auto1",
                    "biped/combat/uchigatana_auto2", "biped/combat/uchigatana_dash")),
            Map.entry("dagger", List.of("biped/combat/dagger_auto1", "biped/combat/dagger_auto2",
                    "biped/combat/dagger_auto3", "biped/combat/dagger_dash")),
            Map.entry("spear", List.of("biped/living/hold_spear", "biped/combat/spear_onehand_auto",
                    "biped/combat/spear_twohand_auto1", "biped/combat/spear_twohand_auto2",
                    "biped/combat/spear_dash")),
            Map.entry("sword", List.of("biped/combat/sword_auto1", "biped/combat/sword_auto2",
                    "biped/combat/sword_auto3", "biped/combat/sword_auto4",
                    "biped/combat/sword_airslash", "biped/combat/sword_dash")),
            Map.entry("axe", List.of("biped/combat/axe_auto1", "biped/combat/axe_auto2",
                    "biped/combat/axe_airslash", "biped/combat/axe_dash")),
            Map.entry("pickaxe", List.of("biped/living/dig_mainhand", "biped/combat/tool_dash")),
            Map.entry("shovel", List.of("biped/living/dig_mainhand", "biped/combat/tool_dash")),
            Map.entry("hoe", List.of("biped/living/dig_mainhand", "biped/combat/tool_dash")),
            Map.entry("trident", List.of("biped/combat/trident_auto1", "biped/combat/trident_auto2",
                    "biped/combat/trident_auto3")),
            Map.entry("bow", List.of("biped/combat/bow_aim_mid", "biped/combat/bow_shot_mid")),
            Map.entry("crossbow", List.of("biped/living/hold_crossbow", "biped/combat/crossbow_reload",
                    "biped/combat/crossbow_aim_mid", "biped/combat/crossbow_shot_mid")),
            Map.entry("shield", List.of("biped/living/shield_mainhand")));

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
    private static Object emptyCapability;
    private static Method setItemToRender;
    private static Method addAnimationToPlay;
    private static Method enableCameraControl;
    private static Method enableZoomingCamera;
    private static Method enableCameraMove;
    private static Method setBackgroundClearColor;
    private static Method setFigureTexture;
    private static Method setActive;
    private static Method widgetTick;
    private static Method widgetDestroy;
    private static Field zoomField;
    private static Object[] categoryConstants;
    private static Class<?> previewerClass;

    // Player-mode reflection (lazy): Epic Fight's non-vanilla modes
    // (EPICFIGHT = battle/mining) replace the first-person hand rendering, so
    // TACZ's native first-person inspect can never appear there — its draw
    // call leaves the state lock up and the caller's lock screen would trap
    // the player (the 2026-11-23 "TACZ inspect freezes in EF battle mode"
    // report). The first-person inspect session takes the player out of that
    // mode for its duration and puts it back afterwards, through the same
    // mode-change path EF's own toggle key uses. Any failure reads as "not in
    // EF mode", keeping the normal first-person flow.
    private static boolean modeProbeResolved;
    private static boolean modeSettersResolved;
    private static Method getPlayerPatch;
    private static Method isEpicFightMode;
    private static Method toVanillaMode;
    private static Method toEpicFightMode;

    // Fit-only reflection (lazy — a signature change here must not kill the
    // whole probe; fitCamera degrades step by step: runtime mesh envelope,
    // then the file-measured constants below, then FIT_ZOOM).
    private static boolean fitResolved;
    private static Method setCameraTransform;
    private static Method accessorGet;
    private static Method meshPositions;
    private static Method getAnimator;

    // Depth servo (lazy, same degradation philosophy): the queued combat
    // animations carry ROOT MOTION along the camera axis — the sword combo's
    // root travels ~0.76 units toward the camera at the first keyframe and
    // several units further mid-swing, against a camera only 2.9 units away,
    // so a static zoom either clips the figure behind the camera (blank
    // preview) or balloons it. The servo reads the animated root each frame
    // and rewrites zoom so the camera-to-figure distance stays constant: the
    // lunge still plays, framed in place.
    //
    // The measurement goes through the SAME call the renderer uses to build
    // the skinning matrix (Armature.getBoundTransformFor), so the number is
    // in the mesh's own space and no file-axis conversion has to be guessed.
    // The bind reference comes from an EMPTY Pose: a missing joint resolves
    // to JointTransform.empty() (identity), so the bound matrix of an empty
    // pose is exactly the joint's bind local transform — independent of what
    // the animator happens to be playing. Reading the reference off the
    // animator instead (getPose(0) at start) sampled the EMPTY_ANIMATION and
    // returned NaN, which NaN'd the zoom and killed every vertex (the
    // 2026-11-23 blank-preview round).
    //
    // The undo is along the axis the camera CURRENTLY looks down, not the
    // model's z: the widget's view is translate -> pitch(xRot) -> yaw(yRot),
    // so once the figure has been drag-rotated a model-z lunge is no longer
    // view-axial, and pinning model z then made the camera retreat from a
    // sideways lunge instead (the figure shrank and swelled through every
    // swing in a rotated view). The servo rotates the root into view space
    // and pins ITS depth. Lateral and vertical root travel stays visible by
    // design — the lunge still plays across the frame.
    private static boolean servoResolved;
    private static Object animator;
    private static Method animatorGetPose;
    private static Method armatureGetBoundTransform;
    private static Method matrixToTranslationVector;
    /** Unwrapped biped armature — Armatures.BIPED is an AssetAccessor, and
     *  getBoundTransformFor lives on the Armature it resolves to. */
    private static Object bipedArmature;
    private static Object rootJoint;
    private static Field vec3fX;
    private static Field vec3fY;
    private static Field vec3fZ;
    /** The previewer's live camera angles, read every frame (see the servo
     *  note above: the pinned axis depends on them). */
    private static Field widgetXRotField;
    private static Field widgetYRotField;
    private static double baseZoom;
    /** Root origin for an empty pose (x, y, z): the bind reference. */
    private static float[] bindRoot = {0F, 0F, 0F};
    private static float bindRootDepth;
    /** Camera-to-root distance for the view being shown: the swing fit
     *  tabulates one per yaw and the servo eases toward the current one, so
     *  dragging never pops the zoom. {@code baseZoom} is the same distance
     *  for the opening view and anchors the offset clamp. */
    private static PreviewCameraFit.Fit swingFit;
    private static double fitDistance;
    /** Uniform factor the figure's size cap puts on the swing table (1 when
     *  the fitted distances already keep the figure under
     *  {@link #FILL} of the item square): the servo scales every per-yaw
     *  distance it looks up by the same amount, so a drag-rotated view keeps
     *  the figure at the opening view's size. */
    private static double swingScale = 1.0D;
    /** One-shot confirmation that the servo sees real root motion (logged
     *  with the numbers it will act on, so a wrong axis or a stale bind
     *  reference shows up as data instead of another screenshot round). */
    private static boolean servoLogged;

    // Swing scan (lazy, same degradation philosophy): the fit used to frame
    // the biped's BIND envelope only, so the held item — hung on the Tool_R
    // joint, swinging well past that envelope at the apex of every combo —
    // left the frame (2026-11-23 report, wooden sword). The scan pre-rolls
    // every queued animation on a scratch AnimationPlayer (the live animator
    // is never touched) and collects the exact skinned body vertices plus
    // the held item's box carried through the same matrix chain
    // renderFigure draws it with. common PreviewCameraFit turns that cloud
    // into a camera distance per yaw. Any failure falls back to the bind
    // envelope fit above.
    private static final Map<String, PreviewCameraFit.Fit> SWING_CACHE = new HashMap<>();
    private static boolean scanResolved;
    private static Method animatorGetEntityPatch;
    private static Constructor<?> animationPlayerCtor;
    private static Method playerSetPlayAnimation;
    private static Method playerSetElapsedTime;
    private static Method playerGetCurrentPose;
    private static Method armatureGetPoseAsTransformMatrix;
    private static Method skinnedJointCounts;
    private static Method skinnedVertexPosition;
    private static Constructor<?> matrixCopyCtor;
    private static Constructor<?> vec3fCtor;
    private static Method matrixMulFront;
    private static Method transform3v;
    /** Tool_R joint of the biped armature (null on an armature without one). */
    private static Object toolJoint;
    /** The fixed item-to-joint offset renderFigure applies: translate(0, 0,
     *  -0.13) then rotate -90 degrees about X (bytecode-checked). */
    private static Object heldItemSpace;

    // Editor-checkbox strip (lazy, same degradation philosophy): the
    // previewer is EF's datapack-editor widget and hard-draws its three
    // debug toggles (collider / item / trail) inside the preview rect every
    // frame — there is no way to flag them off (renderWidget's re-anchor +
    // _renderWidget draw chain skips vanilla's `visible` guard, bytecode
    // checked). Every draw derives from _getWidth/_getHeight and the label
    // (glyph = fill of min(w,h), label drawn at that offset), so a stub
    // CheckBox constructed with zero size and an empty label renders
    // nothing, while _getValue()=TRUE keeps renderFigure's held-item gate
    // exactly as the defaults had it.
    private static boolean stripResolved;
    private static Constructor<?> checkBoxCtor;
    private static Field showItemCheckboxField;
    private static Field showColliderCheckboxField;
    private static Field showTrailCheckboxField;

    /** The figure the previewer renders is the biped SKIN mesh, whose bind
     *  envelope reaches well past the skeleton joint points — measured from
     *  the EF jar's animmodels/entity/biped.json 20.15.2 (2026-11-22, the
     *  torso close-up bug): joints span up-axis 0.386..1.514 while the
     *  skinned vertices span -0.019..2.025 (feet sole + crown stick out
     *  below and above the lowest/highest joints), i.e. 1.81x the joint
     *  span. Fitting to the joint envelope framed a torso close-up at every
     *  zoom. These are only the FALLBACK now — fitCamera measures the
     *  loaded mesh at runtime (StaticMesh.positions(), the same vertices
     *  this file was measured from) so an EF asset update re-fits itself;
     *  the runtime axis swap puts up on +Y. */
    private static final float BIPED_UP_SPAN = 2.0438f;
    private static final float BIPED_UP_CENTER = 1.0032f;
    private static final float BIPED_SIDE_SPAN = 1.0125f;

    /** The figure's size on screen, as a fraction of the ITEM SQUARE the
     *  battle inspect used to be drawn into ({@code itemSquareSize} of
     *  {@link #start}) — i.e. the size it had before the swing fit. On the
     *  FALLBACK path, where only the bind envelope is known, the figure is
     *  framed at exactly this: the remaining margin absorbs the queued
     *  swing's excursions, so a raised sword may still clip there by
     *  design. On the measured path it is a cap: the swing fit never shows
     *  the figure SMALLER than it has to, but never larger than this
     *  either. */
    private static final float FILL = 0.75f;

    /** Target fraction of the battle-inspect viewport the whole swing may
     *  reach on the measured path. The scan already contains the whole
     *  animation, weapon included, and the item rides as a loose box around
     *  its model, so the margin only has to cover the yaw table's 15-degree
     *  interpolation and the servo's easing — and every point of it is
     *  figure size given away: at 0.82 inside the old 144px item square the
     *  figure came out at half its previous size (2026-11-24 report), which
     *  is why the viewport is now the whole room above the toolbar. */
    private static final float SWING_FILL = 0.92f;

    /** The previewer's projection and opening view (bytecode-checked):
     *  setPerspective(50deg fovY, rect aspect, 0.05, 100), yRot = 180. */
    private static final double HALF_TANGENT = Math.tan(Math.toRadians(25.0));
    private static final double OPENING_YAW = 180.0;

    /** Swing scan cadence: one sample per game tick of animation, capped
     *  per clip so a broken or endless clip cannot stall the click. */
    private static final float SCAN_STEP = 0.05F;
    private static final float SCAN_MAX_SECONDS = 10.0F;
    /** Body vertices skinned per sample; larger meshes are strided. */
    private static final int SCAN_VERTEX_BUDGET = 4096;

    /** How far the servo may move the camera off the fitted zoom (units). */
    private static final double SERVO_LIMIT = 4.5D;

    /** Fallback camera distance when the camera-transform reflection fails,
     *  in item-square units: scaled to the viewport height like the figure's
     *  target size, so the figure keeps that size on this path too. */
    private static final double FIT_ZOOM = -3.2d;

    private static AbstractWidget widget;
    private static boolean active;

    /** True when EF is loaded and the item is an EF weapon we can showcase
     *  (arms the look screen's inspect-character-model button). 2026-11-22:
     *  no namespace gate — EF's default datapack hands capabilities to
     *  vanilla and modded weapons too (item_keyword matching), so any item
     *  carrying an inspectable EF WeaponCategory qualifies (FIST does not,
     *  see {@link #INSPECTABLE}). The one-click INFO lines are diagnosis
     *  aids for the arm/no-arm decision and are meant to be stripped once
     *  the feature is confirmed in-game. */
    public static boolean availableFor(ItemStack stack) {
        if (!ensureProbe() || stack.isEmpty()) {
            return false;
        }
        String category = weaponCategoryName(stack);
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (category != null && INSPECTABLE.contains(category)) {
            LOGGER.info("[csgobox] EF inspect armed: item={} category={}", id, category);
            return true;
        }
        LOGGER.info("[csgobox] EF inspect not armed: item={} category={}", id,
                category == null ? "none" : category);
        return false;
    }

    /** True while the EF battle inspect owns the preview area. */
    public static boolean isActive() {
        return active;
    }

    /** True while Epic Fight owns the player's first-person view (its
     *  EPICFIGHT player mode — battle or mining). In that mode EF renders
     *  the hand itself, so native first-person item animations such as
     *  TACZ's inspect never show up; the inspect session calls
     *  {@link #leaveBattleMode} to suspend it. A missing EF, missing patch
     *  or any reflection failure reads false, so the normal flow is never
     *  blocked by this probe. */
    public static boolean isEpicFightMode(Player player) {
        if (player == null) {
            return false;
        }
        try {
            Object patch = playerPatch(player);
            return patch != null && (Boolean) isEpicFightMode.invoke(patch);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Takes the player out of Epic Fight's player mode for a first-person
     *  inspect: EF renders the hand itself while the mode is active, so
     *  TACZ's native gun draw/inspect can never appear there. This is EF's
     *  own mode-change path ({@code toVanillaMode(true)} — the call its
     *  toggle key makes), so the server follows, other players see the
     *  switch, and EF's auto camera switch puts the view in first person.
     *  Returns true when the player really left the mode; callers pair that
     *  with {@link #restoreBattleMode} afterwards. False means EF is absent,
     *  the player was never in the mode, or another mod cancelled the
     *  change. */
    public static boolean leaveBattleMode(Player player) {
        if (!isEpicFightMode(player)) {
            return false;
        }
        try {
            resolveModeSetters();
            Object patch = playerPatch(player);
            if (patch == null) {
                return false;
            }
            toVanillaMode.invoke(patch, true);
        } catch (Throwable t) {
            LOGGER.info("[csgobox] EF battle mode: leave failed ({})", t.toString());
            return false;
        }
        boolean left = !isEpicFightMode(player);
        LOGGER.info("[csgobox] EF battle mode: left={} for first-person inspect", left);
        return left;
    }

    /** Puts the player back into Epic Fight's player mode after a
     *  first-person inspect. No-op unless {@link #leaveBattleMode} reported
     *  that it switched the mode, so vanilla-mode players never see a mode
     *  change they did not ask for. */
    public static void restoreBattleMode(Player player, boolean left) {
        if (!left || player == null) {
            return;
        }
        try {
            resolveModeSetters();
            Object patch = playerPatch(player);
            if (patch != null) {
                toEpicFightMode.invoke(patch, true);
                LOGGER.info("[csgobox] EF battle mode: restored after first-person inspect");
            }
        } catch (Throwable t) {
            LOGGER.info("[csgobox] EF battle mode: restore failed ({})", t.toString());
        }
    }

    /** Resolves the EF player-patch getter and the mode probe. Kept separate
     *  from {@link #resolveModeSetters} so an EF build without the setters
     *  still gets a working "is EF mode active" answer — leaveBattleMode
     *  then reports false and the inspect refuses instead of freezing. */
    private static Object playerPatch(Player player) throws ReflectiveOperationException {
        if (!modeProbeResolved) {
            getPlayerPatch = Class.forName("yesman.epicfight.world.capabilities.EpicFightCapabilities")
                    .getMethod("getPlayerPatch", Player.class);
            isEpicFightMode = Class.forName(
                            "yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch")
                    .getMethod("isEpicFightMode");
            modeProbeResolved = true;
        }
        return getPlayerPatch.invoke(null, player);
    }

    private static void resolveModeSetters() throws ReflectiveOperationException {
        if (modeSettersResolved) {
            return;
        }
        Class<?> patch = Class.forName(
                "yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch");
        toVanillaMode = patch.getMethod("toVanillaMode", boolean.class);
        toEpicFightMode = patch.getMethod("toEpicFightMode", boolean.class);
        modeSettersResolved = true;
    }

    /** Builds and configures the EF previewer widget over the given rect.
     *  {@code itemSquareSize} is the side of the item square the battle
     *  inspect used to be drawn into: the figure is never shown larger than
     *  {@link #FILL} of it (see fitCamera). Returns false (and leaves
     *  nothing behind) when EF is absent or any reflection step fails. */
    public static boolean start(int x, int y, int width, int height, ItemStack weapon,
            ResourceLocation figureTexture, int itemSquareSize) {
        if (!ensureProbe() || weapon.isEmpty()) {
            return false;
        }
        stop();
        try {
            String category = weaponCategoryName(weapon);
            if (category == null || !INSPECTABLE.contains(category)) {
                return false;
            }
            // EF's ctor signature is (x, WIDTH, y, HEIGHT) — width comes
            // before y (bytecode: the AbstractWidget super call receives
            // (p1, p3, p2, p4) while x1/x2/y1/y2 store p1/p2/p3/p4; EF's own
            // WeaponComboScreen passes insets the same way). The previewer
            // renders a window-sized FBO and squeezes it whole into this
            // rect, so the figure self-centers here, and its projection
            // takes the rect's own aspect (bytecode-checked), so the rect
            // may have any shape without distorting the figure. Passing
            // (x, y, w, h) instead lands the rect far down the screen with
            // width/height swapped — the 2026-11-22 oversized-figure bug.
            Object efWidget = previewerCtor.newInstance(x, width, y, height, hSizing, vSizing,
                    armatureBiped, meshBiped);
            stripEditorCheckboxes(efWidget);
            // The EF biped mesh UV-maps the vanilla skin layout, so the
            // figure wears the inspecting player's own skin.
            if (figureTexture != null) {
                setFigureTexture.invoke(efWidget, figureTexture);
            }
            setBackgroundClearColor.invoke(efWidget, transparentClearColor);
            setActive.invoke(efWidget, true);
            List<Object> queued = new ArrayList<>();
            boolean anyAnimation = false;
            for (String id : ANIMATIONS.get(category)) {
                Object accessor = byKey.invoke(null, "epicfight:" + id);
                // EF's checkNull is a failed-assertion guard: TRUE when the
                // accessor is null/empty (missing), FALSE for a valid loaded
                // animation (bytecode-checked 2026-11-22 — the name reads
                // like the opposite). byKey returns null for unknown keys.
                if (accessor != null && !(Boolean) checkNull.invoke(null, accessor)) {
                    addAnimationToPlay.invoke(efWidget, accessor);
                    queued.add(accessor);
                    anyAnimation = true;
                }
            }
            if (!anyAnimation) {
                LOGGER.info("[csgobox] EF inspect: no animations present for {} — not started", category);
                widgetDestroy.invoke(efWidget);
                return false;
            }
            setItemToRender.invoke(efWidget, (Item) weapon.getItem());
            enableCameraControl.invoke(efWidget, true);
            // EF maps scroll to camera zoom (+-0.5 per notch, clamp -0.5 =
            // torso close-up) and middle-drag to panning; either is too easy
            // to hit accidentally inside our look screen and there is no
            // reset. Keep left-drag rotation only — matches the drag-rotate
            // interaction of every other preview here.
            enableZoomingCamera.invoke(efWidget, false);
            enableCameraMove.invoke(efWidget, false);
            fitCamera(efWidget, (float) width / height, height, itemSquareSize, queued, weapon,
                    category);
            LOGGER.info("[csgobox] EF inspect start: item={} category={} rect=({},{} {}x{}) zoom={} win={}x{}",
                    ForgeRegistries.ITEMS.getKey(weapon.getItem()), category,
                    x, y, width, height, zoomField.get(efWidget),
                    Minecraft.getInstance().getWindow().getWidth(),
                    Minecraft.getInstance().getWindow().getHeight());
            widget = (AbstractWidget) efWidget;
            active = true;
            return true;
        } catch (Throwable t) {
            LOGGER.warn("[csgobox] EF inspect start failed: {}", t.toString());
            widget = null;
            active = false;
            return false;
        }
    }

    /** Fits the camera so the whole swing fills {@link #SWING_FILL} of the
     *  viewport, centered: the skinned figure plus the held item over
     *  every queued animation (see the swing scan field block), solved by
     *  common PreviewCameraFit against the previewer's own projection —
     *  setPerspective(50deg fovY, rect aspect, 0.05, 100) and the single
     *  translate {@code (xMove, yMove - 1, zoom)} (bytecode-checked). The
     *  distance is tabulated per camera yaw, so the servo can keep a
     *  drag-rotated view framed too; {@code yMove = 1 - centerY} and
     *  {@code xMove = 0}.
     *
     *  <p>The result is capped at the figure's target size ({@link #FILL} of
     *  {@code itemSquareSize}, see the constant): where the viewport offers
     *  more room than the swing needs, every fitted distance grows by the
     *  same factor until the figure is exactly that size — still framed
     *  with the whole swing, now with room to spare. The caller sizes the
     *  viewport to the room above the toolbar; on a coarsely scaled GUI
     *  that room is only a couple of hundred pixels tall, not enough for a
     *  swing at the target size, so there the swing decides and the figure
     *  stays under the target: a longsword raised overhead reaches half
     *  again the figure's height, and containing that costs figure size
     *  however large the viewport is.
     *
     *  <p>Without a scan (no servo, or the scan failed) the fit falls back
     *  to the biped's BIND envelope at exactly the target size: distance =
     *  max(up-span, side-span / aspect) * viewportHeight / (2*tan(25deg) *
     *  target), centered on it. That envelope is measured from the loaded
     *  mesh itself
     *  ({@code StaticMesh.positions()} — the exact vertices the skinning
     *  renders, see the constants above for the joint-vs-skin measurement
     *  bug this avoids) and only falls back to the file-measured constants
     *  when that reflection fails. Values go through EF's own public
     *  setCameraTransform; the widget re-reads the fields every frame, so
     *  the fit holds (scroll zoom and pan are disabled). If even the
     *  camera-write fails, {@link #FIT_ZOOM} keeps the figure roughly
     *  framed and the servo stays off (it would steer by a distance that
     *  was never applied). */
    private static void fitCamera(Object efWidget, float aspectParam, int viewportHeight,
            int itemSquareSize, List<Object> queued, ItemStack weapon, String category) {
        float[] bindBox = null;
        String source = "fallback-constants";
        try {
            if (!fitResolved) {
                setCameraTransform = previewerClass.getMethod("setCameraTransform",
                        double.class, float.class, float.class, float.class, float.class);
                accessorGet = Class.forName("yesman.epicfight.api.asset.AssetAccessor")
                        .getMethod("get");
                meshPositions = Class.forName("yesman.epicfight.api.client.model.StaticMesh")
                        .getMethod("positions");
                getAnimator = previewerClass.getMethod("getAnimator");
                fitResolved = true;
            }
            bindBox = measureEnvelope();
            if (bindBox != null) {
                source = "runtime-mesh";
            }
        } catch (Throwable t) {
            LOGGER.info("[csgobox] EF inspect fit degraded to constants: {}", t.toString());
        }
        if (bindBox == null) {
            float half = BIPED_SIDE_SPAN / 2.0f;
            bindBox = new float[]{-half, BIPED_UP_CENTER - BIPED_UP_SPAN / 2.0f, -half,
                    half, BIPED_UP_CENTER + BIPED_UP_SPAN / 2.0f, half};
        }
        double upSpan = bindBox[4] - bindBox[1];
        double sideSpan = Math.max(bindBox[3] - bindBox[0], bindBox[5] - bindBox[2]);
        // Rect dims come from our own layout (never <= 0 in practice); the
        // clamp only stops a pathological aspect from dividing wild.
        double aspect = Math.max(0.25f, aspectParam);
        double square = Math.max(1, itemSquareSize);
        try {
            resolveServo(efWidget);
            swingFit = servoResolved ? measureSwing(queued, weapon, bindBox, category, aspect) : null;
            swingScale = 1.0D;
            double centerY;
            double targetPx = FILL * square;
            if (swingFit != null) {
                fitDistance = swingFit.distanceAt(OPENING_YAW);
                centerY = swingFit.centerY();
                double figurePx = figurePixels(upSpan, viewportHeight, fitDistance);
                if (figurePx > targetPx) {
                    swingScale = figurePx / targetPx;
                    fitDistance *= swingScale;
                }
            } else {
                fitDistance = Math.max(upSpan, sideSpan / aspect) * viewportHeight
                        / (2.0 * HALF_TANGENT * targetPx);
                centerY = (bindBox[1] + bindBox[4]) / 2.0;
            }
            // Measure the distance from the ROOT's plane, not the origin:
            // the bind root sits off the origin along the camera axis, so a
            // raw -dist frames the figure from a distance that is off by
            // that much (a visible size error). With the bind reference in
            // hand the bind pose lands exactly at `dist`.
            baseZoom = bindRootDepth - fitDistance;
            setCameraTransform.invoke(efWidget, baseZoom, 0.0f, (float) OPENING_YAW, 0.0f,
                    (float) (1.0 - centerY));
            LOGGER.info("[csgobox] EF inspect fit: source={} servo={} swing={} dist={} center={} aspect={} viewport={}px figure={}px (target {}px) zoom={} bind={}",
                    source, servoResolved, swingFit != null, fitDistance, centerY, aspectParam,
                    viewportHeight, figurePixels(upSpan, viewportHeight, fitDistance), targetPx,
                    baseZoom, bindRootDepth);
        } catch (Throwable t) {
            swingFit = null;
            servoResolved = false;
            LOGGER.info("[csgobox] EF inspect fit failed: {}", t.toString());
            try {
                zoomField.setDouble(efWidget, FIT_ZOOM * viewportHeight / square);
            } catch (Throwable ignored) {
            }
        }
    }

    /** On-screen height (GUI px) of a model-space {@code span} at the given
     *  camera distance: the previewer's 50-degree fovY spreads
     *  {@code 2 * tan(25deg) * distance} of model space over the viewport
     *  height. */
    private static double figurePixels(double span, int viewportHeight, double distance) {
        return span * viewportHeight / (2.0 * HALF_TANGENT * distance);
    }

    /** Pre-rolls the queued animations and fits the camera to everything
     *  they reach (see the swing scan field block). Returns null — the
     *  caller then keeps the bind-envelope fit — when any step fails. The
     *  result is cached per item, category and rect aspect: the envelope
     *  depends on the animation set, the item's model and nothing else. */
    private static PreviewCameraFit.Fit measureSwing(List<Object> queued, ItemStack weapon,
            float[] bindBox, String category, double aspect) {
        String cacheKey = category + "|" + ForgeRegistries.ITEMS.getKey(weapon.getItem())
                + "|" + Math.round(aspect * 100.0);
        PreviewCameraFit.Fit cached = SWING_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        try {
            long scanStart = System.nanoTime();
            resolveScan();
            Object patch = animatorGetEntityPatch.invoke(animator);
            // Scratch player: setPlayAnimation + setElapsedTime + getCurrentPose
            // is the very call chain the live layer uses (Layer.getEnabledPose
            // -> AnimationPlayer.getCurrentPose), so the samples are the poses
            // the widget will render — without touching its animator.
            Object player = animationPlayerCtor.newInstance();
            Object mesh = accessorGet.invoke(meshBiped);
            int vertices = ((int[]) skinnedJointCounts.invoke(mesh)).length;
            int stride = Math.max(1, (vertices + SCAN_VERTEX_BUDGET - 1) / SCAN_VERTEX_BUDGET);
            // The held item's geometry in the space renderFigure consumes:
            // same context, same hand flag, model resolved the same way
            // (overrides with no level/entity), probe-rendered at identity.
            float[] box = toolJoint == null ? null : AnimRenderOps.displayContextBox(weapon,
                    Minecraft.getInstance().getItemRenderer().getModel(weapon, null, null, 0),
                    ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, false);
            Object[] corners = null;
            if (box != null) {
                corners = new Object[8];
                for (int i = 0; i < 8; i++) {
                    corners[i] = vec3fCtor.newInstance((i & 1) == 0 ? box[0] : box[3],
                            (i & 2) == 0 ? box[1] : box[4], (i & 4) == 0 ? box[2] : box[5]);
                }
            }
            Object cornerOut = vec3fCtor.newInstance(0F, 0F, 0F);
            Vector4f vertexOut = new Vector4f();
            PreviewCameraFit.Cloud cloud = new PreviewCameraFit.Cloud();
            // The bind pose is the reference the servo pins to.
            for (int i = 0; i < 8; i++) {
                cloud.add((i & 1) == 0 ? bindBox[0] : bindBox[3], (i & 2) == 0 ? bindBox[1] : bindBox[4],
                        (i & 4) == 0 ? bindBox[2] : bindBox[5], bindRoot[0], bindRoot[2]);
            }
            for (Object accessor : queued) {
                Object animation = accessorGet.invoke(accessor);
                float total = (Float) animation.getClass().getMethod("getTotalTime").invoke(animation);
                if (!(total > 0.0F)) {
                    continue;
                }
                total = Math.min(total, SCAN_MAX_SECONDS);
                int steps = Math.max(1, (int) Math.ceil(total / SCAN_STEP));
                playerSetPlayAnimation.invoke(player, accessor);
                for (int step = 0; step <= steps; step++) {
                    playerSetElapsedTime.invoke(player, Math.min(total, step * SCAN_STEP));
                    Object pose = playerGetCurrentPose.invoke(player, patch, 0F);
                    Object[] matrices = (Object[]) armatureGetPoseAsTransformMatrix
                            .invoke(bipedArmature, pose, false);
                    float[] root = readJointPosition(pose, rootJoint);
                    for (int v = 0; v < vertices; v += stride) {
                        skinnedVertexPosition.invoke(mesh, v, vertexOut, matrices);
                        cloud.add(vertexOut.x, vertexOut.y, vertexOut.z, root[0], root[2]);
                    }
                    if (corners != null) {
                        // Same chain as renderFigure: bound(Tool_R) x item space.
                        Object held = matrixCopyCtor.newInstance(heldItemSpace);
                        matrixMulFront.invoke(held,
                                armatureGetBoundTransform.invoke(bipedArmature, pose, toolJoint));
                        for (Object corner : corners) {
                            transform3v.invoke(null, held, corner, cornerOut);
                            cloud.add((Float) vec3fX.get(cornerOut), (Float) vec3fY.get(cornerOut),
                                    (Float) vec3fZ.get(cornerOut), root[0], root[2]);
                        }
                    }
                }
            }
            if (cloud.count() < 16) {
                LOGGER.info("[csgobox] EF inspect swing scan produced no samples; keeping the bind fit");
                return null;
            }
            PreviewCameraFit.Fit fit = PreviewCameraFit.fit(cloud, HALF_TANGENT, aspect, SWING_FILL,
                    OPENING_YAW);
            if (!(fit.distanceAt(OPENING_YAW) > 0.5) || fit.maxDistance() > 40.0
                    || Double.isNaN(fit.centerY())) {
                LOGGER.info("[csgobox] EF inspect swing scan unusable: dist={} max={} center={}; keeping the bind fit",
                        fit.distanceAt(OPENING_YAW), fit.maxDistance(), fit.centerY());
                return null;
            }
            if (SWING_CACHE.size() >= 256) {
                SWING_CACHE.clear();
            }
            SWING_CACHE.put(cacheKey, fit);
            // Raw reach of the cloud, for the log: the fitted numbers below
            // are only interpretable next to what was measured.
            float minY = Float.MAX_VALUE;
            float maxY = -Float.MAX_VALUE;
            float radius = 0F;
            float[] data = cloud.data();
            for (int o = 0; o < cloud.count() * PreviewCameraFit.STRIDE; o += PreviewCameraFit.STRIDE) {
                minY = Math.min(minY, data[o + 1]);
                maxY = Math.max(maxY, data[o + 1]);
                radius = Math.max(radius, (float) Math.hypot(data[o], data[o + 2]));
            }
            LOGGER.info("[csgobox] EF inspect swing scan: category={} item={} samples={} vertices={} itemBox={} reach=[{}..{}] radius={} dist={} (yaw max {}) center={} ms={}",
                    category, ForgeRegistries.ITEMS.getKey(weapon.getItem()), cloud.count(), vertices,
                    box == null ? "none" : "ok", minY, maxY, radius, fit.distanceAt(OPENING_YAW),
                    fit.maxDistance(), fit.centerY(), (System.nanoTime() - scanStart) / 1_000_000L);
            return fit;
        } catch (Throwable t) {
            LOGGER.info("[csgobox] EF inspect swing scan unavailable: {}", t.toString());
            return null;
        }
    }

    /** One-shot reflection lookup for {@link #measureSwing}; every name below
     *  was bytecode-checked on Epic Fight 20.15.2. */
    private static void resolveScan() throws ReflectiveOperationException {
        if (scanResolved) {
            return;
        }
        Class<?> patchCls = Class.forName("yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch");
        Class<?> poseCls = Class.forName("yesman.epicfight.api.animation.Pose");
        Class<?> matrixCls = Class.forName("yesman.epicfight.api.utils.math.OpenMatrix4f");
        Class<?> vec3fCls = Class.forName("yesman.epicfight.api.utils.math.Vec3f");
        Class<?> accessorCls = Class.forName("yesman.epicfight.api.asset.AssetAccessor");
        Class<?> playerCls = Class.forName("yesman.epicfight.api.animation.AnimationPlayer");
        animatorGetEntityPatch = animator.getClass().getMethod("getEntityPatch");
        animationPlayerCtor = playerCls.getConstructor();
        playerSetPlayAnimation = playerCls.getMethod("setPlayAnimation", accessorCls);
        playerSetElapsedTime = playerCls.getMethod("setElapsedTime", float.class);
        playerGetCurrentPose = playerCls.getMethod("getCurrentPose", patchCls, float.class);
        armatureGetPoseAsTransformMatrix = bipedArmature.getClass()
                .getMethod("getPoseAsTransformMatrix", poseCls, boolean.class);
        Object mesh = accessorGet.invoke(meshBiped);
        skinnedJointCounts = mesh.getClass().getMethod("affectingJointCounts");
        skinnedVertexPosition = mesh.getClass().getMethod("getVertexPosition", int.class,
                Vector4f.class, Class.forName("[Lyesman.epicfight.api.utils.math.OpenMatrix4f;"));
        Constructor<?> matrixCtor = matrixCls.getConstructor();
        Method matrixTranslate = matrixCls.getMethod("translate", float.class, float.class, float.class);
        Method matrixRotateDeg = matrixCls.getMethod("rotateDeg", float.class, vec3fCls);
        heldItemSpace = matrixRotateDeg.invoke(
                matrixTranslate.invoke(matrixCtor.newInstance(), 0F, 0F, -0.13F),
                -90F, vec3fCls.getField("X_AXIS").get(null));
        matrixCopyCtor = matrixCls.getConstructor(matrixCls);
        vec3fCtor = vec3fCls.getConstructor(float.class, float.class, float.class);
        matrixMulFront = matrixCls.getMethod("mulFront", matrixCls);
        transform3v = matrixCls.getMethod("transform3v", matrixCls, vec3fCls, vec3fCls);
        toolJoint = bipedArmature.getClass().getMethod("searchJointByName", String.class)
                .invoke(bipedArmature, "Tool_R");
        scanResolved = true;
    }

    /** Wires the per-frame depth servo (see the servo field block): keeps
     *  the animator, the armature's bound-transform lookup, and a bind
     *  reference measured from an EMPTY pose. Any failure leaves
     *  servoResolved false and the camera runs static (the lunge balloon
     *  comes back, but framing still holds at the bind fit). */
    private static void resolveServo(Object efWidget) {
        try {
            animator = getAnimator.invoke(efWidget);
            animatorGetPose = animator.getClass().getMethod("getPose", float.class);
            Class<?> armatureCls = Class.forName("yesman.epicfight.api.model.Armature");
            armatureGetBoundTransform = armatureCls.getMethod("getBoundTransformFor",
                    Class.forName("yesman.epicfight.api.animation.Pose"),
                    Class.forName("yesman.epicfight.api.animation.Joint"));
            bipedArmature = accessorGet.invoke(armatureBiped);
            if (bipedArmature == null) {
                servoResolved = false;
                LOGGER.info("[csgobox] EF inspect depth servo unavailable: biped armature not loaded");
                return;
            }
            rootJoint = armatureCls.getMethod("searchJointByName", String.class)
                    .invoke(bipedArmature, "Root");
            matrixToTranslationVector = Class.forName("yesman.epicfight.api.utils.math.OpenMatrix4f")
                    .getMethod("toTranslationVector");
            vec3fX = Class.forName("yesman.epicfight.api.utils.math.Vec2f").getField("x");
            vec3fY = Class.forName("yesman.epicfight.api.utils.math.Vec2f").getField("y");
            vec3fZ = Class.forName("yesman.epicfight.api.utils.math.Vec3f").getField("z");
            widgetXRotField = previewerClass.getDeclaredField("xRot");
            widgetYRotField = previewerClass.getDeclaredField("yRot");
            widgetXRotField.setAccessible(true);
            widgetYRotField.setAccessible(true);
            if (rootJoint == null) {
                servoResolved = false;
                LOGGER.info("[csgobox] EF inspect depth servo unavailable: armature has no Root joint");
                return;
            }
            Object emptyPose = Class.forName("yesman.epicfight.api.animation.Pose")
                    .getConstructor().newInstance();
            bindRoot = readJointPosition(emptyPose, rootJoint);
            bindRootDepth = bindRoot[2];
            // Both guards matter: a NaN would NaN the clamps (Math.max/min
            // propagate NaN) and NaN the zoom, killing every vertex; an
            // absurd reference means the axis or the lookup changed shape
            // and the servo would steer the camera somewhere useless.
            if (Float.isNaN(bindRoot[0]) || Float.isNaN(bindRoot[1]) || Float.isNaN(bindRootDepth)
                    || Math.abs(bindRootDepth) > 16.0f) {
                servoResolved = false;
                LOGGER.info("[csgobox] EF inspect depth servo unavailable: bind root=({} {} {})",
                        bindRoot[0], bindRoot[1], bindRootDepth);
                return;
            }
            servoResolved = true;
            servoLogged = false;
        } catch (Throwable t) {
            servoResolved = false;
            LOGGER.info("[csgobox] EF inspect depth servo unavailable: {}", t.toString());
        }
    }

    /** A joint's origin in mesh space for the given pose, through the
     *  armature's own bound-transform lookup — the same composition the
     *  skinning consumes — so the value needs no offline axis conversion.
     *  Returns {x, y, z}. Reflective body; callers catch Throwable. */
    private static float[] readJointPosition(Object pose, Object joint) throws ReflectiveOperationException {
        Object translation = matrixToTranslationVector.invoke(
                armatureGetBoundTransform.invoke(bipedArmature, pose, joint));
        return new float[]{(Float) vec3fX.get(translation), (Float) vec3fY.get(translation),
                (Float) vec3fZ.get(translation)};
    }

    /** Swaps the previewer's three editor checkboxes for one zero-size
     *  stub (see the field block for why). One instance serves all three
     *  fields: they are stateless TRUE toggles and the per-frame anchor
     *  re-derives their position from the same accessors. Failure keeps
     *  the original widgets (current behavior) and logs once per start. */
    private static void stripEditorCheckboxes(Object efWidget) {
        try {
            if (!stripResolved) {
                Class<?> boxCls = Class.forName("yesman.epicfight.client.gui.datapack.widgets.CheckBox");
                Class<?> hSizingCls = Class.forName(
                        "yesman.epicfight.client.gui.datapack.widgets.ResizableComponent$HorizontalSizing");
                Class<?> vSizingCls = Class.forName(
                        "yesman.epicfight.client.gui.datapack.widgets.ResizableComponent$VerticalSizing");
                checkBoxCtor = boxCls.getConstructor(Font.class, int.class, int.class, int.class, int.class,
                        hSizingCls, vSizingCls, Boolean.class, Component.class, Consumer.class);
                showItemCheckboxField = previewerClass.getDeclaredField("showItemCheckbox");
                showColliderCheckboxField = previewerClass.getDeclaredField("showColliderCheckbox");
                showTrailCheckboxField = previewerClass.getDeclaredField("showTrailCheckbox");
                showItemCheckboxField.setAccessible(true);
                showColliderCheckboxField.setAccessible(true);
                showTrailCheckboxField.setAccessible(true);
                stripResolved = true;
            }
            // (font, x, y, w, h, sizing, sizing, value, label, responder):
            // zero width/height and an empty label => min(w,h) glyph fills
            // at 0x0 and the label draw is a no-op; TRUE keeps the held
            // item rendering, matching the defaults all three carried.
            Object stub = checkBoxCtor.newInstance(Minecraft.getInstance().font, 0, 0, 0, 0,
                    null, null, Boolean.TRUE, Component.empty(), null);
            showItemCheckboxField.set(efWidget, stub);
            showColliderCheckboxField.set(efWidget, stub);
            showTrailCheckboxField.set(efWidget, stub);
        } catch (Throwable t) {
            LOGGER.info("[csgobox] EF inspect checkbox strip unavailable: {}", t.toString());
        }
    }

    /** Scans the loaded biped mesh's raw vertex positions for the bind
     *  envelope: the vertices are what the skinning renders at bind pose
     *  (skinning composes pose x toOrigin x vertex, identity pair at bind —
     *  bytecode-checked), stored as xyz triplets on runtime axes. Returns
     *  {minX, minY, minZ, maxX, maxY, maxZ} or null when the data is
     *  unusable: the up axis must be Y (EF's loader swaps the JSON z-up
     *  assets; anything else means the convention moved and the constants
     *  are safer than a guessed axis). */
    private static float[] measureEnvelope() {
        try {
            Object mesh = accessorGet.invoke(meshBiped);
            float[] positions = (float[]) meshPositions.invoke(mesh);
            int verts = positions.length / 3;
            if (verts < 8) {
                return null;
            }
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
            for (int i = 0; i < verts; i++) {
                float x = positions[i * 3];
                float y = positions[i * 3 + 1];
                float z = positions[i * 3 + 2];
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
                minZ = Math.min(minZ, z);
                maxZ = Math.max(maxZ, z);
            }
            float spanX = maxX - minX;
            float spanY = maxY - minY;
            float spanZ = maxZ - minZ;
            // Up = the largest span, and it must be Y.
            if (spanY < spanX || spanY < spanZ) {
                return null;
            }
            if (spanY <= 0.01f || Math.max(spanX, spanZ) <= 0.01f) {
                return null;
            }
            return new float[]{minX, minY, minZ, maxX, maxY, maxZ};
        } catch (Throwable t) {
            return null;
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

    /** Renders the widget's offscreen figure into the preview area. Before
     *  each frame the depth servo re-zooms the camera by the animated
     *  root's travel along the view axis (see the servo field block) so
     *  lunge frames stay at the fitted distance instead of ballooning into
     *  the camera, and re-picks the fitted distance for the current yaw.
     *
     *  Geometry (bytecode-checked): the previewer composes translate(zoom)
     *  then rotateX(xRot), rotateY(yRot) and draws with no further
     *  transform. With {@code u = Ry(yRot) * root}, the root's view depth is
     *  {@code -(u.y * sin(xRot) + u.z * cos(xRot)) - zoom}; holding it at
     *  the fitted distance therefore means {@code zoom = -(...) - dist},
     *  which in the opening view (xRot 0, yRot 180) reduces to
     *  {@code zoom = root.z - dist} — the camera backs off as the figure
     *  lunges toward it. The pose is sampled at Minecraft.getFrameTime() —
     *  the same clock the widget itself passes into renderFigure, so the
     *  correction lands on the frame it is shown. */
    public static void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!active) {
            return;
        }
        if (servoResolved) {
            try {
                float frameTime = Minecraft.getInstance().getFrameTime();
                float[] root = readJointPosition(animatorGetPose.invoke(animator, frameTime), rootJoint);
                if (!Float.isNaN(root[0]) && !Float.isNaN(root[1]) && !Float.isNaN(root[2])) {
                    float xRot = widgetXRotField.getFloat(widget);
                    float yRot = widgetYRotField.getFloat(widget);
                    double pitch = Math.toRadians(xRot);
                    double yaw = Math.toRadians(yRot);
                    double uz = -root[0] * Math.sin(yaw) + root[2] * Math.cos(yaw);
                    // One table entry per 15 degrees of yaw: ease toward the
                    // current one so dragging never pops the zoom at a step.
                    double target = swingFit != null
                            ? swingFit.distanceAt(yRot) * swingScale : fitDistance;
                    fitDistance += (target - fitDistance) * 0.25D;
                    double zoom = -(root[1] * Math.sin(pitch) + uz * Math.cos(pitch)) - fitDistance;
                    double offset = Math.max(-SERVO_LIMIT, Math.min(SERVO_LIMIT, zoom - baseZoom));
                    zoom = baseZoom + offset;
                    if (!Double.isNaN(zoom) && !Double.isInfinite(zoom)) {
                        zoomField.setDouble(widget, zoom);
                    }
                    if (!servoLogged && Math.abs(offset) > 0.05f) {
                        servoLogged = true;
                        LOGGER.info("[csgobox] EF inspect servo active: bind=({} {} {}) root=({} {} {}) yaw={} dist={} offset={} zoom={}",
                                bindRoot[0], bindRoot[1], bindRootDepth, root[0], root[1], root[2],
                                yRot, fitDistance, offset, zoom);
                    }
                }
            } catch (Throwable t) {
                servoResolved = false;
                LOGGER.info("[csgobox] EF inspect depth servo disabled: {}", t.toString());
            }
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

    /** Resolves the item's EF WeaponCategory name (lowercased enum
     *  constant), or null when the item carries no EF weapon category.
     *  Callers decide what to do with categories outside
     *  {@link #INSPECTABLE} (FIST, custom extendable ones). */
    private static String weaponCategoryName(ItemStack stack) {
        try {
            Object capability = itemCapability.invoke(null, stack);
            if (capability == null || capability == emptyCapability) {
                return null;
            }
            Object category = weaponCategory.invoke(capability);
            if (category == null) {
                return null;
            }
            for (Object constant : categoryConstants) {
                if (constant == category) {
                    return ((Enum<?>) constant).name().toLowerCase(Locale.ROOT);
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
            // getItemStackCapability never returns null for a non-empty
            // stack — items without an EF capability get the shared EMPTY
            // sentinel (bytecode: LazyOptional.orElse(CapabilityItem.EMPTY)).
            emptyCapability = capabilityItem.getField("EMPTY").get(null);
            weaponCategory = capabilityItem.getMethod("getWeaponCategory");
            setItemToRender = previewer.getMethod("setItemToRender", Item.class);
            addAnimationToPlay = previewer.getMethod("addAnimationToPlay", accessor);
            // Upstream spelling ("Controll") is part of the API.
            enableCameraControl = previewer.getMethod("enableCameraControll", boolean.class);
            enableZoomingCamera = previewer.getMethod("enableZoomingCamera", boolean.class);
            enableCameraMove = previewer.getMethod("enableCameraMove", boolean.class);
            zoomField = previewer.getDeclaredField("zoom");
            zoomField.setAccessible(true);
            previewerClass = previewer;
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
