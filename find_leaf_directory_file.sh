#!/bin/bash

# スクリプト名: find_leaf_directories.sh
# 機能: 指定されたディレクトリ内のleaf directoryを見つけてtxtファイルに出力

# 使用方法をチェック
if [ $# -eq 0 ]; then
    echo "使用方法: $0 <検索対象ディレクトリ> [出力ファイル名]"
    echo "例: $0 /path/to/directory leaf_dirs.txt"
    exit 1
fi

# 引数を変数に代入
TARGET_DIR="$$1"
OUTPUT_FILE="${2:-leaf_directories.txt}"

# ターゲットディレクトリが存在するかチェック
if [ ! -d "$TARGET_DIR" ]; then
    echo "エラー: ディレクトリ '$TARGET_DIR' が存在しません。"
    exit 1
fi

# 出力ファイルを初期化
> "$OUTPUT_FILE"

echo "検索中: $TARGET_DIR"
echo "出力先: $OUTPUT_FILE"

# leaf directoryを見つける関数
find_leaf_directories() {
    # findコマンドでディレクトリを検索し、各ディレクトリについて
    # 子ディレクトリが存在しないかチェック
    find "$TARGET_DIR" -type d | while read -r dir; do
        # 現在のディレクトリに子ディレクトリがあるかチェック
        if [ ! "$(find "$dir" -mindepth 1 -maxdepth 1 -type d 2>/dev/null)" ]; then
            # 絶対パスに変換して出力
            realpath "$dir"
        fi
    done
}

# leaf directoryを見つけて出力ファイルに保存
leaf_count=0
find_leaf_directories | while read -r leaf_dir; do
    echo "$leaf_dir" >> "$OUTPUT_FILE"
    leaf_count=$((leaf_count + 1))
done

# 結果を表示
actual_count=$(wc -l < "$OUTPUT_FILE" 2>/dev/null || echo "0")
echo "完了: ${actual_count}個のleaf directoryが見つかりました。"
echo "結果は '$OUTPUT_FILE' に保存されました。"