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
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
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

    // Swarm変身システム
    EntityType swarmDisguiseType = null;
    int swarmDeathCount = 0;
    int aggroRadius = 20;
    Set<UUID> aggroMobs = new HashSet<>();
    Set<UUID> provokedMobs = new HashSet<>();

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

        // フィールド生成（0,0,0を中心に固定、範囲内の最高地点を基準）
        World world = swarmPlayer.getWorld();
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

        // Swarmは装備なし、最大HP1設定（満腹回復を防ぐ）
        swarmPlayer.getInventory().clear();
        AttributeInstance maxHpAttr = swarmPlayer.getAttribute(Attribute.MAX_HEALTH);
        if (maxHpAttr != null) maxHpAttr.setBaseValue(1.0);
        swarmPlayer.setHealth(1.0);

        // テレポート
        Location swarmSpawn = fieldOrigin.clone().add(fieldSize / 4.0, 1, fieldSize / 2.0);
        Location hunterSpawn = fieldOrigin.clone().add(fieldSize * 3.0 / 4.0, 1, fieldSize / 2.0);
        swarmPlayer.teleport(swarmSpawn);
        hunterPlayer.teleport(hunterSpawn);

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

    // === Swarm変身システム ===

    void transformSwarm(EntityType type) {
        // 既に変身中なら現在の敵対mobを解除
        if (swarmDisguiseType != null) {
            deaggroAllMobs();
        }

        swarmDisguiseType = type;

        // 透明化（暫定Disguise: LibsDisguises導入まで透明+ネームタグ非表示で代用）
        swarmPlayer.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, true, false));
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

        // 周囲の同種mobを敵対化
        aggroNearbyMobs(type);

        swarmPlayer.sendMessage(ChatColor.GREEN + type.name() + " に変身した！周囲の同種mobがHunterを襲う！");
        hunterPlayer.sendMessage(ChatColor.RED + "Swarmが " + type.name() + " に変身した！警戒せよ！");
    }

    void revertSwarm() {
        // 敵対mob解除
        deaggroAllMobs();

        swarmDisguiseType = null;
        swarmDeathCount++;

        // 透明化解除
        swarmPlayer.removePotionEffect(PotionEffectType.INVISIBILITY);

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
        hunterPlayer.sendMessage(ChatColor.GREEN + "Swarmを倒した！ (キル: " + swarmDeathCount + "/3)");
    }

    // === ゲーム停止・クリーンアップ ===
    void stopGame() {
        // フィールドmobを全除去
        if (fieldOrigin != null) {
            World world = fieldOrigin.getWorld();
            for (UUID id : fieldMobs) {
                Entity entity = world.getEntity(id);
                if (entity != null) entity.remove();
            }
        }
        fieldMobs.clear();
        aggroMobs.clear();
        provokedMobs.clear();

        // プレイヤー状態復元
        for (Player p : new Player[]{swarmPlayer, hunterPlayer}) {
            if (p == null || !p.isOnline()) continue;
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

        // ステート初期化
        swarmDisguiseType = null;
        swarmDeathCount = 0;
        selectedMobTypes.clear();
        playerSelections.clear();
        gameState = GameState.WAITING;

        Bukkit.broadcastMessage(ChatColor.GOLD + "[SVH] ゲームが停止されました");
    }

    void aggroNearbyMobs(EntityType type) {
        aggroMobs.clear();
        try {
            for (var entity : swarmPlayer.getWorld().getNearbyEntities(swarmPlayer.getLocation(), aggroRadius, aggroRadius, aggroRadius)) {
                if (entity.getType() == type && fieldMobs.contains(entity.getUniqueId()) && entity instanceof Mob mob) {
                    mob.setTarget(hunterPlayer);
                    aggroMobs.add(entity.getUniqueId());
                }
            }
        } catch (Exception e) {
            // getNearbyEntitiesがテスト環境で動かない場合は無視
        }
    }

    void deaggroAllMobs() {
        try {
            World world = swarmPlayer.getWorld();
            for (UUID id : aggroMobs) {
                Entity entity = world.getEntity(id);
                if (entity instanceof Mob mob) {
                    mob.setTarget(null);
                }
            }
        } catch (Exception e) {
            // テスト環境等で無視
        }
        aggroMobs.clear();
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

        // Case 1: Swarmがフィールドmobを殴る → 挑発（mob がSwarmを狙う）
        if (damager.equals(swarmPlayer) && fieldMobs.contains(event.getEntity().getUniqueId())) {
            if (event.getEntity() instanceof Mob mob) {
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

    // 環境ダメージ（落下、火等）からSwarmを保護
    @EventHandler(priority = EventPriority.LOW)
    public void onEntityDamage(EntityDamageEvent event) {
        if (gameState != GameState.PLAYING) return;
        if (!event.getEntity().equals(swarmPlayer)) return;
        if (!(event instanceof EntityDamageByEntityEvent)) {
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

    // === 中立mobシステム: EntityTargetEventキャンセル（敵対化mob・挑発mob例外あり） ===
    @EventHandler
    public void onEntityTarget(EntityTargetEvent event) {
        if (gameState != GameState.PLAYING) return;
        if (event.getTarget() == null) return;
        UUID mobId = event.getEntity().getUniqueId();
        if (!fieldMobs.contains(mobId)) return;

        // 敵対化mobがHunterを狙うのは許可
        if (aggroMobs.contains(mobId) && event.getTarget().equals(hunterPlayer)) return;
        // 挑発されたmobがSwarmを狙うのは許可
        if (provokedMobs.contains(mobId) && event.getTarget().equals(swarmPlayer)) return;

        // それ以外のターゲット設定はキャンセル（中立化）
        event.setCancelled(true);
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
