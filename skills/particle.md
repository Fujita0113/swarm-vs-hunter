# Particle API（Paper 1.21.x）

## やるな
- **`Particle.FLASH`**: Color必須で、渡してもバグる。使うな
- **`Particle.ENTITY_EFFECT`**: `DustOptions` を渡さないと実行時エラー

## こうしろ
- 色付きエフェクト → `Particle.DUST` + `new Particle.DustOptions(Color, size)` が最も安全
- 爆発エフェクト → `Particle.EXPLOSION`（FLASHの代替）
