---
name: potion-api
description: ポーション・ThrownPotion・PotionSplashEventを実装するとき参照する。スプラッシュポーションの自爆バグ防止パターン。
user-invocable: false
---

# ポーション / ThrownPotion

## やるな
- `ThrownPotion` を spawn して `setShooter()` を呼ばない → 投げた本人に効果が適用されて自爆する

## こうしろ
1. spawn 直後に `potion.setShooter(player)` を必ず呼ぶ
2. `PotionSplashEvent` で自分と味方mobへの効果を `setIntensity(entity, 0)` で無効化する

## コード例
```java
// スプラッシュポーション生成（安全）
ThrownPotion potion = player.launchProjectile(ThrownPotion.class);
potion.setShooter(player); // ← これを忘れると自爆

// PotionSplashEventで味方除外
@EventHandler
public void onPotionSplash(PotionSplashEvent e) {
    if (e.getEntity().getShooter() instanceof Player shooter) {
        for (LivingEntity affected : e.getAffectedEntities()) {
            if (affected.equals(shooter) || isAlly(affected)) {
                e.setIntensity(affected, 0);
            }
        }
    }
}
```