#!/bin/bash
set -e

PAPER_VERSION="1.21.1"
PAPER_BUILD="127"
PAPER_JAR="paper-1.21.11-$PAPER_BUILD.jar"
PAPER_URL="https://api.papermc.io/v2/projects/paper/versions/$PAPER_VERSION/builds/$PAPER_BUILD/downloads/paper-$PAPER_VERSION-$PAPER_BUILD.jar"
SERVER_DIR="minecraft-server"

echo "=== Swarm vs Hunter v3 - Server Setup ==="

# Java 21 チェック
if command -v java &> /dev/null; then
    JAVA_VER=$(java -version 2>&1 | head -1 | sed 's/.*"\([0-9]*\).*/\1/')
    if [ "$JAVA_VER" -lt 21 ] 2>/dev/null; then
        echo "[WARN] Java 21 が必要です (検出: Java $JAVA_VER)"
        echo "  JAVA_HOME に Java 21 を設定してください"
    else
        echo "[OK] Java $JAVA_VER 検出"
    fi
else
    echo "[WARN] java コマンドが見つかりません。Java 21 をインストールしてください"
fi

# Maven チェック
if command -v mvn &> /dev/null; then
    echo "[OK] Maven 検出"
else
    echo "[WARN] mvn コマンドが見つかりません。Maven をインストールしてください"
fi

# サーバーディレクトリ作成
mkdir -p "$SERVER_DIR/plugins"
echo "[OK] $SERVER_DIR/ ディレクトリ作成"

# Paper jar ダウンロード
if [ -f "$SERVER_DIR/$PAPER_JAR" ]; then
    echo "[SKIP] $PAPER_JAR は既に存在します"
else
    echo "Paper $PAPER_VERSION build $PAPER_BUILD をダウンロード中..."
    curl -L -o "$SERVER_DIR/$PAPER_JAR" "$PAPER_URL"
    echo "[OK] $PAPER_JAR ダウンロード完了"
fi

# eula.txt
echo "eula=true" > "$SERVER_DIR/eula.txt"
echo "[OK] eula.txt 作成 (eula=true)"

# start.bat (Windows用)
cat > "$SERVER_DIR/start.bat" << 'BATCH'
@echo off
java -Xmx2G -Xms2G -jar paper-1.21.11-127.jar nogui
pause
BATCH
echo "[OK] start.bat 作成"

# start.sh (Linux/Mac用)
cat > "$SERVER_DIR/start.sh" << 'SHELL'
#!/bin/bash
java -Xmx2G -Xms2G -jar paper-1.21.11-127.jar nogui
SHELL
chmod +x "$SERVER_DIR/start.sh"
echo "[OK] start.sh 作成"

# サーバー初回起動 → 設定ファイル生成 → 停止
if [ ! -f "$SERVER_DIR/server.properties" ]; then
    echo "サーバーを初回起動して設定ファイルを生成します..."
    cd "$SERVER_DIR"
    java -Xmx2G -Xms2G -jar "$PAPER_JAR" nogui &
    SERVER_PID=$!
    # server.properties が生成されるまで待つ (最大60秒)
    for i in $(seq 1 60); do
        if [ -f "server.properties" ]; then
            break
        fi
        sleep 1
    done
    sleep 3
    kill $SERVER_PID 2>/dev/null || true
    wait $SERVER_PID 2>/dev/null || true
    cd ..
    echo "[OK] 設定ファイル生成完了"
else
    echo "[SKIP] server.properties は既に存在します"
fi

# server.properties を開発用に調整
if [ -f "$SERVER_DIR/server.properties" ]; then
    sed -i 's/online-mode=true/online-mode=false/' "$SERVER_DIR/server.properties"
    sed -i 's/spawn-protection=16/spawn-protection=0/' "$SERVER_DIR/server.properties"
    # level-type を flat に設定
    sed -i 's/level-type=minecraft\\:normal/level-type=minecraft\\:flat/' "$SERVER_DIR/server.properties"
    echo "[OK] server.properties を開発用に調整"
fi

# プラグインビルド & 配置
echo "プラグインをビルド中..."
JAVA_HOME="${JAVA_HOME:-C:/Program Files/Microsoft/jdk-21.0.10.7-hotspot}" mvn clean package -q
cp target/swarm-vs-hunter-3.0.jar "$SERVER_DIR/plugins/"
echo "[OK] プラグイン jar を plugins/ に配置"

echo ""
echo "=== セットアップ完了 ==="
echo "サーバー起動: cd $SERVER_DIR && ./start.sh (または start.bat)"
