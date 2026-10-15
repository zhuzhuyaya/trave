package com.travel.diary.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.travel.diary.common.BizException;
import com.travel.diary.common.PageResult;
import com.travel.diary.dto.TripAggRow;
import com.travel.diary.dto.TripRequest;
import com.travel.diary.entity.Entry;
import com.travel.diary.entity.EntryPhoto;
import com.travel.diary.entity.Trip;
import com.travel.diary.mapper.EntryMapper;
import com.travel.diary.mapper.EntryPhotoMapper;
import com.travel.diary.mapper.TripMapper;
import com.travel.diary.security.LoginUser;
import com.travel.diary.service.TripService;
import com.travel.diary.service.ViewAssembler;
import com.travel.diary.vo.TripDetailVO;
import com.travel.diary.vo.TripVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TripServiceImpl implements TripService {

    /** 卡片副标题最多显示几个地点,再多就加省略号。 */
    private static final int LOCATION_SUMMARY_LIMIT = 3;

    private final TripMapper tripMapper;
    private final EntryMapper entryMapper;
    private final EntryPhotoMapper photoMapper;
    private final ViewAssembler assembler;

    @Override
    public PageResult<TripVO> list(LoginUser owner, Integer page, Integer size, Boolean includeArchived) {
        long currentPage = PageResult.safePage(page);
        long pageSize = PageResult.safeSize(size);

        LambdaQueryWrapper<Trip> wrapper = new LambdaQueryWrapper<Trip>()
                .eq(Trip::getUserId, owner.id())
                // 归档行程默认不出现在主列表,但"我的"页里可以勾选查看
                .eq(!Boolean.TRUE.equals(includeArchived), Trip::getIsArchived, 0)
                .orderByDesc(Trip::getCreatedAt)
                .orderByDesc(Trip::getId);

        IPage<Trip> result = tripMapper.selectPage(new Page<>(currentPage, pageSize), wrapper);
        if (result.getRecords().isEmpty()) {
            return PageResult.of(List.of(), result.getTotal(), currentPage, pageSize);
        }

        // 一条聚合 SQL 覆盖整页行程,而不是每张卡片各查三次
        Map<Long, TripAggRow> aggregates = tripMapper.aggregateByUser(owner.id()).stream()
                .collect(Collectors.toMap(TripAggRow::getTripId, row -> row, (a, b) -> a));

        // 自定义封面同理:攒齐这一页用到的图片 id,一次查完
        Map<Long, String> covers = assembler.loadPhotoUrls(result.getRecords().stream()
                .map(Trip::getCoverPhotoId)
                .toList());

        List<TripVO> list = result.getRecords().stream()
                .map(trip -> toVO(trip, aggregates.get(trip.getId()), owner, covers))
                .toList();

        return PageResult.of(list, result.getTotal(), currentPage, pageSize);
    }

    @Override
    public TripDetailVO detail(Long id, LoginUser viewer) {
        Trip trip = requireReadable(id, viewer);

        List<Entry> entries = entryMapper.selectList(new LambdaQueryWrapper<Entry>()
                .eq(Entry::getTripId, id)
                .orderByDesc(Entry::getEntryDate)
                .orderByDesc(Entry::getId));

        TripDetailVO detail = new TripDetailVO();
        detail.setTrip(toVO(trip, tripMapper.aggregateOne(id), viewer, coversOf(trip)));
        detail.setEntries(assembler.toSummaries(entries, viewer));
        return detail;
    }

    @Override
    @Transactional
    public TripVO create(TripRequest request, LoginUser owner) {
        Trip trip = new Trip();
        trip.setUserId(owner.id());
        trip.setTitle(titleOr(request.getTitle(), "未命名行程"));
        trip.setDescription(trimToNull(request.getDescription()));
        trip.setVisibility(normalizeVisibility(request.getVisibility()));
        trip.setIsArchived(Boolean.TRUE.equals(request.getArchived()) ? 1 : 0);
        trip.setCoverPhotoId(ownedCoverId(request.getCoverPhotoId(), owner));
        tripMapper.insert(trip);

        return toVO(trip, tripMapper.aggregateOne(trip.getId()), owner, coversOf(trip));
    }

    @Override
    @Transactional
    public TripVO update(Long id, TripRequest request, LoginUser editor) {
        Trip trip = requireOwned(id, editor);

        // 逐字段累加,只动客户端真的传了的字段。写成 updateById(整个实体)
        // 会把没传的字段一起写成 null —— "只改标题"顺手清空了简介。
        LambdaUpdateWrapper<Trip> update = new LambdaUpdateWrapper<Trip>().eq(Trip::getId, id);
        boolean dirty = false;

        if (request.getTitle() != null) {
            update.set(Trip::getTitle, titleOr(request.getTitle(), trip.getTitle()));
            dirty = true;
        }
        if (request.getDescription() != null) {
            update.set(Trip::getDescription, trimToNull(request.getDescription()));
            dirty = true;
        }
        if (request.getVisibility() != null) {
            update.set(Trip::getVisibility, normalizeVisibility(request.getVisibility()));
            dirty = true;
        }
        if (request.getArchived() != null) {
            update.set(Trip::getIsArchived, request.getArchived() ? 1 : 0);
            dirty = true;
        }
        if (request.getCoverPhotoId() != null) {
            update.set(Trip::getCoverPhotoId, ownedCoverId(request.getCoverPhotoId(), editor));
            dirty = true;
        }

        // 一个字段都没传就不发 UPDATE:空更新会把 updated_at 顶到当前时间,
        // 按更新时间排序的列表会因此莫名重排。
        if (dirty) {
            tripMapper.update(null, update);
        }

        Trip saved = tripMapper.selectById(id);
        return toVO(saved, tripMapper.aggregateOne(id), editor, coversOf(saved));
    }

    @Override
    @Transactional
    public void delete(Long id, LoginUser operator) {
        Trip trip = requireOwned(id, operator);

        // 日记一并逻辑删除。不做物理级联清理:逻辑删是可恢复的,
        // 误删一段行程不至于永久丢掉所有内容。
        entryMapper.softDeleteByTrip(trip.getId(), operator.id());
        tripMapper.deleteById(trip.getId());
    }

    @Override
    public Trip requireReadable(Long id, LoginUser viewer) {
        Trip trip = tripMapper.selectById(id);
        if (trip == null) {
            throw BizException.notFound("行程");
        }
        boolean ownerOrAdmin = viewer != null
                && (Objects.equals(trip.getUserId(), viewer.id()) || viewer.isAdmin());
        if (!ownerOrAdmin && !trip.isPubliclyVisible()) {
            // 私密行程对非作者一律按"不存在"处理 ——
            // 抛 403 等于告诉对方"这里确实有个行程,只是你看不了"。
            throw BizException.notFound("行程");
        }
        return trip;
    }

    @Override
    public Trip requireOwned(Long id, LoginUser editor) {
        Trip trip = tripMapper.selectById(id);
        if (trip == null) {
            throw BizException.notFound("行程");
        }
        if (editor == null || (!Objects.equals(trip.getUserId(), editor.id()) && !editor.isAdmin())) {
            throw BizException.forbidden("只能操作自己的行程");
        }
        return trip;
    }

    /* ---------------------------------------------------------------------
     *  内部工具
     * ------------------------------------------------------------------- */

    private TripVO toVO(Trip trip, TripAggRow aggregate, LoginUser viewer, Map<Long, String> customCovers) {
        TripVO vo = new TripVO();
        vo.setId(trip.getId());
        vo.setTitle(trip.getTitle());
        vo.setDescription(trip.getDescription());
        vo.setVisibility(trip.getVisibility());
        vo.setArchived(ViewAssembler.isTrue(trip.getIsArchived()));
        vo.setCreatedAt(trip.getCreatedAt());
        vo.setCanEdit(canEdit(trip, viewer));
        vo.setCoverPhotoId(trip.getCoverPhotoId());
        vo.setCoverUrl(coverUrl(trip, aggregate, customCovers));

        if (aggregate != null) {
            vo.setEntryCount((int) aggregate.getEntryCount());
            vo.setStartDate(aggregate.getStartDate());
            vo.setEndDate(aggregate.getEndDate());
            vo.setLocationSummary(summarizeLocations(aggregate.getLocations()));
        } else {
            vo.setEntryCount(0);
        }
        return vo;
    }

    /**
     * 解析这个行程实际该显示哪张图:自定义封面优先,其次行程内最新一篇日记的首图。
     *
     * <p>两者都没有就返回 null,由前端渲染渐变占位块。
     */
    private String coverUrl(Trip trip, TripAggRow aggregate, Map<Long, String> customCovers) {
        if (trip.getCoverPhotoId() != null) {
            String url = customCovers.get(trip.getCoverPhotoId());
            if (url != null) {
                return url;
            }
            // 那张图后来被删了。悄悄退回兜底 —— 把 id 原样丢给前端只会换一个 404 的破图。
        }
        return aggregate == null ? null : aggregate.getFallbackCover();
    }

    /**
     * 把请求里的 coverPhotoId 翻译成"该写进库的值"。
     *
     * <p>{@link TripRequest#COVER_NONE} 表示清掉自定义封面(写 null,退回兜底);
     * 其余值必须确实是调用者自己上传的图。
     */
    private Long ownedCoverId(Long requested, LoginUser owner) {
        if (requested == TripRequest.COVER_NONE) {
            return null;
        }
        EntryPhoto photo = photoMapper.selectById(requested);
        // 查不到通常意味着图已被删除(EntryPhoto.deleted 上是 @TableLogic,
        // selectById 会把它过滤掉)。这种 id 存进去只会得到一张永远加载不出来的封面。
        if (photo == null || (!Objects.equals(photo.getUserId(), owner.id()) && !owner.isAdmin())) {
            // 和 PhotoService.assertOwnedBy 一个口径:那边按 url 查,这里直接按 id 查。
            // 拒绝未知 id 而不仅是"别人的 id" —— 后者会让拼错的 id 悄悄存进去。
            throw BizException.forbidden("封面图不属于当前账号");
        }
        return requested;
    }

    /** 一段行程的封面表。只是把 loadPhotoUrls 的单元素用法收在一处,顺手挡掉 List.of(null)。 */
    private Map<Long, String> coversOf(Trip trip) {
        return trip.getCoverPhotoId() == null
                ? Map.of()
                : assembler.loadPhotoUrls(List.of(trip.getCoverPhotoId()));
    }

    /**
     * 把 GROUP_CONCAT 出来的地点串压成卡片副标题。
     *
     * <p>去重是必要的:一段在大理的行程,12 篇日记的地点全是"大理",
     * 副标题不该写成"大理 · 大理 · 大理"。
     */
    private String summarizeLocations(String joined) {
        if (joined == null || joined.isBlank()) {
            return null;
        }
        List<String> names = Arrays.stream(joined.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .distinct()
                .toList();
        if (names.isEmpty()) {
            return null;
        }
        String head = String.join(" · ", names.subList(0, Math.min(names.size(), LOCATION_SUMMARY_LIMIT)));
        return names.size() > LOCATION_SUMMARY_LIMIT ? head + " …" : head;
    }

    private String titleOr(String title, String fallback) {
        String trimmed = trimToNull(title);
        return trimmed == null ? fallback : trimmed;
    }

    private String normalizeVisibility(String visibility) {
        return Trip.VISIBILITY_PUBLIC.equalsIgnoreCase(visibility)
                ? Trip.VISIBILITY_PUBLIC
                : Trip.VISIBILITY_PRIVATE;
    }

    /** 作者本人或管理员——与 requireOwned 同一个判断,只是这里要的是"能不能",不是"抛不抛"。 */
    private boolean canEdit(Trip trip, LoginUser viewer) {
        if (viewer == null) {
            return false;
        }
        return Objects.equals(trip.getUserId(), viewer.id()) || viewer.isAdmin();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
