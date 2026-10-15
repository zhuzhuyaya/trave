package com.travel.diary.service;

import com.travel.diary.dto.EntryPhotoRow;
import com.travel.diary.dto.EntryTagRow;
import com.travel.diary.dto.GeoPointRow;
import com.travel.diary.entity.Entry;
import com.travel.diary.entity.EntryPhoto;
import com.travel.diary.entity.Tag;
import com.travel.diary.entity.Trip;
import com.travel.diary.entity.User;
import com.travel.diary.mapper.EntryPhotoMapper;
import com.travel.diary.mapper.TagMapper;
import com.travel.diary.mapper.UserMapper;
import com.travel.diary.security.LoginUser;
import com.travel.diary.vo.EntryDetailVO;
import com.travel.diary.vo.EntrySummaryVO;
import com.travel.diary.vo.MapPointVO;
import com.travel.diary.vo.PhotoVO;
import com.travel.diary.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 实体 → VO 的装配。
 *
 * <p>刻意做成一个具体类而不是接口:它是 service 层的实现细节,
 * 没有第二个实现,也没有被外部依赖的理由。
 *
 * <p>核心职责是**消灭 N+1**:一页 10 篇日记,装配前把所有涉及的
 * 用户 / 图片 / 标签 / 行程一次批量查回来装进 Map,再按 id 取。
 * 列表页的查询次数因此是常数,与页大小无关。
 */
@Component
@RequiredArgsConstructor
public class ViewAssembler {

    private final UserMapper userMapper;
    private final EntryPhotoMapper photoMapper;
    private final TagMapper tagMapper;
    private final com.travel.diary.mapper.TripMapper tripMapper;

    /* ---------------------------------------------------------------------
     *  用户
     * ------------------------------------------------------------------- */

    public UserVO toUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setBio(user.getBio());
        vo.setRole(user.getRole());
        vo.setCreatedAt(user.getCreatedAt());
        return vo;
    }

    /** 批量取用户,返回 id → VO。 */
    public Map<Long, UserVO> loadUsers(Collection<Long> userIds) {
        Set<Long> ids = distinctNonNull(userIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(User::getId, this::toUserVO));
    }

    /* ---------------------------------------------------------------------
     *  日记列表
     * ------------------------------------------------------------------- */

    /**
     * 批量装配日记摘要。
     *
     * <p>四张表各查一次(图片、标签、行程、封面),然后纯内存拼接 ——
     * 而不是每篇日记各查四次。
     */
    public List<EntrySummaryVO> toSummaries(List<Entry> entries, LoginUser viewer) {
        if (entries == null || entries.isEmpty()) {
            return List.of();
        }

        List<Long> entryIds = entries.stream().map(Entry::getId).toList();

        // 图片:一次拿到全部,同时标出每篇的封面
        List<EntryPhotoRow> photoRows = photoMapper.findByEntryIds(entryIds);
        Map<Long, List<EntryPhotoRow>> photosByEntry = photoRows.stream()
                .collect(Collectors.groupingBy(EntryPhotoRow::getEntryId));
        Map<Long, String> coverByEntry = photoRows.stream()
                .filter(row -> isTrue(row.getIsCover()))
                .collect(Collectors.toMap(EntryPhotoRow::getEntryId, EntryPhotoRow::getUrl, (a, b) -> a));

        // 标签:一次查完再按 entry_id 分组
        Map<Long, List<String>> tagsByEntry = tagMapper.findByEntryIds(entryIds).stream()
                .collect(Collectors.groupingBy(EntryTagRow::getEntryId,
                        Collectors.mapping(EntryTagRow::getName, Collectors.toList())));

        // 行程标题:只查这一页真正用到的行程 id
        Map<Long, String> tripTitles = loadTripTitles(entries.stream()
                .map(Entry::getTripId)
                .filter(Objects::nonNull)
                .toList());

        List<EntrySummaryVO> result = new ArrayList<>(entries.size());
        for (Entry entry : entries) {
            EntrySummaryVO vo = new EntrySummaryVO();
            vo.setId(entry.getId());
            vo.setTitle(entry.getTitle());
            vo.setExcerpt(entry.getExcerpt());
            vo.setEntryDate(entry.getEntryDate());
            vo.setLocationName(entry.getLocationName());
            vo.setLatitude(toDouble(entry.getLatitude()));
            vo.setLongitude(toDouble(entry.getLongitude()));
            vo.setWeather(entry.getWeather());
            vo.setMood(entry.getMood());

            List<EntryPhotoRow> photos = photosByEntry.getOrDefault(entry.getId(), List.of());
            vo.setPhotoCount(photos.size());
            vo.setCoverUrl(coverByEntry.get(entry.getId()));

            vo.setTags(tagsByEntry.getOrDefault(entry.getId(), List.of()));

            vo.setTripId(entry.getTripId());
            vo.setTripTitle(entry.getTripId() == null ? null : tripTitles.get(entry.getTripId()));

            vo.setCanEdit(canEdit(entry, viewer));
            result.add(vo);
        }
        return result;
    }

    /* ---------------------------------------------------------------------
     *  日记详情
     * ------------------------------------------------------------------- */

    public EntryDetailVO toDetail(Entry entry, LoginUser viewer, boolean bookmarked) {
        EntryDetailVO vo = new EntryDetailVO();
        vo.setId(entry.getId());
        vo.setTitle(entry.getTitle());
        vo.setContent(entry.getContent());
        vo.setEntryDate(entry.getEntryDate());
        vo.setLocationName(entry.getLocationName());
        vo.setLatitude(toDouble(entry.getLatitude()));
        vo.setLongitude(toDouble(entry.getLongitude()));
        vo.setWeather(entry.getWeather());
        vo.setMood(entry.getMood());
        vo.setViewCount(entry.getViewCount());
        vo.setCreatedAt(entry.getCreatedAt());
        vo.setUpdatedAt(entry.getUpdatedAt());

        List<EntryPhoto> photos = photoMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EntryPhoto>()
                        .eq(EntryPhoto::getEntryId, entry.getId())
                        .orderByAsc(EntryPhoto::getSortOrder)
                        .orderByAsc(EntryPhoto::getId));
        vo.setPhotos(photos.stream().map(this::toPhotoVO).toList());

        vo.setTags(tagMapper.findByEntryIds(List.of(entry.getId())).stream()
                .map(EntryTagRow::getName)
                .toList());

        vo.setTripId(entry.getTripId());
        if (entry.getTripId() != null) {
            vo.setTripTitle(loadTripTitles(List.of(entry.getTripId())).get(entry.getTripId()));
        }

        vo.setCanEdit(canEdit(entry, viewer));
        vo.setBookmarked(bookmarked);
        return vo;
    }

    public PhotoVO toPhotoVO(EntryPhoto photo) {
        PhotoVO vo = new PhotoVO();
        vo.setId(photo.getId());
        vo.setUrl(photo.getUrl());
        vo.setThumbUrl(photo.getThumbUrl() == null ? photo.getUrl() : photo.getThumbUrl());
        vo.setWidth(photo.getWidth());
        vo.setHeight(photo.getHeight());
        vo.setSortOrder(photo.getSortOrder());
        return vo;
    }

    /**
     * 按 id 批量取图片地址,返回 id → url。
     *
     * <p>行程封面用:一页行程各自带一个 coverPhotoId,攒起来一次查完,
     * 而不是每张卡片查一次。已被(逻辑)删除的 id 不会出现在结果里 ——
     * 调用方拿到 null 就该走兜底,而不是拼一个指向不存在文件的地址去。
     */
    public Map<Long, String> loadPhotoUrls(Collection<Long> photoIds) {
        Set<Long> ids = distinctNonNull(photoIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        return photoMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(EntryPhoto::getId, EntryPhoto::getUrl, (a, b) -> a));
    }

    /* ---------------------------------------------------------------------
     *  地图
     * ------------------------------------------------------------------- */

    public List<MapPointVO> toMapPoints(List<GeoPointRow> rows, Map<Long, String> covers) {
        return rows.stream().map(row -> {
            MapPointVO vo = new MapPointVO();
            vo.setId(row.getId());
            vo.setTitle(row.getTitle());
            vo.setLatitude(row.getLatitude() == null ? 0 : row.getLatitude());
            vo.setLongitude(row.getLongitude() == null ? 0 : row.getLongitude());
            vo.setEntryDate(row.getEntryDate());
            vo.setLocationName(row.getLocationName());
            vo.setTripId(row.getTripId());
            vo.setTripTitle(row.getTripTitle());
            vo.setCoverUrl(covers.get(row.getId()));
            return vo;
        }).toList();
    }

    /* ---------------------------------------------------------------------
     *  内部工具
     * ------------------------------------------------------------------- */

    private Map<Long, String> loadTripTitles(List<Long> tripIds) {
        Set<Long> ids = distinctNonNull(tripIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> titles = new HashMap<>();
        for (Trip trip : tripMapper.selectBatchIds(ids)) {
            titles.put(trip.getId(), trip.getTitle());
        }
        return titles;
    }

    /** 作者本人或管理员可编辑。 */
    private boolean canEdit(Entry entry, LoginUser viewer) {
        if (viewer == null) {
            return false;
        }
        return Objects.equals(entry.getUserId(), viewer.id()) || viewer.isAdmin();
    }

    /** 去掉 null 并去重,避免把 null 塞进 selectBatchIds 的 IN 子句。 */
    public static <T> Set<T> distinctNonNull(Collection<T> values) {
        if (values == null) {
            return Set.of();
        }
        return values.stream().filter(Objects::nonNull).collect(Collectors.toSet());
    }

    public static boolean isTrue(Integer flag) {
        return flag != null && flag != 0;
    }

    /** DECIMAL(10,7) 映射到 BigDecimal,前端要的是普通数字,这里降一档。 */
    public static Double toDouble(java.math.BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }
}
