#!/bin/bash

# 使用方法: ./replace_string.sh [対象ディレクトリ]
# 引数がない場合は現在のディレクトリを対象とする

# 対象ディレクトリ（引数がない場合は現在のディレクトリ）
TARGET_DIR="${1:-.}"

# 置換前の文字列
OLD_STRING="/Users/satorukano/repository/research/TraceCollector/repos/main/zookeeper"
# 置換後の文字列
NEW_STRING="/work/satoru-k/projects/zookeeper"

# 処理開始
echo "置換処理を開始します..."
echo "対象ディレクトリ: $TARGET_DIR"
echo "置換前: $OLD_STRING"
echo "置換後: $NEW_STRING"
echo ""

# カウンター
count=0

# findコマンドでファイルを検索し、grepで対象文字列を含むファイルのみ処理
find "$TARGET_DIR" -type f -name "*" 2>/dev/null | while read -r file; do
    # バイナリファイルをスキップ
    if file "$file" | grep -q "text"; then
        # ファイルに対象文字列が含まれているかチェック
        if grep -q "$OLD_STRING" "$file" 2>/dev/null; then
            echo "処理中: $file"
            
            # sedで置換（バックアップなし）
            if [[ "$OSTYPE" == "darwin"* ]]; then
                # macOS
                sed -i '' "s|$OLD_STRING|$NEW_STRING|g" "$file"
            else
                # Linux
                sed -i "s|$OLD_STRING|$NEW_STRING|g" "$file"
            fi
            
            if [ $? -eq 0 ]; then
                echo "  → 置換成功"
                ((count++))
            else
                echo "  → 置換失敗"
            fi
        fi
    fi
done

echo ""
echo "処理完了: $count 個のファイルを置換しました"