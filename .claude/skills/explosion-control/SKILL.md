---
name: explosion-control
description: 爆発制御（ブロック破壊無効化、EntityExplodeEvent）を実装するとき参照する。
user-invocable: false
---

# 爆発制御

## こうしろ
- ブロック破壊だけ無効化 → `EntityExplodeEvent.blockList().clear()`
- エンティティダメージは残る。ダメージも消したい場合は別途 `EntityDamageEvent` で対処

## コード例
```java
@EventHandler
public void onEntityExplode(EntityExplodeEvent e) {
    e.blockList().clear(); // ブロック破壊のみ無効化、ダメージは残る
}
```