package com.travel.diary.service;

import com.travel.diary.common.PageResult;
import com.travel.diary.dto.EntryQuery;
import com.travel.diary.dto.EntryRequest;
import com.travel.diary.entity.Entry;
import com.travel.diary.security.LoginUser;
import com.travel.diary.vo.BookmarkPageVO;
import com.travel.diary.vo.EntryDetailVO;
import com.travel.diary.vo.EntrySummaryVO;
import com.travel.diary.vo.ToggleVO;

public interface EntryService {

    PageResult<EntrySummaryVO> list(EntryQuery query, LoginUser viewer);

    EntryDetailVO detail(Long id, LoginUser viewer);

    EntryDetailVO create(EntryRequest request, LoginUser author);

    EntryDetailVO update(Long id, EntryRequest request, LoginUser editor);

    void delete(Long id, LoginUser operator);

    void recordView(Long id, LoginUser viewer);

    ToggleVO toggleBookmark(Long id, LoginUser user);

    /**
     * 收藏列表:当前用户收藏的、此刻仍可读的日记,新→旧,支持按标签筛选。
     *
     * <p>返回值里除了分页卡片,还带上"当前收藏里出现过的全部标签" ——
     * 筛选 chips 需要它,调用方不必再发一次请求去猜有哪些可选。
     */
    BookmarkPageVO listBookmarks(String tag, LoginUser user);

    /**
     * 取实体并做读权限校验。
     *
     * <p>公开给其他 service 复用(收藏、图片挂载都要先确认这篇日记可见),
     * 免得每个入口各写一遍 userId 比对 —— 漏一处就是越权读。
     */
    Entry requireReadable(Long id, LoginUser viewer);

    /** 取实体并做写权限校验(作者本人或管理员)。 */
    Entry requireWritable(Long id, LoginUser editor);
}
