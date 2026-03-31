# テストガイド — ゼロからプラグインを動かすまで

## 前提
- Minecraft Java版を持っている（遊んだことがある）
- Java 17 以上がインストール済み（このプロジェクトのビルドで確認済み）

---

## STEP 1: プラグインをビルドする

プロジェクトのルートディレクトリで以下を実行：

```bash
JAVA_HOME="C:/Users/yufuj/AppData/Local/Programs/Microsoft/jdk-17.0.15.6-hotspot" mvn clean package
```

成功すると `target/swarm-vs-hunter-3.0.jar` が生成される。

---

## STEP 2: サーバー環境（セットアップ済み）

プロジェクト配下の `minecraft-server/` にPaperサーバーが構築済み。

```
minecraft-project-swarm-vs-hunter/
  minecraft-server/
    paper-1.21.11-127.jar    ← Paperサーバー本体
    start.bat                ← 起動スクリプト（Java 21で起動）
    eula.txt                 ← EULA同意済み
    server.properties        ← フラットワールド設定済み
    plugins/                 ← プラグインはここに置く
```

- **Java 21**: `C:\Program Files\Microsoft\jdk-21.0.10.7-hotspot` （wingetでインストール済み）
- **Java 17**: `C:\Users\yufuj\AppData\Local\Programs\Microsoft\jdk-17.0.15.6-hotspot` （ビルド用）

---

## STEP 3: サーバーを起動する

### 方法1: start.bat をダブルクリック

`minecraft-server\start.bat` をダブルクリック。

### 方法2: ターミナルから

```bash
cd minecraft-server
"C:\Program Files\Microsoft\jdk-21.0.10.7-hotspot\bin\java.exe" -Xmx2G -Xms2G -jar paper-1.21.11-127.jar nogui
```

### 起動成功の確認

コンソールに以下のような表示が出れば成功：

```
[SwarmVsHunter] SwarmVsHunter v3.0 enabled!
Done (XX.XXXs)! For help, type "help"
```

### サーバーを止める

コンソールに `stop` と入力して Enter。

---

## STEP 4: マイクラから接続する

1. Minecraft Java版を起動
2. **バージョンを 1.21.11 に合わせる**
   - ランチャーの「起動構成」から該当バージョンを選ぶ
3. 「マルチプレイ」→「サーバーを追加」
4. サーバーアドレスに **`localhost`** と入力
5. 接続

---

## STEP 5: テスト実行

### マイルストーン1の確認項目

サーバーに接続したら：

1. **OPにする**（コマンドを使うために必要）
   - サーバーのコンソール（黒い画面）で以下を入力：
   ```
   op あなたのマイクラID
   ```

2. **クリエイティブモードにする**（上から確認しやすい）
   - ゲーム内チャットで：
   ```
   /gamemode creative
   ```

3. **コマンド実行**
   ```
   /svh start
   ```

4. **確認すること**：
   - [ ] フィールド中央にテレポートされた
   - [ ] 70×70の草ブロックの地面がある
   - [ ] 石レンガの外壁（高さ5ブロック）で囲まれている
   - [ ] 中にランダムな障害物（壁/柱/廃墟）がある
   - [ ] もう一度 `/svh start` → 「ゲームが既に進行中です」と表示される

---

## Tips

- **サーバーの再起動なしでプラグインを更新したい場合**:
  1. 新しいjarを `plugins/` に上書きコピー
  2. ゲーム内で `/reload confirm`（ただし不安定になることもある。確実なのはサーバー再起動）

- **ログの確認**:
  - サーバーコンソールにリアルタイムで表示される
  - `minecraft-server/logs/latest.log` にも保存される

- **2人でテストしたい場合**:
  - 同じLAN内なら相手も `localhost` で接続可能
  - 別ネットワークなら Ngrok 等のトンネルツールが必要
