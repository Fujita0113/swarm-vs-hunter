package com.swarmvshunter;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.util.Vector;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import me.libraryaddict.disguise.DisguiseAPI;
import me.libraryaddict.disguise.disguisetypes.DisguiseType;
import me.libraryaddict.disguise.disguisetypes.MobDisguise;

import java.util.*;

public class SwarmVsHunter extends JavaPlugin implements Listener {

    // ゲームステート
    enum GameState { WAITING, SELECTING, PLAYING }
    GameState gameState = GameState.WAITING;

    // デバッグモード（1人テスト用）
    boolean debugMode = false;

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
    static final String MOB_SELECT_TITLE_DEBUG = "mob選択 (4体選んでください)";

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
            EntityType.CAVE_SPIDER, EntityType.ZOMBIFIED_PIGLIN,
            EntityType.VINDICATOR, EntityType.PILLAGER,
            EntityType.RAVAGER, EntityType.VEX
    );

    // フィールド内に配置されたmob
    Set<UUID> fieldMobs = new HashSet<>();

    // Swarm変身システム
    EntityType swarmDisguiseType = null;
    int swarmDeathCount = 0;
    int aggroRadius = 20;
    Set<UUID> followingMobs = new HashSet<>(); // Swarmに追従する同種mob
    long lastAbilityTime = 0; // 能力クールタイム管理
    Set<UUID> provokedMobs = new HashSet<>();
    BukkitRunnable followTask = null; // 追従タスク

    // 接触ダメージタスク（スライム、ブレイズ用）
    BukkitRunnable contactDamageTask = null;

    // 卵システム: 味方mob管理
    Map<UUID, Player> allyMobOwner = new HashMap<>(); // ally mob UUID → owner
    Set<UUID> hunterAllyMobs = new HashSet<>();
    Set<UUID> swarmAllyMobs = new HashSet<>();
    int eggStealRadius = 10;

    // タイマー・勝利条件
    BukkitRunnable gameTimer = null;
    int gameTimeRemaining = 180;
    int gameDurationSeconds = 180;
    int maxDeaths = 3;
    int hunterArrowCount = 16;
    int hunterFoodCount = 4;

    // 右クリック能力を持つmob（それ以外はアイテム付与 or パッシブのみ）
    static final Set<EntityType> RIGHT_CLICK_ABILITY_MOBS = Set.of(
            EntityType.CREEPER, EntityType.BLAZE, EntityType.RAVAGER
    );

    // mobステータステーブル（バニラ攻撃力）
    static final Map<EntityType, Double> MOB_ATTACK_DAMAGE = Map.ofEntries(
            Map.entry(EntityType.ZOMBIE, 3.0),
            Map.entry(EntityType.SKELETON, 2.0),
            Map.entry(EntityType.SPIDER, 2.0),
            Map.entry(EntityType.CREEPER, 0.0),
            Map.entry(EntityType.ENDERMAN, 7.0),
            Map.entry(EntityType.WITCH, 0.0),
            Map.entry(EntityType.BLAZE, 6.0),
            Map.entry(EntityType.SLIME, 3.0),
            Map.entry(EntityType.CAVE_SPIDER, 2.0),
            Map.entry(EntityType.ZOMBIFIED_PIGLIN, 5.0),
            Map.entry(EntityType.VINDICATOR, 13.0),
            Map.entry(EntityType.PILLAGER, 4.0),
            Map.entry(EntityType.RAVAGER, 12.0),
            Map.entry(EntityType.VEX, 9.0)
    );

    // mobステータステーブル（バニラ移動速度）
    static final Map<EntityType, Double> MOB_MOVEMENT_SPEED = Map.ofEntries(
            Map.entry(EntityType.ZOMBIE, 0.23),
            Map.entry(EntityType.SKELETON, 0.25),
            Map.entry(EntityType.SPIDER, 0.3),
            Map.entry(EntityType.CREEPER, 0.25),
            Map.entry(EntityType.ENDERMAN, 0.3),
            Map.entry(EntityType.WITCH, 0.25),
            Map.entry(EntityType.BLAZE, 0.23),
            Map.entry(EntityType.SLIME, 0.2),
            Map.entry(EntityType.CAVE_SPIDER, 0.3),
            Map.entry(EntityType.ZOMBIFIED_PIGLIN, 0.23),
            Map.entry(EntityType.VINDICATOR, 0.35),
            Map.entry(EntityType.PILLAGER, 0.35),
            Map.entry(EntityType.RAVAGER, 0.3),
            Map.entry(EntityType.VEX, 0.33),
            Map.entry(EntityType.PIG, 0.25),
            Map.entry(EntityType.COW, 0.2),
            Map.entry(EntityType.SHEEP, 0.23),
            Map.entry(EntityType.CHICKEN, 0.25),
            Map.entry(EntityType.RABBIT, 0.3),
            Map.entry(EntityType.TURTLE, 0.1),
            Map.entry(EntityType.HORSE, 0.225),
            Map.entry(EntityType.DONKEY, 0.175),
            Map.entry(EntityType.CAT, 0.3),
            Map.entry(EntityType.PARROT, 0.2),
            Map.entry(EntityType.FOX, 0.3),
            Map.entry(EntityType.FROG, 0.25),
            Map.entry(EntityType.MOOSHROOM, 0.2)
    );

    static double getMobAttackDamage(EntityType type) {
        return MOB_ATTACK_DAMAGE.getOrDefault(type, 0.0);
    }

    static double getMobMovementSpeed(EntityType type) {
        return MOB_MOVEMENT_SPEED.getOrDefault(type, 0.2);
    }

    @Override
    public void onEnable() {
        getLogger().info("SwarmVsHunter v3.0 enabled!");
        getServer().getPluginManager().registerEvents(this, this);
        loadConfig();
    }

    void loadConfig() {
        saveDefaultConfig();
        var config = getConfig();
        fieldSize = config.getInt("field.size", 70);
        mobCountPerType = config.getInt("field.mob_count_per_type", 8);
        aggroRadius = config.getInt("swarm.aggro_radius", 20);
        maxDeaths = config.getInt("swarm.max_deaths", 3);
        gameDurationSeconds = config.getInt("game.duration_seconds", 180);
        eggStealRadius = config.getInt("egg.steal_radius", 10);
        hunterArrowCount = config.getInt("hunter.arrow_count", 16);
        hunterFoodCount = config.getInt("hunter.food_count", 4);
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
            player.sendMessage(ChatColor.YELLOW + "/svh stop - ゲームを停止");
            player.sendMessage(ChatColor.YELLOW + "/svh debug <swarm|hunter> - デバッグモード（1人テスト）");
            return true;
        }

        if (args[0].equalsIgnoreCase("stop")) {
            if (gameState == GameState.WAITING) {
                player.sendMessage(ChatColor.RED + "ゲームが開始されていません");
                return true;
            }
            stopGame();
            player.sendMessage(ChatColor.GREEN + "ゲームを停止しました");
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

        if (args[0].equalsIgnoreCase("debug")) {
            if (!player.isOp()) {
                player.sendMessage(ChatColor.RED + "デバッグモードはOP権限が必要です");
                return true;
            }
            if (gameState == GameState.PLAYING || gameState == GameState.SELECTING) {
                player.sendMessage(ChatColor.RED + "ゲームが既に進行中です");
                return true;
            }
            if (args.length < 2 || (!args[1].equalsIgnoreCase("swarm") && !args[1].equalsIgnoreCase("hunter"))) {
                player.sendMessage(ChatColor.RED + "使い方: /svh debug <swarm|hunter>");
                return true;
            }

            debugMode = true;
            boolean isSwarm = args[1].equalsIgnoreCase("swarm");

            if (isSwarm) {
                swarmPlayer = player;
                hunterPlayer = null;
                player.sendMessage(ChatColor.GREEN + "[デバッグ] あなたはSwarmです！mobを4体選んでください");
            } else {
                hunterPlayer = player;
                swarmPlayer = null;
                player.sendMessage(ChatColor.GREEN + "[デバッグ] あなたはHunterです！mobを4体選んでください");
            }

            playerSelections.clear();
            selectedMobTypes.clear();
            gameState = GameState.SELECTING;

            openMobSelectionGUI(player);
            return true;
        }

        return false;
    }

    // === mob選択GUI ===
    static final int SLOT_SELECT_1 = 46; // 6行目: 1体目スロット
    static final int SLOT_SELECT_2 = 48; // 6行目: 2体目スロット
    static final int SEPARATOR_ROW_START = 36; // 5行目開始
    static final int SEPARATOR_ROW_END = 44;   // 5行目終了

    int getMaxSelections() {
        return debugMode ? 4 : 2;
    }

    String getMobSelectTitle() {
        return debugMode ? MOB_SELECT_TITLE_DEBUG : MOB_SELECT_TITLE;
    }

    void openMobSelectionGUI(Player player) {
        int size = 54; // 6行チェスト
        Inventory gui = Bukkit.createInventory(null, size, getMobSelectTitle());

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

        // 選択スロット（黒ガラス板）
        if (debugMode) {
            gui.setItem(46, createEmptySlot("§e1体目を選んでください"));
            gui.setItem(47, createEmptySlot("§e2体目を選んでください"));
            gui.setItem(49, createEmptySlot("§e3体目を選んでください"));
            gui.setItem(50, createEmptySlot("§e4体目を選んでください"));
        } else {
            gui.setItem(SLOT_SELECT_1, createEmptySlot("§e1体目を選んでください"));
            gui.setItem(SLOT_SELECT_2, createEmptySlot("§e2体目を選んでください"));
        }

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
        int maxSel = getMaxSelections();
        boolean isReady = selections.size() >= maxSel;

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
        if (debugMode) {
            int[] debugSlots = {46, 47, 49, 50};
            for (int i = 0; i < 4; i++) {
                if (selections.size() > i) {
                    gui.setItem(debugSlots[i], createSelectedSlot(selections.get(i), i + 1));
                } else {
                    gui.setItem(debugSlots[i], createEmptySlot("§e" + (i + 1) + "体目を選んでください"));
                }
            }
        } else {
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
    }

    ItemStack createSelectedSlot(EntityType type, int number) {
        Material eggMat = getSpawnEggMaterial(type);
        if (eggMat == null) eggMat = Material.BARRIER;
        ItemStack item = new ItemStack(eggMat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GREEN + "" + number + "体目: " + type.name());
            meta.setLore(java.util.Collections.singletonList(ChatColor.GRAY + "クリックで選択解除"));
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
        if (!event.getView().getTitle().equals(MOB_SELECT_TITLE) && !event.getView().getTitle().equals(MOB_SELECT_TITLE_DEBUG)) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();

        // 6行目の選択スロットをクリック → その選択を解除
        if (slot >= 45 && slot <= 53) {
            List<EntityType> selections = playerSelections.get(player);
            if (selections == null || selections.isEmpty()) return;
            int[] selectSlots;
            if (debugMode) {
                selectSlots = new int[]{46, 47, 49, 50};
            } else {
                selectSlots = new int[]{SLOT_SELECT_1, SLOT_SELECT_2};
            }
            for (int i = 0; i < selectSlots.length; i++) {
                if (slot == selectSlots[i] && i < selections.size()) {
                    EntityType removed = selections.remove(i);
                    player.sendMessage(ChatColor.YELLOW + removed.name() + " の選択を解除しました");
                    notifyOtherPlayer(player, removed, false);
                    refreshGUI(player);
                    return;
                }
            }
            return;
        }

        // 5行目（区切り線）のクリックは無視
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

        // 選択上限チェック
        int maxSel = getMaxSelections();
        if (selections.size() >= maxSel) {
            player.sendMessage(ChatColor.RED + "既に" + maxSel + "体選択済みです。解除するには選択済みmobをクリック");
            return;
        }

        selections.add(selectedType);
        player.sendMessage(ChatColor.GREEN + selectedType.name() + " を選択しました (" + selections.size() + "/" + maxSel + ")");
        notifyOtherPlayer(player, selectedType, true);
        refreshGUI(player);

        // 選択完了チェック
        if (bothPlayersReady()) {
            // 選択完了 → ゲーム開始処理
            if (debugMode) {
                player.closeInventory();
                selectedMobTypes.clear();
                selectedMobTypes.addAll(selections);
            } else {
                swarmPlayer.closeInventory();
                hunterPlayer.closeInventory();
                selectedMobTypes.clear();
                selectedMobTypes.addAll(playerSelections.get(swarmPlayer));
                selectedMobTypes.addAll(playerSelections.get(hunterPlayer));
            }
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
        if (debugMode) {
            Player debugPlayer = swarmPlayer != null ? swarmPlayer : hunterPlayer;
            return playerSelections.containsKey(debugPlayer) && playerSelections.get(debugPlayer).size() == 4;
        }
        return playerSelections.containsKey(swarmPlayer) && playerSelections.get(swarmPlayer).size() == 2
                && playerSelections.containsKey(hunterPlayer) && playerSelections.get(hunterPlayer).size() == 2;
    }

    void sendMessageToPlayer(Player player, String msg) {
        if (player != null && player.isOnline()) player.sendMessage(msg);
    }

    // === ゲーム開始処理 ===
    void startGame() {
        sendMessageToPlayer(swarmPlayer, ChatColor.GOLD + "全員の選択が完了！ゲーム開始準備中...");
        sendMessageToPlayer(hunterPlayer, ChatColor.GOLD + "全員の選択が完了！ゲーム開始準備中...");

        // フィールド生成（0,0,0を中心に固定、範囲内の最高地点を基準）
        Player activePlayer = swarmPlayer != null ? swarmPlayer : hunterPlayer;
        World world = activePlayer.getWorld();
        int ox = -fieldSize / 2;
        int oz = -fieldSize / 2;
        int maxY = 0;
        // 四隅+中央+辺の中点の9箇所で最高地点を調べる
        int[][] checkPoints = {
            {ox, oz}, {ox + fieldSize - 1, oz}, {ox, oz + fieldSize - 1}, {ox + fieldSize - 1, oz + fieldSize - 1},
            {0, 0}, {ox, oz + fieldSize / 2}, {ox + fieldSize - 1, oz + fieldSize / 2},
            {ox + fieldSize / 2, oz}, {ox + fieldSize / 2, oz + fieldSize - 1}
        };
        for (int[] p : checkPoints) {
            int y = world.getHighestBlockYAt(p[0], p[1]);
            if (y > maxY) maxY = y;
        }
        fieldOrigin = new Location(world, ox, maxY, oz);
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
                    sendMessageToPlayer(swarmPlayer, msg);
                    sendMessageToPlayer(hunterPlayer, msg);
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
        // ゲームモードをサバイバルに強制（アドベンチャー等だと攻撃できない）
        if (swarmPlayer != null) swarmPlayer.setGameMode(GameMode.SURVIVAL);
        if (hunterPlayer != null) hunterPlayer.setGameMode(GameMode.SURVIVAL);

        // Hunter装備
        if (hunterPlayer != null) equipHunter(hunterPlayer);

        // Swarmは装備なし、最大HP1設定（満腹回復を防ぐ）
        if (swarmPlayer != null) {
            swarmPlayer.getInventory().clear();
            AttributeInstance maxHpAttr = swarmPlayer.getAttribute(Attribute.MAX_HEALTH);
            if (maxHpAttr != null) maxHpAttr.setBaseValue(1.0);
            swarmPlayer.setHealth(1.0);
        }

        // テレポート
        Player activePlayer = swarmPlayer != null ? swarmPlayer : hunterPlayer;
        if (debugMode) {
            Location spawn = fieldOrigin.clone().add(fieldSize / 2.0, 1, fieldSize / 2.0);
            clearSpawnArea(spawn);
            activePlayer.teleport(spawn);
        } else {
            Location swarmSpawn = fieldOrigin.clone().add(fieldSize / 4.0, 1, fieldSize / 2.0);
            Location hunterSpawn = fieldOrigin.clone().add(fieldSize * 3.0 / 4.0, 1, fieldSize / 2.0);
            clearSpawnArea(swarmSpawn);
            clearSpawnArea(hunterSpawn);
            swarmPlayer.teleport(swarmSpawn);
            hunterPlayer.teleport(hunterSpawn);
        }

        // フィールド内の不要mob（ゲーム開始前に自然スポーンしたもの）を除去
        if (fieldOrigin != null) {
            for (Entity entity : fieldOrigin.getWorld().getLivingEntities()) {
                if (entity instanceof Player) continue;
                if (fieldMobs.contains(entity.getUniqueId())) continue;
                if (isInsideField(entity.getLocation())) {
                    entity.remove();
                }
            }
        }

        gameState = GameState.PLAYING;
        if (debugMode) {
            sendMessageToPlayer(activePlayer, ChatColor.GREEN + "[デバッグ] ゲーム開始！ /svh stop で終了");
        } else {
            sendMessageToPlayer(swarmPlayer, ChatColor.GREEN + "ゲーム開始！");
            sendMessageToPlayer(hunterPlayer, ChatColor.GREEN + "ゲーム開始！");
        }

        // タイマー開始（デバッグモードでは無効）
        if (!debugMode) {
            gameTimeRemaining = gameDurationSeconds;
            gameTimer = new BukkitRunnable() {
                @Override
                public void run() {
                    if (gameState != GameState.PLAYING) { cancel(); return; }
                    gameTimeRemaining--;

                    // アクションバーに残り時間表示
                    int min = gameTimeRemaining / 60;
                    int sec = gameTimeRemaining % 60;
                    String timeStr = String.format("%d:%02d", min, sec);
                    ChatColor color = gameTimeRemaining <= 30 ? ChatColor.RED : ChatColor.GREEN;
                    String bar = color + "残り時間: " + timeStr + ChatColor.GRAY + " | " +
                            ChatColor.YELLOW + "Swarm死亡: " + swarmDeathCount + "/" + maxDeaths;
                    for (Player p : new Player[]{swarmPlayer, hunterPlayer}) {
                        if (p != null && p.isOnline()) {
                            p.sendActionBar(net.kyori.adventure.text.Component.text(bar));
                        }
                    }

                    // タイムアップ → Hunter勝利
                    if (gameTimeRemaining <= 0) {
                        cancel();
                        endGame("Hunter", "3分間生き延びた！（タイムアップ）");
                    }
                }
            };
            gameTimer.runTaskTimer(this, 20, 20); // 1秒ごと
        }

        // 追従タスク: 10tick(0.5秒)ごとに追従mob・味方mobをオーナーに向かわせる
        followTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (gameState != GameState.PLAYING) { cancel(); return; }
                try {
                    // Swarm追従mob
                    if (swarmPlayer != null && !followingMobs.isEmpty()) {
                        World world = swarmPlayer.getWorld();
                        Location swarmLoc = swarmPlayer.getLocation();
                        for (UUID id : new HashSet<>(followingMobs)) {
                            Entity entity = world.getEntity(id);
                            if (entity instanceof Mob mob && mob.isValid()) {
                                if (mob.getTarget() == null) {
                                    mob.getPathfinder().moveTo(swarmLoc);
                                }
                            } else {
                                followingMobs.remove(id);
                            }
                        }
                    }

                    // 味方mob追従（Hunterの味方）
                    if (hunterPlayer != null && !hunterAllyMobs.isEmpty()) {
                        World world = hunterPlayer.getWorld();
                        Location hunterLoc = hunterPlayer.getLocation();
                        for (UUID id : new HashSet<>(hunterAllyMobs)) {
                            Entity entity = world.getEntity(id);
                            if (entity instanceof Mob mob && mob.isValid()) {
                                if (mob.getTarget() == null) {
                                    mob.getPathfinder().moveTo(hunterLoc);
                                }
                            } else {
                                hunterAllyMobs.remove(id);
                                allyMobOwner.remove(id);
                            }
                        }
                    }

                    // 味方mob追従（Swarmの味方）
                    if (swarmPlayer != null && !swarmAllyMobs.isEmpty()) {
                        Location swarmLoc = swarmPlayer.getLocation();
                        World world = swarmPlayer.getWorld();
                        for (UUID id : new HashSet<>(swarmAllyMobs)) {
                            Entity entity = world.getEntity(id);
                            if (entity instanceof Mob mob && mob.isValid()) {
                                if (mob.getTarget() == null) {
                                    mob.getPathfinder().moveTo(swarmLoc);
                                }
                            } else {
                                swarmAllyMobs.remove(id);
                                allyMobOwner.remove(id);
                            }
                        }
                    }

                    // 寝取りチェック（Swarm変身中、同種のHunter味方mobが近くにいたら奪う）
                    if (swarmPlayer != null && swarmDisguiseType != null && hunterPlayer != null) {
                        Location swarmLoc = swarmPlayer.getLocation();
                        World world = swarmPlayer.getWorld();
                        for (UUID id : new HashSet<>(hunterAllyMobs)) {
                            Entity entity = world.getEntity(id);
                            if (entity == null || !entity.isValid()) continue;
                            if (entity.getType() != swarmDisguiseType) continue;
                            if (entity.getLocation().distance(swarmLoc) <= eggStealRadius) {
                                // 寝取り発動！
                                hunterAllyMobs.remove(id);
                                swarmAllyMobs.add(id);
                                allyMobOwner.put(id, swarmPlayer);
                                if (entity instanceof Mob mob) {
                                    mob.setCustomName(ChatColor.RED + swarmPlayer.getName() + "の味方");
                                    mob.setTarget(null);
                                }
                                sendMessageToPlayer(swarmPlayer, ChatColor.GREEN + "Hunterの味方mobが寝返った！");
                                sendMessageToPlayer(hunterPlayer, ChatColor.RED + "味方mobがSwarmに寝返った！");
                            }
                        }
                    }
                } catch (Exception e) {
                    // テスト環境等で無視
                }
            }
        };
        followTask.runTaskTimer(this, 10, 10);
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
        hunter.getInventory().addItem(new ItemStack(Material.ARROW, hunterArrowCount));
        hunter.getInventory().addItem(new ItemStack(Material.BREAD, hunterFoodCount));
    }

    // === Swarm変身システム ===

    void transformSwarm(EntityType type) {
        // 既に変身中なら前の状態をクリーンアップ
        if (swarmDisguiseType != null) {
            releaseFollowingMobs();
            stopContactDamageTask();
            swarmPlayer.setAllowFlight(false);
            swarmPlayer.setFlying(false);
        }

        swarmDisguiseType = type;

        // LibsDisguisesでmobの見た目に変身
        applyDisguise(swarmPlayer, type);
        swarmPlayer.getInventory().clear();

        // 攻撃力・移動速度をmobのバニラ値に設定
        try {
            AttributeInstance atkAttr = swarmPlayer.getAttribute(Attribute.ATTACK_DAMAGE);
            if (atkAttr != null) atkAttr.setBaseValue(getMobAttackDamage(type));
            AttributeInstance spdAttr = swarmPlayer.getAttribute(Attribute.MOVEMENT_SPEED);
            if (spdAttr != null) spdAttr.setBaseValue(getMobMovementSpeed(type));
        } catch (Exception e) {
            // テスト環境等でAttribute未サポートの場合は無視
        }

        // ポーション効果クリア（前の変身の残り）
        for (var effect : swarmPlayer.getActivePotionEffects()) {
            swarmPlayer.removePotionEffect(effect.getType());
        }

        // ネームタグ非表示
        hideSwarmNametag();

        // 最大HP1維持
        AttributeInstance maxHp = swarmPlayer.getAttribute(Attribute.MAX_HEALTH);
        if (maxHp != null) maxHp.setBaseValue(1.0);
        swarmPlayer.setHealth(1.0);

        // 周囲の同種mobを追従させる（狼のように）
        recruitFollowingMobs(type);

        // mob別パッシブ効果・アイテム付与
        applyMobAbilities(type);

        swarmPlayer.sendMessage(ChatColor.GREEN + type.name() + " に変身した！周囲の同種mobがついてくる！");
        sendMessageToPlayer(hunterPlayer, ChatColor.RED + "Swarmが " + type.name() + " に変身した！警戒せよ！");

        if (RIGHT_CLICK_ABILITY_MOBS.contains(type)) {
            swarmPlayer.sendMessage(ChatColor.GOLD + "右クリックで能力発動！");
        }
    }

    void revertSwarm() {
        // 追従mob解除
        releaseFollowingMobs();
        stopContactDamageTask();

        swarmDisguiseType = null;
        swarmDeathCount++;

        // Disguise解除
        removeDisguise(swarmPlayer);

        // 飛行解除
        swarmPlayer.setAllowFlight(false);
        swarmPlayer.setFlying(false);

        // ポーション効果全除去
        for (var effect : swarmPlayer.getActivePotionEffects()) {
            swarmPlayer.removePotionEffect(effect.getType());
        }

        // ステータスリセット
        try {
            AttributeInstance atkAttr = swarmPlayer.getAttribute(Attribute.ATTACK_DAMAGE);
            if (atkAttr != null) atkAttr.setBaseValue(1.0); // 素手
            AttributeInstance spdAttr = swarmPlayer.getAttribute(Attribute.MOVEMENT_SPEED);
            if (spdAttr != null) spdAttr.setBaseValue(0.1); // デフォルト
        } catch (Exception e) {
            // テスト環境等でAttribute未サポートの場合は無視
        }

        // ネームタグ表示復帰
        showSwarmNametag();

        // HP1でリスポーン（フィールド内ランダム位置）
        swarmPlayer.getInventory().clear();
        swarmPlayer.setHealth(1.0);
        if (fieldOrigin != null) {
            Random rand = new Random();
            int x = fieldOrigin.getBlockX() + 3 + rand.nextInt(fieldSize - 6);
            int z = fieldOrigin.getBlockZ() + 3 + rand.nextInt(fieldSize - 6);
            swarmPlayer.teleport(new Location(fieldOrigin.getWorld(), x + 0.5, fieldOrigin.getBlockY() + 1, z + 0.5));
        }

        swarmPlayer.sendMessage(ChatColor.YELLOW + "Hunterに倒された！人間に戻された (死亡: " + swarmDeathCount + "/" + maxDeaths + ")");
        sendMessageToPlayer(hunterPlayer, ChatColor.GREEN + "Swarmを倒した！ (キル: " + swarmDeathCount + "/" + maxDeaths + ")");

        // 勝利判定
        checkWinConditions();
    }

    // === 勝利判定・ゲーム終了 ===
    void endGame(String winner, String reason) {
        if (gameState != GameState.PLAYING) return;

        // タイトル表示
        String title;
        String subtitle;
        if (winner.equals("Swarm")) {
            title = ChatColor.RED + "Swarm WIN!";
            subtitle = ChatColor.YELLOW + reason;
        } else {
            title = ChatColor.AQUA + "Hunter WIN!";
            subtitle = ChatColor.YELLOW + reason;
        }

        for (Player p : new Player[]{swarmPlayer, hunterPlayer}) {
            if (p != null && p.isOnline()) {
                p.sendTitle(title, subtitle, 10, 60, 20);
                p.sendMessage(ChatColor.GOLD + "===========================");
                p.sendMessage(ChatColor.GOLD + " " + winner + " の勝利！");
                p.sendMessage(ChatColor.GOLD + " " + reason);
                p.sendMessage(ChatColor.GOLD + "===========================");
            }
        }

        // 3秒後にクリーンアップ
        new BukkitRunnable() {
            @Override
            public void run() {
                stopGame();
            }
        }.runTaskLater(this, 60); // 3秒後
    }

    void checkWinConditions() {
        if (gameState != GameState.PLAYING || debugMode) return;

        // Hunter勝利: Swarmを規定回数倒した
        if (swarmDeathCount >= maxDeaths) {
            endGame("Hunter", "Swarmを" + maxDeaths + "回倒した！");
        }
    }

    // === ゲーム停止・クリーンアップ ===
    void stopGame() {
        // タイマー停止
        if (gameTimer != null) {
            gameTimer.cancel();
            gameTimer = null;
        }
        // 追従タスク停止
        if (followTask != null) {
            followTask.cancel();
            followTask = null;
        }
        // フィールドmobを全除去
        if (fieldOrigin != null) {
            World world = fieldOrigin.getWorld();
            for (UUID id : fieldMobs) {
                Entity entity = world.getEntity(id);
                if (entity != null) entity.remove();
            }
        }
        // 味方mob除去
        if (fieldOrigin != null) {
            World allyWorld = fieldOrigin.getWorld();
            for (UUID id : hunterAllyMobs) {
                Entity e = allyWorld.getEntity(id);
                if (e != null) e.remove();
            }
            for (UUID id : swarmAllyMobs) {
                Entity e = allyWorld.getEntity(id);
                if (e != null) e.remove();
            }
        }

        fieldMobs.clear();
        followingMobs.clear();
        provokedMobs.clear();
        hunterAllyMobs.clear();
        swarmAllyMobs.clear();
        allyMobOwner.clear();

        // プレイヤー状態復元
        for (Player p : new Player[]{swarmPlayer, hunterPlayer}) {
            if (p == null || !p.isOnline()) continue;
            // Disguise解除
            removeDisguise(p);
            p.getInventory().clear();
            p.getInventory().setArmorContents(null);
            // 最大HP復元
            AttributeInstance maxHp = p.getAttribute(Attribute.MAX_HEALTH);
            if (maxHp != null) maxHp.setBaseValue(20.0);
            p.setHealth(20.0);
            p.setFoodLevel(20);
            p.setSaturation(5.0f);
            // ポーション効果除去
            for (var effect : p.getActivePotionEffects()) {
                p.removePotionEffect(effect.getType());
            }
            // 飛行解除
            p.setAllowFlight(false);
            p.setFlying(false);
            // ステータスリセット
            try {
                AttributeInstance atkAttr = p.getAttribute(Attribute.ATTACK_DAMAGE);
                if (atkAttr != null) atkAttr.setBaseValue(1.0);
                AttributeInstance spdAttr = p.getAttribute(Attribute.MOVEMENT_SPEED);
                if (spdAttr != null) spdAttr.setBaseValue(0.1);
            } catch (Exception e) {}
        }

        // ネームタグ復帰
        if (swarmPlayer != null) showSwarmNametag();

        // 接触ダメージタスク停止
        stopContactDamageTask();

        // ステート初期化
        swarmDisguiseType = null;
        swarmDeathCount = 0;
        selectedMobTypes.clear();
        playerSelections.clear();
        debugMode = false;
        gameState = GameState.WAITING;

        Bukkit.broadcastMessage(ChatColor.GOLD + "[SVH] ゲームが停止されました");
    }

    // 周囲の同種mobをSwarmの追従mobとして登録
    void recruitFollowingMobs(EntityType type) {
        followingMobs.clear();
        try {
            // Pass 1: 対象mobを収集
            List<Mob> recruits = new ArrayList<>();
            for (var entity : swarmPlayer.getWorld().getNearbyEntities(swarmPlayer.getLocation(), aggroRadius, aggroRadius, aggroRadius)) {
                if (entity.getType() == type && fieldMobs.contains(entity.getUniqueId()) && entity instanceof Mob mob) {
                    recruits.add(mob);
                }
            }
            // Pass 2: 怒りクリア（respawnでUUID変わる場合あり）→ followingMobsに登録
            for (Mob mob : recruits) {
                mob.setTarget(null);
                clearMobAnger(mob); // Piglinはrespawnされ、followingMobsに新UUIDが入る
                // respawnされなかったmob（PigZombie等）はここで追加
                if (mob.isValid() && !mob.isDead()) {
                    followingMobs.add(mob.getUniqueId());
                }
            }
        } catch (Exception e) {
            // getNearbyEntitiesがテスト環境で動かない場合は無視
        }
    }

    // ピグリン・ゾンビピグリン・ホグリン等の内部怒り状態をクリア
    void clearMobAnger(Mob mob) {
        // ZombifiedPiglin（PigZombie API）: setAngry/setAnger で怒り解除
        if (mob instanceof PigZombie pigZombie) {
            pigZombie.setAngry(false);
            pigZombie.setAnger(0);
        }
        // Piglin: Brain系は内部メモリに怒りが残るためAPIでは消せない
        // → 削除して同じ位置に新品を再スポーンする
        if (mob instanceof Piglin) {
            respawnMobClean(mob);
        }
    }

    // Brain系mobの怒りリセット: 削除→再スポーンで完全に怒り状態を消す
    void respawnMobClean(Mob oldMob) {
        Location loc = oldMob.getLocation();
        EntityType type = oldMob.getType();
        UUID oldId = oldMob.getUniqueId();
        fieldMobs.remove(oldId);
        followingMobs.remove(oldId);
        oldMob.remove();

        try {
            Entity newEntity = loc.getWorld().spawnEntity(loc, type);
            if (newEntity instanceof Mob newMob) {
                fieldMobs.add(newEntity.getUniqueId());
                followingMobs.add(newEntity.getUniqueId());
                newMob.setTarget(null);
            }
        } catch (Exception e) {
            // スポーン失敗は無視
        }
    }

    // イベントハンドラ内から安全に怒りクリア（次tickで実行、respawnが必要な場合も安全）
    void clearMobAngerDeferred(Mob mob) {
        // PigZombie: APIで即座にクリア可能（respawn不要）
        if (mob instanceof PigZombie pigZombie) {
            pigZombie.setAngry(false);
            pigZombie.setAnger(0);
            return;
        }
        // Piglin: 次tickでrespawnクリア
        if (mob instanceof Piglin) {
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (mob.isValid() && !mob.isDead() && fieldMobs.contains(mob.getUniqueId())) {
                    respawnMobClean(mob);
                }
            }, 1L);
        }
    }

    // 追従mobを全て解除（中立に戻す）
    void releaseFollowingMobs() {
        try {
            World world = swarmPlayer.getWorld();
            for (UUID id : followingMobs) {
                Entity entity = world.getEntity(id);
                if (entity instanceof Mob mob) {
                    mob.setTarget(null);
                }
            }
        } catch (Exception e) {
            // テスト環境等で無視
        }
        followingMobs.clear();
    }

    // Swarmが何かを攻撃した時、追従mobも同じターゲットを攻撃する
    void commandFollowingMobsToAttack(Entity target) {
        try {
            World world = swarmPlayer.getWorld();
            for (UUID id : followingMobs) {
                Entity entity = world.getEntity(id);
                if (entity instanceof Mob mob) {
                    if (target instanceof LivingEntity livingTarget) {
                        mob.setTarget(livingTarget);
                    }
                }
            }
        } catch (Exception e) {
            // テスト環境等で無視
        }
    }

    void applyDisguise(Player player, EntityType type) {
        try {
            DisguiseAPI.undisguiseToAll(player);
            MobDisguise disguise = new MobDisguise(DisguiseType.getType(type));
            DisguiseAPI.disguiseToAll(player, disguise);
        } catch (NoClassDefFoundError | Exception e) {
            // テスト環境等でLibsDisguises不在の場合は無視
        }
    }

    void removeDisguise(Player player) {
        try {
            DisguiseAPI.undisguiseToAll(player);
        } catch (NoClassDefFoundError | Exception e) {
            // テスト環境等でLibsDisguises不在の場合は無視
        }
    }

    void hideSwarmNametag() {
        try {
            Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
            Team team = board.getTeam("svh_hidden");
            if (team == null) {
                team = board.registerNewTeam("svh_hidden");
                team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
            }
            team.addEntry(swarmPlayer.getName());
        } catch (Exception e) {
            // テスト環境で無視
        }
    }

    void showSwarmNametag() {
        try {
            Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
            Team team = board.getTeam("svh_hidden");
            if (team != null) {
                team.removeEntry(swarmPlayer.getName());
            }
        } catch (Exception e) {
            // テスト環境で無視
        }
    }

    // === ダメージイベント処理 ===

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (gameState != GameState.PLAYING) return;

        // 攻撃者の特定（飛び道具の場合は射手を辿る）
        Entity damager = event.getDamager();
        if (damager instanceof Projectile proj && proj.getShooter() instanceof Entity shooter) {
            damager = (Entity) shooter;
        }

        // Swarmが何かを攻撃した時の共通パッシブ効果
        if (damager.equals(swarmPlayer) && swarmDisguiseType != null && event.getEntity() instanceof LivingEntity target) {
            // 洞窟グモ: 攻撃ヒット時に毒付与
            if (swarmDisguiseType == EntityType.CAVE_SPIDER) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 0));
            }
        }

        // Case 1a: Swarmが変身中にHunterを攻撃 → 追従mobもHunterを攻撃
        if (damager.equals(swarmPlayer) && event.getEntity().equals(hunterPlayer)) {
            if (swarmDisguiseType != null) {
                commandFollowingMobsToAttack(hunterPlayer);
            }
            return;
        }

        // Case 1b: Swarmがフィールドmobを殴る
        if (damager.equals(swarmPlayer) && fieldMobs.contains(event.getEntity().getUniqueId())) {
            if (swarmDisguiseType != null) {
                // 変身中 → 追従mobもそのmobを攻撃
                commandFollowingMobsToAttack(event.getEntity());
            }
            if (event.getEntity() instanceof Mob mob) {
                // 未変身でも変身中でも：殴ったmobはSwarmに反撃する（挑発）
                provokedMobs.add(mob.getUniqueId());
                mob.setTarget(swarmPlayer);
            }
            return;
        }

        // Case 2: フィールドmobがSwarmを攻撃 → 変身
        if (event.getEntity().equals(swarmPlayer) && fieldMobs.contains(damager.getUniqueId())) {
            event.setCancelled(true);
            EntityType mobType = damager.getType();
            provokedMobs.remove(damager.getUniqueId());
            if (damager instanceof Mob mob) {
                mob.setTarget(null);
            }
            transformSwarm(mobType);
            return;
        }

        // Case 3: HunterがSwarmを攻撃
        if (event.getEntity().equals(swarmPlayer) && damager.equals(hunterPlayer)) {
            event.setCancelled(true);
            if (swarmDisguiseType != null) {
                // 変身中 → 人間に戻す（キルカウント）
                revertSwarm();
            }
            // 人間状態 → ダメージキャンセルのみ（キルカウントなし）
            return;
        }
    }

    // === Hunter死亡 → Swarm勝利判定 ===
    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (gameState != GameState.PLAYING || debugMode) return;
        if (!event.getEntity().equals(hunterPlayer)) return;

        // Swarmが変身中にHunterが死亡 → Swarm勝利
        if (swarmDisguiseType != null) {
            // 死亡メッセージをカスタマイズ
            event.setDeathMessage(null);
            endGame("Swarm", "Hunterを倒した！");
        }
    }

    // === フィールド内環境ダメージ無効（全mob + Swarm変身中） ===
    private static final java.util.Set<EntityDamageEvent.DamageCause> ENVIRONMENTAL_CAUSES = java.util.EnumSet.of(
            EntityDamageEvent.DamageCause.FIRE, EntityDamageEvent.DamageCause.FIRE_TICK,
            EntityDamageEvent.DamageCause.LAVA, EntityDamageEvent.DamageCause.DROWNING,
            EntityDamageEvent.DamageCause.DRYOUT, EntityDamageEvent.DamageCause.FREEZE,
            EntityDamageEvent.DamageCause.HOT_FLOOR);

    @EventHandler(priority = EventPriority.LOW)
    public void onEntityDamageEnvironment(EntityDamageEvent event) {
        if (gameState != GameState.PLAYING) return;
        if (!ENVIRONMENTAL_CAUSES.contains(event.getCause())) return;

        // フィールドmob・味方mobの環境ダメージ無効
        UUID entityId = event.getEntity().getUniqueId();
        if (fieldMobs.contains(entityId) || allyMobOwner.containsKey(entityId)) {
            event.setCancelled(true);
            return;
        }

        // Swarm変身中の環境ダメージ無効（雨、炎、溶岩等）
        if (event.getEntity().equals(swarmPlayer) && swarmDisguiseType != null) {
            event.setCancelled(true);
            return;
        }
    }

    // Swarm変身中のmob固有耐性（落下ダメージ無効等 — フィールドルールではカバーしない）
    @EventHandler(priority = EventPriority.LOW)
    public void onEntityDamage(EntityDamageEvent event) {
        if (gameState != GameState.PLAYING) return;
        if (!event.getEntity().equals(swarmPlayer)) return;
        if (swarmDisguiseType == null) return;

        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            if (swarmDisguiseType == EntityType.SPIDER || swarmDisguiseType == EntityType.CAVE_SPIDER
                    || swarmDisguiseType == EntityType.SLIME) {
                event.setCancelled(true);
            }
        }

        // クリーパー変身中は自分の爆発ダメージを無効化
        if (swarmDisguiseType == EntityType.CREEPER
                && (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION
                    || event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION)) {
            event.setCancelled(true);
        }
    }

    // ホグリンのゾグリン化防止（ディメンション不適合による変換をキャンセル）
    @EventHandler
    public void onEntityTransform(EntityTransformEvent event) {
        if (gameState != GameState.PLAYING) return;
        UUID eid = event.getEntity().getUniqueId();
        if (fieldMobs.contains(eid) || allyMobOwner.containsKey(eid)) {
            event.setCancelled(true);
        }
    }

    // エンダーマンの雨テレポ暴走防止（フィールドmobのエンダーマンが勝手にTPしないようにする）
    @EventHandler
    public void onEntityTeleport(EntityTeleportEvent event) {
        if (gameState != GameState.PLAYING) return;
        if (event.getEntity().getType() == EntityType.ENDERMAN
                && fieldMobs.contains(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // クリーパー爆発によるブロック破壊を防止
    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        if (gameState != GameState.PLAYING) return;
        event.blockList().clear();
    }

    // === 卵システム: mob撃破 → 卵ドロップ ===
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (gameState != GameState.PLAYING) return;
        UUID mobId = event.getEntity().getUniqueId();
        if (!fieldMobs.contains(mobId)) return;

        // 通常ドロップ・経験値を無効化
        event.getDrops().clear();
        event.setDroppedExp(0);

        // SVH卵をドロップ（カスタムloreで識別）
        EntityType type = event.getEntityType();
        Material eggMat = getSpawnEggMaterial(type);
        if (eggMat == null) return;

        ItemStack egg = new ItemStack(eggMat);
        ItemMeta meta = egg.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "味方の卵: " + type.name());
            meta.setLore(List.of(
                    ChatColor.GRAY + "右クリックで味方mobを召喚",
                    ChatColor.DARK_PURPLE + "SVH_ALLY_EGG"
            ));
            egg.setItemMeta(meta);
        }
        event.getEntity().getWorld().dropItemNaturally(event.getEntity().getLocation(), egg);

        // fieldMobsから除去
        fieldMobs.remove(mobId);
        followingMobs.remove(mobId);
    }

    // SVH卵かどうかの判定
    boolean isSvhAllyEgg(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) return false;
        return meta.getLore().contains(ChatColor.DARK_PURPLE + "SVH_ALLY_EGG");
    }

    // 卵使用 → 味方mob召喚
    void spawnAllyMob(Player player, EntityType type, Location loc) {
        try {
            Entity entity = loc.getWorld().spawnEntity(loc, type);
            if (entity instanceof Mob mob) {
                mob.setTarget(null);
                mob.setCustomName(ChatColor.GREEN + player.getName() + "の味方");
                mob.setCustomNameVisible(true);
            }
            UUID allyId = entity.getUniqueId();
            allyMobOwner.put(allyId, player);
            if (player.equals(hunterPlayer)) {
                hunterAllyMobs.add(allyId);
            } else if (player.equals(swarmPlayer)) {
                swarmAllyMobs.add(allyId);
            }
            player.sendMessage(ChatColor.GREEN + type.name() + " の味方mobを召喚した！");
        } catch (Exception e) {
            // スポーン失敗
        }
    }

    // === ブロック破壊・設置禁止 ===
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (gameState != GameState.PLAYING) return;
        if (fieldOrigin != null && isInsideField(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (gameState != GameState.PLAYING) return;
        if (fieldOrigin != null && isInsideField(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
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

    // === フィールド内の自然スポーン抑制 ===
    @EventHandler
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (gameState != GameState.PLAYING) return;
        if (event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM
                || event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.COMMAND) return;
        // プラグインが配置したmobは許可済み（fieldMobsに登録済み）
        if (fieldMobs.contains(event.getEntity().getUniqueId())) return;

        // フィールド範囲内かチェック
        if (fieldOrigin != null && isInsideField(event.getEntity().getLocation())) {
            event.setCancelled(true);
        }
    }

    boolean isInsideField(Location loc) {
        int ox = fieldOrigin.getBlockX();
        int oz = fieldOrigin.getBlockZ();
        int x = loc.getBlockX();
        int z = loc.getBlockZ();
        return x >= ox && x < ox + fieldSize && z >= oz && z < oz + fieldSize;
    }

    // === 中立mobシステム: EntityTargetEventキャンセル（追従mob・挑発mob・味方mob例外あり） ===
    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        if (gameState != GameState.PLAYING) return;
        if (event.getTarget() == null) return;
        UUID mobId = event.getEntity().getUniqueId();

        // 味方mobのターゲット制御
        if (allyMobOwner.containsKey(mobId)) {
            Player owner = allyMobOwner.get(mobId);
            // 味方mobはオーナーを狙わない
            if (event.getTarget().equals(owner)) {
                event.setCancelled(true);
            }
            return; // 味方mobは自由にターゲット可能（オーナー以外）
        }

        if (!fieldMobs.contains(mobId)) return;

        // 追従mobはSwarm以外へのターゲット設定を許可（Swarmが指令した攻撃対象を狙える）
        // Swarmを狙おうとする場合はキャンセル（味方の誤爆による敵対化防止）
        if (followingMobs.contains(mobId)) {
            if (event.getTarget().equals(swarmPlayer)) {
                event.setCancelled(true);
                // ピグリン系は怒りが残り続けるのでイベント外で再スポーンクリア
                if (event.getEntity() instanceof Mob mob) {
                    clearMobAngerDeferred(mob);
                }
            }
            return;
        }
        // 挑発されたmobがSwarmを狙うのは許可
        if (provokedMobs.contains(mobId) && event.getTarget().equals(swarmPlayer)) return;

        // それ以外のターゲット設定はキャンセル（中立化）
        event.setCancelled(true);
        // ピグリン系の集団怒りもクリア
        if (event.getEntity() instanceof Mob mob) {
            clearMobAngerDeferred(mob);
        }
    }

    // === Swarm右クリック能力（クールタイムなし） ===

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (gameState != GameState.PLAYING) return;

        // 右クリックのみ
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();

        // SVH卵の使用（全プレイヤー共通）
        if (isSvhAllyEgg(event.getItem())) {
            event.setCancelled(true);
            String typeName = event.getItem().getType().name().replace("_SPAWN_EGG", "");
            try {
                EntityType type = EntityType.valueOf(typeName);
                Location spawnLoc = player.getLocation().add(player.getLocation().getDirection().normalize().multiply(2));
                spawnLoc.setY(player.getLocation().getY());
                spawnAllyMob(player, type, spawnLoc);
                // 卵を1個消費
                event.getItem().setAmount(event.getItem().getAmount() - 1);
            } catch (IllegalArgumentException e) {
                // invalid type
            }
            return;
        }

        // スポーンエッグの通常使用を禁止（フィールド内）
        if (event.getItem() != null && event.getItem().getType().name().endsWith("_SPAWN_EGG")) {
            if (fieldOrigin != null && isInsideField(player.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }

        // Swarm右クリック能力
        if (!player.equals(swarmPlayer)) return;
        if (swarmDisguiseType == null) return;
        if (!RIGHT_CLICK_ABILITY_MOBS.contains(swarmDisguiseType)) return;

        // アイテムを持っている場合はそのアイテムの通常動作を優先（弓、エンダーパール等）
        if (event.getItem() != null && event.getItem().getType() != Material.AIR) return;

        useAbility(swarmDisguiseType);
    }

    // ウィッチ: スプラッシュポーション自爆防止
    @EventHandler
    public void onPotionSplash(PotionSplashEvent event) {
        if (gameState != GameState.PLAYING) return;
        if (!(event.getPotion().getShooter() instanceof Player shooter)) return;
        if (!shooter.equals(swarmPlayer)) return;
        if (swarmDisguiseType != EntityType.WITCH) return;
        // 自分と味方mobへの効果を除外
        event.setIntensity(swarmPlayer, 0);
        for (LivingEntity affected : event.getAffectedEntities()) {
            if (followingMobs.contains(affected.getUniqueId())) {
                event.setIntensity(affected, 0);
            }
        }
    }

    // ウィッチ: ポーション無限補充（投げたら同じものを1tick後に補充）
    @EventHandler
    public void onPotionThrow(ProjectileLaunchEvent event) {
        if (gameState != GameState.PLAYING) return;
        if (!(event.getEntity() instanceof ThrownPotion potion)) return;
        if (!(potion.getShooter() instanceof Player shooter)) return;
        if (!shooter.equals(swarmPlayer)) return;
        if (swarmDisguiseType != EntityType.WITCH) return;

        // 投げたポーションと同じものを1tick後に補充
        ItemStack thrownItem = potion.getItem().clone();
        new BukkitRunnable() {
            @Override
            public void run() {
                if (swarmPlayer != null && swarmPlayer.isOnline() && swarmDisguiseType == EntityType.WITCH) {
                    swarmPlayer.getInventory().addItem(thrownItem);
                }
            }
        }.runTaskLater(this, 1);
    }

    // エンダーマン: エンダーパール投げ → 即座にテレポート（ダメージなし、無限補充）
    @EventHandler
    public void onEnderPearlThrow(ProjectileLaunchEvent event) {
        if (gameState != GameState.PLAYING) return;
        if (!(event.getEntity() instanceof EnderPearl pearl)) return;
        if (!(pearl.getShooter() instanceof Player shooter)) return;
        if (!shooter.equals(swarmPlayer)) return;
        if (swarmDisguiseType != EntityType.ENDERMAN) return;

        // バニラのパール飛行をキャンセル
        event.setCancelled(true);

        // 視線方向にレイキャスト（最大40ブロック）
        var rayResult = shooter.getWorld().rayTraceBlocks(
                shooter.getEyeLocation(),
                shooter.getEyeLocation().getDirection(),
                40,
                FluidCollisionMode.NEVER,
                true
        );

        Location dest;
        if (rayResult != null && rayResult.getHitBlock() != null) {
            // ブロックにぶつかった→その手前にテレポート
            dest = rayResult.getHitPosition().toLocation(shooter.getWorld());
            // ブロックの面に応じて少しずらす（埋まり防止）
            dest.add(rayResult.getHitBlockFace().getDirection().multiply(0.5));
        } else {
            // 何も当たらなかった→40ブロック先
            dest = shooter.getEyeLocation().add(shooter.getEyeLocation().getDirection().multiply(40));
        }

        // 向きを維持
        dest.setYaw(shooter.getLocation().getYaw());
        dest.setPitch(shooter.getLocation().getPitch());

        // エフェクト（出発地点）
        Location from = shooter.getLocation();
        from.getWorld().playSound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 1.0f);
        from.getWorld().spawnParticle(Particle.PORTAL, from.add(0, 1, 0), 40, 0.3, 0.5, 0.3, 0.5);

        // テレポート
        shooter.teleport(dest);

        // エフェクト（到着地点）
        dest.getWorld().playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 1.2f);
        dest.getWorld().spawnParticle(Particle.PORTAL, dest.clone().add(0, 1, 0), 40, 0.3, 0.5, 0.3, 0.5);

        // パール補充（無限）
        new BukkitRunnable() {
            @Override
            public void run() {
                if (swarmPlayer != null && swarmPlayer.isOnline() && swarmDisguiseType == EntityType.ENDERMAN) {
                    swarmPlayer.getInventory().addItem(new ItemStack(Material.ENDER_PEARL, 1));
                }
            }
        }.runTaskLater(this, 1);
    }

    // エンダーパールによるダメージ無効化（万が一バニラTPが発動した場合のフォールバック）
    @EventHandler
    public void onPlayerTeleportByPearl(PlayerTeleportEvent event) {
        if (gameState != GameState.PLAYING) return;
        if (event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) return;
        if (!event.getPlayer().equals(swarmPlayer)) return;
        if (swarmDisguiseType != EntityType.ENDERMAN) return;
        // バニラのパールTP自体をキャンセル（カスタムTPで処理済み）
        event.setCancelled(true);
    }

    void useAbility(EntityType type) {
        // クリーパーのみクールタイム3秒
        if (type == EntityType.CREEPER) {
            long now = System.currentTimeMillis();
            if (now - lastAbilityTime < 3000) {
                long remaining = (3000 - (now - lastAbilityTime)) / 1000 + 1;
                swarmPlayer.sendActionBar(net.kyori.adventure.text.Component.text(
                        "§c爆発クールタイム: あと" + remaining + "秒"));
                return;
            }
            lastAbilityTime = now;
        }
        switch (type) {
            case CREEPER -> abilityCreeper();
            case BLAZE -> abilityBlaze();
            case RAVAGER -> abilityRavager();
            default -> {}
        }
    }

    // === 右クリック能力（クールタイムなし） ===
    // === 右クリック能力（クールタイムなし） ===

    // クリーパー: 爆発（Swarm自身ノーダメージ、味方mob除外）
    void abilityCreeper() {
        Location loc = swarmPlayer.getLocation();
        World world = loc.getWorld();
        world.playSound(loc, Sound.ENTITY_CREEPER_PRIMED, 2.0f, 1.0f);
        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (gameState != GameState.PLAYING || swarmDisguiseType == null) { cancel(); return; }
                tick++;
                Location current = swarmPlayer.getLocation().add(0, 1, 0);
                world.spawnParticle(Particle.EXPLOSION, current, 1, 0, 0, 0, 0);
                world.spawnParticle(Particle.SMOKE, current, 8, 0.3, 0.5, 0.3, 0.02);
                if (tick >= 15) {
                    cancel();
                    Location expLoc = swarmPlayer.getLocation();
                    world.createExplosion(expLoc.getX(), expLoc.getY(), expLoc.getZ(), 3.0f, false, false);
                    // 自分を爆発の反動で後方にノックバック
                    Vector knockback = swarmPlayer.getLocation().getDirection().normalize().multiply(-1.5);
                    knockback.setY(0.5);
                    swarmPlayer.setVelocity(knockback);
                    for (Entity e : world.getNearbyEntities(expLoc, 5, 5, 5)) {
                        if (e.equals(swarmPlayer)) continue;
                        if (followingMobs.contains(e.getUniqueId())) continue;
                        if (e instanceof LivingEntity living) {
                            living.damage(8.0, swarmPlayer);
                        }
                    }
                }
            }
        }.runTaskTimer(this, 0, 1);
    }

    // ブレイズ: ファイアボール射撃
    void abilityBlaze() {
        Location eye = swarmPlayer.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        eye.getWorld().playSound(eye, Sound.ENTITY_BLAZE_SHOOT, 1.5f, 1.0f);
        eye.getWorld().spawnParticle(Particle.FLAME, eye, 15, 0.2, 0.2, 0.2, 0.08);
        var fireball = (SmallFireball) swarmPlayer.getWorld().spawnEntity(
                eye.add(dir), EntityType.SMALL_FIREBALL);
        fireball.setDirection(dir);
        fireball.setShooter(swarmPlayer);
        fireball.setIsIncendiary(false);
    }

    // ラヴェジャー: 咆哮（味方mob除外）
    void abilityRavager() {
        Location loc = swarmPlayer.getLocation();
        loc.getWorld().playSound(loc, Sound.ENTITY_RAVAGER_ROAR, 2.0f, 1.0f);
        for (int ring = 0; ring < 4; ring++) {
            final int r = ring;
            new BukkitRunnable() {
                @Override
                public void run() {
                    Location center = swarmPlayer.getLocation();
                    double radius = 1.5 + r * 1.5;
                    for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 12) {
                        double x = Math.cos(angle) * radius;
                        double z = Math.sin(angle) * radius;
                        center.getWorld().spawnParticle(Particle.CLOUD,
                                center.clone().add(x, 0.5, z), 2, 0.1, 0.1, 0.1, 0.02);
                    }
                }
            }.runTaskLater(this, ring * 2L);
        }
        for (Entity e : loc.getWorld().getNearbyEntities(loc, 6, 3, 6)) {
            if (e.equals(swarmPlayer)) continue;
            if (followingMobs.contains(e.getUniqueId())) continue;
            if (e instanceof LivingEntity living) {
                Vector kb = living.getLocation().toVector()
                        .subtract(loc.toVector()).normalize().multiply(2.0).setY(0.6);
                living.setVelocity(kb);
                living.damage(6.0, swarmPlayer);
            }
        }
    }

    // === mob別パッシブ効果・アイテム付与 ===

    void applyMobAbilities(EntityType type) {
        switch (type) {
            // === 戦闘mob ===
            case ZOMBIE -> {
                swarmPlayer.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
                // ランダムな剣（石〜ダイヤ）
                Material[] swords = {Material.STONE_SWORD, Material.IRON_SWORD, Material.GOLDEN_SWORD, Material.DIAMOND_SWORD};
                swarmPlayer.getInventory().addItem(new ItemStack(swords[new Random().nextInt(swords.length)]));
            }
            case SKELETON -> {
                ItemStack bow = new ItemStack(Material.BOW);
                bow.addEnchantment(Enchantment.INFINITY, 1);
                swarmPlayer.getInventory().addItem(bow);
                swarmPlayer.getInventory().addItem(new ItemStack(Material.ARROW, 1));
            }
            case CREEPER -> {
                // 右クリで爆発（onPlayerInteractで処理）
            }
            case SPIDER -> {
                swarmPlayer.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false));
                swarmPlayer.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, 2, false, false));
            }
            case CAVE_SPIDER -> {
                swarmPlayer.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, Integer.MAX_VALUE, 0, false, false));
                swarmPlayer.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, 2, false, false));
            }
            case ENDERMAN -> {
                swarmPlayer.getInventory().addItem(new ItemStack(Material.ENDER_PEARL, 5));
            }
            case WITCH -> {
                // 攻撃系
                swarmPlayer.getInventory().addItem(makeSplashPotion(PotionEffectType.POISON, 100, 0));
                swarmPlayer.getInventory().addItem(makeSplashPotion(PotionEffectType.INSTANT_DAMAGE, 1, 0));
                swarmPlayer.getInventory().addItem(makeSplashPotion(PotionEffectType.SLOWNESS, 100, 1));
                swarmPlayer.getInventory().addItem(makeSplashPotion(PotionEffectType.WEAKNESS, 100, 0));
                // 変わり種（自分や味方に投げても面白い）
                swarmPlayer.getInventory().addItem(makeSplashPotion(PotionEffectType.SPEED, 200, 1));
                swarmPlayer.getInventory().addItem(makeSplashPotion(PotionEffectType.JUMP_BOOST, 200, 1));
                swarmPlayer.getInventory().addItem(makeSplashPotion(PotionEffectType.LEVITATION, 60, 0));
                // ポーションは無限（投げたら補充される: onPotionThrowで処理）
            }
            case BLAZE -> {
                swarmPlayer.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
                startContactDamageTask(type);
            }
            case SLIME -> {
                swarmPlayer.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, Integer.MAX_VALUE, 2, false, false));
                startContactDamageTask(type);
            }
            case ZOMBIFIED_PIGLIN -> {
                swarmPlayer.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
                swarmPlayer.getInventory().addItem(new ItemStack(Material.GOLDEN_SWORD));
            }
            case PILLAGER -> {
                swarmPlayer.getInventory().addItem(new ItemStack(Material.CROSSBOW));
                swarmPlayer.getInventory().addItem(new ItemStack(Material.ARROW, 64));
            }
            case VINDICATOR -> {
                swarmPlayer.getInventory().addItem(new ItemStack(Material.IRON_AXE));
            }
            case RAVAGER -> {
                // 右クリで咆哮（onPlayerInteractで処理）
            }
            case VEX -> {
                swarmPlayer.setAllowFlight(true);
                swarmPlayer.setFlying(true);
                swarmPlayer.getInventory().addItem(new ItemStack(Material.IRON_SWORD));
            }
            default -> {}
        }
    }

    private static final Map<PotionEffectType, String> POTION_NAMES = Map.of(
        PotionEffectType.POISON, ChatColor.GREEN + "毒のスプラッシュ",
        PotionEffectType.INSTANT_DAMAGE, ChatColor.RED + "ダメージのスプラッシュ",
        PotionEffectType.SLOWNESS, ChatColor.GRAY + "鈍化のスプラッシュ",
        PotionEffectType.WEAKNESS, ChatColor.DARK_GRAY + "弱体化のスプラッシュ",
        PotionEffectType.SPEED, ChatColor.AQUA + "俊敏のスプラッシュ",
        PotionEffectType.JUMP_BOOST, ChatColor.YELLOW + "跳躍のスプラッシュ",
        PotionEffectType.LEVITATION, ChatColor.WHITE + "浮遊のスプラッシュ"
    );

    ItemStack makeSplashPotion(PotionEffectType effectType, int duration, int amplifier) {
        ItemStack potion = new ItemStack(Material.SPLASH_POTION);
        org.bukkit.inventory.meta.PotionMeta meta = (org.bukkit.inventory.meta.PotionMeta) potion.getItemMeta();
        meta.addCustomEffect(new PotionEffect(effectType, duration, amplifier), true);
        String name = POTION_NAMES.getOrDefault(effectType, ChatColor.WHITE + "スプラッシュポーション");
        meta.setDisplayName(name);
        potion.setItemMeta(meta);
        return potion;
    }

    // 接触ダメージタスク（スライム: 近接ダメージ、ブレイズ: 炎上付与）
    void startContactDamageTask(EntityType type) {
        stopContactDamageTask();
        contactDamageTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (gameState != GameState.PLAYING || swarmPlayer == null || !swarmPlayer.isOnline() || swarmDisguiseType != type) {
                    cancel();
                    return;
                }
                try {
                    for (Entity e : swarmPlayer.getWorld().getNearbyEntities(swarmPlayer.getLocation(), 2, 2, 2)) {
                        if (e.equals(swarmPlayer)) continue;
                        if (followingMobs.contains(e.getUniqueId())) continue;
                        if (!(e instanceof LivingEntity living)) continue;
                        if (type == EntityType.SLIME) {
                            living.damage(3.0, swarmPlayer);
                        } else if (type == EntityType.BLAZE) {
                            living.setFireTicks(60);
                        }
                    }
                } catch (Exception e) {}
            }
        };
        contactDamageTask.runTaskTimer(this, 10, 10);
    }

    void stopContactDamageTask() {
        if (contactDamageTask != null) {
            contactDamageTask.cancel();
            contactDamageTask = null;
        }
    }

    // スポーン地点周辺の障害物を除去（3x3x3の空間を確保）
    void clearSpawnArea(Location center) {
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    Block b = world.getBlockAt(cx + dx, cy + dy, cz + dz);
                    if (b.getType() != Material.AIR) {
                        b.setType(Material.AIR);
                    }
                }
            }
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
