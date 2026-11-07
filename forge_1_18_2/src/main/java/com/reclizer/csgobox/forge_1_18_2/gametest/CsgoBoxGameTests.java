package com.reclizer.csgobox.forge_1_18_2.gametest;

import com.reclizer.csgobox.logic.BoxConstraintTracker;
import com.reclizer.csgobox.forge_1_18_2.box.BoxDefinition;
import com.reclizer.csgobox.forge_1_18_2.box.BoxJsonLoader;
import com.reclizer.csgobox.forge_1_18_2.box.BoxRegistry;
import com.reclizer.csgobox.forge_1_18_2.item.ItemCsgoBox;
import com.reclizer.csgobox.forge_1_18_2.item.ModItems;
import com.reclizer.csgobox.forge_1_18_2.packet.BoxOpenExecutor;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.nio.file.Path;

/**
 * Headless server-side GameTest suite (run via
 * {@code ./gradlew :forge_1_18_2:gameTestServer -Pactive_versions=forge-1.20.1}
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
 * <p>Era notes (1.18.2): the grade stamp is an ItemStack NBT int
 * ({@link ItemCsgoBox#getGrade}), not a DataComponent; the template folder is
 * {@code data/csgobox/structures/} (plural) and ships its own empty5x5.nbt
 * with DataVersion 2975. {@code makeMockPlayer()} predates the GameType
 * parameter (1.19.4) and is creative by default — the executor branches on
 * {@code abilities.instabuild}, which {@link #freshPlayer} forces false.</p>
 *
 * <p>These classes ship in the main jar but are inert outside the gametest
 * server: registration only happens when {@code forge.enabledGameTestNamespaces}
 * includes {@code csgobox} and the game-test launch handler is active.</p>
 */
@GameTestHolder("csgobox")
@PrefixGameTestTemplate(false)
public final class CsgoBoxGameTests {

    private static final ResourceLocation PLAIN_ID = id("gametest_plain");
    private static final ResourceLocation KEYED_ID = id("gametest_keyed");
    private static final ResourceLocation PITY_ID = id("gametest_pity");
    private static final ResourceLocation CAPPED_ID = id("gametest_capped");
    private static final ResourceLocation TERMINAL_ID = id("gametest_terminal");

    private CsgoBoxGameTests() {
    }

    private static ResourceLocation id(String name) {
        return new ResourceLocation("csgobox", name);
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private static Path boxesDir() {
        return FMLPaths.CONFIGDIR.get().resolve("csbox");
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

    /** A generic box item stamped with the given box id (identity is the NBT tag). */
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
        Player player = helper.makeMockPlayer();
        player.getAbilities().instabuild = false;
        player.onUpdateAbilities();
        return player;
    }

    /** 1.18.2 predates GameTestHelper.assertTrue — throw the era-native assertion directly. */
    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
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

    @GameTest(batch = "gt1", template = "empty5x5", timeoutTicks = 400)
    public static void gametestRegistryLoadsFixtureBoxes(GameTestHelper helper) throws IOException {
        deployFixtures();
        check(BoxRegistry.get(PLAIN_ID) != null, "plain fixture box must be registered");
        check(BoxRegistry.get(KEYED_ID) != null, "keyed fixture box must be registered");
        check(BoxRegistry.get(PITY_ID) != null, "pity fixture box must be registered");
        BoxDefinition capped = BoxRegistry.get(CAPPED_ID);
        check(capped != null, "capped fixture box must be registered");
        if (capped != null) {
            check(capped.maxPerPlayer() == 1, "max_per_player=1 must parse through");
        }
        check(BoxRegistry.get(TERMINAL_ID) != null, "terminal fixture box must be registered");
        helper.succeed();
    }

    @GameTest(batch = "gt2", template = "empty5x5", timeoutTicks = 400)
    public static void gametestOpenGrantsRewardAndConsumesBox(GameTestHelper helper) throws IOException {
        deployFixtures();
        Player player = freshPlayer(helper);
        ItemStack box = boxStack(PLAIN_ID);

        BoxOpenExecutor.Outcome outcome = BoxOpenExecutor.execute(player, box, true, true);
        check(outcome != null, "keyless open must succeed (boxIdComp="
                + ItemCsgoBox.getBoxId(box)
                + ", registryHasPlain=" + (BoxRegistry.get(PLAIN_ID) != null) + ")");
        if (outcome != null) {
            check(outcome.grade() >= 1 && outcome.grade() <= 5,
                    "rolled grade must be a valid tier level, got " + outcome.grade());
            // Fixture weights [100,0,0,0,0] — the only positive tier is level 1
            // (stone); an explicit 0 must keep its tier out of the roll.
            int stones = countInInventory(player, Items.STONE);
            check(stones == 1,
                    "prize must be granted to the inventory (stones=" + stones
                            + ", prize=" + outcome.giveItem().getItem() + "x" + outcome.giveItem().getCount()
                            + ", grade=" + outcome.grade()
                            + ", inv=" + invSummary(player) + ")");
            Integer stamped = ItemCsgoBox.getGrade(outcome.giveItem());
            check(stamped != null && stamped == outcome.grade(),
                    "granted item must carry the stamped grade NBT (grade="
                            + outcome.grade() + ", component=" + stamped
                            + ", prize=" + outcome.giveItem().getItem() + "x" + outcome.giveItem().getCount()
                            + ", inv=" + invSummary(player) + ")");
        }
        check(box.isEmpty(), "box stack must be consumed on open");

        // The 10-tick OpenBlockGuard must refuse an immediate second open.
        BoxOpenExecutor.Outcome second = BoxOpenExecutor.execute(player, boxStack(PLAIN_ID), true, true);
        check(second == null, "second open within the 10-tick guard must be refused");
        helper.succeed();
    }

    @GameTest(batch = "gt3", template = "empty5x5", timeoutTicks = 400)
    public static void gametestMissingKeyRefusesOpen(GameTestHelper helper) throws IOException {
        deployFixtures();
        Player player = freshPlayer(helper);
        ItemStack box = boxStack(KEYED_ID);

        BoxOpenExecutor.Outcome refused = BoxOpenExecutor.execute(player, box, true, true);
        check(refused == null, "open without the required key must be refused");
        check(!box.isEmpty(), "a refused open must not consume the box");
        check(countInInventory(player, Items.STONE) == 0,
                "a refused open must not grant a prize");

        player.getInventory().add(new ItemStack(ModItems.ITEM_CSGO_KEY0.get()));
        BoxOpenExecutor.Outcome opened = BoxOpenExecutor.execute(player, box, true, true);
        check(opened != null, "open with the key present must succeed");
        check(countInInventory(player, ModItems.ITEM_CSGO_KEY0.get()) == 0,
                "the key must be consumed on success");
        helper.succeed();
    }

    @GameTest(batch = "gt4", template = "empty5x5", timeoutTicks = 400)
    public static void gametestFirstOpenPityForcesTopTier(GameTestHelper helper) throws IOException {
        deployFixtures();
        Player player = freshPlayer(helper);
        ItemStack box = boxStack(PITY_ID);

        BoxOpenExecutor.Outcome outcome = BoxOpenExecutor.execute(player, box, true, true);
        check(outcome != null, "first open must succeed");
        if (outcome != null) {
            // every=100 keeps shouldForce(streak=0) false, so the ONLY way to
            // reach tier 5 here is the first-open force (first_open: true).
            check(outcome.grade() == 5,
                    "first_open must force the target tier (classified=5), got " + outcome.grade());
            check(countInInventory(player, Items.DIAMOND_BLOCK) == 1,
                    "the forced classified-tier prize must be granted");
        }
        helper.succeed();
    }

    @GameTest(batch = "gt5", template = "empty5x5", timeoutTicks = 400)
    public static void gametestMaxPerPlayerEnforced(GameTestHelper helper) throws IOException {
        deployFixtures();
        Player player = freshPlayer(helper);
        ItemStack box = boxStack(CAPPED_ID);

        BoxOpenExecutor.Outcome first = BoxOpenExecutor.execute(player, box, true, true);
        check(first != null, "first open under the cap must succeed");

        // Wait out the 10-tick OpenBlockGuard so the second attempt reaches
        // the max_per_player check instead of tripping the spam guard.
        helper.runAfterDelay(20, () -> {
            BoxOpenExecutor.Outcome second = BoxOpenExecutor.execute(player, boxStack(CAPPED_ID), true, true);
            check(second == null, "max_per_player=1 must refuse the second open");
            check(!BoxConstraintTracker.underPerPlayerCap(
                            player.getStringUUID(), CAPPED_ID.toString(), 1),
                    "the tracker must have recorded the first open");
            helper.succeed();
        });
    }

    @GameTest(batch = "gt6", template = "empty5x5", timeoutTicks = 400)
    public static void gametestTerminalRejectedByBoxExecutor(GameTestHelper helper) throws IOException {
        deployFixtures();
        Player player = freshPlayer(helper);
        ItemStack box = boxStack(TERMINAL_ID);

        BoxOpenExecutor.Outcome refused = BoxOpenExecutor.execute(player, box, true, true);
        check(refused == null,
                "terminal-type definitions must be rejected by the box executor");
        check(!box.isEmpty(), "the refused terminal stack must stay intact");
        helper.succeed();
    }
}
