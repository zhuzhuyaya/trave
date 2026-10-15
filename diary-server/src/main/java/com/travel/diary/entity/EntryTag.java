package com.travel.diary.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 日记-标签关联。
 *
 * <p>复合主键,没有自增 id,因此没有 {@code @TableId} ——
 * MyBatis-Plus 对这类实体只支持 {@code insert} 与 {@code delete(Wrapper)},
 * 恰好够用(关联记录只会整体替换,不会单独更新)。
 */
@Data
@TableName("entry_tags")
public class EntryTag {

    private Long entryId;

    private Long tagId;

    public static EntryTag of(Long entryId, Long tagId) {
        EntryTag row = new EntryTag();
        row.setEntryId(entryId);
        row.setTagId(tagId);
        return row;
    }
}
