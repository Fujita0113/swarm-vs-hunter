# GameMode

## 落とし穴
- ゲーム開始時に GameMode が ADVENTURE 等のままだと mob を攻撃できない

## こうしろ
- ゲーム開始時に `player.setGameMode(GameMode.SURVIVAL)` を明示的にセットする
