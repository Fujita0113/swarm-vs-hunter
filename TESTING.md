# テストガイド — ゼロからプラグインを動かすまで

## 前提
- Minecraft Java版を持っている（遊んだことがある）
- Java 17 以上がインストール済み（このプロジェクトのビルドで確認済み）
- 2人テストにはPC2台が必要（アカウントは1つでOK。`online-mode=false` 設定済み）

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

### マイルストーン2の確認項目

> **2人必要です。** 1人で `/svh start` すると「2人のプレイヤーが必要です」と出ます。
> LAN内なら2台目のPCから `localhost` で接続するか、同じPCで別アカウントで2つ起動してください。

> **関連コード**: [SwarmVsHunter.java](src/main/java/com/swarmvshunter/SwarmVsHunter.java) の `onCommand`(L93)、`openMobSelectionGUI`(L132)、`startGame`(L247)、`actuallyStartGame`(L280)

#### 準備

1. 2人ともサーバーに接続する
2. 両方のプレイヤーをOPにする（サーバーコンソールで `op プレイヤー名` を2回）

#### テスト A: mob選択GUI

1人目が `/svh start` を実行。

- [ ] 両プレイヤーに役割メッセージが表示される（「あなたはSwarmです」「あなたはHunterです」）
- [ ] 両プレイヤーに6行チェストGUIが開く
- [ ] GUIにスポーンエッグが並んでいる（豚、牛、ゾンビ、スケルトン等）
- [ ] 各エッグに「非戦闘mob」（緑）or「戦闘mob」（赤）のラベルがある
- [ ] エッグをクリック → 「〇〇 を選択しました (1/2)」とチャットに表示
- [ ] 相手側にも「Swarm/Hunterが 〇〇 を選択しました」と通知される
- [ ] 戦闘mob2体を選ぼうとする → 「1体は非戦闘mob（豚、牛等）を選んでください」と拒否
- [ ] 選択済みmobをクリック → 選択解除される
- [ ] 2体選択済みでさらにクリック → 「既に2体選択済み」と拒否

> **見るべきコード**: `openMobSelectionGUI`(L132) — GUIの中身、`onInventoryClick`(L168) — 選択ロジック・制約チェック

#### テスト B: カウントダウン → ゲーム開始

両プレイヤーが2体ずつ選択完了すると自動で進む。

- [ ] GUIが自動で閉じる
- [ ] 「全員の選択が完了！ゲーム開始準備中...」と表示
- [ ] 「>>> 3 <<<」「>>> 2 <<<」「>>> 1 <<<」と1秒間隔でカウントダウン表示
- [ ] カウントダウン後にフィールドが生成される
- [ ] SwarmとHunterがフィールド内の別々の位置にテレポートされる

> **見るべきコード**: `startGame`(L247) — カウントダウンのBukkitRunnable、`actuallyStartGame`(L280) — TP・装備

#### テスト C: Hunter初期装備

カウントダウン完了後のHunterを確認。

- [ ] 鉄ヘルメット、鉄チェストプレート、鉄レギンス、鉄ブーツを着用している
- [ ] メインハンドに鉄剣
- [ ] オフハンドに盾
- [ ] インベントリに弓×1、矢×16、パン×4がある

> **見るべきコード**: `equipHunter`(L300)

#### テスト D: 中立mobシステム

- [ ] フィールド内にmobがスポーンしている（選択した4種類）
- [ ] ゾンビやスケルトン等の敵対mobもプレイヤーを攻撃しない（中立化されている）
- [ ] mobの近くに立っても襲ってこない

> **見るべきコード**: `spawnMobs`(L317) — mob配置、`onEntityTarget`(L347) — 中立化イベントハンドラ

#### テスト E: Swarmの初期状態

- [ ] Swarmのインベントリは空
- [ ] SwarmのHPが1（ハート半分）

---

### マイルストーン3の確認項目

> **2人必要です。** マイルストーン2と同じセットアップ。
> **関連コード**: [SwarmVsHunter.java](src/main/java/com/swarmvshunter/SwarmVsHunter.java) の `transformSwarm`、`revertSwarm`、`aggroNearbyMobs`、`onEntityDamageByEntity`、`onEntityTarget`

#### テスト F: Swarm変身（mob殴り → 変身）

1. `/svh start` でゲーム開始、mob選択完了まで進める
2. Swarm側で近くのmobを殴る

- [ ] 殴ったmobがSwarmに向かって反撃してくる
- [ ] mobの攻撃を受けるとSwarmがそのmob種に「変身」する（透明化＋ネームタグ消失）
- [ ] 変身時メッセージ「〇〇 に変身した！」が表示される
- [ ] Hunter側にも「Swarmが 〇〇 に変身した！」と表示される
- [ ] Swarmは透明になり、ネームタグが見えない

#### テスト G: mob敵対化

- [ ] 変身した瞬間、半径20ブロック以内の同種mobがHunterに向かって移動し攻撃する
- [ ] 同種でないmobは中立のまま
- [ ] 敵対化したmobの中にSwarm本体が紛れている状態になる

#### テスト H: Hunterによるキル → 人間に戻る

1. HunterがSwarm（透明状態）を攻撃する

- [ ] Swarmが人間に戻る（透明化解除、ネームタグ復活）
- [ ] 敵対化していたmobが中立に戻る
- [ ] Swarmがフィールド内ランダム位置にリスポーンする
- [ ] 「Hunterに倒された！人間に戻された (死亡: X/3)」とSwarmに表示
- [ ] 「Swarmを倒した！ (キル: X/3)」とHunterに表示

#### テスト I: 変身切り替え

1. 変身中のSwarmが別の種類のmobを殴る

- [ ] 別のmobに反撃されると、新しいmob種に変身が切り替わる
- [ ] 以前の敵対mobが中立に戻り、新しい同種mobが敵対化する

#### テスト J: 環境ダメージ保護

- [ ] Swarmが高所から落下してもダメージを受けない（HP1保護）
- [ ] 火や溶岩でもダメージを受けない

---

## Tips

- **サーバーの再起動なしでプラグインを更新したい場合**:
  1. 新しいjarを `plugins/` に上書きコピー
  2. ゲーム内で `/reload confirm`（ただし不安定になることもある。確実なのはサーバー再起動）

- **ログの確認**:
  - サーバーコンソールにリアルタイムで表示される
  - `minecraft-server/logs/latest.log` にも保存される

- **2台目のPCから接続する方法**（アカウント1つ・PC2台の場合）:

  **サーバー側（PC1）の準備:**
  1. `server.properties` の `online-mode=false` を確認（設定済み）
  2. PC1のローカルIPアドレスを調べる:
     - Windowsキー → 「cmd」→ `ipconfig` を実行
     - 「IPv4 アドレス」の値をメモ（例: `192.168.1.10`）
  3. `start.bat` でサーバーを起動する

  **2台目（PC2）の準備:**
  1. PC2でMinecraft Java版を起動（PC1と同じアカウントでOK）
  2. ランチャーの「起動構成」でバージョンを **1.21.1** に合わせる
  3. **重要**: 起動構成の名前は何でもよいが、ゲーム内のプレイヤー名はPC1と同じになる。
     別名にしたい場合は、起動後に `server.properties` は不要で、サーバーコンソールで区別可能
  4. 「マルチプレイ」→「サーバーを追加」
  5. サーバーアドレスに **PC1のIPアドレス**（例: `192.168.1.10`）を入力
  6. 接続

  **接続できない場合のチェックリスト:**
  - [ ] PC1とPC2は同じWi-Fi / LANに接続しているか？
  - [ ] PC1のファイアウォールがポート25565を許可しているか？
    - Windowsの場合: 「Windows Defender ファイアウォール」→「詳細設定」→「受信の規則」→「新しい規則」→ ポート 25565 (TCP) を許可
  - [ ] `ipconfig` で調べたIPアドレスは正しいか？（`192.168.x.x` や `10.x.x.x` の形）
  - [ ] サーバーが起動完了しているか？（`Done` メッセージを確認）

  **注意**: `online-mode=false` はローカルテスト専用の設定です。外部に公開するサーバーでは絶対に使わないでください。
