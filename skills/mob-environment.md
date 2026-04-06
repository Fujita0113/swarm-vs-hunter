# mob の環境ダメージ制御

## 落とし穴
- フィールド内mobは日光・雨・水・凍結で勝手に死ぬ → `EntityDamageEvent` で原因別にキャンセルが必要
- **ホグリン**: ネザー外で `EntityTransformEvent` をキャンセルしないとゾグリンに変身する
- **エンダーマン**: `EntityTeleportEvent` をキャンセルしないと雨天時に無限テレポートする

## こうしろ
- `EntityDamageEvent` で `DamageCause` が `FIRE`, `FIRE_TICK`, `DROWNING`, `FREEZE`, `DRYOUT` 等ならキャンセル
- `EntityTransformEvent` で `TransformReason.DROWNED` 等をキャンセル（ホグリン→ゾグリン防止）
- `EntityTeleportEvent` でフィールド内エンダーマンのテレポートをキャンセル
