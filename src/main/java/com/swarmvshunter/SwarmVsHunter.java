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
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
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
            EntityType.CAVE_SPIDER, EntityType.ZOMBIFIED_PIGLIN, EntityType.PIGLIN,
            EntityType.HOGLIN, EntityType.VINDICATOR, EntityType.PILLAGER,
            EntityType.RAVAGER, EntityType.VEX, EntityType.WARDEN
    );

    // フィールド内に配置されたmob
    Set<UUID> fieldMobs = new HashSet<>();

    // Swarm変身システム
    EntityType swarmDisguiseType = null;
    int swarmDeathCount = 0;
    int aggroRadius = 20;
    Set<UUID> followingMobs = new HashSet<>(); // Swarmに追従する同種mob
    Set<UUID> provokedMobs = new HashSet<>();
    BukkitRunnable followTask = null; // 追従タスク

    // 固有能力クールダウン（秒）
    long lastAbilityUse = 0;
    BukkitRunnable cooldownBarTask = null;
    static final Map<EntityType, Integer> ABILITY_COOLDOWN_SECONDS = Map.ofEntries(
            Map.entry(EntityType.CREEPER, 5),
            Map.entry(EntityType.ZOMBIE, 1),
            Map.entry(EntityType.SKELETON, 1),
            Map.entry(EntityType.SPIDER, 3),
            Map.entry(EntityType.ENDERMAN, 3),
            Map.entry(EntityType.WITCH, 3),
            Map.entry(EntityType.BLAZE, 2),
            Map.entry(EntityType.SLIME, 3),
            Map.entry(EntityType.CAVE_SPIDER, 3),
            Map.entry(EntityType.ZOMBIFIED_PIGLIN, 5),
            Map.entry(EntityType.PIGLIN, 1),
            Map.entry(EntityType.HOGLIN, 3),
            Map.entry(EntityType.VINDICATOR, 2),
            Map.entry(EntityType.PILLAGER, 1),
            Map.entry(EntityType.RAVAGER, 4),
            Map.entry(EntityType.VEX, 4),
            Map.entry(EntityType.WARDEN, 5)
    );

    // 能力名テーブル
    static final Map<EntityType, String> ABILITY_NAMES = Map.ofEntries(
            Map.entry(EntityType.CREEPER, "爆発"),
            Map.entry(EntityType.ZOMBIE, "突進"),
            Map.entry(EntityType.SKELETON, "射撃"),
            Map.entry(EntityType.SPIDER, "跳躍"),
            Map.entry(EntityType.ENDERMAN, "瞬移"),
            Map.entry(EntityType.WITCH, "投擲"),
            Map.entry(EntityType.BLAZE, "火炎"),
            Map.entry(EntityType.SLIME, "跳撃"),
            Map.entry(EntityType.CAVE_SPIDER, "猛毒"),
            Map.entry(EntityType.ZOMBIFIED_PIGLIN, "激怒"),
            Map.entry(EntityType.PIGLIN, "射撃"),
            Map.entry(EntityType.HOGLIN, "突進"),
            Map.entry(EntityType.VINDICATOR, "斬撃"),
            Map.entry(EntityType.PILLAGER, "射撃"),
            Map.entry(EntityType.RAVAGER, "咆哮"),
            Map.entry(EntityType.VEX, "飛行"),
            Map.entry(EntityType.WARDEN, "音波")
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
            Map.entry(EntityType.PIGLIN, 5.0),
            Map.entry(EntityType.HOGLIN, 6.0),
            Map.entry(EntityType.VINDICATOR, 13.0),
            Map.entry(EntityType.PILLAGER, 4.0),
            Map.entry(EntityType.RAVAGER, 12.0),
            Map.entry(EntityType.VEX, 9.0),
            Map.entry(EntityType.WARDEN, 30.0)
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
            Map.entry(EntityType.PIGLIN, 0.23),
            Map.entry(EntityType.HOGLIN, 0.3),
            Map.entry(EntityType.VINDICATOR, 0.35),
            Map.entry(EntityType.PILLAGER, 0.35),
            Map.entry(EntityType.RAVAGER, 0.3),
            Map.entry(EntityType.VEX, 0.33),
            Map.entry(EntityType.WARDEN, 0.3),
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
        // Hunter装備
        if (hunterPlayer != null) equipHunter(hunterPlayer);

        // Swarmは装備なし、最大HP1設���（満腹回復を防ぐ）
        if (swarmPlayer != null) {
            swarmPlayer.getInventory().clear();
            AttributeInstance maxHpAttr = swarmPlayer.getAttribute(Attribute.MAX_HEALTH);
            if (maxHpAttr != null) maxHpAttr.setBaseValue(1.0);
            swarmPlayer.setHealth(1.0);
        }

        // ���レポート
        Player activePlayer = swarmPlayer != null ? swarmPlayer : hunterPlayer;
        if (debugMode) {
            Location spawn = fieldOrigin.clone().add(fieldSize / 2.0, 1, fieldSize / 2.0);
            activePlayer.teleport(spawn);
        } else {
            Location swarmSpawn = fieldOrigin.clone().add(fieldSize / 4.0, 1, fieldSize / 2.0);
            Location hunterSpawn = fieldOrigin.clone().add(fieldSize * 3.0 / 4.0, 1, fieldSize / 2.0);
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

        // 追従タスク: 10tick(0.5秒)ごとに追従mobをSwarmに向かわせる
        followTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (gameState != GameState.PLAYING || swarmPlayer == null) {
                    cancel();
                    return;
                }
                if (followingMobs.isEmpty()) return;
                try {
                    World world = swarmPlayer.getWorld();
                    Location swarmLoc = swarmPlayer.getLocation();
                    for (UUID id : new HashSet<>(followingMobs)) {
                        Entity entity = world.getEntity(id);
                        if (entity instanceof Mob mob && mob.isValid()) {
                            // ターゲットがなければSwarmの方に歩かせる
                            if (mob.getTarget() == null) {
                                mob.getPathfinder().moveTo(swarmLoc);
                            }
                        } else {
                            followingMobs.remove(id);
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
        hunter.getInventory().addItem(new ItemStack(Material.ARROW, 16));
        hunter.getInventory().addItem(new ItemStack(Material.BREAD, 4));
    }

    // === Swarm変身システム ===

    void transformSwarm(EntityType type) {
        // 既に変身中なら現在の追従mobを解除
        if (swarmDisguiseType != null) {
            releaseFollowingMobs();
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

        // ネームタグ非表示
        hideSwarmNametag();

        // 最大HP1維持
        AttributeInstance maxHp = swarmPlayer.getAttribute(Attribute.MAX_HEALTH);
        if (maxHp != null) maxHp.setBaseValue(1.0);
        swarmPlayer.setHealth(1.0);

        // 周囲の同種mobを追従させる（狼のように）
        recruitFollowingMobs(type);

        swarmPlayer.sendMessage(ChatColor.GREEN + type.name() + " に変身した��周囲の同種mobがついてくる！");
        sendMessageToPlayer(hunterPlayer, ChatColor.RED + "Swarmが " + type.name() + " に変身した！警戒せよ！");

        // 能力名があればアクションバーに表示
        String abilityName = ABILITY_NAMES.get(type);
        if (abilityName != null) {
            swarmPlayer.sendMessage(ChatColor.GOLD + "右クリックで固有能力【" + abilityName + "】が使える！");
            swarmPlayer.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                    net.md_5.bungee.api.chat.TextComponent.fromLegacy(
                            ChatColor.GREEN + "§l【" + abilityName + "】 ████████████████████ READY!"));
        }
        lastAbilityUse = 0;
    }

    void revertSwarm() {
        // 追従mob解除
        releaseFollowingMobs();

        swarmDisguiseType = null;
        swarmDeathCount++;
        lastAbilityUse = 0;
        if (cooldownBarTask != null) { cooldownBarTask.cancel(); cooldownBarTask = null; }

        // Disguise解除
        removeDisguise(swarmPlayer);

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

        swarmPlayer.sendMessage(ChatColor.YELLOW + "Hunterに倒された！人間に戻された (死亡: " + swarmDeathCount + "/3)");
        sendMessageToPlayer(hunterPlayer, ChatColor.GREEN + "Swarmを倒した！ (キル: " + swarmDeathCount + "/3)");
    }

    // === ゲーム停止・クリーンアップ ===
    void stopGame() {
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
        fieldMobs.clear();
        followingMobs.clear();
        provokedMobs.clear();

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

        // クールダウンバー停止
        if (cooldownBarTask != null) { cooldownBarTask.cancel(); cooldownBarTask = null; }

        // ステート初期化
        swarmDisguiseType = null;
        swarmDeathCount = 0;
        lastAbilityUse = 0;
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
            for (var entity : swarmPlayer.getWorld().getNearbyEntities(swarmPlayer.getLocation(), aggroRadius, aggroRadius, aggroRadius)) {
                if (entity.getType() == type && fieldMobs.contains(entity.getUniqueId()) && entity instanceof Mob mob) {
                    followingMobs.add(entity.getUniqueId());
                }
            }
        } catch (Exception e) {
            // getNearbyEntitiesがテスト環境で動かない場合は無視
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

    // Swarmのダメージ処理: 環境ダメージはそのまま通す（死亡可能）、mob攻撃は変身トリガー（onEntityDamageByEntityで処理済み）
    @EventHandler(priority = EventPriority.LOW)
    public void onEntityDamage(EntityDamageEvent event) {
        if (gameState != GameState.PLAYING) return;
        if (!event.getEntity().equals(swarmPlayer)) return;
        // EntityDamageByEntityEventは別ハンドラで処理するのでここではスルー
        // 環境ダメージ（落下、火、溺れ等）はそのまま通す → Swarmは環境ダメージで死ぬ
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

    // === 中立mobシステム: EntityTargetEventキャンセル（追従mob・挑発mob例外あり） ===
    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        if (gameState != GameState.PLAYING) return;
        if (event.getTarget() == null) return;
        UUID mobId = event.getEntity().getUniqueId();
        if (!fieldMobs.contains(mobId)) return;

        // 追従mobはターゲット設定を許可（Swarmが指令した攻撃対象を狙える）
        if (followingMobs.contains(mobId)) return;
        // 挑発されたmobがSwarmを狙うのは許可
        if (provokedMobs.contains(mobId) && event.getTarget().equals(swarmPlayer)) return;

        // それ以外のターゲット設定はキャンセル（中立化）
        event.setCancelled(true);
    }

    // === Swarm固有能力（右クリック発動） ===

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (gameState != GameState.PLAYING) return;
        if (!event.getPlayer().equals(swarmPlayer)) return;
        if (swarmDisguiseType == null) return;

        // 右クリックのみ
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        // クールダウンチェック
        int cooldown = ABILITY_COOLDOWN_SECONDS.getOrDefault(swarmDisguiseType, 3);
        long now = System.currentTimeMillis();
        long elapsed = now - lastAbilityUse;
        if (elapsed < cooldown * 1000L) {
            // アクションバーに残り時間をゲージ表示（チャットに出さない）
            return;
        }
        lastAbilityUse = now;

        // mob別能力発動
        useAbility(swarmDisguiseType);

        // クールダウンゲージ開始
        startCooldownBar(cooldown);
    }

    // アクションバーにクールダウンゲージを表示
    void startCooldownBar(int cooldownSeconds) {
        if (cooldownBarTask != null) cooldownBarTask.cancel();
        final int totalTicks = cooldownSeconds * 20;
        final int barLength = 20;
        final String abilityName = ABILITY_NAMES.getOrDefault(swarmDisguiseType, "能力");
        cooldownBarTask = new BukkitRunnable() {
            int elapsed = 0;
            @Override
            public void run() {
                if (gameState != GameState.PLAYING || swarmPlayer == null || !swarmPlayer.isOnline()) {
                    cancel();
                    return;
                }
                elapsed += 2; // 2tick間隔
                float progress = (float) elapsed / totalTicks;
                if (progress >= 1.0f) {
                    // クールダウン完了
                    swarmPlayer.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                            net.md_5.bungee.api.chat.TextComponent.fromLegacy(
                                    ChatColor.GREEN + "§l【" + abilityName + "】" + ChatColor.GREEN + " ████████████████████ READY!"));
                    cancel();
                    return;
                }
                int filled = (int) (barLength * progress);
                int empty = barLength - filled;
                StringBuilder bar = new StringBuilder();
                bar.append(ChatColor.RED).append("§l【").append(abilityName).append("】 ");
                bar.append(ChatColor.GREEN);
                for (int i = 0; i < filled; i++) bar.append("█");
                bar.append(ChatColor.DARK_GRAY);
                for (int i = 0; i < empty; i++) bar.append("█");
                swarmPlayer.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                        net.md_5.bungee.api.chat.TextComponent.fromLegacy(bar.toString()));
            }
        };
        cooldownBarTask.runTaskTimer(this, 0, 2); // 2tick(0.1秒)間隔で更新
    }

    void useAbility(EntityType type) {
        switch (type) {
            case CREEPER -> abilityCreeper();
            case ZOMBIE -> abilityZombie();
            case SKELETON -> abilitySkeleton();
            case SPIDER -> abilitySpider();
            case CAVE_SPIDER -> abilityCaveSpider();
            case ENDERMAN -> abilityEnderman();
            case WITCH -> abilityWitch();
            case BLAZE -> abilityBlaze();
            case SLIME -> abilitySlime();
            case ZOMBIFIED_PIGLIN -> abilityZombifiedPiglin();
            case PIGLIN -> abilityPiglin();
            case HOGLIN -> abilityHoglin();
            case VINDICATOR -> abilityVindicator();
            case PILLAGER -> abilityPillager();
            case RAVAGER -> abilityRavager();
            case VEX -> abilityVex();
            case WARDEN -> abilityWarden();
            default -> {
                swarmPlayer.sendMessage(ChatColor.GRAY + "このmobには固有能力がありません");
                lastAbilityUse = 0; // クールダウンリセット
            }
        }
    }

    // クリーパー: 爆発（チャージ演出→爆発）
    void abilityCreeper() {
        Location loc = swarmPlayer.getLocation();
        World world = loc.getWorld();
        world.playSound(loc, Sound.ENTITY_CREEPER_PRIMED, 2.0f, 1.0f);
        // チャージ演出（白くなるパーティクル）
        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (gameState != GameState.PLAYING || swarmDisguiseType == null) { cancel(); return; }
                tick++;
                Location current = swarmPlayer.getLocation().add(0, 1, 0);
                world.spawnParticle(Particle.FLASH, current, 1, 0, 0, 0, 0);
                world.spawnParticle(Particle.SMOKE, current, 8, 0.3, 0.5, 0.3, 0.02);
                if (tick >= 15) { // 0.75秒後に爆発
                    cancel();
                    Location expLoc = swarmPlayer.getLocation();
                    world.createExplosion(expLoc.getX(), expLoc.getY(), expLoc.getZ(), 3.0f, false, false);
                    world.spawnParticle(Particle.EXPLOSION_EMITTER, expLoc, 3, 0.5, 0.5, 0.5, 0);
                    world.spawnParticle(Particle.FLAME, expLoc, 60, 1.5, 1, 1.5, 0.15);
                    if (hunterPlayer != null && hunterPlayer.getLocation().distance(expLoc) <= 5.0) {
                        hunterPlayer.damage(8.0, swarmPlayer);
                    }
                }
            }
        }.runTaskTimer(this, 0, 1);
    }

    // ゾンビ: 突進攻撃（前方ダッシュ + 衝撃波演出）
    void abilityZombie() {
        Location loc = swarmPlayer.getLocation();
        loc.getWorld().playSound(loc, Sound.ENTITY_ZOMBIE_ATTACK_WOODEN_DOOR, 1.5f, 0.6f);
        loc.getWorld().spawnParticle(Particle.CRIT, loc.add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.3);
        Vector dir = swarmPlayer.getLocation().getDirection().normalize().multiply(1.5);
        dir.setY(0.3);
        swarmPlayer.setVelocity(dir);
        new BukkitRunnable() {
            @Override
            public void run() {
                Location land = swarmPlayer.getLocation();
                land.getWorld().spawnParticle(Particle.SWEEP_ATTACK, land.add(0, 1, 0), 5, 0.8, 0.3, 0.8, 0);
                if (hunterPlayer != null && hunterPlayer.getLocation().distance(land) <= 3.0) {
                    hunterPlayer.damage(getMobAttackDamage(EntityType.ZOMBIE) + 2.0, swarmPlayer);
                    land.getWorld().playSound(land, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, 0.8f);
                }
            }
        }.runTaskLater(this, 10);
    }

    // スケルトン: 矢を射る（弓の音+軌跡パーティクル）
    void abilitySkeleton() {
        Location eye = swarmPlayer.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        eye.getWorld().playSound(eye, Sound.ENTITY_ARROW_SHOOT, 1.5f, 1.0f);
        eye.getWorld().spawnParticle(Particle.CRIT, eye, 10, 0.1, 0.1, 0.1, 0.3);
        var arrow = swarmPlayer.getWorld().spawnArrow(eye.add(dir), dir, 2.5f, 1.0f);
        arrow.setShooter(swarmPlayer);
        arrow.setDamage(4.0);
        arrow.setCritical(true);
    }

    // スパイダー: 跳躍攻撃 + 蜘蛛の巣パーティクル + 着地鈍足
    void abilitySpider() {
        Location loc = swarmPlayer.getLocation();
        loc.getWorld().playSound(loc, Sound.ENTITY_SPIDER_AMBIENT, 2.0f, 0.5f);
        loc.getWorld().spawnParticle(Particle.ITEM, loc.add(0, 0.5, 0), 20, 0.5, 0.3, 0.5, 0.1,
                new ItemStack(Material.COBWEB));
        Vector dir = swarmPlayer.getLocation().getDirection().normalize().multiply(1.2);
        dir.setY(0.8);
        swarmPlayer.setVelocity(dir);
        new BukkitRunnable() {
            @Override
            public void run() {
                Location land = swarmPlayer.getLocation();
                land.getWorld().spawnParticle(Particle.ITEM, land, 30, 2, 0.3, 2, 0.05,
                        new ItemStack(Material.COBWEB));
                land.getWorld().playSound(land, Sound.ENTITY_SPIDER_STEP, 2.0f, 0.5f);
                if (hunterPlayer != null) {
                    for (Entity e : land.getWorld().getNearbyEntities(land, 4, 4, 4)) {
                        if (e.equals(hunterPlayer)) {
                            hunterPlayer.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                        }
                    }
                }
            }
        }.runTaskLater(this, 15);
    }

    // 洞窟グモ: 毒霧（大量パーティクル + 毒）
    void abilityCaveSpider() {
        Location loc = swarmPlayer.getLocation();
        loc.getWorld().playSound(loc, Sound.ENTITY_SPIDER_AMBIENT, 2.0f, 1.5f);
        // 毒雲パーティクルを派手に
        for (int i = 0; i < 5; i++) {
            final int delay = i;
            new BukkitRunnable() {
                @Override
                public void run() {
                    Location center = swarmPlayer.getLocation().add(0, 1, 0);
                    center.getWorld().spawnParticle(Particle.ITEM, center, 40, 3, 1.5, 3, 0.05,
                            new ItemStack(Material.FERMENTED_SPIDER_EYE));
                    center.getWorld().spawnParticle(Particle.ENTITY_EFFECT, center, 20, 2, 1, 2, 1);
                }
            }.runTaskLater(this, i * 4L);
        }
        if (hunterPlayer != null) {
            for (Entity e : loc.getWorld().getNearbyEntities(loc, 5, 3, 5)) {
                if (e.equals(hunterPlayer)) {
                    hunterPlayer.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 0));
                }
            }
        }
    }

    // エンダーマン: 視線方向にテレポート（最大16ブロック + 派手な演出）
    void abilityEnderman() {
        Location from = swarmPlayer.getLocation().clone();
        Location eye = swarmPlayer.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        Location target = eye.clone();
        for (int i = 1; i <= 16; i++) {
            Location check = eye.clone().add(dir.clone().multiply(i));
            if (check.getBlock().getType().isSolid()) break;
            if (!check.clone().add(0, -1, 0).getBlock().getType().isSolid() && i > 1) continue;
            target = check;
        }
        target.setYaw(from.getYaw());
        target.setPitch(from.getPitch());
        World world = from.getWorld();
        // 消える側: 大量パーティクル + サウンド
        world.spawnParticle(Particle.PORTAL, from.add(0, 1, 0), 100, 0.5, 1, 0.5, 1.0);
        world.spawnParticle(Particle.REVERSE_PORTAL, from, 50, 0.3, 0.8, 0.3, 0.1);
        world.playSound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 2.0f, 1.2f);
        swarmPlayer.teleport(target);
        // 出現側: 大量パーティクル + サウンド
        world.spawnParticle(Particle.PORTAL, target.clone().add(0, 1, 0), 100, 0.5, 1, 0.5, 1.0);
        world.spawnParticle(Particle.REVERSE_PORTAL, target, 50, 0.3, 0.8, 0.3, 0.1);
        world.playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 2.0f, 0.8f);
    }

    // ウィッチ: ダメージポーション投擲（投擲音 + 軌跡）
    void abilityWitch() {
        Location eye = swarmPlayer.getEyeLocation();
        eye.getWorld().playSound(eye, Sound.ENTITY_WITCH_THROW, 2.0f, 1.0f);
        eye.getWorld().spawnParticle(Particle.WITCH, eye, 15, 0.3, 0.3, 0.3, 0.1);
        var potion = (org.bukkit.entity.ThrownPotion) swarmPlayer.getWorld().spawnEntity(eye, EntityType.POTION);
        ItemStack potionItem = new ItemStack(Material.SPLASH_POTION);
        org.bukkit.inventory.meta.PotionMeta potionMeta = (org.bukkit.inventory.meta.PotionMeta) potionItem.getItemMeta();
        potionMeta.addCustomEffect(new PotionEffect(PotionEffectType.INSTANT_DAMAGE, 1, 0), true);
        potionItem.setItemMeta(potionMeta);
        potion.setItem(potionItem);
        potion.setVelocity(eye.getDirection().normalize().multiply(1.2));
        potion.setShooter(swarmPlayer);
    }

    // ブレイズ: 火の玉3連射（発射音 + 炎パーティクル）
    void abilityBlaze() {
        for (int i = 0; i < 3; i++) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (gameState != GameState.PLAYING) return;
                    Location fireEye = swarmPlayer.getEyeLocation();
                    Vector fireDir = fireEye.getDirection().normalize();
                    fireEye.getWorld().playSound(fireEye, Sound.ENTITY_BLAZE_SHOOT, 1.5f, 1.0f);
                    fireEye.getWorld().spawnParticle(Particle.FLAME, fireEye, 15, 0.2, 0.2, 0.2, 0.08);
                    fireEye.getWorld().spawnParticle(Particle.LAVA, fireEye, 5, 0.2, 0.2, 0.2, 0);
                    var fireball = (SmallFireball) swarmPlayer.getWorld().spawnEntity(
                            fireEye.add(fireDir), EntityType.SMALL_FIREBALL);
                    fireball.setDirection(fireDir);
                    fireball.setShooter(swarmPlayer);
                    fireball.setIsIncendiary(false);
                }
            }.runTaskLater(this, i * 5L);
        }
    }

    // スライム: 大ジャンプ + 着地AoEダメージ（スライム音 + 緑パーティクル）
    void abilitySlime() {
        Location loc = swarmPlayer.getLocation();
        loc.getWorld().playSound(loc, Sound.ENTITY_SLIME_JUMP, 2.0f, 0.8f);
        loc.getWorld().spawnParticle(Particle.ITEM, loc, 30, 0.5, 0.2, 0.5, 0.15,
                new ItemStack(Material.SLIME_BLOCK));
        swarmPlayer.setVelocity(new Vector(0, 1.5, 0).add(
                swarmPlayer.getLocation().getDirection().normalize().multiply(0.5)));
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                ticks++;
                if (ticks > 40 || swarmPlayer.isOnGround()) {
                    cancel();
                    Location land = swarmPlayer.getLocation();
                    land.getWorld().playSound(land, Sound.ENTITY_SLIME_SQUISH, 2.0f, 0.6f);
                    land.getWorld().spawnParticle(Particle.ITEM, land, 50, 2, 0.3, 2, 0.15,
                            new ItemStack(Material.SLIME_BLOCK));
                    land.getWorld().spawnParticle(Particle.EXPLOSION, land, 3, 1, 0.5, 1, 0);
                    if (hunterPlayer != null) {
                        for (Entity e : land.getWorld().getNearbyEntities(land, 4, 2, 4)) {
                            if (e.equals(hunterPlayer)) {
                                hunterPlayer.damage(4.0, swarmPlayer);
                                Vector kb = hunterPlayer.getLocation().toVector()
                                        .subtract(land.toVector()).normalize().multiply(0.8).setY(0.4);
                                hunterPlayer.setVelocity(kb);
                            }
                        }
                    }
                }
            }
        }.runTaskTimer(this, 5, 1);
    }

    // ゾンビピグリン: バーサクモード（赤パーティクル + 怒りの咆哮）
    void abilityZombifiedPiglin() {
        Location loc = swarmPlayer.getLocation();
        loc.getWorld().playSound(loc, Sound.ENTITY_ZOMBIFIED_PIGLIN_ANGRY, 2.0f, 0.5f);
        loc.getWorld().playSound(loc, Sound.ENTITY_ZOMBIFIED_PIGLIN_ANGRY, 2.0f, 0.8f);
        loc.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, loc.clone().add(0, 2, 0), 10, 0.5, 0.3, 0.5, 0);
        loc.getWorld().spawnParticle(Particle.FLAME, loc.clone().add(0, 1, 0), 30, 0.5, 0.8, 0.5, 0.05);
        swarmPlayer.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 1));
        swarmPlayer.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 100, 1));
        // 持続エフェクト（赤オーラ）
        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                tick++;
                if (tick > 50 || gameState != GameState.PLAYING) { cancel(); return; }
                swarmPlayer.getWorld().spawnParticle(Particle.DUST,
                        swarmPlayer.getLocation().add(0, 1, 0), 5, 0.4, 0.6, 0.4, 0,
                        new Particle.DustOptions(org.bukkit.Color.RED, 1.5f));
            }
        }.runTaskTimer(this, 0, 2);
    }

    // ピグリン: クロスボウ射撃（射撃音 + パーティクル）
    void abilityPiglin() {
        Location eye = swarmPlayer.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        eye.getWorld().playSound(eye, Sound.ITEM_CROSSBOW_SHOOT, 2.0f, 1.0f);
        eye.getWorld().spawnParticle(Particle.CRIT, eye, 12, 0.1, 0.1, 0.1, 0.4);
        var arrow = swarmPlayer.getWorld().spawnArrow(eye.add(dir), dir, 2.5f, 0.0f);
        arrow.setShooter(swarmPlayer);
        arrow.setDamage(5.0);
        arrow.setCritical(true);
    }

    // ホグリン: 突進ノックバック（地響き + ダストパーティクル）
    void abilityHoglin() {
        Location loc = swarmPlayer.getLocation();
        loc.getWorld().playSound(loc, Sound.ENTITY_HOGLIN_ATTACK, 2.0f, 0.8f);
        loc.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc, 20, 0.5, 0.1, 0.5, 0.02);
        Vector dir = swarmPlayer.getLocation().getDirection().normalize().multiply(2.0);
        dir.setY(0.2);
        swarmPlayer.setVelocity(dir);
        new BukkitRunnable() {
            @Override
            public void run() {
                Location land = swarmPlayer.getLocation();
                land.getWorld().spawnParticle(Particle.EXPLOSION, land, 2, 0.5, 0.3, 0.5, 0);
                if (hunterPlayer != null && hunterPlayer.getLocation().distance(land) <= 3.5) {
                    hunterPlayer.damage(getMobAttackDamage(EntityType.HOGLIN), swarmPlayer);
                    land.getWorld().playSound(land, Sound.ENTITY_HOGLIN_ATTACK, 1.5f, 1.2f);
                    Vector kb = hunterPlayer.getLocation().toVector()
                            .subtract(land.toVector()).normalize().multiply(1.5).setY(0.5);
                    hunterPlayer.setVelocity(kb);
                }
            }
        }.runTaskLater(this, 8);
    }

    // ヴィンディケーター: 範囲斧攻撃（斬撃エフェクト）
    void abilityVindicator() {
        Location loc = swarmPlayer.getLocation();
        loc.getWorld().playSound(loc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 2.0f, 0.7f);
        loc.getWorld().playSound(loc, Sound.ENTITY_VINDICATOR_CELEBRATE, 1.5f, 0.8f);
        // 360度斬撃パーティクル
        for (double angle = 0; angle < Math.PI * 2; angle += Math.PI / 8) {
            double x = Math.cos(angle) * 2;
            double z = Math.sin(angle) * 2;
            loc.getWorld().spawnParticle(Particle.SWEEP_ATTACK,
                    loc.clone().add(x, 1, z), 1, 0, 0, 0, 0);
        }
        loc.getWorld().spawnParticle(Particle.CRIT, loc.clone().add(0, 1, 0), 20, 1.5, 0.5, 1.5, 0.3);
        if (hunterPlayer != null) {
            for (Entity e : loc.getWorld().getNearbyEntities(loc, 3, 2, 3)) {
                if (e.equals(hunterPlayer)) {
                    hunterPlayer.damage(getMobAttackDamage(EntityType.VINDICATOR), swarmPlayer);
                }
            }
        }
    }

    // ピリジャー: クロスボウ射撃
    void abilityPillager() {
        Location eye = swarmPlayer.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        eye.getWorld().playSound(eye, Sound.ITEM_CROSSBOW_SHOOT, 2.0f, 1.0f);
        eye.getWorld().spawnParticle(Particle.CRIT, eye, 12, 0.1, 0.1, 0.1, 0.4);
        var arrow = swarmPlayer.getWorld().spawnArrow(eye.add(dir), dir, 2.5f, 0.0f);
        arrow.setShooter(swarmPlayer);
        arrow.setDamage(4.0);
    }

    // ラヴェジャー: 咆哮（範囲ノックバック + 衝撃波パーティクル）
    void abilityRavager() {
        Location loc = swarmPlayer.getLocation();
        loc.getWorld().playSound(loc, Sound.ENTITY_RAVAGER_ROAR, 2.0f, 1.0f);
        // 衝撃波の円を外に広げるアニメーション
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
        loc.getWorld().spawnParticle(Particle.EXPLOSION, loc, 5, 1, 0.5, 1, 0);
        if (hunterPlayer != null) {
            for (Entity e : loc.getWorld().getNearbyEntities(loc, 6, 3, 6)) {
                if (e.equals(hunterPlayer)) {
                    Vector kb = hunterPlayer.getLocation().toVector()
                            .subtract(loc.toVector()).normalize().multiply(2.0).setY(0.6);
                    hunterPlayer.setVelocity(kb);
                    hunterPlayer.damage(6.0, swarmPlayer);
                }
            }
        }
    }

    // ヴェックス: 短時間飛行（羽パーティクル + 浮遊音）
    void abilityVex() {
        Location loc = swarmPlayer.getLocation();
        loc.getWorld().playSound(loc, Sound.ENTITY_VEX_CHARGE, 2.0f, 1.0f);
        loc.getWorld().spawnParticle(Particle.CLOUD, loc.clone().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
        loc.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.08);
        swarmPlayer.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 40, 0));
        swarmPlayer.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 2));
        swarmPlayer.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 80, 0));
        // 飛行中パーティクル
        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                tick++;
                if (tick > 40 || gameState != GameState.PLAYING) { cancel(); return; }
                swarmPlayer.getWorld().spawnParticle(Particle.END_ROD,
                        swarmPlayer.getLocation().add(0, 0.5, 0), 3, 0.3, 0.2, 0.3, 0.02);
            }
        }.runTaskTimer(this, 0, 2);
    }

    // ウォーデン: ソニックブーム（衝撃波パーティクル + 大音量）
    void abilityWarden() {
        Location eye = swarmPlayer.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        eye.getWorld().playSound(eye, Sound.ENTITY_WARDEN_SONIC_BOOM, 3.0f, 1.0f);
        eye.getWorld().playSound(eye, Sound.ENTITY_WARDEN_SONIC_CHARGE, 2.0f, 1.0f);
        eye.getWorld().spawnParticle(Particle.FLASH, eye, 2, 0, 0, 0, 0);
        boolean hit = false;
        for (int i = 1; i <= 15; i++) {
            Location check = eye.clone().add(dir.clone().multiply(i));
            check.getWorld().spawnParticle(Particle.SONIC_BOOM, check, 1, 0, 0, 0, 0);
            check.getWorld().spawnParticle(Particle.DUST, check, 5, 0.2, 0.2, 0.2, 0,
                    new Particle.DustOptions(org.bukkit.Color.fromRGB(0, 150, 200), 2.0f));
            if (!hit && hunterPlayer != null && hunterPlayer.getLocation().distance(check) <= 2.0) {
                hunterPlayer.damage(10.0, swarmPlayer);
                check.getWorld().spawnParticle(Particle.FLASH, check, 3, 0.3, 0.3, 0.3, 0);
                hit = true;
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
