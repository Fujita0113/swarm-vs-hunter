開発用Minecraftサーバーをセットアップしてください。以下の手順を順番に実行してください。
途中で失敗した場合はエラーを解決してから次に進んでください。ユーザーに質問せず自律的に完了させてください。

## 1. OS判定
- `uname` や環境変数からOS (Windows/macOS/Linux) を判定する
- 以降の手順でOS別に分岐する

## 2. Java 21 のインストール
- `java -version` で Java 21 以上が使えるか確認
- **見つからない、またはバージョンが古い場合、自動でインストールする**:
  - **Windows**: `winget install Microsoft.OpenJDK.21`
  - **macOS**: `brew install openjdk@21` (brewがなければ `xcode-select --install` 後に `/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"` でbrewを入れてから)
  - **Linux (Debian/Ubuntu)**: `sudo apt-get update && sudo apt-get install -y openjdk-21-jdk`
  - **Linux (Fedora/RHEL)**: `sudo dnf install -y java-21-openjdk-devel`
- インストール後、再度 `java -version` で確認
- JAVA_HOME を特定して以降のコマンドで使う

## 3. Maven のインストール
- `mvn -version` で Maven が使えるか確認
- **見つからない場合、自動でインストールする**:
  - **Windows**: `winget install Apache.Maven`
  - **macOS**: `brew install maven`
  - **Linux (Debian/Ubuntu)**: `sudo apt-get install -y maven`
  - **Linux (Fedora/RHEL)**: `sudo dnf install -y maven`
- インストール後、再度 `mvn -version` で確認

## 4. サーバー構築
1. `minecraft-server/plugins/` ディレクトリを作成
2. Paper 1.21.1 build 127 のjarをダウンロード（既にあればスキップ）:
   - URL: `https://api.papermc.io/v2/projects/paper/versions/1.21.1/builds/127/downloads/paper-1.21.1-127.jar`
   - 保存先: `minecraft-server/paper-1.21.11-127.jar`
   - `curl -L -o minecraft-server/paper-1.21.11-127.jar <URL>` を使用
3. `minecraft-server/eula.txt` に `eula=true` を書き込み
4. `minecraft-server/start.bat` を作成 (Windows用):
   ```
   @echo off
   java -Xmx2G -Xms2G -jar paper-1.21.11-127.jar nogui
   pause
   ```
5. `minecraft-server/start.sh` を作成 (macOS/Linux用、chmod +x):
   ```
   #!/bin/bash
   java -Xmx2G -Xms2G -jar paper-1.21.11-127.jar nogui
   ```

## 5. 依存プラグインのインストール
1. LibsDisguises（Swarm変身の見た目変更に必要）:
   - GitHub Releases APIで最新バージョンのURLを取得:
     `curl -sL "https://api.github.com/repos/libraryaddict/LibsDisguises/releases/latest"` からjarのURLを取得
   - `minecraft-server/plugins/` にダウンロード
   - 既にLibsDisguisesのjarがあればスキップ

## 6. 初回起動（server.propertiesが無い場合のみ）
1. `minecraft-server/` でサーバーをバックグラウンド起動
2. `server.properties` が生成されるまで待機（最大60秒）
3. サーバーを停止

## 7. 設定調整（server.propertiesがある場合）
以下を開発用に変更:
- `online-mode=false`
- `spawn-protection=0`
- `level-type=minecraft\:flat`

## 8. プラグインビルド & 配置
1. 手順2で特定したJAVA_HOMEを使って `mvn clean package` を実行
2. `target/swarm-vs-hunter-3.0.jar` を `minecraft-server/plugins/` にコピー

## 9. 完了報告
セットアップ結果をまとめて報告してください。各ステップの成否を含めること。
