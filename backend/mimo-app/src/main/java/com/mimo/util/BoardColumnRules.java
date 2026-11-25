package com.mimo.util;

/**
 * 看板列的语义判定 —— **只有这一处**。
 *
 * <p>为什么单独抽出来：看板给"能不能把卡片拖进完成列""验收门禁""趋势统计"用同一个判断，
 * 以前这个判断写在 `BoardService#isTerminalColumn` 里，别处要用只能再抄一遍
 * （抄出来就会不一致，和 BUG-115/117 是同一类毛病）。</p>
 *
 * <p>判定依据只看列名，因为 `board_columns` 表里没有"列类型"字段：
 * 名字里含 完成 / done / 关闭 / closed 即视为终态列。</p>
 */
public final class BoardColumnRules {

    private BoardColumnRules() {
    }

    /** 这一列是不是"终态/完成"列（拖进去就算做完） */
    public static boolean isTerminalName(String columnName) {
        if (columnName == null) return false;
        String name = columnName.toLowerCase();
        return name.contains("完成") || name.contains("done")
                || name.contains("关闭") || name.contains("closed");
    }
}
