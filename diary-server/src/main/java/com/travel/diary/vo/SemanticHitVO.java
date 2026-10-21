package com.travel.diary.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 语义搜索的一条命中。
 *
 * <p>直接复用时间线卡片 VO,前端不必为 AI 结果再写一套卡片组件;
 * score 是余弦相似度(0~1),用于展示相关度,不参与业务判断。
 */
@Data
@AllArgsConstructor
public class SemanticHitVO {

    private EntrySummaryVO entry;

    private double score;
}
