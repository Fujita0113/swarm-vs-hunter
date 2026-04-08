開発用Minecraftサーバーをセットアップしてください。以下の手順を順番に実行してください。
途中で失敗した場合はエラーを解決してから次に進んでください。ユーザーに質問せず自律的に完了させてください。

## 1. OS判定
- `uname` でOS (Windows/macOS/Linux) を判定する
- Windows の場合、シェルは Git Bash (MINGW64) なので **パスは `/c/...` 形式**を使う

## 2. Java 21 のインストール
- まず `/c/Program Files/Microsoft/` 配下に `java.exe` があるか `find` で確認する
- なければ `java -version 2>&1` でも確認する
- **Java 21以上が見つからない場合、自動でインストールする**:
  - **Windows**: `winget install Microsoft.OpenJDK.21 --accept-source-agreements --accept-package-agreements`
  - **macOS**: `brew install openjdk@21`
  - **Linux (Debian/Ubuntu)**: `sudo apt-get update && sudo apt-get install -y openjdk-21-jdk`
  - **Linux (Fedora/RHEL)**: `sudo dnf install -y java-21-openjdk-devel`
- **Windowsの注意**: wingetインストール後、現在のシェルセッションではPATHが更新されない。
  `find "/c/Program Files/Microsoft/" -name "java.exe" 2>/dev/null | head -1` でフルパスを取得し、
  それを `JAVA_EXE` 変数に入れて以降のコマンドで使う（例: `JAVA_EXE="/c/Program Files/Microsoft/jdk-21.0.10.7-hotspot/bin/java"`）
- `JAVA_HOME` はその親ディレクトリ（例: `/c/Program Files/Microsoft/jdk-21.0.10.7-hotspot`）

## 3. Maven のインストール
- `mvn -version 2>&1` で Maven が使えるか確認
- 見つからない場合:
  - **Windows**: `winget install Apache.Maven` は **存在しない**。代わりに以下を実行:
    ```bash
    curl -L -o /tmp/maven.zip "https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.zip"
    unzip -q /tmp/maven.zip -d "/c/tools/"
    ```
    その後 `MAVEN_BIN="/c/tools/apache-maven-3.9.9/bin"` を変数に入れる
  - **macOS**: `brew install maven`
  - **Linux (Debian/Ubuntu)**: `sudo apt-get install -y maven`
  - **Linux (Fedora/RHEL)**: `sudo dnf install -y maven`
- インストール後、`PATH=$MAVEN_BIN:$JAVA_HOME/bin:$PATH JAVA_HOME="$JAVA_HOME" mvn -version` で確認

## 4. ~/.bashrc に PATH を永続化（Windowsのみ）
`~/.bashrc` に以下を追記（すでに書いてあればスキップ）:
```bash
export JAVA_HOME="<手順2で特定したJAVA_HOME>"
export PATH="/c/tools/apache-maven-3.9.9/bin:$JAVA_HOME/bin:$PATH"
```
既存の ~/.bashrc がなければ新規作成する。

## 5. サーバー構築
1. `minecraft-server/plugins/` ディレクトリを作成
2. Paper 1.21.1 build 127 のjarをダウンロード（既にあればスキップ）:
   - 保存先: `minecraft-server/paper-1.21.11-127.jar`
   - `curl -L -o minecraft-server/paper-1.21.11-127.jar "https://api.papermc.io/v2/projects/paper/versions/1.21.1/builds/127/downloads/paper-1.21.1-127.jar"`
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
1. `minecraft-server/` に `cd` してから、**フルパスのjava**でバックグラウンド起動:
   ```bash
   cd /c/Users/.../minecraft-server
   "$JAVA_EXE" -Xmx512M -Xms256M -jar paper-1.21.11-127.jar nogui &
   SERVER_PID=$!
   ```
2. `server.properties` が生成されるまで1秒ごとにポーリング（最大90秒）:
   ```bash
   for i in $(seq 1 90); do
     sleep 1
     [ -f "server.properties" ] && echo "Generated after ${i}s" && break
     kill -0 $SERVER_PID 2>/dev/null || { echo "Server exited at ${i}s"; break; }
   done
   ```
3. サーバーを停止してから `ls server.properties` で生成を確認:
   ```bash
   kill $SERVER_PID 2>/dev/null || true
   wait $SERVER_PID 2>/dev/null || true
   ls -la server.properties
   ```

## 7. 設定調整（server.propertiesがある場合）
以下を開発用に変更（Editツールで1行ずつ確実に）:
- `online-mode=true` → `online-mode=false`
- `spawn-protection=16` → `spawn-protection=0`
- `level-type=minecraft\:normal` → `level-type=minecraft\:flat`

## 8. プラグインビルド & 配置
1. プロジェクトルートで `JAVA_HOME` と `PATH` をセットしてビルド:
   ```bash
   export JAVA_HOME="<手順2のJAVA_HOME>"
   export PATH="/c/tools/apache-maven-3.9.9/bin:$JAVA_HOME/bin:$PATH"
   mvn clean package
   ```
2. `target/swarm-vs-hunter-3.0.jar` を `minecraft-server/plugins/` にコピー

## 9. 完了報告
セットアップ結果をまとめて報告してください。各ステップの成否を含めること。
