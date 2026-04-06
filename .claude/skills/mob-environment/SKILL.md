---
name: mob-environment
description: mob・プレイヤーの環境制御全般（環境ダメージ、ゾグリン化、テレポート、爆発、GameMode）を実装するとき参照する。
user-invocable: true
---

# mob・プレイヤー環境制御

## 環境ダメージ

### 落とし穴
- フィールド内mobは日光・雨・水・凍結で勝手に死ぬ
- **ホグリン**: ネザー外で勝手にゾグリンに変身する
- **エンダーマン**: 雨天時に無限テレポートする

### こうしろ
```java
@EventHandler
public void onEntityDamage(EntityDamageEvent e) {
    if (!isInField(e.getEntity())) return;
    switch (e.getCause()) {
        case FIRE, FIRE_TICK, DROWNING, FREEZE, DRYOUT -> e.setCancelled(true);
    }
}

@EventHandler
public void onEntityTransform(EntityTransformEvent e) {
    if (isInField(e.getEntity())) e.setCancelled(true);
}

@EventHandler
public void onEntityTeleport(EntityTeleportEvent e) {
    if (e.getEntity() instanceof Enderman && isInField(e.getEntity())) {
        e.setCancelled(true);
    }
}
```

## 爆発制御

- ブロック破壊だけ無効化 → `EntityExplodeEvent.blockList().clear()`
- エンティティダメージは残る。ダメージも消したい場合は別途 `EntityDamageEvent` で対処

```java
@EventHandler
public void onEntityExplode(EntityExplodeEvent e) {
    e.blockList().clear();
}
```

## GameMode

- ゲーム開始時に `GameMode.SURVIVAL` を明示セットしないと mob を攻撃できない

```java
player.setGameMode(GameMode.SURVIVAL); // ゲーム開始時に必ず呼ぶ
```