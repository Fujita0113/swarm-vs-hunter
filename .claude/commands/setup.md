開発用Minecraftサーバーをセットアップしてください。以下の手順を順番に実行してください。

## 前提チェック
- Java 21 が利用可能か確認（`java -version`）。なければユーザーに警告
- Maven が利用可能か確認（`mvn -version`）。なければユーザーに警告

## サーバー構築
1. `minecraft-server/plugins/` ディレクトリを作成
2. Paper 1.21.1 build 127 のjarをダウンロード（既にあればスキップ）:
   - URL: `https://api.papermc.io/v2/projects/paper/versions/1.21.1/builds/127/downloads/paper-1.21.1-127.jar`
   - 保存先: `minecraft-server/paper-1.21.11-127.jar`
   - `curl -L -o minecraft-server/paper-1.21.11-127.jar <URL>` を使用
3. `minecraft-server/eula.txt` に `eula=true` を書き込み
4. `minecraft-server/start.bat` を作成:
   ```
   @echo off
   java -Xmx2G -Xms2G -jar paper-1.21.11-127.jar nogui
   pause
   ```
5. `minecraft-server/start.sh` を作成（chmod +x）:
   ```
   #!/bin/bash
   java -Xmx2G -Xms2G -jar paper-1.21.11-127.jar nogui
   ```

## 初回起動（server.propertiesが無い場合のみ）
1. `minecraft-server/` でサーバーをバックグラウンド起動
2. `server.properties` が生成されるまで待機（最大60秒）
3. サーバーを停止

## 設定調整（server.propertiesがある場合）
以下を開発用に変更:
- `online-mode=false`
- `spawn-protection=0`
- `level-type=minecraft\:flat`

## プラグインビルド & 配置
1. `JAVA_HOME="C:/Program Files/Microsoft/jdk-21.0.10.7-hotspot" mvn clean package` を実行
2. `target/swarm-vs-hunter-3.0.jar` を `minecraft-server/plugins/` にコピー

## 完了報告
セットアップ結果をまとめて報告してください。
