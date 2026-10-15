package com.travel.diary.vo;

import lombok.Data;

/** 开关型操作的结果(收藏等)。 */
@Data
public class ToggleVO {

    /** 操作之后的开关状态 */
    private boolean active;

    /** 操作之后的总数 */
    private long count;

    public static ToggleVO of(boolean active, long count) {
        ToggleVO vo = new ToggleVO();
        vo.setActive(active);
        vo.setCount(count);
        return vo;
    }
}
