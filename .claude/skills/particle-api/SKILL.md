---
name: particle-api
description: Particle APIを使うとき（パーティクル、エフェクト、Particle.DUST、Particle.EXPLOSION）に参照する。Paper 1.21.xの既知の罠と正しい実装パターン。
user-invocable: false
---

# Particle API（Paper 1.21.x）

## やるな
- **`Particle.FLASH`**: Color必須で、渡してもバグる。絶対に使うな
- **`Particle.ENTITY_EFFECT`**: `DustOptions` を渡さないと実行時エラー

## こうしろ
- 色付きエフェクト → `Particle.DUST` + `new Particle.DustOptions(Color.fromRGB(r,g,b), size)` が最も安全
- 爆発エフェクト → `Particle.EXPLOSION`（FLASHの代替）

## コード例
```java
// 色付きパーティクル（安全）
player.getWorld().spawnParticle(Particle.DUST,
    location, 10,
    new Particle.DustOptions(Color.fromRGB(255, 0, 0), 1.5f));

// 爆発エフェクト（安全）
player.getWorld().spawnParticle(Particle.EXPLOSION, location, 1);
```