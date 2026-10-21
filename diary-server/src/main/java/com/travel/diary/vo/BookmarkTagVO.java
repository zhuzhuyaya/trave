package com.travel.diary.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 标签筛选项:名字 + 当前收藏集里挂这个标签的日记数。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookmarkTagVO {

    private String name;

    private long count;
}
