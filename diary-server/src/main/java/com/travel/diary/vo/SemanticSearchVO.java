package com.travel.diary.vo;

import lombok.Data;

import java.util.List;

/**
 * 语义搜索结果。
 *
 * <p>enabled 把"AI 没开"和"有 AI 但没搜到"分成两种状态:
 * 前者前端应提示去配置 AI,后者才是换种说法再试。
 */
@Data
public class SemanticSearchVO {

    private boolean enabled;

    private List<SemanticHitVO> hits = List.of();
}
