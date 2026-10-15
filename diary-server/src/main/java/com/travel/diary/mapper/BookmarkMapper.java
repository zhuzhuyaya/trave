package com.travel.diary.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.travel.diary.entity.Bookmark;

/**
 * 收藏。
 *
 * <p>全部走 BaseMapper 的通用方法 —— 开关收藏只需要按
 * {@code (entry_id, user_id)} 条件查一次、插一条或删一条,唯一索引负责兜住并发重复。
 */
public interface BookmarkMapper extends BaseMapper<Bookmark> {
}
