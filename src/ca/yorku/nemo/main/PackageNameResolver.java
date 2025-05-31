package ca.yorku.nemo.main;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class PackageNameResolver {

    private Map<String, String> fullyQualifiedClassNameToFilePathMap;

    public PackageNameResolver(String qualifyNameFilePath) throws IOException {
        fullyQualifiedClassNameToFilePathMap = new HashMap<>();
        loadQualifyNameFile(qualifyNameFilePath);
    }

    private void loadQualifyNameFile(String filePath) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",", 2);
                if (parts.length == 2) {
                    fullyQualifiedClassNameToFilePathMap.put(parts[0].trim(), parts[1].trim());
                }
            }
        }
    }

    /**
     * 省略されたパッケージ名とクラス名から、完全なパッケージ名を復元します。
     *
     * @param abbreviatedPackage 例: "o.a.z.m"
     * @param className          例: "Counter"
     * @return 完全なパッケージ名 (例: "org.apache.zookeeper.metrics")、見つからない場合は null
     */
    public String restoreFullPackageName(String abbreviatedPackage, String className) {
        String[] abbreviatedParts = abbreviatedPackage.split("\\.");

        for (String fullClassName : fullyQualifiedClassNameToFilePathMap.keySet()) {
            if (fullClassName.endsWith("." + className)) {
                String fullPackageName = fullClassName.substring(0, fullClassName.lastIndexOf("." + className));
                String[] fullPackageParts = fullPackageName.split("\\.");

                if (abbreviatedParts.length == fullPackageParts.length) {
                    boolean match = true;
                    for (int i = 0; i < abbreviatedParts.length; i++) {
                        if (abbreviatedParts[i].isEmpty() || fullPackageParts[i].isEmpty() ||
                                !fullPackageParts[i].startsWith(abbreviatedParts[i])) {
                            // Logback の %C{precision} の挙動で、
                            // o.a.z.MyClass が o.MyClass のように中間のパッケージが省略される場合も考慮するなら、
                            // startsWith だけでは不十分かもしれない。
                            // ここでは、問題の前提「o.a.z.m」のように各要素の先頭文字が与えられると仮定。
                            // もし o.a.z.m.VeryLongPackage.MyClass が o.m.MyClass のように省略される場合、
                            // このロジックは修正が必要。
                            // 今回の前提「o.a.z.mでパッケージ名が一意に決まる」に基づき、
                            // 各要素の先頭文字が一致するかで判断。
                            if (abbreviatedParts[i].length() == 1 && // 省略形が1文字の場合
                                    fullPackageParts[i].charAt(0) != abbreviatedParts[i].charAt(0)) {
                                match = false;
                                break;
                            } else if (abbreviatedParts[i].length() > 1 && // 省略形が複数文字の場合 (通常はないはずだが念のため)
                                    !fullPackageParts[i].startsWith(abbreviatedParts[i])) {
                                match = false;
                                break;
                            }
                            // 上記は「o.a.z.m」が「org.apache.zookeeper.metrics」の各単語の頭文字であると解釈した場合。
                            // もし「o.a.z.m」が「org.apache.zookeeper.metrics」の部分文字列「o.a.z.m」と直接比較できるなら
                            // (つまり、ログ出力側がそういう省略をするなら)
                            // fullPackageParts[i].startsWith(abbreviatedParts[i]) だけで良い。
                            // Logback の %C{1} の挙動はクラス名のみを短縮するため、パッケージはそのままのはず。
                            // しかし、ログの例では o.a.z.Environment となっており、
                            // これは Logback のパターン %C{1} ではなく %C{depth} や %C そのものの挙動に近い。
                            // ここでは「o.a.z.m」が「org.apache.zookeeper.metrics」の各要素の
                            // 先頭文字(o, a, z, m)であるという前提で実装。
                        }
                    }
                    if (match) {
                        return fullPackageName;
                    }
                }
            }
        }
        return null; // 見つからない場合
    }

    /**
     * Logback の %C (または %C{precision}) が出力するような形式 (例: o.a.z.Environment) から、
     * 完全なパッケージ名とクラス名を推測して復元します。
     *
     * @param loggerNameFromLog 例: "o.a.z.Environment" や "o.a.z.m.Counter"
     * @return 完全修飾クラス名 (例: "org.apache.zookeeper.Environment")、見つからない場合は null
     */
    public String resolveFullClassName(String loggerNameFromLog) {
        if (loggerNameFromLog == null || loggerNameFromLog.isEmpty()) {
            return null;
        }

        // 既存のマップに完全一致するものがあればそれを返す
        if (fullyQualifiedClassNameToFilePathMap.containsKey(loggerNameFromLog)) {
            return loggerNameFromLog;
        }

        String[] logParts = loggerNameFromLog.split("\\.");
        String classNameFromLog = logParts[logParts.length - 1]; // ログから取得した単純クラス名

        // 完全修飾クラス名の候補を絞り込む
        for (String fullQlName : fullyQualifiedClassNameToFilePathMap.keySet()) {
            if (fullQlName.endsWith("." + classNameFromLog)) { // まずクラス名が一致するか
                String fullPackageName = fullQlName.substring(0, fullQlName.lastIndexOf('.'));
                String[] fullPackageParts = fullPackageName.split("\\.");

                // ログのパッケージ部分の要素数 (クラス名を除く)
                int logPackagePartsCount = logParts.length - 1;

                if (logPackagePartsCount > fullPackageParts.length) {
                    continue; // ログのパッケージ要素の方が多いのはおかしい
                }

                boolean possibleMatch = true;
                // 後方から比較していく (org.apache.zookeeper.metrics と o.a.z.m のように)
                // j = フルパッケージのインデックス, k = ログのパッケージのインデックス
                for (int j = fullPackageParts.length - 1, k = logPackagePartsCount - 1; k >= 0; j--, k--) {
                    if (j < 0) { // フルパッケージの方が短い (ありえないが念のため)
                        possibleMatch = false;
                        break;
                    }
                    // ログのパッケージ要素が1文字の場合、フルパッケージ要素の先頭文字と比較
                    // ログのパッケージ要素が複数文字の場合、フルパッケージ要素がそれで始まるか比較 (通常ないはず)
                    if (logParts[k].length() == 1) {
                        if (fullPackageParts[j].charAt(0) != logParts[k].charAt(0)) {
                            possibleMatch = false;
                            break;
                        }
                    } else { // logParts[k].length() > 1
                        if (!fullPackageParts[j].equals(logParts[k])) { // 完全一致を期待
                            possibleMatch = false;
                            break;
                        }
                    }
                }

                if (possibleMatch) {
                    // 「o.a.z.mでパッケージ名が一意に決まる」という仮定に基づき、
                    // 最初に見つかったものを正しいとして返す
                    return fullQlName;
                }
            }
        }
        System.err.println("Could not resolve full class name for: " + loggerNameFromLog);
        return null; // 見つからなかった場合
    }


    public static void main(String[] args) {
        // 使用例
        // 事前に "qualifyname_filepath.txt" がカレントディレクトリにあるか、
        // 正しいパスを指定してください。
        String mapFilePath = "qualifyname_filepath.txt"; // FindFileOfClassで生成されたファイル

        try {
            PackageNameResolver resolver = new PackageNameResolver(mapFilePath);

            // テストケース1: ログの形式が "o.a.z.m.Counter" の場合
            String abbreviatedLogName1 = "o.a.z.m.Counter";
            String fullClassName1 = resolver.resolveFullClassName(abbreviatedLogName1);
            if (fullClassName1 != null) {
                System.out.println(abbreviatedLogName1 + " -> " + fullClassName1);
                // 期待値: org.apache.zookeeper.metrics.Counter (qualifyname_filepath.txt の内容に依存)
            } else {
                System.out.println("Could not resolve: " + abbreviatedLogName1);
            }

            // テストケース2: ログの形式が "o.a.z.Environment" の場合
            String abbreviatedLogName2 = "o.a.z.Environment";
            String fullClassName2 = resolver.resolveFullClassName(abbreviatedLogName2);
            if (fullClassName2 != null) {
                System.out.println(abbreviatedLogName2 + " -> " + fullClassName2);
                // 期待値: org.apache.zookeeper.Environment (qualifyname_filepath.txt の内容に依存)
            } else {
                System.out.println("Could not resolve: " + abbreviatedLogName2);
            }

            // テストケース3: マップに直接存在する完全修飾名
            String abbreviatedLogName3 = "org.apache.zookeeper.ZooKeeperMain"; // 仮の完全修飾名
            String fullClassName3 = resolver.resolveFullClassName(abbreviatedLogName3);
            if (fullClassName3 != null) {
                System.out.println(abbreviatedLogName3 + " -> " + fullClassName3);
            } else {
                System.out.println("Could not resolve: " + abbreviatedLogName3);
            }


        } catch (IOException e) {
            System.err.println("Error loading or processing the qualify name file: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
