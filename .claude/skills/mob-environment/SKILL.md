---
name: mob-environment
description: mobの環境ダメージ制御（日光、雨、水、凍結、ゾグリン化、エンダーマンテレポート）を実装するとき参照する。
user-invocable: false
---

# mob の環境ダメージ制御

## 落とし穴
- フィールド内mobは日光・雨・水・凍結で勝手に死ぬ
- **ホグリン**: ネザー外で勝手にゾグリンに変身する
- **エンダーマン**: 雨天時に無限テレポートする

## こうしろ
- `EntityDamageEvent` で `DamageCause` が環境系ならキャンセル（`FIRE`, `FIRE_TICK`, `DROWNING`, `FREEZE`, `DRYOUT` 等）
- `EntityTransformEvent` をキャンセル（ホグリン→ゾグリン防止）
- `EntityTeleportEvent` でフィールド内エンダーマンのテレポートをキャンセル

## コード例
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