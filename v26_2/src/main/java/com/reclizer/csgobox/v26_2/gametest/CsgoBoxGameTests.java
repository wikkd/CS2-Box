package com.reclizer.csgobox.v26_2.gametest;

import com.reclizer.csgobox.logic.BoxConstraintTracker;
import com.reclizer.csgobox.v26_2.CsgoBox;
import com.reclizer.csgobox.v26_2.box.BoxDefinition;
import com.reclizer.csgobox.v26_2.box.BoxJsonLoader;
import com.reclizer.csgobox.v26_2.box.BoxRegistry;
import com.reclizer.csgobox.v26_2.item.ItemCsgoBox;
import com.reclizer.csgobox.v26_2.item.ModItems;
import com.reclizer.csgobox.v26_2.service.OpenService;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.io.IOException;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * Headless server-side GameTest suite (run via
 * {@code ./gradlew :v26_2:gameTestServer -Pactive_versions=<mc version>} and the
 * "GameTest Integration" CI job). Exercises the server-authoritative open
 * pipeline ({@link OpenService#openSingle}) end to end against fixture box
 * configs written to {@code config/csbox/} at test time — the same
 * config → registry → roll → grant → bookkeeping path a real server runs.
 *
 * <p>Isolation contract: every test asks for a fresh mock player (unique
 * UUID), and every fixture box has its own id, so the per-(player, box)
 * static state (OpenBlockGuard / BoxConstraintTracker / PityTracker) never
 * leaks between tests. The 10-tick open guard is asserted inside a single
 * test instead.</p>
 *
 * <p>26.x registration model: the old {@code @GameTestHolder} annotation
 * scanning is gone — tests are {@code GameTestInstance} registry objects,
 * registered through {@link RegisterGameTestsEvent} (fired during datapack
 * registry load when {@code neoforge.enableGameTest=true}, which the
 * NeoGradle {@code gameTestServer} run sets). Each test gets its own
 * environment id ({@code csgobox:env_…}), which makes it its own batch —
 * batches run sequentially, so the shared registry/loader state is never
 * touched by two tests at once. The test functions live in the built-in
 * TEST_FUNCTION registry (registered via {@link RegisterEvent}); the
 * instances wrap them as {@link FunctionGameTestInstance}s over the
 * {@code csgobox:empty5x5} structure template (each platform ships its own
 * template with the matching DataVersion).</p>
 *
 * <p>These classes ship in the main jar but are inert outside the gametest
 * server: the event only fires when the enable-game-test property is set.</p>
 */
@EventBusSubscriber(modid = CsgoBox.MODID)
public final class CsgoBoxGameTests {

    private static final Identifier STRUCTURE_ID = id("empty5x5");
    private static final int TIMEOUT_TICKS = 400;

    private static final Identifier PLAIN_ID = id("gametest_plain");
    private static final Identifier KEYED_ID = id("gametest_keyed");
    private static final Identifier PITY_ID = id("gametest_pity");
    private static final Identifier CAPPED_ID = id("gametest_capped");
    private static final Identifier TERMINAL_ID = id("gametest_terminal");

    private record Case(String name, Consumer<GameTestHelper> fn) {
    }

    private static final List<Case> CASES = List.of(
            new Case("gametest_registry_loads_fixture_boxes", CsgoBoxGameTests::gametestRegistryLoadsFixtureBoxes),
            new Case("gametest_open_grants_reward_and_consumes_box", CsgoBoxGameTests::gametestOpenGrantsRewardAndConsumesBox),
            new Case("gametest_missing_key_refuses_open", CsgoBoxGameTests::gametestMissingKeyRefusesOpen),
            new Case("gametest_first_open_pity_forces_top_tier", CsgoBoxGameTests::gametestFirstOpenPityForcesTopTier),
            new Case("gametest_max_per_player_enforced", CsgoBoxGameTests::gametestMaxPerPlayerEnforced),
            new Case("gametest_terminal_rejected_by_box_executor", CsgoBoxGameTests::gametestTerminalRejectedByBoxExecutor)
    );

    private CsgoBoxGameTests() {
    }

    private static Identifier id(String name) {
        return Identifier.fromNamespaceAndPath("csgobox", name);
    }

    // ------------------------------------------------------------------
    // Registration (TEST_FUNCTION entries + GameTestInstance wrappers)
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onRegisterTestFunctions(RegisterEvent event) {
        if (!event.getRegistryKey().equals(Registries.TEST_FUNCTION)) {
            return;
        }
        for (Case c : CASES) {
            event.register(Registries.TEST_FUNCTION, id("fn_" + c.name()), () -> c.fn());
        }
    }

    @SubscribeEvent
    public static void onRegisterGameTests(RegisterGameTestsEvent event) {
        for (Case c : CASES) {
            // One environment per test = one batch per test; batches run
            // sequentially so shared registry/loader state is never raced.
            Holder<TestEnvironmentDefinition<?>> env = event.registerEnvironment(
                    id("env_" + c.name()), new TestEnvironmentDefinition.AllOf(List.of()));
            TestData<Holder<TestEnvironmentDefinition<?>>> data =
                    new TestData<>(env, STRUCTURE_ID, TIMEOUT_TICKS, 0, true);
            event.registerTest(id(c.name()), new FunctionGameTestInstance(
                    ResourceKey.create(Registries.TEST_FUNCTION, id("fn_" + c.name())), data));
        }
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
    private static void deployFixtures() {
        if (BoxRegistry.get(PLAIN_ID) != null) {
            return;
        }
        try {
            deployFixturesNow();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write gametest fixtures", e);
        }
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
    private static ItemStack boxStack(Identifier boxId) {
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
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty() && stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** First inventory stack of the given item (or null). */
    private static ItemStack findInInventory(Player player, Item item) {
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty() && stack.is(item)) {
                return stack;
            }
        }
        return null;
    }

    /** Slot-by-slot dump for failure diagnostics (36 main slots). */
    private static String invSummary(Player player) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < player.getInventory().getNonEquipmentItems().size(); i++) {
            ItemStack stack = player.getInventory().getNonEquipmentItems().get(i);
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

    private static void gametestRegistryLoadsFixtureBoxes(GameTestHelper helper) {
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

    private static void gametestOpenGrantsRewardAndConsumesBox(GameTestHelper helper) {
        deployFixtures();
        Player player = freshPlayer(helper);
        ItemStack box = boxStack(PLAIN_ID);

        OpenService.SingleOpenOutcome outcome = OpenService.openSingle(player, box);
        helper.assertTrue(outcome != null, "keyless open must succeed (boxIdComp="
                + ItemCsgoBox.getBoxId(box)
                + ", registryHasPlain=" + (BoxRegistry.get(PLAIN_ID) != null) + ")");
        if (outcome != null) {
            helper.assertTrue(outcome.finalGrade() >= 1 && outcome.finalGrade() <= 5,
                    "rolled grade must be a valid tier level, got " + outcome.finalGrade());
            // Fixture weights [100,0,0,0,0] — the only positive tier is level 1
            // (stone); an explicit 0 must keep its tier out of the roll.
            int stones = countInInventory(player, Items.STONE);
            helper.assertTrue(stones == 1,
                    "prize must be granted to the inventory (stones=" + stones
                            + ", grade=" + outcome.finalGrade()
                            + ", inv=" + invSummary(player) + ")");
            ItemStack granted = findInInventory(player, Items.STONE);
            Integer stamped = granted == null ? null : granted.get(ItemCsgoBox.GRADE.get());
            helper.assertTrue(stamped != null && stamped == outcome.finalGrade(),
                    "granted item must carry the stamped grade component (grade="
                            + outcome.finalGrade() + ", component=" + stamped
                            + ", inv=" + invSummary(player) + ")");
        }
        helper.assertTrue(box.isEmpty(), "box stack must be consumed on open");

        // The 10-tick OpenBlockGuard must refuse an immediate second open.
        OpenService.SingleOpenOutcome second = OpenService.openSingle(player, boxStack(PLAIN_ID));
        helper.assertTrue(second == null, "second open within the 10-tick guard must be refused");
        helper.succeed();
    }

    private static void gametestMissingKeyRefusesOpen(GameTestHelper helper) {
        deployFixtures();
        Player player = freshPlayer(helper);
        ItemStack box = boxStack(KEYED_ID);

        OpenService.SingleOpenOutcome refused = OpenService.openSingle(player, box);
        helper.assertTrue(refused == null, "open without the required key must be refused");
        helper.assertTrue(!box.isEmpty(), "a refused open must not consume the box");
        helper.assertTrue(countInInventory(player, Items.STONE) == 0,
                "a refused open must not grant a prize");

        player.getInventory().add(new ItemStack(ModItems.ITEM_CSGO_KEY0.get()));
        OpenService.SingleOpenOutcome opened = OpenService.openSingle(player, box);
        helper.assertTrue(opened != null, "open with the key present must succeed");
        helper.assertTrue(countInInventory(player, ModItems.ITEM_CSGO_KEY0.get()) == 0,
                "the key must be consumed on success");
        helper.succeed();
    }

    private static void gametestFirstOpenPityForcesTopTier(GameTestHelper helper) {
        deployFixtures();
        Player player = freshPlayer(helper);
        ItemStack box = boxStack(PITY_ID);

        OpenService.SingleOpenOutcome outcome = OpenService.openSingle(player, box);
        helper.assertTrue(outcome != null, "first open must succeed");
        if (outcome != null) {
            // every=100 keeps shouldForce(streak=0) false, so the ONLY way to
            // reach tier 5 here is the first-open force (first_open: true).
            helper.assertTrue(outcome.finalGrade() == 5,
                    "first_open must force the target tier (classified=5), got " + outcome.finalGrade());
            helper.assertTrue(countInInventory(player, Items.DIAMOND_BLOCK) == 1,
                    "the forced classified-tier prize must be granted");
        }
        helper.succeed();
    }

    private static void gametestMaxPerPlayerEnforced(GameTestHelper helper) {
        deployFixtures();
        Player player = freshPlayer(helper);
        ItemStack box = boxStack(CAPPED_ID);

        OpenService.SingleOpenOutcome first = OpenService.openSingle(player, box);
        helper.assertTrue(first != null, "first open under the cap must succeed");

        // Wait out the 10-tick OpenBlockGuard so the second attempt reaches
        // the max_per_player check instead of tripping the spam guard.
        helper.runAfterDelay(20, () -> {
            OpenService.SingleOpenOutcome second = OpenService.openSingle(player, boxStack(CAPPED_ID));
            helper.assertTrue(second == null, "max_per_player=1 must refuse the second open");
            helper.assertTrue(!BoxConstraintTracker.underPerPlayerCap(
                            player.getStringUUID(), CAPPED_ID.toString(), 1),
                    "the tracker must have recorded the first open");
            helper.succeed();
        });
    }

    private static void gametestTerminalRejectedByBoxExecutor(GameTestHelper helper) {
        deployFixtures();
        Player player = freshPlayer(helper);
        ItemStack box = boxStack(TERMINAL_ID);

        OpenService.SingleOpenOutcome refused = OpenService.openSingle(player, box);
        helper.assertTrue(refused == null,
                "terminal-type definitions must be rejected by the box executor");
        helper.assertTrue(!box.isEmpty(), "the refused terminal stack must stay intact");
        helper.succeed();
    }
}
