package com.swarmvshunter;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class SwarmVsHunter extends JavaPlugin implements Listener {

    // ゲームステート
    enum GameState { WAITING, SELECTING, PLAYING }
    GameState gameState = GameState.WAITING;

    // フィールド設定
    int fieldSize = 70;
    int mobCountPerType = 8;
    Location fieldOrigin; // フィールドの左下角

    // プレイヤー
    Player swarmPlayer;
    Player hunterPlayer;

    // mob選択
    List<EntityType> selectedMobTypes = new ArrayList<>();
    Map<Player, List<EntityType>> playerSelections = new HashMap<>();
    static final String MOB_SELECT_TITLE = "mob選択 (2体選んでください)";

    // 非戦闘mob一覧
    static final Set<EntityType> NON_COMBAT_MOBS = Set.of(
            EntityType.PIG, EntityType.COW, EntityType.SHEEP, EntityType.CHICKEN,
            EntityType.RABBIT, EntityType.TURTLE, EntityType.COD, EntityType.SALMON,
            EntityType.SQUID, EntityType.MOOSHROOM, EntityType.DONKEY, EntityType.HORSE,
            EntityType.CAT, EntityType.PARROT, EntityType.FOX, EntityType.FROG
    );

    // 選択可能mob一覧（スポーンエッグが存在するもの）
    static final List<EntityType> SELECTABLE_MOBS = List.of(
            // 非戦闘mob
            EntityType.PIG, EntityType.COW, EntityType.SHEEP, EntityType.CHICKEN,
            EntityType.RABBIT, EntityType.TURTLE, EntityType.HORSE, EntityType.DONKEY,
            EntityType.CAT, EntityType.PARROT, EntityType.FOX, EntityType.FROG,
            EntityType.MOOSHROOM,
            // 戦闘mob
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER, EntityType.CREEPER,
            EntityType.ENDERMAN, EntityType.WITCH, EntityType.BLAZE, EntityType.SLIME,
            EntityType.CAVE_SPIDER, EntityType.ZOMBIFIED_PIGLIN, EntityType.PIGLIN,
            EntityType.HOGLIN, EntityType.VINDICATOR, EntityType.PILLAGER,
            EntityType.RAVAGER, EntityType.VEX, EntityType.WARDEN
    );

    // フィールド内に配置されたmob
    Set<UUID> fieldMobs = new HashSet<>();

    @Override
    public void onEnable() {
        getLogger().info("SwarmVsHunter v3.0 enabled!");
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        getLogger().info("SwarmVsHunter disabled.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("svh")) return false;
        if (!(sender instanceof Player player)) {
            sender.sendMessage("プレイヤーのみ実行可能です");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(ChatColor.YELLOW + "/svh start - ゲームを開始");
            return true;
        }

        if (args[0].equalsIgnoreCase("start")) {
            if (gameState == GameState.PLAYING || gameState == GameState.SELECTING) {
                player.sendMessage(ChatColor.RED + "ゲームが既に進行中です");
                return true;
            }

            // 2人必要チェック
            var onlinePlayers = new ArrayList<>(Bukkit.getOnlinePlayers());
            if (onlinePlayers.size() < 2) {
                player.sendMessage(ChatColor.RED + "2人のプレイヤーが必要です");
                return true;
            }

            // Swarm = コマンド実行者, Hunter = もう1人
            swarmPlayer = player;
            hunterPlayer = null;
            for (Player p : onlinePlayers) {
                if (!p.equals(player)) {
                    hunterPlayer = p;
                    break;
                }
            }

            playerSelections.clear();
            selectedMobTypes.clear();
            gameState = GameState.SELECTING;

            swarmPlayer.sendMessage(ChatColor.GREEN + "あなたはSwarmです！mobを2体選んでください（1体は非戦闘mob必須）");
            hunterPlayer.sendMessage(ChatColor.GREEN + "あなたはHunterです！mobを2体選んでください（1体は非戦闘mob必須）");

            openMobSelectionGUI(swarmPlayer);
            openMobSelectionGUI(hunterPlayer);
            return true;
        }

        return false;
    }

    // === mob選択GUI ===
    void openMobSelectionGUI(Player player) {
        int size = 54; // 6行チェスト
        Inventory gui = Bukkit.createInventory(null, size, MOB_SELECT_TITLE);

        for (int i = 0; i < SELECTABLE_MOBS.size() && i < size; i++) {
            EntityType type = SELECTABLE_MOBS.get(i);
            Material eggMat = getSpawnEggMaterial(type);
            if (eggMat != null) {
                ItemStack egg = new ItemStack(eggMat);
                ItemMeta meta = egg.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(ChatColor.WHITE + type.name());
                    if (NON_COMBAT_MOBS.contains(type)) {
                        meta.setLore(List.of(ChatColor.GREEN + "非戦闘mob"));
                    } else {
                        meta.setLore(List.of(ChatColor.RED + "戦闘mob"));
                    }
                    egg.setItemMeta(meta);
                }
                gui.setItem(i, egg);
            }
        }

        player.openInventory(gui);
    }

    Material getSpawnEggMaterial(EntityType type) {
        String eggName = type.name() + "_SPAWN_EGG";
        try {
            return Material.valueOf(eggName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (gameState != GameState.SELECTING) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(MOB_SELECT_TITLE)) return;

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.getType().name().endsWith("_SPAWN_EGG")) return;

        // エッグ名からEntityType取得
        String eggName = clicked.getType().name();
        String typeName = eggName.replace("_SPAWN_EGG", "");
        EntityType selectedType;
        try {
            selectedType = EntityType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            return;
        }

        List<EntityType> selections = playerSelections.computeIfAbsent(player, k -> new ArrayList<>());

        // 既に選択済みなら解除
        if (selections.contains(selectedType)) {
            selections.remove(selectedType);
            player.sendMessage(ChatColor.YELLOW + selectedType.name() + " の選択を解除しました");
            notifyOtherPlayer(player, selectedType, false);
            return;
        }

        // 2体選択済みなら拒否
        if (selections.size() >= 2) {
            player.sendMessage(ChatColor.RED + "既に2体選択済みです。解除するには選択済みmobをクリック");
            return;
        }

        // 2体目の制約チェック: 1体は非戦闘mob必須
        if (selections.size() == 1) {
            boolean firstIsNonCombat = NON_COMBAT_MOBS.contains(selections.get(0));
            boolean secondIsNonCombat = NON_COMBAT_MOBS.contains(selectedType);
            if (!firstIsNonCombat && !secondIsNonCombat) {
                player.sendMessage(ChatColor.RED + "1体は非戦闘mob（豚、牛等）を選んでください");
                return;
            }
        }

        selections.add(selectedType);
        player.sendMessage(ChatColor.GREEN + selectedType.name() + " を選択しました (" + selections.size() + "/2)");
        notifyOtherPlayer(player, selectedType, true);

        // 両者2体ずつ選択完了チェック
        if (bothPlayersReady()) {
            // 選択完了 → ゲーム開始処理
            swarmPlayer.closeInventory();
            hunterPlayer.closeInventory();
            selectedMobTypes.clear();
            selectedMobTypes.addAll(playerSelections.get(swarmPlayer));
            selectedMobTypes.addAll(playerSelections.get(hunterPlayer));
            startGame();
        }
    }

    void notifyOtherPlayer(Player selector, EntityType type, boolean selected) {
        Player other = selector.equals(swarmPlayer) ? hunterPlayer : swarmPlayer;
        if (other != null) {
            String role = selector.equals(swarmPlayer) ? "Swarm" : "Hunter";
            if (selected) {
                other.sendMessage(ChatColor.AQUA + role + "が " + type.name() + " を選択しました");
            } else {
                other.sendMessage(ChatColor.AQUA + role + "が " + type.name() + " の選択を解除しました");
            }
        }
    }

    boolean bothPlayersReady() {
        return playerSelections.containsKey(swarmPlayer) && playerSelections.get(swarmPlayer).size() == 2
                && playerSelections.containsKey(hunterPlayer) && playerSelections.get(hunterPlayer).size() == 2;
    }

    // === ゲーム開始処理 ===
    void startGame() {
        swarmPlayer.sendMessage(ChatColor.GOLD + "全員の選択が完了！ゲーム開始準備中...");
        hunterPlayer.sendMessage(ChatColor.GOLD + "全員の選択が完了！ゲーム開始準備中...");

        // フィールド生成
        fieldOrigin = swarmPlayer.getLocation().clone();
        fieldOrigin.setX(fieldOrigin.getBlockX() - fieldSize / 2);
        fieldOrigin.setY(fieldOrigin.getBlockY());
        fieldOrigin.setZ(fieldOrigin.getBlockZ() - fieldSize / 2);
        generateField(fieldOrigin, fieldSize);

        // mob配置
        spawnMobs();

        // Hunter装備
        equipHunter(hunterPlayer);

        // Swarmは装備なし、HP1設定
        swarmPlayer.getInventory().clear();
        swarmPlayer.setHealth(1.0);

        // テレポート
        Location swarmSpawn = fieldOrigin.clone().add(fieldSize / 4.0, 1, fieldSize / 2.0);
        Location hunterSpawn = fieldOrigin.clone().add(fieldSize * 3.0 / 4.0, 1, fieldSize / 2.0);
        swarmPlayer.teleport(swarmSpawn);
        hunterPlayer.teleport(hunterSpawn);

        gameState = GameState.PLAYING;
        swarmPlayer.sendMessage(ChatColor.GREEN + "ゲーム開始！");
        hunterPlayer.sendMessage(ChatColor.GREEN + "ゲーム開始！");
    }

    // === Hunter初期装備 ===
    void equipHunter(Player hunter) {
        hunter.getInventory().clear();
        // 防具
        hunter.getInventory().setHelmet(new ItemStack(Material.IRON_HELMET));
        hunter.getInventory().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        hunter.getInventory().setLeggings(new ItemStack(Material.IRON_LEGGINGS));
        hunter.getInventory().setBoots(new ItemStack(Material.IRON_BOOTS));
        // 武器
        hunter.getInventory().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
        hunter.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));
        // 道具
        hunter.getInventory().addItem(new ItemStack(Material.BOW));
        hunter.getInventory().addItem(new ItemStack(Material.ARROW, 16));
        hunter.getInventory().addItem(new ItemStack(Material.BREAD, 4));
    }

    // === mob配置 ===
    void spawnMobs() {
        if (fieldOrigin == null || selectedMobTypes.isEmpty()) return;
        World world = fieldOrigin.getWorld();
        int ox = fieldOrigin.getBlockX();
        int oz = fieldOrigin.getBlockZ();
        int baseY = fieldOrigin.getBlockY();
        Random rand = new Random();

        fieldMobs.clear();
        for (EntityType type : selectedMobTypes) {
            for (int i = 0; i < mobCountPerType; i++) {
                int x = ox + 3 + rand.nextInt(fieldSize - 6);
                int z = oz + 3 + rand.nextInt(fieldSize - 6);
                Location spawnLoc = new Location(world, x + 0.5, baseY + 1, z + 0.5);
                try {
                    var entity = world.spawnEntity(spawnLoc, type);
                    fieldMobs.add(entity.getUniqueId());
                    // AI無効化で中立にする
                    if (entity instanceof Mob mob) {
                        mob.setTarget(null);
                    }
                } catch (Exception e) {
                    // スポーン失敗は無視
                }
            }
        }
    }

    // === 中立mobシステム: EntityTargetEventキャンセル ===
    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        if (gameState != GameState.PLAYING) return;
        if (event.getTarget() == null) return;
        // フィールド内mobは中立化: ターゲット設定をキャンセル
        if (fieldMobs.contains(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // === フィールド生成 ===
    void generateField(Location origin, int size) {
        World world = origin.getWorld();
        int baseY = origin.getBlockY();
        int ox = origin.getBlockX();
        int oz = origin.getBlockZ();
        Random rand = new Random();

        // 1. 地面を平坦化 (草ブロック)
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                // 地面
                world.getBlockAt(ox + x, baseY, oz + z).setType(Material.GRASS_BLOCK);
                // 地面より上を空気で埋める (高さ15ブロック分)
                for (int y = 1; y <= 15; y++) {
                    world.getBlockAt(ox + x, baseY + y, oz + z).setType(Material.AIR);
                }
            }
        }

        // 2. 外壁 (石レンガ、高さ5)
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                if (x == 0 || x == size - 1 || z == 0 || z == size - 1) {
                    for (int y = 1; y <= 5; y++) {
                        world.getBlockAt(ox + x, baseY + y, oz + z).setType(Material.STONE_BRICKS);
                    }
                }
            }
        }

        // 3. 障害物をランダム配置
        int numObstacles = 15 + rand.nextInt(10); // 15〜24個
        for (int i = 0; i < numObstacles; i++) {
            int cx = 3 + rand.nextInt(size - 6);
            int cz = 3 + rand.nextInt(size - 6);
            int obstacleType = rand.nextInt(3);

            switch (obstacleType) {
                case 0 -> placeWallSegment(world, ox + cx, baseY, oz + cz, rand);
                case 1 -> placePillar(world, ox + cx, baseY, oz + cz, rand);
                case 2 -> placeRuins(world, ox + cx, baseY, oz + cz, rand);
            }
        }
    }

    // 壁セグメント: ランダムな方向に伸びる壁
    void placeWallSegment(World world, int x, int baseY, int z, Random rand) {
        boolean horizontal = rand.nextBoolean();
        int length = 3 + rand.nextInt(6); // 3〜8ブロック
        int height = 2 + rand.nextInt(3); // 2〜4ブロック
        Material mat = rand.nextBoolean() ? Material.COBBLESTONE : Material.STONE_BRICKS;

        for (int i = 0; i < length; i++) {
            int bx = horizontal ? x + i : x;
            int bz = horizontal ? z : z + i;
            for (int y = 1; y <= height; y++) {
                Block b = world.getBlockAt(bx, baseY + y, bz);
                if (b.getType() == Material.AIR) {
                    b.setType(mat);
                }
            }
        }
    }

    // 柱: 1〜2ブロック幅の柱
    void placePillar(World world, int x, int baseY, int z, Random rand) {
        int height = 3 + rand.nextInt(3); // 3〜5ブロック
        Material mat = Material.OAK_LOG;
        boolean wide = rand.nextBoolean();

        for (int dx = 0; dx < (wide ? 2 : 1); dx++) {
            for (int dz = 0; dz < (wide ? 2 : 1); dz++) {
                for (int y = 1; y <= height; y++) {
                    Block b = world.getBlockAt(x + dx, baseY + y, z + dz);
                    if (b.getType() == Material.AIR) {
                        b.setType(mat);
                    }
                }
            }
        }
    }

    // 廃墟: L字やコの字の壁
    void placeRuins(World world, int x, int baseY, int z, Random rand) {
        int height = 2 + rand.nextInt(2); // 2〜3ブロック
        Material mat = Material.MOSSY_COBBLESTONE;
        int ruinSize = 3 + rand.nextInt(3); // 3〜5

        // L字型
        for (int i = 0; i < ruinSize; i++) {
            for (int y = 1; y <= height; y++) {
                Block b1 = world.getBlockAt(x + i, baseY + y, z);
                if (b1.getType() == Material.AIR) b1.setType(mat);
            }
        }
        for (int i = 1; i < ruinSize; i++) {
            for (int y = 1; y <= height; y++) {
                Block b2 = world.getBlockAt(x, baseY + y, z + i);
                if (b2.getType() == Material.AIR) b2.setType(mat);
            }
        }
    }
}
