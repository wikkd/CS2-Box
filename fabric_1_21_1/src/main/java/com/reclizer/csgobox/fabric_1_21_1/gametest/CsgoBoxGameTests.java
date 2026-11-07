package com.reclizer.csgobox.fabric_1_21_1.gametest;

import com.reclizer.csgobox.logic.BoxConstraintTracker;
import com.reclizer.csgobox.fabric_1_21_1.box.BoxDefinition;
import com.reclizer.csgobox.fabric_1_21_1.box.BoxJsonLoader;
import com.reclizer.csgobox.fabric_1_21_1.box.BoxRegistry;
import com.reclizer.csgobox.fabric_1_21_1.item.ItemCsgoBox;
import com.reclizer.csgobox.fabric_1_21_1.item.ModItems;
import com.reclizer.csgobox.fabric_1_21_1.packet.BoxOpenExecutor;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import java.io.IOException;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.nio.file.Path;

/**
 * Headless server-side GameTest suite (run via
 * {@code ./gradlew :fabric_1_21_1:runGametest -Pactive_versions=fabric-1.21.1}
 * and the "GameTest Integration" CI job). Exercises the server-authoritative
 * open pipeline ({@link BoxOpenExecutor#execute}) end to end against fixture
 * box configs written to {@code config/csbox/} at test time — the same
 * config → registry → roll → grant → bookkeeping path a real server runs.
 *
 * <p>Isolation contract: every test asks for a fresh mock player (unique
 * UUID), and every fixture box has its own id, so the per-(player, box)
 * static state (OpenBlockGuard / BoxConstraintTracker / PityTracker) never
 * leaks between tests. The 10-tick open guard is asserted inside a single
 * test instead.</p>
 *
 * <p>Fabric notes: registration goes through the {@code fabric-gametest}
 * entrypoint (fabric-gametest-api-v1, part of fabric-api which this module
 * already depends on) and each test reuses fabric-api's bundled empty
 * {@link FabricGameTest#EMPTY_STRUCTURE}, so no custom .nbt template is
 * needed. The class MUST keep a public no-arg constructor — the gametest
 * invoker instantiates it reflectively.</p>
 *
 * <p>These classes ship in the main jar but are inert outside the gametest
 * server: registered tests only run when {@code -Dfabric-api.gametest} is
 * set.</p>
 */
public final class CsgoBoxGameTests implements FabricGameTest {

    private static final ResourceLocation PLAIN_ID = id("gametest_plain");
    private static final ResourceLocation KEYED_ID = id("gametest_keyed");
    private static final ResourceLocation PITY_ID = id("gametest_pity");
    private static final ResourceLocation CAPPED_ID = id("gametest_capped");
    private static final ResourceLocation TERMINAL_ID = id("gametest_terminal");

    // No explicit constructor: fabric-gametest-api-v1 instantiates this class
    // reflectively via getConstructor() and REQUIRES a public no-arg one.

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath("csgobox", name);
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private static Path boxesDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("csbox");
    }

    /**
     * Writes the fixture box configs (first run only) and reloads the
     * registry from disk. Idempotent on both levels: existing files are left
     * as-is, and once the plain fixture is registered the whole deployment is
     * skipped — with per-test batches (sequential) only the first test runs
     * the loader, so later tests just consume the ready registry.
     */
    private static void deployFixtures() throws IOException {
        if (BoxRegistry.get(PLAIN_ID) != null) {
            return;
        }
        deployFixturesNow();
    }

    private static void deployFixturesNow() throws IOException {
        Path dir = boxesDir();
        Files.createDirectories(dir);
        writeIfAbsent(dir, "gametest_plain.json", """
                {
                  "name": "Gametest Plain",
                  "key": "minecraft:air",
                  "random": [100, 0, 0, 0, 0],
                  "grade1": [{ "id": "minecraft:stone" }],
                  "grade2": [{ "id": "minecraft:iron_ingot" }],
                  "grade3": [{ "id": "minecraft:diamond" }],
                  "grade4": [{ "id": "minecraft:netherite_ingot" }],
                  "grade5": [{ "id": "minecraft:diamond_block" }]
                }
                """);
        writeIfAbsent(dir, "gametest_keyed.json", """
                {
                  "name": "Gametest Keyed",
                  "random": [100, 0, 0, 0, 0],
                  "grade1": [{ "id": "minecraft:stone" }],
                  "grade2": [{ "id": "minecraft:iron_ingot" }],
                  "grade3": [{ "id": "minecraft:diamond" }],
                  "grade4": [{ "id": "minecraft:netherite_ingot" }],
                  "grade5": [{ "id": "minecraft:diamond_block" }]
                }
                """);
        writeIfAbsent(dir, "gametest_pity.json", """
                {
                  "name": "Gametest Pity",
                  "key": "minecraft:air",
                  "random": [40, 30, 20, 8, 2],
                  "pity": { "grade": "classified", "every": 100, "first_open": true },
                  "grade1": [{ "id": "minecraft:stone" }],
                  "grade2": [{ "id": "minecraft:iron_ingot" }],
                  "grade3": [{ "id": "minecraft:diamond" }],
                  "grade4": [{ "id": "minecraft:netherite_ingot" }],
                  "grade5": [{ "id": "minecraft:diamond_block" }]
                }
                """);
        writeIfAbsent(dir, "gametest_capped.json", """
                {
                  "name": "Gametest Capped",
                  "key": "minecraft:air",
                  "max_per_player": 1,
                  "random": [100, 0, 0, 0, 0],
                  "grade1": [{ "id": "minecraft:stone" }],
                  "grade2": [{ "id": "minecraft:iron_ingot" }],
                  "grade3": [{ "id": "minecraft:diamond" }],
                  "grade4": [{ "id": "minecraft:netherite_ingot" }],
                  "grade5": [{ "id": "minecraft:diamond_block" }]
                }
                """);
        writeIfAbsent(dir, "gametest_terminal.json", """
                {
                  "name": "Gametest Terminal",
                  "type": "terminal",
                  "random": [10, 20, 40, 80, 160],
                  "grade1": [{ "id": "minecraft:stone" }],
                  "grade2": [{ "id": "minecraft:iron_ingot" }],
                  "grade3": [{ "id": "minecraft:diamond" }],
                  "grade4": [{ "id": "minecraft:netherite_ingot" }],
                  "grade5": [{ "id": "minecraft:diamond_block" }]
                }
                """);
        ensureFixturePrices(dir);
        BoxJsonLoader.loadAll();
    }

    private static final Gson GSON = new Gson();

    /**
     * Fixture item prices. The loader REJECTS boxes containing unpriced ids,
     * so every fixture item needs a table entry — merged into any existing
     * _prices.json (a dev environment may already have a populated table
     * without our fixture items; existing keys always win, idempotent).
     */
    private static final Map<String, Integer> FIXTURE_PRICES = Map.of(
            "minecraft:stone", 1,
            "minecraft:iron_ingot", 2,
            "minecraft:diamond", 10,
            "minecraft:netherite_ingot", 40,
            "minecraft:diamond_block", 90);

    private static void ensureFixturePrices(Path dir) throws IOException {
        Path prices = dir.resolve("_prices.json");
        JsonObject table = null;
        if (Files.exists(prices)) {
            try {
                table = GSON.fromJson(Files.readString(prices, StandardCharsets.UTF_8), JsonObject.class);
            } catch (RuntimeException e) {
                // corrupt table: the loader will report it; start a fresh one
            }
        }
        if (table == null) {
            table = new JsonObject();
        }
        boolean changed = false;
        for (Map.Entry<String, Integer> e : FIXTURE_PRICES.entrySet()) {
            if (!table.has(e.getKey())) {
                table.addProperty(e.getKey(), e.getValue());
                changed = true;
            }
        }
        if (changed) {
            Files.writeString(prices, GSON.toJson(table), StandardCharsets.UTF_8);
        }
    }

    private static void writeIfAbsent(Path dir, String fileName, String json) throws IOException {
        Path file = dir.resolve(fileName);
        if (!Files.exists(file)) {
            Files.writeString(file, json, StandardCharsets.UTF_8);
        }
    }

    /** A generic box item stamped with the given box id (identity is the component). */
    private static ItemStack boxStack(ResourceLocation boxId) {
        return ItemCsgoBox.setBoxId(boxId, new ItemStack(ModItems.ITEM_CSGOBOX.get()));
    }

    /**
     * A fresh mock player with a unique UUID per call. Deliberately uses
     * {@link GameTestHelper#makeMockPlayer} (NOT makeMockServerPlayerInLevel):
     * the placed variant runs placeNewPlayer, whose player-join definition
     * sync ({@code PacketSyncBoxDefinitions}) is refused by the mock's
     * embedded channel ("may not be sent to the client") and aborts the mock
     * creation. The unplaced mock is enough for the server-authoritative open
     * path — only the stats/permission/loot-resolution branches need a real
     * ServerPlayer, and none of them are on this suite's assertions. Survival
     * abilities are forced so key/box consumption paths are always exercised.
     */
    private static Player freshPlayer(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getAbilities().instabuild = false;
        player.onUpdateAbilities();
        return player;
    }

    private static int countInInventory(Player player, Item item) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** Slot-by-slot dump for failure diagnostics (36 main slots). */
    private static String invSummary(Player player) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            ItemStack stack = player.getInventory().items.get(i);
            if (!stack.isEmpty()) {
                if (sb.length() > 1) {
                    sb.append(", ");
                }
                sb.append(i).append(':').append(stack.getItem()).append('x').append(stack.getCount());
            }
        }
        return sb.append(']').toString();
    }

    // ------------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------------

    @GameTest(batch = "gt1", template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400)
    public void gametestRegistryLoadsFixtureBoxes(GameTestHelper helper) throws IOException {
        deployFixtures();
        helper.assertTrue(BoxRegistry.get(PLAIN_ID) != null, "plain fixture box must be registered");
        helper.assertTrue(BoxRegistry.get(KEYED_ID) != null, "keyed fixture box must be registered");
        helper.assertTrue(BoxRegistry.get(PITY_ID) != null, "pity fixture box must be registered");
        BoxDefinition capped = BoxRegistry.get(CAPPED_ID);
        helper.assertTrue(capped != null, "capped fixture box must be registered");
        if (capped != null) {
            helper.assertTrue(capped.maxPerPlayer() == 1, "max_per_player=1 must parse through");
        }
        helper.assertTrue(BoxRegistry.get(TERMINAL_ID) != null, "terminal fixture box must be registered");
        helper.succeed();
    }

    @GameTest(batch = "gt2", template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400)
    public void gametestOpenGrantsRewardAndConsumesBox(GameTestHelper helper) throws IOException {
        deployFixtures();
        Player player = freshPlayer(helper);
        ItemStack box = boxStack(PLAIN_ID);

        BoxOpenExecutor.Outcome outcome = BoxOpenExecutor.execute(player, box, true, true);
        helper.assertTrue(outcome != null, "keyless open must succeed (boxIdComp="
                + ItemCsgoBox.getBoxId(box)
                + ", registryHasPlain=" + (BoxRegistry.get(PLAIN_ID) != null) + ")");
        if (outcome != null) {
            helper.assertTrue(outcome.grade() >= 1 && outcome.grade() <= 5,
                    "rolled grade must be a valid tier level, got " + outcome.grade());
            // Fixture weights [100,0,0,0,0] — the only positive tier is level 1
            // (stone); an explicit 0 must keep its tier out of the roll.
            int stones = countInInventory(player, Items.STONE);
            helper.assertTrue(stones == 1,
                    "prize must be granted to the inventory (stones=" + stones
                            + ", prize=" + outcome.giveItem().getItem() + "x" + outcome.giveItem().getCount()
                            + ", grade=" + outcome.grade()
                            + ", inv=" + invSummary(player) + ")");
            Integer stamped = outcome.giveItem().get(ItemCsgoBox.GRADE.get());
            helper.assertTrue(stamped != null && stamped == outcome.grade(),
                    "granted item must carry the stamped grade component (grade="
                            + outcome.grade() + ", component=" + stamped
                            + ", prize=" + outcome.giveItem().getItem() + "x" + outcome.giveItem().getCount()
                            + ", inv=" + invSummary(player) + ")");
        }
        helper.assertTrue(box.isEmpty(), "box stack must be consumed on open");

        // The 10-tick OpenBlockGuard must refuse an immediate second open.
        BoxOpenExecutor.Outcome second = BoxOpenExecutor.execute(player, boxStack(PLAIN_ID), true, true);
        helper.assertTrue(second == null, "second open within the 10-tick guard must be refused");
        helper.succeed();
    }

    @GameTest(batch = "gt3", template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400)
    public void gametestMissingKeyRefusesOpen(GameTestHelper helper) throws IOException {
        deployFixtures();
        Player player = freshPlayer(helper);
        ItemStack box = boxStack(KEYED_ID);

        BoxOpenExecutor.Outcome refused = BoxOpenExecutor.execute(player, box, true, true);
        helper.assertTrue(refused == null, "open without the required key must be refused");
        helper.assertTrue(!box.isEmpty(), "a refused open must not consume the box");
        helper.assertTrue(countInInventory(player, Items.STONE) == 0,
                "a refused open must not grant a prize");

        player.getInventory().add(new ItemStack(ModItems.ITEM_CSGO_KEY0.get()));
        BoxOpenExecutor.Outcome opened = BoxOpenExecutor.execute(player, box, true, true);
        helper.assertTrue(opened != null, "open with the key present must succeed");
        helper.assertTrue(countInInventory(player, ModItems.ITEM_CSGO_KEY0.get()) == 0,
                "the key must be consumed on success");
        helper.succeed();
    }

    @GameTest(batch = "gt4", template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400)
    public void gametestFirstOpenPityForcesTopTier(GameTestHelper helper) throws IOException {
        deployFixtures();
        Player player = freshPlayer(helper);
        ItemStack box = boxStack(PITY_ID);

        BoxOpenExecutor.Outcome outcome = BoxOpenExecutor.execute(player, box, true, true);
        helper.assertTrue(outcome != null, "first open must succeed");
        if (outcome != null) {
            // every=100 keeps shouldForce(streak=0) false, so the ONLY way to
            // reach tier 5 here is the first-open force (first_open: true).
            helper.assertTrue(outcome.grade() == 5,
                    "first_open must force the target tier (classified=5), got " + outcome.grade());
            helper.assertTrue(countInInventory(player, Items.DIAMOND_BLOCK) == 1,
                    "the forced classified-tier prize must be granted");
        }
        helper.succeed();
    }

    @GameTest(batch = "gt5", template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400)
    public void gametestMaxPerPlayerEnforced(GameTestHelper helper) throws IOException {
        deployFixtures();
        Player player = freshPlayer(helper);
        ItemStack box = boxStack(CAPPED_ID);

        BoxOpenExecutor.Outcome first = BoxOpenExecutor.execute(player, box, true, true);
        helper.assertTrue(first != null, "first open under the cap must succeed");

        // Wait out the 10-tick OpenBlockGuard so the second attempt reaches
        // the max_per_player check instead of tripping the spam guard.
        helper.runAfterDelay(20, () -> {
            BoxOpenExecutor.Outcome second = BoxOpenExecutor.execute(player, boxStack(CAPPED_ID), true, true);
            helper.assertTrue(second == null, "max_per_player=1 must refuse the second open");
            helper.assertTrue(!BoxConstraintTracker.underPerPlayerCap(
                            player.getStringUUID(), CAPPED_ID.toString(), 1),
                    "the tracker must have recorded the first open");
            helper.succeed();
        });
    }

    @GameTest(batch = "gt6", template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 400)
    public void gametestTerminalRejectedByBoxExecutor(GameTestHelper helper) throws IOException {
        deployFixtures();
        Player player = freshPlayer(helper);
        ItemStack box = boxStack(TERMINAL_ID);

        BoxOpenExecutor.Outcome refused = BoxOpenExecutor.execute(player, box, true, true);
        helper.assertTrue(refused == null,
                "terminal-type definitions must be rejected by the box executor");
        helper.assertTrue(!box.isEmpty(), "the refused terminal stack must stay intact");
        helper.succeed();
    }
}
