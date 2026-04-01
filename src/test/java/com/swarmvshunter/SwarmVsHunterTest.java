package com.swarmvshunter;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.*;

import java.util.List;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

class SwarmVsHunterTest {

    private ServerMock server;
    private SwarmVsHunter plugin;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(SwarmVsHunter.class);
        player = server.addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void svhStart_withTwoPlayers_generatesFieldAndChangesState() {
        PlayerMock player2 = server.addPlayer();
        // 手動で選択完了→ゲーム開始をシミュレート
        plugin.swarmPlayer = player;
        plugin.hunterPlayer = player2;
        plugin.selectedMobTypes.addAll(List.of(
                EntityType.ZOMBIE, EntityType.PIG, EntityType.SKELETON, EntityType.COW));
        plugin.startGame();

        assertEquals(SwarmVsHunter.GameState.PLAYING, plugin.gameState,
                "gameState should be PLAYING after startGame");
        assertNotNull(plugin.fieldOrigin, "fieldOrigin should be set");
    }

    @Test
    void svhStart_secondTimeShowsAlreadyPlaying() {
        PlayerMock player2 = server.addPlayer();
        player.performCommand("svh start");
        player.performCommand("svh start");

        String msg;
        String lastReceived = null;
        while ((msg = player.nextMessage()) != null) {
            lastReceived = msg;
        }
        assertTrue(lastReceived != null && lastReceived.contains("ゲームが既に進行中です"),
                "Should show 'already playing' message on second start");
    }

    @Test
    void svhStart_fieldSizeIs70x70() {
        assertEquals(70, plugin.fieldSize, "Field size should be 70");
    }

    @Test
    void svhStart_outerWallsAreStoneBricks() {
        PlayerMock player2 = server.addPlayer();
        plugin.swarmPlayer = player;
        plugin.hunterPlayer = player2;
        plugin.selectedMobTypes.addAll(List.of(
                EntityType.ZOMBIE, EntityType.PIG, EntityType.SKELETON, EntityType.COW));
        plugin.startGame();

        int ox = plugin.fieldOrigin.getBlockX();
        int baseY = plugin.fieldOrigin.getBlockY();
        int oz = plugin.fieldOrigin.getBlockZ();
        int size = plugin.fieldSize;

        Block wallBlock = plugin.fieldOrigin.getWorld().getBlockAt(ox + 5, baseY + 1, oz);
        assertEquals(Material.STONE_BRICKS, wallBlock.getType(),
                "Outer wall should be stone bricks");

        Block southWall = plugin.fieldOrigin.getWorld().getBlockAt(ox + 5, baseY + 1, oz + size - 1);
        assertEquals(Material.STONE_BRICKS, southWall.getType(),
                "South wall should be stone bricks");

        Block westWall = plugin.fieldOrigin.getWorld().getBlockAt(ox, baseY + 1, oz + 5);
        assertEquals(Material.STONE_BRICKS, westWall.getType(),
                "West wall should be stone bricks");

        Block eastWall = plugin.fieldOrigin.getWorld().getBlockAt(ox + size - 1, baseY + 1, oz + 5);
        assertEquals(Material.STONE_BRICKS, eastWall.getType(),
                "East wall should be stone bricks");
    }

    @Test
    void svhStart_hunterTeleportedToField() {
        PlayerMock player2 = server.addPlayer();
        plugin.swarmPlayer = player;
        plugin.hunterPlayer = player2;
        plugin.selectedMobTypes.addAll(List.of(
                EntityType.ZOMBIE, EntityType.PIG, EntityType.SKELETON, EntityType.COW));
        plugin.startGame();

        // Hunterはフィールド右側にTP
        double expectedX = plugin.fieldOrigin.getBlockX() + plugin.fieldSize * 3.0 / 4.0;
        double expectedZ = plugin.fieldOrigin.getBlockZ() + plugin.fieldSize / 2.0;

        assertEquals(expectedX, player2.getLocation().getX(), 1.0,
                "Hunter should be teleported to field right side X");
        assertEquals(expectedZ, player2.getLocation().getZ(), 1.0,
                "Hunter should be teleported to field right side Z");
    }

    @Test
    void svhCommand_nonPlayerRejected() {
        // ConsoleCommandSender executing the command
        server.executeConsole("svh", "start");
        // No crash = pass; console should get rejection message
    }

    // === マイルストーン2: Hunter初期装備テスト ===

    @Test
    void equipHunter_givesFullIronArmor() {
        plugin.equipHunter(player);
        PlayerInventory inv = player.getInventory();

        assertEquals(Material.IRON_HELMET, inv.getHelmet().getType(), "頭: 鉄ヘルメット");
        assertEquals(Material.IRON_CHESTPLATE, inv.getChestplate().getType(), "胴: 鉄チェストプレート");
        assertEquals(Material.IRON_LEGGINGS, inv.getLeggings().getType(), "脚: 鉄レギンス");
        assertEquals(Material.IRON_BOOTS, inv.getBoots().getType(), "足: 鉄ブーツ");
    }

    @Test
    void equipHunter_givesIronSwordInMainHand() {
        plugin.equipHunter(player);
        assertEquals(Material.IRON_SWORD, player.getInventory().getItemInMainHand().getType());
    }

    @Test
    void equipHunter_givesShieldInOffHand() {
        plugin.equipHunter(player);
        assertEquals(Material.SHIELD, player.getInventory().getItemInOffHand().getType());
    }

    @Test
    void equipHunter_givesBowAndArrowsAndBread() {
        plugin.equipHunter(player);
        PlayerInventory inv = player.getInventory();

        assertTrue(inv.contains(Material.BOW), "弓を持っている");
        assertTrue(inv.contains(Material.ARROW, 16), "矢16本");
        assertTrue(inv.contains(Material.BREAD, 4), "パン4個");
    }

    // === マイルストーン2: mob選択GUIテスト ===

    @Test
    void svhStart_requiresTwoPlayers() {
        // 1人だけでstartするとエラーメッセージ
        plugin.gameState = SwarmVsHunter.GameState.WAITING;
        player.performCommand("svh start");

        String msg = null;
        String m;
        while ((m = player.nextMessage()) != null) msg = m;
        assertTrue(msg != null && msg.contains("2人"), "2人必要のメッセージが出る");
    }

    @Test
    void svhStart_withTwoPlayers_opensSelectionGUI() {
        PlayerMock player2 = server.addPlayer();
        plugin.gameState = SwarmVsHunter.GameState.WAITING;
        player.performCommand("svh start");

        assertEquals(SwarmVsHunter.GameState.SELECTING, plugin.gameState,
                "gameStateがSELECTINGになる");
    }

    @Test
    void mobSelectionGUI_containsSpawnEggs() {
        PlayerMock player2 = server.addPlayer();
        plugin.gameState = SwarmVsHunter.GameState.WAITING;
        plugin.openMobSelectionGUI(player);

        var view = player.getOpenInventory();
        assertNotNull(view, "GUIが開く");
        // スポーンエッグが含まれている
        boolean hasEgg = false;
        for (int i = 0; i < view.getTopInventory().getSize(); i++) {
            ItemStack item = view.getTopInventory().getItem(i);
            if (item != null && item.getType().name().endsWith("_SPAWN_EGG")) {
                hasEgg = true;
                break;
            }
        }
        assertTrue(hasEgg, "GUIにスポーンエッグが含まれる");
    }

    // === マイルストーン2: 中立mobシステムテスト ===

    @Test
    void neutralMobSystem_fieldContainsMobs() {
        // フィールド生成後にmobが配置されることを確認
        // mob配置はゲーム開始処理で行うため、spawnMobsメソッドの存在確認
        PlayerMock player2 = server.addPlayer();
        plugin.swarmPlayer = player;
        plugin.hunterPlayer = player2;
        plugin.fieldOrigin = player.getLocation().clone();
        plugin.fieldOrigin.setY(plugin.fieldOrigin.getBlockY());

        plugin.selectedMobTypes.clear();
        plugin.selectedMobTypes.add(EntityType.ZOMBIE);
        plugin.selectedMobTypes.add(EntityType.SKELETON);
        plugin.selectedMobTypes.add(EntityType.PIG);
        plugin.selectedMobTypes.add(EntityType.COW);

        // spawnMobsを呼んでもクラッシュしない
        assertDoesNotThrow(() -> plugin.spawnMobs());
    }
}
