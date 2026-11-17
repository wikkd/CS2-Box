package com.reclizer.csgobox.forge_1_20_1.utils;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Prefab blueprint (蓝图) structure preview: every blueprint item places one
 * prefab building whose block list ships as a gz-compressed JSON resource in
 * the mod jar ({@code assets/prefab/structures/<name>.gz}). The mod's own
 * ghost-preview flow deserializes them via
 * {@code Structure.CreateInstance(location, class)} (Gson) and resolves the
 * block states at build time through {@code BuildBlock.SetBlockState}; we
 * mirror that exact flow reflectively — no Prefab class is compiled against,
 * and the item id gate ({@link #isPrefabBlueprint}) proves the mod is loaded
 * before any class name here is resolved.
 *
 * <p>The 1.20.1 Prefab line (1.10.0.2) lives under {@code com.wuest.prefab}
 * and its {@code SetBlockState} is world-free (6 args, the facing rides as
 * the last parameter), otherwise the flow matches the 1.21.1 line one to
 * one. The item → structure mapping is the mod's own GUI defaults (the first
 * style/option entry of each blueprint family, verified identical to the
 * 1.0.9 mapping: house family keeps dedicated classes, all other blueprints
 * route through {@code StructureBasic}).</p>
 *
 * <p>Positions come from the mod's own {@code PositionOffset.getRelativePos
 * ition} (side-offset coordinates → block positions, re-oriented by the
 * clear-space shape direction vs the house facing — both mirrored verbatim so
 * the preview layout matches the in-world ghost exactly). The resolved block
 * budget mirrors the Create schematic policy; over budget or any reflection
 * failure degrades to the flat icon via a sentinel cache.</p>
 */
public final class PrefabStructurePreview {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Hard block budget for one preview, same policy as the Create
     *  schematic path. Prefab's own defaults top out below it (largest
     *  shipped default = aqua_base_improved at ~12.7k blocks). */
    private static final int BLOCK_BUDGET = 32768;

    /** {@code item path → structure resource + concrete classes}. The house
     *  family uses dedicated predefined structures; everything else is a
     *  {@code StructureBasic}. Locations are Prefab 1.10.0.2 jar resources,
     *  identical to the 1.0.9 mapping. */
    private static final Map<String, BlueprintSpec> BLUEPRINTS = Map.ofEntries(
            Map.entry("item_house", new BlueprintSpec("assets/prefab/structures/house_basic.gz",
                    "com.wuest.prefab.structures.predefined.StructureHouse", "com.wuest.prefab.structures.config.HouseConfiguration")),
            Map.entry("item_house_improved", new BlueprintSpec("assets/prefab/structures/house_improved_spruce.gz",
                    "com.wuest.prefab.structures.predefined.StructureHouseImproved", "com.wuest.prefab.structures.config.HouseImprovedConfiguration")),
            Map.entry("item_house_advanced", new BlueprintSpec("assets/prefab/structures/house_advanced_manor.gz",
                    "com.wuest.prefab.structures.predefined.StructureHouseAdvanced", "com.wuest.prefab.structures.config.HouseAdvancedConfiguration")),
            Map.entry("item_instant_bridge", new BlueprintSpec("assets/prefab/structures/instant_bridge.gz",
                    "com.wuest.prefab.structures.predefined.StructureInstantBridge", "com.wuest.prefab.structures.config.InstantBridgeConfiguration")),
            basicSpec("item_machinery_tower", "machinery_tower"),
            basicSpec("item_defense_bunker", "defense_bunker"),
            basicSpec("item_mineshaft_entrance", "mineshaft_entrance"),
            basicSpec("item_ender_gateway", "ender_gateway"),
            basicSpec("item_aqua_base", "aqua_base"),
            basicSpec("item_grassy_plain", "grassy_plain"),
            basicSpec("item_magic_temple", "magic_temple"),
            basicSpec("item_watch_tower", "watch_tower"),
            basicSpec("item_welcome_center", "welcome_center"),
            basicSpec("item_jail", "jail"),
            basicSpec("item_saloon", "saloon"),
            basicSpec("item_ski_lodge", "ski_lodge"),
            basicSpec("item_wind_mill", "wind_mill"),
            basicSpec("item_town_hall", "town_hall"),
            basicSpec("item_nether_gate", "nether_gate"),
            basicSpec("item_aqua_base_improved", "aqua_base_improved"),
            basicSpec("item_villager_houses", "villager_house_flat"),
            basicSpec("item_warehouse", "warehouse"),
            basicSpec("item_warehouse_improved", "warehouse_improved"),
            basicSpec("item_modern_buildings", "modern_hipster_fruit_stand"),
            basicSpec("item_farm", "beef_farm"),
            basicSpec("item_farm_improved", "chicken_coop_improved"),
            basicSpec("item_farm_advanced", "automated_bamboo_farm"),
            basicSpec("item_modern_buildings_improved", "modern_mall_store"),
            basicSpec("item_modern_buildings_advanced", "modern_tree_house"));

    private static Map.Entry<String, BlueprintSpec> basicSpec(String itemPath, String structureName) {
        return Map.entry(itemPath, new BlueprintSpec("assets/prefab/structures/" + structureName + ".gz",
                "com.wuest.prefab.structures.predefined.StructureBasic",
                "com.wuest.prefab.structures.config.BasicStructureConfiguration"));
    }

    private record BlueprintSpec(String location, String structureClass, String configClass) {
    }

    /** Fully resolved preview payload for one blueprint: baked block map +
     *  re-parented block entities + bounds, all in structure-relative block
     *  coordinates (anchor = {@code BlockPos.ZERO}). */
    record StructureData(Map<BlockPos, BlockState> blocks, Map<BlockPos, BlockEntity> blockEntities,
                         Vector3f center, float span) {
    }

    /** Per-item resolved bundles; values are {@code StructureData} or the
     *  {@link #FAILED} sentinel. Strong refs on purpose (the WeakHashMap
     *  lesson: string keys get GC'd and silently recompute). */
    private static final Map<String, Object> BUNDLE_CACHE = new HashMap<>();
    private static final Object FAILED = new Object();

    /** One-shot degrade log keys, mirrors the maid preview pattern. */
    private static final Map<String, Boolean> DEGRADE_LOGGED = new HashMap<>();

    private static boolean API_RESOLVED;
    private static Method CREATE_INSTANCE;
    private static Method GET_BLOCKS;
    private static Method GET_CLEAR_SPACE;
    private static Method GET_SHAPE;
    private static Method GET_SHAPE_DIRECTION;
    private static Method GET_STARTING_POSITION;
    private static Method GET_RELATIVE_POSITION;
    private static Method GET_BLOCK_STATE;
    private static Method GET_RESOURCE_LOCATION;
    private static Method GET_SUB_BLOCK;
    private static Method SET_BLOCK_STATE;
    private static Method TE_GET_STARTING_POSITION;
    private static Method TE_GET_DATA_TAG;
    private static Field BLOCK_POS_FIELD;
    private static Field CONFIG_POS_FIELD;
    private static Field CONFIG_FACING_FIELD;
    private static Field TILE_ENTITIES_FIELD;

    private PrefabStructurePreview() {
    }

    /** Gate: a registered {@code prefab:} item whose path is a known
     *  blueprint. The namespace check doubles as the mod-loaded proof that
     *  makes the reflective class loads below safe. */
    public static boolean isPrefabBlueprint(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!"prefab".equals(itemId.getNamespace())) {
            return false;
        }
        return BLUEPRINTS.containsKey(itemId.getPath());
    }

    /** Cached bundle lookup; {@code null} marks "unrenderable, degrade". */
    static StructureData bundle(ItemStack stack, LivingEntity player) {
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        Object cached = BUNDLE_CACHE.get(path);
        if (cached instanceof StructureData data) {
            return data;
        }
        if (cached == FAILED) {
            return null;
        }
        StructureData data = buildBundle(path, player);
        BUNDLE_CACHE.put(path, data == null ? FAILED : data);
        if (data == null) {
            logDegradeOnce(path);
        }
        return data;
    }

    private static void logDegradeOnce(String path) {
        if (DEGRADE_LOGGED.putIfAbsent(path, Boolean.TRUE) == null) {
            LOGGER.info("[csgobox] prefab blueprint preview: flat degrade - {}", path);
        }
    }

    /** Mirrors Prefab's own client bake loop (its {@code StructureRenderHand
     *  ler} ghost flow): Gson-deserialize the structure JSON, anchor the
     *  config at {@code BlockPos.ZERO} facing north, then per block compute
     *  the relative position with the mod's own offset math and resolve the
     *  state through {@code BuildBlock.SetBlockState} (the 1.20.1 overload is
     *  world-free — pure property application keyed on the shape direction).
     *  Sub blocks (door upper halves etc.) ride along on their own
     *  {@code blockPos}. Block entities are deserialized from the per-tile
     *  SNBT and re-parented to the client level. */
    private static StructureData buildBundle(String path, LivingEntity player) {
        try {
            if (!API_RESOLVED) {
                resolveApi();
            }
            BlueprintSpec spec = BLUEPRINTS.get(path);
            Object structure = CREATE_INSTANCE.invoke(null,
                    spec.location(), Class.forName(spec.structureClass()));
            Object config = Class.forName(spec.configClass()).getDeclaredConstructor().newInstance();
            CONFIG_POS_FIELD.set(config, BlockPos.ZERO);
            CONFIG_FACING_FIELD.set(config, Direction.NORTH);
            Object shapeDirection = GET_SHAPE_DIRECTION.invoke(
                    GET_SHAPE.invoke(GET_CLEAR_SPACE.invoke(structure)));

            Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
            List<?> blockList = (List<?>) GET_BLOCKS.invoke(structure);
            for (Object block : blockList) {
                BlockPos pos = relativePosition(block, config, structure, shapeDirection);
                bakeInto(blocks, block, pos, config, structure, shapeDirection);
                Object sub = GET_SUB_BLOCK.invoke(block);
                if (sub != null) {
                    bakeInto(blocks, sub, relativePosition(sub, config, structure, shapeDirection),
                            config, structure, shapeDirection);
                }
            }
            if (blocks.isEmpty() || blocks.size() > BLOCK_BUDGET) {
                return null;
            }

            Map<BlockPos, BlockEntity> blockEntities = new LinkedHashMap<>();
            Object teList = TILE_ENTITIES_FIELD.get(structure);
            if (teList instanceof List<?> tileEntities) {
                for (Object tileEntity : tileEntities) {
                    BlockPos pos = relativePosition(tileEntity, config, structure, shapeDirection);
                    CompoundTag tag = (CompoundTag) TE_GET_DATA_TAG.invoke(tileEntity);
                    if (tag == null) {
                        continue;
                    }
                    BlockState host = blocks.get(pos);
                    BlockEntity be = BlockEntity.loadStatic(pos,
                            host != null ? host : Blocks.AIR.defaultBlockState(),
                            tag);
                    if (be != null) {
                        be.setLevel(player.level());
                        blockEntities.put(pos, be);
                    }
                }
            }

            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (BlockPos pos : blocks.keySet()) {
                minX = Math.min(minX, pos.getX());
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX());
                maxY = Math.max(maxY, pos.getY());
                maxZ = Math.max(maxZ, pos.getZ());
            }
            Vector3f center = new Vector3f((minX + maxX + 1) * 0.5F, (minY + maxY + 1) * 0.5F,
                    (minZ + maxZ + 1) * 0.5F);
            float span = Math.max(maxX - minX + 1, Math.max(maxY - minY + 1, maxZ - minZ + 1));
            return new StructureData(blocks, blockEntities, center, span);
        } catch (Throwable t) {
            LOGGER.warn("[csgobox] prefab blueprint bundle build failed: {}", path, t);
            return null;
        }
    }

    /** One block (or sub block): resolve its state through the mod's own
     *  {@code SetBlockState} and store it. The mod's ghost bake reads both
     *  the position and state off the resolved block, so we do too (the
     *  {@code blockPos} field carries the build-relative position once the
     *  anchor config was zeroed). */
    private static void bakeInto(Map<BlockPos, BlockState> blocks, Object block, BlockPos pos,
                                 Object config, Object structure, Object shapeDirection) throws Exception {
        BlockState state0 = (BlockState) GET_BLOCK_STATE.invoke(block);
        if (state0 == null) {
            ResourceLocation id = (ResourceLocation) GET_RESOURCE_LOCATION.invoke(block);
            state0 = BuiltInRegistries.BLOCK.get(id).defaultBlockState();
        }
        Object resolved = SET_BLOCK_STATE.invoke(null, config,
                CONFIG_POS_FIELD.get(config), block, state0.getBlock(), state0, shapeDirection);
        BlockState state = resolved != null ? (BlockState) GET_BLOCK_STATE.invoke(resolved) : null;
        if (state == null || state.isAir() || state.getRenderShape() != RenderShape.MODEL) {
            return;
        }
        BlockPos key = resolved != null ? (BlockPos) BLOCK_POS_FIELD.get(resolved) : null;
        blocks.put(key != null ? key : pos, state);
    }

    private static BlockPos relativePosition(Object blockOrTile, Object config, Object structure,
                                             Object shapeDirection) throws Exception {
        return (BlockPos) GET_RELATIVE_POSITION.invoke(
                GET_STARTING_POSITION.invoke(blockOrTile),
                CONFIG_POS_FIELD.get(config),
                shapeDirection,
                CONFIG_FACING_FIELD.get(config));
    }

    /** Resolves the Prefab reflection surface once. Every signature below was
     *  verified against the Prefab 1.10.0.2 jar bytecode (1.20.1 line, the
     *  {@code com.wuest.prefab} package root; {@code SetBlockState} is
     *  world-free here and takes the shape direction as its facing). */
    private static void resolveApi() throws Exception {
        Class<?> structureBase = Class.forName("com.wuest.prefab.structures.base.Structure");
        Class<?> buildBlock = Class.forName("com.wuest.prefab.structures.base.BuildBlock");
        Class<?> offset = Class.forName("com.wuest.prefab.structures.base.PositionOffset");
        Class<?> buildTile = Class.forName("com.wuest.prefab.structures.base.BuildTileEntity");
        Class<?> buildClear = Class.forName("com.wuest.prefab.structures.base.BuildClear");
        Class<?> buildShape = Class.forName("com.wuest.prefab.structures.base.BuildShape");
        Class<?> configBase = Class.forName("com.wuest.prefab.structures.config.StructureConfiguration");

        CREATE_INSTANCE = structureBase.getMethod("CreateInstance", String.class, Class.class);
        GET_BLOCKS = structureBase.getMethod("getBlocks");
        GET_CLEAR_SPACE = structureBase.getMethod("getClearSpace");
        TILE_ENTITIES_FIELD = structureBase.getField("tileEntities");
        GET_SHAPE = buildClear.getMethod("getShape");
        GET_SHAPE_DIRECTION = buildShape.getMethod("getDirection");
        GET_STARTING_POSITION = buildBlock.getMethod("getStartingPosition");
        GET_RELATIVE_POSITION = offset.getMethod("getRelativePosition",
                BlockPos.class, Direction.class, Direction.class);
        GET_BLOCK_STATE = buildBlock.getMethod("getBlockState");
        GET_RESOURCE_LOCATION = buildBlock.getMethod("getResourceLocation");
        GET_SUB_BLOCK = buildBlock.getMethod("getSubBlock");
        SET_BLOCK_STATE = buildBlock.getMethod("SetBlockState", configBase,
                BlockPos.class, buildBlock, Block.class, BlockState.class, Direction.class);
        BLOCK_POS_FIELD = buildBlock.getField("blockPos");
        TE_GET_STARTING_POSITION = buildTile.getMethod("getStartingPosition");
        TE_GET_DATA_TAG = buildTile.getMethod("getEntityDataTag");
        CONFIG_POS_FIELD = configBase.getField("pos");
        CONFIG_FACING_FIELD = configBase.getField("houseFacing");
        API_RESOLVED = true;
    }
}
