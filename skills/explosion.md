# 爆発制御

## こうしろ
- ブロック破壊だけ無効化したい → `EntityExplodeEvent.blockList().clear()`
  - エンティティへのダメージは残るので、ダメージも消したい場合は別途 `EntityDamageEvent` で対処
