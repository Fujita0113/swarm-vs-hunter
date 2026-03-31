# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## プロジェクト概要

Minecraft 2人対戦ミニゲーム「Swarm vs Hunter v3」のSpigot/Paperプラグイン。
Swarm(HP1、mobに変身して群れで襲う)とHunter(鉄装備、3分生存or3回キルで勝利)が戦う。
詳細仕様は `requirements.md` を参照。

## 技術スタック

- Spigot / Paper Plugin (Minecraft 1.21.x)
- Java (Spigot API)
- ビルド: Maven or Gradle（セットアップ時に決定）

## 開発方針

- **動けばいい。** コード品質・保守性・設計パターンは気にしない。
- 1ファイルに全部書いても構わない。分割の必要なし。
- リファクタリング・コメント追加・型整理などの「改善」は不要。
- エラーが出たらログを読んで原因特定→修正→ビルドまで自律的にやる。ユーザーに聞かずに直せ。

## ビルド・実行

```bash
# ビルド（Maven の場合）
mvn clean package

# ビルド（Gradle の場合）
./gradlew build

# 生成されるjar: target/ または build/libs/ 配下
# jarをサーバーのplugins/にコピーしてサーバー再起動で反映
```

## 主要なゲームメカニクス（実装時の注意点）

- **全mobの中立化**: バニラ敵対mob（ゾンビ等）もフィールド内では中立にする。EntityTargetEventをキャンセルして制御。
- **Swarm変身**: プレイヤーをDisguise（見た目変更）し、HP1を維持。変身先mobのバニラ攻撃力・移動速度を適用。ネームタグ非表示。
- **mob敵対化**: 変身時、aggro_radius内の同種mobにHunterをターゲットさせる。PathfinderGoalの差し替えまたはEntityTargetEventで制御。
- **卵システム**: mob撃破→卵ドロップ→使用で味方mob確定出現。味方mobはオーナーに追従。
- **寝取り**: Swarmが同種mob変身中にHunterの味方mob近くに来ると、その味方がSwarm側に寝返る。
- **ブロック破壊・設置は不可**: BlockBreakEvent, BlockPlaceEventをキャンセル。
