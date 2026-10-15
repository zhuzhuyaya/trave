package com.travel.diary.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tags")
public class Tag {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private Integer useCount;

    private LocalDateTime createdAt;
}
