# ポーション / ThrownPotion

## やるな
- `ThrownPotion` を spawn して `setShooter()` を呼ばない → 自爆する

## こうしろ
- spawn 直後に `potion.setShooter(player)` を必ず呼ぶ
- `PotionSplashEvent` で自分と味方mobへの効果を `setIntensity(entity, 0)` で無効化する
