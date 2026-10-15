package com.travel.diary.service.impl;

import com.travel.diary.dto.EntryPhotoRow;
import com.travel.diary.dto.GeoPointRow;
import com.travel.diary.dto.MemoryRow;
import com.travel.diary.dto.MonthStatRow;
import com.travel.diary.dto.TotalsRow;
import com.travel.diary.dto.YearStatRow;
import com.travel.diary.mapper.EntryMapper;
import com.travel.diary.mapper.EntryPhotoMapper;
import com.travel.diary.security.LoginUser;
import com.travel.diary.service.InsightService;
import com.travel.diary.service.ViewAssembler;
import com.travel.diary.vo.FootprintVO;
import com.travel.diary.vo.MapPointVO;
import com.travel.diary.vo.MemoryVO;
import com.travel.diary.vo.StatsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 地图 / 统计 / 回忆。
 *
 * <p>三个功能都是"读一组聚合结果再拼成 VO",共用一个实现类,
 * 免得为三个纯查询各写一对接口 + 实现。所有数字都来自实时聚合 SQL,
 * 没有冗余计数表 —— 个人日记的量级下,维护一致性比省那次 COUNT 贵得多。
 */
@Service
@RequiredArgsConstructor
public class InsightServiceImpl implements InsightService {

    /** 月度统计只回看最近 12 个月。 */
    private static final int MONTHLY_WINDOW = 12;

    private final EntryMapper entryMapper;
    private final EntryPhotoMapper photoMapper;
    private final ViewAssembler assembler;

    @Override
    public List<MapPointVO> mapPoints(LoginUser user) {
        List<GeoPointRow> rows = entryMapper.findGeoPoints(user.id());
        return assembler.toMapPoints(rows, loadCovers(rows.stream().map(GeoPointRow::getId).toList()));
    }

    @Override
    public List<FootprintVO> footprints(LoginUser user) {
        List<GeoPointRow> rows = entryMapper.findGeoPoints(user.id());
        Map<Long, String> covers = loadCovers(rows.stream().map(GeoPointRow::getId).toList());

        // SQL 已按 entry_date 升序,于是 LinkedHashMap 的键顺序天然是
        // "该行程第一次出现的时间",正是侧栏列表想要的顺序。
        Map<Long, List<GeoPointRow>> grouped = new LinkedHashMap<>();
        for (GeoPointRow row : rows) {
            grouped.computeIfAbsent(row.getTripId(), key -> new ArrayList<>()).add(row);
        }

        List<FootprintVO> result = new ArrayList<>(grouped.size());
        for (Map.Entry<Long, List<GeoPointRow>> bucket : grouped.entrySet()) {
            List<GeoPointRow> points = bucket.getValue();
            if (points.isEmpty()) {
                continue;
            }

            FootprintVO footprint = new FootprintVO();
            footprint.setTripId(bucket.getKey());
            footprint.setTripTitle(points.get(0).getTripTitle());
            footprint.setPointCount(points.size());
            footprint.setStartDate(points.get(0).getEntryDate());
            footprint.setEndDate(points.get(points.size() - 1).getEntryDate());
            footprint.setPoints(assembler.toMapPoints(points, covers));
            result.add(footprint);
        }

        // 未归类(tripId == null)的一桶排到最后,不与真实行程混在一起
        result.sort((a, b) -> {
            if (a.getTripId() == null) {
                return 1;
            }
            if (b.getTripId() == null) {
                return -1;
            }
            return 0;
        });
        return result;
    }

    @Override
    public StatsVO stats(LoginUser user) {
        TotalsRow totals = entryMapper.totals(user.id());

        StatsVO vo = new StatsVO();
        vo.setEntryCount(totals == null ? 0 : totals.getEntryCount());
        vo.setDayCount(totals == null ? 0 : totals.getDayCount());
        vo.setPlaceCount(totals == null ? 0 : totals.getPlaceCount());
        vo.setPhotoCount(totals == null ? 0 : totals.getPhotoCount());
        vo.setTripCount(totals == null ? 0 : totals.getTripCount());
        vo.setFirstDate(totals == null ? null : totals.getFirstDate());

        List<YearStatRow> yearRows = entryMapper.statsByYear(user.id());
        vo.setYearCount(yearRows.size());
        vo.setYearly(yearRows.stream().map(row -> {
            StatsVO.YearStat stat = new StatsVO.YearStat();
            stat.setYear(row.getEntryYear() == null ? 0 : row.getEntryYear());
            stat.setEntryCount(row.getEntryCount());
            stat.setDayCount(row.getDayCount());
            stat.setPlaceCount(row.getPlaceCount());
            return stat;
        }).toList());

        vo.setMonthly(buildMonthly(user.id()));
        return vo;
    }

    @Override
    public List<MemoryVO> memories(LoginUser user) {
        LocalDate today = LocalDate.now();
        List<MemoryRow> rows = entryMapper.findMemories(
                user.id(), today.getYear(), today.getMonthValue(), today.getDayOfMonth());

        return rows.stream().map(row -> {
            MemoryVO vo = new MemoryVO();
            vo.setEntryId(row.getId());
            vo.setTitle(row.getTitle());
            vo.setExcerpt(row.getExcerpt());
            vo.setLocationName(row.getLocationName());
            vo.setEntryDate(row.getEntryDate());
            vo.setCoverUrl(row.getCoverUrl());

            int entryYear = row.getEntryYear() == null ? today.getYear() : row.getEntryYear();
            int yearsAgo = today.getYear() - entryYear;
            vo.setYearsAgo(yearsAgo);
            vo.setCaption(caption(yearsAgo, row.getLocationName()));
            return vo;
        }).toList();
    }

    /* ---------------------------------------------------------------------
     *  内部工具
     * ------------------------------------------------------------------- */

    /**
     * 批量取每篇日记的封面图。
     *
     * <p>空列表直接短路 —— 一个还没有任何坐标点的新用户打开地图页,
     * 不该因为没有 id 可传而生成一次 {@code IN ()} 的语法错误。
     */
    private Map<Long, String> loadCovers(List<Long> entryIds) {
        if (entryIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> covers = new LinkedHashMap<>();
        for (EntryPhotoRow row : photoMapper.findByEntryIds(entryIds)) {
            // findByEntryIds 已用 ROW_NUMBER() 标出每篇的第一张,这里只挑 isCover
            if (row.getIsCover() != null && row.getIsCover() == 1) {
                covers.putIfAbsent(row.getEntryId(), row.getUrl());
            }
        }
        return covers;
    }

    /**
     * 补出最近 12 个月的自然月序列。
     *
     * <p>SQL 只返回"有记录的月份",直接画图柱子会忽宽忽窄;
     * 这里补齐成等距的 12 格,没记录的月份是 0。
     */
    private List<StatsVO.MonthStat> buildMonthly(Long userId) {
        YearMonth current = YearMonth.now();
        LocalDate since = current.minusMonths(MONTHLY_WINDOW - 1).atDay(1);

        Map<String, Long> counts = new LinkedHashMap<>();
        for (MonthStatRow row : entryMapper.statsByMonth(userId, since)) {
            counts.put(key(row.getEntryYear(), row.getEntryMonth()), row.getEntryCount());
        }

        List<StatsVO.MonthStat> result = new ArrayList<>(MONTHLY_WINDOW);
        for (int i = MONTHLY_WINDOW - 1; i >= 0; i--) {
            YearMonth month = current.minusMonths(i);
            StatsVO.MonthStat stat = new StatsVO.MonthStat();
            stat.setYear(month.getYear());
            stat.setMonth(month.getMonthValue());
            stat.setEntryCount(counts.getOrDefault(key(month.getYear(), month.getMonthValue()), 0L));
            result.add(stat);
        }
        return result;
    }

    private String key(Integer year, Integer month) {
        return (year == null ? 0 : year) + "-" + (month == null ? 0 : month);
    }

    private String caption(int yearsAgo, String locationName) {
        String when = switch (yearsAgo) {
            case 0 -> "就在今天";
            case 1 -> "一年前的今天";
            case 2 -> "两年前的今天";
            case 3 -> "三年前的今天";
            default -> yearsAgo + " 年前的今天";
        };
        if (locationName == null || locationName.isBlank()) {
            return when;
        }
        return when + ",你在" + locationName;
    }
}
