package com.travel.diary.common;

/**
 * 从 Markdown 原文生成列表页要用的纯文本摘要。
 *
 * <p>刻意做得"粗糙但可预测":列表页只需要一行能看懂的文字,
 * 用完整 Markdown 解析器在这里纯属浪费 —— 而且渲染是前端的事,
 * 后端只负责剥掉那些在卡片上会变成噪音的记号。
 */
public final class Markdown {

    /** 卡片摘要长度上限。比数据库里的 excerpt 字段(255)短,留足截断空间。 */
    private static final int EXCERPT_LENGTH = 120;

    private Markdown() {
    }

    /**
     * 剥离 Markdown 记号并截断。
     *
     * @return 纯文本摘要,永不为 null
     */
    public static String toExcerpt(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }

        String text = markdown
                // 代码块整块丢掉:它们在卡片上既读不懂也占地方
                .replaceAll("(?s)```.*?```", " ")
                // 图片与链接只保留可见文字
                .replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", " ")
                .replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1")
                // 行首的标题号、引用号、列表符
                .replaceAll("(?m)^\\s{0,3}#{1,6}\\s*", "")
                .replaceAll("(?m)^\\s{0,3}>\\s?", "")
                .replaceAll("(?m)^\\s{0,3}[-*+]\\s+", "")
                .replaceAll("(?m)^\\s{0,3}\\d+\\.\\s+", "")
                // 强调、行内代码、删除线
                .replaceAll("[*_`~]{1,3}", "")
                // 分隔线
                .replaceAll("(?m)^\\s{0,3}([-*_]\\s*){3,}$", " ")
                // 表格竖线与多余空行
                .replace("|", " ")
                .replaceAll("\\s+", " ");

        text = text.trim();
        if (text.length() <= EXCERPT_LENGTH) {
            return text;
        }
        return text.substring(0, EXCERPT_LENGTH) + "…";
    }
}
