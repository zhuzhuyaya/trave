package com.travel.diary.service;

import com.travel.diary.security.LoginUser;
import com.travel.diary.vo.FootprintVO;
import com.travel.diary.vo.MapPointVO;
import com.travel.diary.vo.MemoryVO;
import com.travel.diary.vo.StatsVO;

import java.util.List;

/** 地图 / 统计 / 回忆 —— 都是只读的聚合查询,合在一个 service 里省掉三层空壳。 */
public interface InsightService {

    /** 全部有坐标的日记点。 */
    List<MapPointVO> mapPoints(LoginUser user);

    /** 按行程分组的足迹路线。 */
    List<FootprintVO> footprints(LoginUser user);

    StatsVO stats(LoginUser user);

    /** 历史上的今天。 */
    List<MemoryVO> memories(LoginUser user);
}
