package com.travel.diary.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("bookmarks")
public class Bookmark {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long entryId;

    private Long userId;

    private LocalDateTime createdAt;
}
