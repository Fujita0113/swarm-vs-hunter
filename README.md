# Swarm vs Hunter v3

Minecraft 2人対戦ミニゲーム「Swarm vs Hunter」のSpigot/Paperプラグイン。

- **Swarm**: HP1、mobに変身して群れで襲う
- **Hunter**: 鉄装備、3分生存 or 3回キルで勝利

詳細仕様は `requirements.md` を参照。

## 必要環境

- Java 21 (`/setup` が未インストールなら自動でインストールします)
- Maven (`/setup` が未インストールなら自動でインストールします)

## セットアップ

### Claude Code を使う場合 (推奨)

リポジトリをcloneしてClaude Codeを起動し、`/setup` を実行するだけです。

```bash
git clone <repo-url>
cd minecraft-project-swarm-vs-hunter
claude   # Claude Code を起動
```

Claude Code 内で:
```
/setup
```

Paper 1.21.1 (build 127) のダウンロード、サーバー構築、設定調整、プラグインビルド&配置まで全自動で行います。

### シェルスクリプトを使う場合

```bash
bash setup-server.sh
```

### 手動セットアップ

1. [PaperMC](https://papermc.io/downloads/paper) から Paper 1.21.1 build 127 をダウンロード
2. `minecraft-server/` フォルダを作成し、jarを配置
3. `eula.txt` に `eula=true` を記述
4. サーバーを起動: `java -Xmx2G -Xms2G -jar paper-1.21.11-127.jar nogui`
5. `server.properties` で `online-mode=false`, `spawn-protection=0` に設定
6. プラグインをビルドしてjarを `plugins/` にコピー

## ビルド

```bash
# JAVA_HOME が Java 21 でない場合は指定する
JAVA_HOME="C:/Program Files/Microsoft/jdk-21.0.10.7-hotspot" mvn clean package
```

生成物: `target/swarm-vs-hunter-3.0.jar`

## テスト

```bash
JAVA_HOME="C:/Program Files/Microsoft/jdk-21.0.10.7-hotspot" mvn test
```

## サーバー起動

```bash
cd minecraft-server

# Windows
start.bat

# Linux/Mac
./start.sh
```
