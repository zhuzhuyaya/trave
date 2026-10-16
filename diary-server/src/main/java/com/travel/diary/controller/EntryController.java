package com.travel.diary.controller;

import com.travel.diary.common.PageResult;
import com.travel.diary.common.R;
import com.travel.diary.dto.EntryQuery;
import com.travel.diary.dto.EntryRequest;
import com.travel.diary.security.RequireLogin;
import com.travel.diary.security.UserContext;
import com.travel.diary.service.EntryService;
import com.travel.diary.vo.EntryDetailVO;
import com.travel.diary.vo.EntrySummaryVO;
import com.travel.diary.vo.ToggleVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 日记。
 *
 * <p>分页与筛选参数全部走 {@link EntryQuery} 对象绑定,而不是一长串
 * {@code @RequestParam} —— 加一个筛选条件时只改 DTO 一处,
 * 不用同步修改 Controller 签名和前端调用。
 */
@RestController
@RequestMapping("/api/entries")
@RequiredArgsConstructor
public class EntryController {

    private final EntryService entryService;

    @GetMapping
    @RequireLogin
    public R<PageResult<EntrySummaryVO>> list(EntryQuery query) {
        return R.ok(entryService.list(query, UserContext.require()));
    }

    @GetMapping("/{id}")
    @RequireLogin
    public R<EntryDetailVO> detail(@PathVariable Long id) {
        return R.ok(entryService.detail(id, UserContext.require()));
    }

    /**
     * 阅读数 +1。
     *
     * <p>单独一个接口而不是"读详情时顺便加" —— GET 应当是幂等的,
     * 让它在缓存、预取、后退重放时都产生副作用会很难排查。
     */
    @PostMapping("/{id}/view")
    @RequireLogin
    public R<Void> recordView(@PathVariable Long id) {
        entryService.recordView(id, UserContext.require());
        return R.ok();
    }

    @PostMapping
    @RequireLogin
    public R<EntryDetailVO> create(@Valid @RequestBody EntryRequest request) {
        return R.ok(entryService.create(request, UserContext.require()));
    }

    @PutMapping("/{id}")
    @RequireLogin
    public R<EntryDetailVO> update(@PathVariable Long id, @Valid @RequestBody EntryRequest request) {
        return R.ok(entryService.update(id, request, UserContext.require()));
    }

    @DeleteMapping("/{id}")
    @RequireLogin
    public R<Void> delete(@PathVariable Long id) {
        entryService.delete(id, UserContext.require());
        return R.ok();
    }

    @PostMapping("/{id}/bookmark")
    @RequireLogin
    public R<ToggleVO> toggleBookmark(@PathVariable Long id) {
        return R.ok(entryService.toggleBookmark(id, UserContext.require()));
    }
}
