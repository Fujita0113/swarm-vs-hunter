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
- タスクを1つ実装したら、`.commit-message` ファイルにコミットメッセージを書け。Stopフックが自動でコミットする。デバッグで戻れるよう意味のあるメッセージにすること。
- `.commit-message` を書く前に必ず `JAVA_HOME="C:/Program Files/Microsoft/jdk-21.0.10.7-hotspot" mvn clean package` を実行し、ビルド成功を確認せよ。失敗したら修正→再ビルドを繰り返し、成功してからコミットメッセージを書くこと。
- タスク進捗は `TODO.md` で管理する。完了したら `[x]` に更新せよ。
- マイルストーン完了時に必ずやること:
  1. `TESTING.md` にそのマイルストーンの手動テスト項目を追加する
     - チェックリスト形式（`- [ ]`）で確認項目を列挙
     - 各テストセクションに「見るべきコード」としてファイル名・メソッド名・行番号を記載
     - 前提条件（人数、OP権限等）があれば冒頭に明記
  2. 実装した機能ごとに **「最終形」か「暫定」** かを明示してユーザーに伝える

## ビルド・実行

```bash
# ビルド（JAVA_HOMEがJava11を指しているためMavenビルド時に上書き必須）
JAVA_HOME="C:/Program Files/Microsoft/jdk-21.0.10.7-hotspot" mvn clean package

# 生成されるjar: target/swarm-vs-hunter-3.0.jar
# pom.xmlのantrun pluginにより minecraft-server/plugins/ へ自動コピーされる
# サーバー再起動で反映
```

## テスト方針

- タスク実装前にテストを書く（TDD）。テストが通ることをゴールにして実装する
- MockBukkitで再現困難な機能（NMS直接操作等）は実装後にテストを書く
- `JAVA_HOME="C:/Program Files/Microsoft/jdk-21.0.10.7-hotspot" mvn test` で全テスト通過を確認してからコミットする
- 実装完了後、Sonnetサブエージェントを起動してレビューさせる:
  1. `mvn test` 実行 → 全テスト通過確認
  2. requirements.md と実装コードの差分チェック
  3. 問題があれば報告

## フィードバック方針

- 各マイルストーン完了時、実装した機能ごとに **「最終形」か「暫定」** かを明示する
- ユーザーは「最終形」の部分にのみフィードバックを出す。暫定部分は後のマイルストーンで変わるため今は不要
- これにより手戻りを最小化しつつ、早い段階でイメージのズレを修正できる

## 主要なゲームメカニクス（実装時の注意点）

- **全mobの中立化**: バニラ敵対mob（ゾンビ等）もフィールド内では中立にする。EntityTargetEventをキャンセルして制御。
- **Swarm変身**: プレイヤーをDisguise（見た目変更）し、HP1を維持。変身先mobのバニラ攻撃力・移動速度を適用。ネームタグ非表示。
- **mob敵対化**: 変身時、aggro_radius内の同種mobにHunterをターゲットさせる。PathfinderGoalの差し替えまたはEntityTargetEventで制御。
- **卵システム**: mob撃破→卵ドロップ→使用で味方mob確定出現。味方mobはオーナーに追従。
- **寝取り**: Swarmが同種mob変身中にHunterの味方mob近くに来ると、その味方がSwarm側に寝返る。
- **ブロック破壊・設置は不可**: BlockBreakEvent, BlockPlaceEventをキャンセル。
