package com.travel.diary.controller;

import com.travel.diary.common.R;
import com.travel.diary.security.RequireLogin;
import com.travel.diary.security.UserContext;
import com.travel.diary.service.InsightService;
import com.travel.diary.vo.FootprintVO;
import com.travel.diary.vo.MapPointVO;
import com.travel.diary.vo.MemoryVO;
import com.travel.diary.vo.StatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 地图 / 统计 / 回忆 —— 三个只读的聚合视图。
 *
 * <p>合成一个 Controller 是因为它们共用同一批 service 方法与权限模型;
 * 拆成三个只会多出三份近乎相同的注解与样板。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class InsightController {

    private final InsightService insightService;

    /** 地图上的全部足迹点。 */
    @GetMapping("/map/points")
    @RequireLogin
    public R<List<MapPointVO>> points() {
        return R.ok(insightService.mapPoints(UserContext.require()));
    }

    /** 按行程分组的足迹路线,供地图连线与侧栏分组。 */
    @GetMapping("/map/footprints")
    @RequireLogin
    public R<List<FootprintVO>> footprints() {
        return R.ok(insightService.footprints(UserContext.require()));
    }

    /** 统计页的全部数字。 */
    @GetMapping("/stats")
    @RequireLogin
    public R<StatsVO> stats() {
        return R.ok(insightService.stats(UserContext.require()));
    }

    /** 历史上的今天。 */
    @GetMapping("/memories/today")
    @RequireLogin
    public R<List<MemoryVO>> memories() {
        return R.ok(insightService.memories(UserContext.require()));
    }
}
