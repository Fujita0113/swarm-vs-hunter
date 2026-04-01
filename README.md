# Swarm vs Hunter v3

Minecraft 2人対戦ミニゲーム「Swarm vs Hunter」のSpigot/Paperプラグイン。

- **Swarm**: HP1、mobに変身して群れで襲う
- **Hunter**: 鉄装備、3分生存 or 3回キルで勝利

詳細仕様は `requirements.md` を参照。

## 必要環境

- Java 21
- Maven
- Git Bash (Windows) または bash (Linux/Mac)

## セットアップ

### 自動セットアップ (推奨)

リポジトリをcloneした後、スクリプト1発で開発用サーバーが構築されます。

```bash
git clone <repo-url>
cd minecraft-project-swarm-vs-hunter
bash setup-server.sh
```

スクリプトが行うこと:
1. Java / Maven の存在チェック
2. Paper 1.21.1 (build 127) のダウンロード
3. `minecraft-server/` にサーバー環境を構築
4. `eula.txt` 自動承認
5. 起動スクリプト (`start.bat` / `start.sh`) 生成
6. 初回起動で設定ファイル生成 (既に存在する場合はスキップ)
7. `server.properties` を開発用に調整 (`online-mode=false`, `spawn-protection=0`, `level-type=flat`)
8. プラグインをビルドして `plugins/` に配置

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
