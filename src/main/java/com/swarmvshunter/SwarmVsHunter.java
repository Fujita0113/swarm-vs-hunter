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
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
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

            swarmPlayer.sendMessage(ChatColor.GREEN + "あなたはSwarmです！mobを2体選んでください");
            hunterPlayer.sendMessage(ChatColor.GREEN + "あなたはHunterです！mobを2体選んでください");

            openMobSelectionGUI(swarmPlayer);
            openMobSelectionGUI(hunterPlayer);
            return true;
        }

        return false;
    }

    // === mob選択GUI ===
    static final int SLOT_SELECT_1 = 46; // 6行目: 1体目スロット
    static final int SLOT_SELECT_2 = 48; // 6行目: 2体目スロット
    static final int SEPARATOR_ROW_START = 36; // 5行目開始
    static final int SEPARATOR_ROW_END = 44;   // 5行目終了

    void openMobSelectionGUI(Player player) {
        int size = 54; // 6行チェスト
        Inventory gui = Bukkit.createInventory(null, size, MOB_SELECT_TITLE);

        // 1〜4行目: mob一覧
        for (int i = 0; i < SELECTABLE_MOBS.size() && i < 36; i++) {
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

        // 5行目: 区切り線（灰色ガラス板）
        ItemStack separator = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta sepMeta = separator.getItemMeta();
        if (sepMeta != null) {
            sepMeta.setDisplayName(" ");
            separator.setItemMeta(sepMeta);
        }
        for (int i = SEPARATOR_ROW_START; i <= SEPARATOR_ROW_END; i++) {
            gui.setItem(i, separator.clone());
        }

        // 6行目: 選択スロットエリア
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setDisplayName(" ");
            filler.setItemMeta(fillerMeta);
        }
        for (int i = 45; i <= 53; i++) {
            gui.setItem(i, filler.clone());
        }

        // 1体目・2体目の空スロット（黒ガラス板）
        gui.setItem(SLOT_SELECT_1, createEmptySlot("§e1体目を選んでください"));
        gui.setItem(SLOT_SELECT_2, createEmptySlot("§e2体目を選んでください"));

        player.openInventory(gui);
    }

    ItemStack createEmptySlot(String name) {
        ItemStack slot = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = slot.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            slot.setItemMeta(meta);
        }
        return slot;
    }

    void refreshGUI(Player player) {
        Inventory gui = player.getOpenInventory().getTopInventory();
        if (gui == null || gui.getSize() != 54) return;

        List<EntityType> selections = playerSelections.getOrDefault(player, List.of());
        boolean isReady = selections.size() >= 2;

        // 1〜4行目: mob一覧の選択状態を更新
        for (int i = 0; i < SELECTABLE_MOBS.size() && i < 36; i++) {
            EntityType type = SELECTABLE_MOBS.get(i);
            Material eggMat = getSpawnEggMaterial(type);
            if (eggMat == null) continue;

            ItemStack egg = new ItemStack(eggMat);
            ItemMeta meta = egg.getItemMeta();
            if (meta == null) continue;

            if (selections.contains(type)) {
                // 選択済み: エンチャントグロー + 緑チェックマーク
                meta.setDisplayName(ChatColor.GREEN + "\u2714 " + type.name());
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                if (NON_COMBAT_MOBS.contains(type)) {
                    meta.setLore(List.of(ChatColor.GREEN + "非戦闘mob", ChatColor.YELLOW + "クリックで選択解除"));
                } else {
                    meta.setLore(List.of(ChatColor.RED + "戦闘mob", ChatColor.YELLOW + "クリックで選択解除"));
                }
            } else {
                meta.setDisplayName(ChatColor.WHITE + type.name());
                if (NON_COMBAT_MOBS.contains(type)) {
                    meta.setLore(List.of(ChatColor.GREEN + "非戦闘mob"));
                } else {
                    meta.setLore(List.of(ChatColor.RED + "戦闘mob"));
                }
            }
            egg.setItemMeta(meta);
            gui.setItem(i, egg);
        }

        // 5行目: 区切り線の色を更新
        Material sepMat = isReady ? Material.LIME_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;
        for (int i = SEPARATOR_ROW_START; i <= SEPARATOR_ROW_END; i++) {
            ItemStack sep = new ItemStack(sepMat);
            ItemMeta sepMeta = sep.getItemMeta();
            if (sepMeta != null) {
                if (isReady && i == 40) {
                    // 中央に完了メッセージ
                    sepMeta.setDisplayName(ChatColor.GREEN + "\u2714 選択完了！相手を待っています...");
                } else {
                    sepMeta.setDisplayName(" ");
                }
                sep.setItemMeta(sepMeta);
            }
            gui.setItem(i, sep);
        }

        // 6行目: 選択スロットを更新
        if (selections.size() >= 1) {
            gui.setItem(SLOT_SELECT_1, createSelectedSlot(selections.get(0), 1));
        } else {
            gui.setItem(SLOT_SELECT_1, createEmptySlot("§e1体目を選んでください"));
        }
        if (selections.size() >= 2) {
            gui.setItem(SLOT_SELECT_2, createSelectedSlot(selections.get(1), 2));
        } else {
            gui.setItem(SLOT_SELECT_2, createEmptySlot("§e2体目を選んでください"));
        }
    }

    ItemStack createSelectedSlot(EntityType type, int number) {
        Material eggMat = getSpawnEggMaterial(type);
        if (eggMat == null) eggMat = Material.BARRIER;
        ItemStack item = new ItemStack(eggMat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + "" + number + "体目: " + type.name());
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
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

        int slot = event.getRawSlot();
        // 5行目（区切り線）・6行目（選択スロット）のクリックは無視
        if (slot >= SEPARATOR_ROW_START) return;

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
            refreshGUI(player);
            return;
        }

        // 2体選択済みなら拒否
        if (selections.size() >= 2) {
            player.sendMessage(ChatColor.RED + "既に2体選択済みです。解除するには選択済みmobをクリック");
            return;
        }

        selections.add(selectedType);
        player.sendMessage(ChatColor.GREEN + selectedType.name() + " を選択しました (" + selections.size() + "/2)");
        notifyOtherPlayer(player, selectedType, true);
        refreshGUI(player);

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

        // カウントダウン (3, 2, 1, 開始!)
        new BukkitRunnable() {
            int count = 3;
            @Override
            public void run() {
                if (count > 0) {
                    String msg = ChatColor.YELLOW + ">>> " + count + " <<<";
                    swarmPlayer.sendMessage(msg);
                    hunterPlayer.sendMessage(msg);
                    count--;
                } else {
                    cancel();
                    // カウントダウン完了 → ゲーム実開始
                    actuallyStartGame();
                }
            }
        }.runTaskTimer(this, 0, 20); // 0tick後に開始、20tick(1秒)間隔
    }

    void actuallyStartGame() {
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
