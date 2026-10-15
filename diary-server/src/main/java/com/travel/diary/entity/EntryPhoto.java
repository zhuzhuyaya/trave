package com.travel.diary.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 日记附件。
 *
 * <p>{@code entryId} 允许为空:上传接口先把文件落盘并返回 url,
 * 用户点"保存"时才把附件集合提交上来,由服务端回填 entryId。
 * 代价是会有少量"传了图但没保存日记"的孤儿文件,本地磁盘场景下可以接受。
 */
@Data
@TableName("entry_photos")
public class EntryPhoto {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long entryId;

    private Long userId;

    private String url;

    /** 本期与 url 同值,留字段备将来做服务端缩放 */
    private String thumbUrl;

    private Integer width;

    private Integer height;

    private Integer sortOrder;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;
}
