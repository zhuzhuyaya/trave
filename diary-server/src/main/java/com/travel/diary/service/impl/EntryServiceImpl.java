package com.travel.diary.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.travel.diary.common.BizException;
import com.travel.diary.common.Markdown;
import com.travel.diary.common.PageResult;
import com.travel.diary.dto.EntryQuery;
import com.travel.diary.dto.EntryRequest;
import com.travel.diary.dto.EntryTagRow;
import com.travel.diary.entity.Bookmark;
import com.travel.diary.entity.Entry;
import com.travel.diary.entity.EntryPhoto;
import com.travel.diary.entity.EntryTag;
import com.travel.diary.entity.Tag;
import com.travel.diary.mapper.BookmarkMapper;
import com.travel.diary.mapper.EntryMapper;
import com.travel.diary.mapper.EntryPhotoMapper;
import com.travel.diary.mapper.EntryTagMapper;
import com.travel.diary.mapper.TagMapper;
import com.travel.diary.security.LoginUser;
import com.travel.diary.service.EntryService;
import com.travel.diary.service.PhotoService;
import com.travel.diary.service.TripService;
import com.travel.diary.service.ViewAssembler;
import com.travel.diary.vo.BookmarkPageVO;
import com.travel.diary.vo.BookmarkTagVO;
import com.travel.diary.vo.EntryDetailVO;
import com.travel.diary.vo.EntrySummaryVO;
import com.travel.diary.vo.ToggleVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EntryServiceImpl implements EntryService {

    /** 关键词最短长度。一两个字符的搜索会命中几乎所有记录,纯属浪费查询。 */
    private static final int MIN_KEYWORD_LENGTH = 2;

    private final EntryMapper entryMapper;
    private final EntryPhotoMapper photoMapper;
    private final EntryTagMapper entryTagMapper;
    private final TagMapper tagMapper;
    private final BookmarkMapper bookmarkMapper;
    private final TripService tripService;
    private final PhotoService photoService;
    private final ViewAssembler assembler;

    @Override
    public PageResult<EntrySummaryVO> list(EntryQuery query, LoginUser viewer) {
        long page = PageResult.safePage(query.getPage());
        long size = PageResult.safeSize(query.getSize());

        LambdaQueryWrapper<Entry> wrapper = new LambdaQueryWrapper<>();

        // 只看自己的日记。将来做"公开主页"时,这里换成
        // "user_id = 目标作者" + 可见性过滤,其余条件都不用动。
        wrapper.eq(Entry::getUserId, viewer.id());

        if (query.getTripId() != null) {
            wrapper.eq(Entry::getTripId, query.getTripId());
        }

        if (query.getYear() != null) {
            // 用 YEAR()/MONTH() 函数而不是拼 entry_date 的范围:
            // 后者要为闰年、月末天数各写一套边界,函数式写法一眼能看懂。
            wrapper.apply("YEAR(entry_date) = {0}", query.getYear());
            if (query.getMonth() != null && query.getMonth() >= 1 && query.getMonth() <= 12) {
                wrapper.apply("MONTH(entry_date) = {0}", query.getMonth());
            }
        }

        String keyword = normalizeKeyword(query.getKeyword());
        if (keyword != null) {
            // 条件整体括起来,否则 OR 会把它前后的 AND 条件拆散,
            // 变成"我的日记 或 任意一篇含关键词的日记" —— 越权读的经典写法。
            wrapper.and(w -> w.like(Entry::getTitle, keyword)
                    .or().like(Entry::getExcerpt, keyword)
                    .or().like(Entry::getLocationName, keyword));
        }

        if (query.getTag() != null && !query.getTag().isBlank()) {
            // 标签名由服务端拼进 SQL 之前先转义单引号,
            // 避免把用户输入直接当 SQL 片段用。
            String tag = query.getTag().trim().replace("'", "''");
            wrapper.inSql(Entry::getId,
                    "SELECT et.entry_id FROM entry_tags et JOIN tags t ON t.id = et.tag_id WHERE t.name = '" + tag + "'");
        }

        // 时间线默认新→旧;同一日期内再按 id 兜底保证分页稳定,
        // 否则同一天的多篇日记在翻页时可能重复出现或漏掉。
        boolean oldestFirst = "oldest".equalsIgnoreCase(query.getSort());
        if (oldestFirst) {
            wrapper.orderByAsc(Entry::getEntryDate).orderByAsc(Entry::getId);
        } else {
            wrapper.orderByDesc(Entry::getEntryDate).orderByDesc(Entry::getId);
        }

        var result = entryMapper.selectPage(new Page<>(page, size), wrapper);
        List<EntrySummaryVO> list = assembler.toSummaries(result.getRecords(), viewer);
        return PageResult.of(list, result.getTotal(), page, size);
    }

    @Override
    public EntryDetailVO detail(Long id, LoginUser viewer) {
        Entry entry = requireReadable(id, viewer);
        boolean bookmarked = viewer != null && bookmarkMapper.exists(new LambdaQueryWrapper<Bookmark>()
                .eq(Bookmark::getEntryId, id)
                .eq(Bookmark::getUserId, viewer.id()));
        return assembler.toDetail(entry, viewer, bookmarked);
    }

    @Override
    @Transactional
    public EntryDetailVO create(EntryRequest request, LoginUser author) {
        Entry entry = new Entry();
        entry.setUserId(author.id());
        entry.setTitle(request.getTitle().trim());
        entry.setContent(request.getContent());
        entry.setExcerpt(Markdown.toExcerpt(request.getContent()));
        entry.setEntryDate(parseDate(request.getEntryDate()));
        entry.setTripId(validatedTripId(request.getTripId(), author));
        applyLocation(entry, request);
        entry.setWeather(trimToNull(request.getWeather()));
        entry.setMood(trimToNull(request.getMood()));
        entry.setViewCount(0);
        entryMapper.insert(entry);

        replaceTags(entry.getId(), request.getTags());
        syncPhotos(entry.getId(), author.id(), request.getPhotoUrls());

        return assembler.toDetail(entryMapper.selectById(entry.getId()), author, false);
    }

    @Override
    @Transactional
    public EntryDetailVO update(Long id, EntryRequest request, LoginUser editor) {
        Entry existing = requireWritable(id, editor);

        LambdaUpdateWrapper<Entry> update = new LambdaUpdateWrapper<Entry>().eq(Entry::getId, id);
        update.set(Entry::getTitle, request.getTitle().trim());
        update.set(Entry::getContent, request.getContent());
        // 摘要跟着正文走 —— 只改正文却不重算摘要,列表页会一直显示改之前的开头。
        update.set(Entry::getExcerpt, Markdown.toExcerpt(request.getContent()));
        update.set(Entry::getEntryDate, parseDate(request.getEntryDate()));
        update.set(Entry::getTripId, validatedTripId(request.getTripId(), editor));
        update.set(Entry::getWeather, trimToNull(request.getWeather()));
        update.set(Entry::getMood, trimToNull(request.getMood()));
        if (hasBothCoordinates(request)) {
            update.set(Entry::getLatitude, BigDecimal.valueOf(request.getLatitude()));
            update.set(Entry::getLongitude, BigDecimal.valueOf(request.getLongitude()));
        } else {
            // 编辑时清掉坐标也要能生效,所以是显式写 null 而不是"跳过"
            update.set(Entry::getLatitude, null);
            update.set(Entry::getLongitude, null);
        }
        update.set(Entry::getLocationName, trimToNull(request.getLocationName()));
        entryMapper.update(null, update);

        // 标签按使用数增减,而不是"清空后重建全文" —— 后者会让所有标签的
        // use_count 归零再重算,并发下数字会跳来跳去。
        replaceTags(id, request.getTags());
        syncPhotos(id, editor.id(), request.getPhotoUrls());

        boolean bookmarked = bookmarkMapper.exists(new LambdaQueryWrapper<Bookmark>()
                .eq(Bookmark::getEntryId, id)
                .eq(Bookmark::getUserId, editor.id()));
        return assembler.toDetail(entryMapper.selectById(id), editor, bookmarked);
    }

    @Override
    @Transactional
    public void delete(Long id, LoginUser operator) {
        Entry entry = requireWritable(id, operator);

        // 先回退标签使用数,再删关联;顺序反了就查不到关联关系了
        List<EntryTag> links = entryTagMapper.selectList(new LambdaQueryWrapper<EntryTag>()
                .eq(EntryTag::getEntryId, id));
        for (EntryTag link : links) {
            tagMapper.addUseCount(link.getTagId(), -1);
        }
        entryTagMapper.deleteByEntryId(id);

        // 附件与收藏一并逻辑删除,留下的图片文件不主动清理 ——
        // 本地磁盘场景下,清文件的收益抵不上"误删后无法恢复"的风险。
        photoMapper.delete(new LambdaQueryWrapper<EntryPhoto>().eq(EntryPhoto::getEntryId, id));
        bookmarkMapper.delete(new LambdaQueryWrapper<Bookmark>().eq(Bookmark::getEntryId, id));

        entryMapper.deleteById(entry.getId());
    }

    @Override
    public void recordView(Long id, LoginUser viewer) {
        // 先过读权限,再动计数。少了这一步,任何人拿到 id 就能给别人的私密日记
        // 刷阅读数 —— 内容虽然读不到,但那一行确实被改了,这就是对别人数据的写。
        requireReadable(id, viewer);
        entryMapper.incrementViewCount(id);
    }

    @Override
    @Transactional
    public ToggleVO toggleBookmark(Long id, LoginUser user) {
        Entry entry = requireReadable(id, user);

        LambdaQueryWrapper<Bookmark> existing = new LambdaQueryWrapper<Bookmark>()
                .eq(Bookmark::getEntryId, id)
                .eq(Bookmark::getUserId, user.id());
        Bookmark bookmark = bookmarkMapper.selectOne(existing);

        boolean active;
        if (bookmark != null) {
            bookmarkMapper.deleteById(bookmark.getId());
            active = false;
        } else {
            Bookmark row = new Bookmark();
            row.setEntryId(entry.getId());
            row.setUserId(user.id());
            try {
                bookmarkMapper.insert(row);
            } catch (DuplicateKeyException e) {
                // 连点两下收藏:两次请求都读到"未收藏",其中一个 insert 撞唯一索引。
                // 此时状态其实已经是"已收藏",静默算成功比抛冲突给用户看更合理。
            }
            active = true;
        }

        long count = bookmarkMapper.selectCount(new LambdaQueryWrapper<Bookmark>()
                .eq(Bookmark::getEntryId, id));
        return ToggleVO.of(active, count);
    }

    @Override
    public BookmarkPageVO listBookmarks(String tag, LoginUser user) {
        // 全量候选一次取回(单用户收藏规模是几十条),标签筛选与打标计数都在内存里做,
        // 换筛选条件不会多打一次 SQL
        List<Long> entryIds = bookmarkMapper.findReadableEntryIds(user.id());

        List<Long> filteredIds = entryIds;
        boolean filtered = tag != null && !tag.isBlank();
        if (filtered && entryIds.size() > 1) {
            // 标签 → entry 映射只查一次;候选 id 多而命中标签的少,内存交集最省
            Map<Long, List<String>> tagsByEntry = tagNamesByEntry(entryIds);
            filteredIds = entryIds.stream()
                    .filter(id -> tagsByEntry.getOrDefault(id, List.of()).contains(tag.trim()))
                    .toList();
        }

        // 分页在 id 列表上做,再批量装配 —— 与 PageResult 的语义保持一致
        long page = PageResult.safePage(null);
        long size = PageResult.MAX_SIZE;
        long fromIndex = Math.min((page - 1) * size, filteredIds.size());
        long toIndex = Math.min(fromIndex + size, filteredIds.size());
        List<Long> pageIds = filteredIds.subList((int) fromIndex, (int) toIndex);

        List<Entry> entries = pageIds.isEmpty()
                ? List.of()
                : entryMapper.selectBatchIds(pageIds);
        // selectBatchIds 不保序,按收藏的时间(new→old)重排回 id 顺序
        Map<Long, Entry> entryById = entries.stream()
                .collect(Collectors.toMap(Entry::getId, e -> e));
        List<EntrySummaryVO> list = pageIds.stream()
                .map(entryById::get)
                .filter(Objects::nonNull)
                .map(entry -> assembler.toSummaries(List.of(entry), user).get(0))
                .toList();

        // 筛选 chips:未筛选时统计全量收藏,已筛选时统计命中集 ——
        // 用过滤后的集合,数字才是"选这个标签还能看到几篇"
        List<Long> countableIds = filtered ? filteredIds : entryIds;
        Map<String, Long> counts = new LinkedHashMap<>();
        if (!countableIds.isEmpty()) {
            Map<Long, List<String>> tagsByEntry = tagNamesByEntry(countableIds);
            for (List<String> names : tagsByEntry.values()) {
                for (String name : names) {
                    counts.merge(name, 1L, Long::sum);
                }
            }
        }
        List<BookmarkTagVO> tags = counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .map(e -> new BookmarkTagVO(e.getKey(), e.getValue()))
                .toList();

        BookmarkPageVO vo = new BookmarkPageVO();
        vo.setList(list);
        vo.setTotal(filteredIds.size());
        vo.setTags(tags);
        vo.setFiltered(filtered);
        return vo;
    }

    /** 一批日记的标签名映射,单次 SQL(复用 ViewAssembler 同款 mapper 方法)。 */
    private Map<Long, List<String>> tagNamesByEntry(List<Long> entryIds) {
        return tagMapper.findByEntryIds(entryIds).stream()
                .collect(Collectors.groupingBy(EntryTagRow::getEntryId,
                        Collectors.mapping(EntryTagRow::getName, Collectors.toList())));
    }

    @Override
    public Entry requireReadable(Long id, LoginUser viewer) {
        Entry entry = entryMapper.selectById(id);
        if (entry == null) {
            throw BizException.notFound("日记");
        }
        boolean ownerOrAdmin = viewer != null
                && (Objects.equals(entry.getUserId(), viewer.id()) || viewer.isAdmin());
        if (!ownerOrAdmin && !isPubliclyReadable(entry)) {
            // 别人的私密日记一律按"不存在"处理
            throw BizException.notFound("日记");
        }
        return entry;
    }

    @Override
    public Entry requireWritable(Long id, LoginUser editor) {
        Entry entry = entryMapper.selectById(id);
        if (entry == null) {
            throw BizException.notFound("日记");
        }
        if (editor == null || (!Objects.equals(entry.getUserId(), editor.id()) && !editor.isAdmin())) {
            throw BizException.forbidden("只能编辑自己的日记");
        }
        return entry;
    }

    /** 日记的可见性跟随它所属的行程;未归类(无行程)的日记默认只有自己能看。 */
    private boolean isPubliclyReadable(Entry entry) {
        if (entry.getTripId() == null) {
            return false;
        }
        var trip = tripService.requireReadable(entry.getTripId(), null);
        return trip.isPubliclyVisible();
    }

    /* ---------------------------------------------------------------------
     *  内部工具
     * ------------------------------------------------------------------- */

    /** 归属校验:行程必须是自己的,否则不能把日记挂进去。 */
    private Long validatedTripId(Long tripId, LoginUser owner) {
        if (tripId == null) {
            return null;
        }
        tripService.requireOwned(tripId, owner);
        return tripId;
    }

    /** 经纬度必须成对出现。只给一个谈不上是"一个地点",整对丢弃好过存半截。 */
    private boolean hasBothCoordinates(EntryRequest request) {
        return request.getLatitude() != null && request.getLongitude() != null;
    }

    private void applyLocation(Entry entry, EntryRequest request) {
        entry.setLocationName(trimToNull(request.getLocationName()));
        if (hasBothCoordinates(request)) {
            entry.setLatitude(BigDecimal.valueOf(request.getLatitude()));
            entry.setLongitude(BigDecimal.valueOf(request.getLongitude()));
        }
    }

    /** 日期留空即今天。解析失败直接报错,而不是悄悄退化成今天 —— 那会静默写错日期。 */
    private LocalDate parseDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw BizException.badRequest("日期格式不对,应为 yyyy-MM-dd");
        }
    }

    /** 关键词太短就不当作筛选条件,免得一次搜索把整库拉出来。 */
    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.length() < MIN_KEYWORD_LENGTH ? null : trimmed;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 整体替换标签。
     *
     * <p>做法是"先减旧、再加新"而不是"清空重建":标签的 use_count 是全局共享的,
     * 清空重算会让别的日记正在用的标签计数瞬间归零。
     *
     * <p>注意传进来的 {@code tags} 已经过 DTO 层校验(单个 ≤ 12 字、最多 5 个),
     * 这里只做去重、去空白与丢空串。
     */
    private void replaceTags(Long entryId, List<String> tags) {
        List<String> incoming = tags == null ? List.of() : tags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .distinct()
                .toList();

        List<EntryTag> oldLinks = entryTagMapper.selectList(new LambdaQueryWrapper<EntryTag>()
                .eq(EntryTag::getEntryId, entryId));
        for (EntryTag link : oldLinks) {
            tagMapper.addUseCount(link.getTagId(), -1);
        }
        if (!oldLinks.isEmpty()) {
            entryTagMapper.deleteByEntryId(entryId);
        }

        for (String name : incoming) {
            Tag tag = tagMapper.selectOne(new LambdaQueryWrapper<Tag>().eq(Tag::getName, name));
            if (tag == null) {
                Tag fresh = new Tag();
                fresh.setName(name);
                fresh.setUseCount(0);
                try {
                    tagMapper.insert(fresh);
                    tag = fresh;
                } catch (DuplicateKeyException e) {
                    // 并发下另一个请求刚建了同名标签,回查一次即可
                    tag = tagMapper.selectOne(new LambdaQueryWrapper<Tag>().eq(Tag::getName, name));
                    if (tag == null) {
                        continue;
                    }
                }
            }
            try {
                entryTagMapper.insert(EntryTag.of(entryId, tag.getId()));
                tagMapper.addUseCount(tag.getId(), 1);
            } catch (DuplicateKeyException e) {
                // 该日记已经挂过这个标签(去重后不该出现,兜住并发)
            }
        }
    }

    /**
     * 同步图片:按传入的 urls 顺序重建关联。
     *
     * <p>url 是上传接口返回的相对路径,这里是"认领"过程 ——
     * 把 entry_id 为空(或属于这篇日记)的行挂到当前日记上,
     * 并顺手校验归属,防止把别人上传的图片地址粘进来。
     */
    private void syncPhotos(Long entryId, Long userId, List<String> urls) {
        List<String> incoming = urls == null ? List.of() : urls.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(url -> !url.isEmpty())
                .distinct()
                .toList();

        photoService.assertOwnedBy(incoming, userId);

        // 之前挂在这篇日记上、这次没出现的图片,解除关联(逻辑删)
        List<EntryPhoto> existing = photoMapper.selectList(new LambdaQueryWrapper<EntryPhoto>()
                .eq(EntryPhoto::getEntryId, entryId));
        for (EntryPhoto photo : existing) {
            if (!incoming.contains(photo.getUrl())) {
                photoMapper.deleteById(photo.getId());
            }
        }

        int order = 0;
        for (String url : incoming) {
            EntryPhoto photo = photoMapper.selectOne(new LambdaQueryWrapper<EntryPhoto>()
                    .eq(EntryPhoto::getUserId, userId)
                    .eq(EntryPhoto::getUrl, url)
                    .orderByDesc(EntryPhoto::getId)
                    .last("LIMIT 1"));
            if (photo == null) {
                // 种子数据或手工造的 url,补齐一条记录,保证列表页能取到封面
                photo = new EntryPhoto();
                photo.setUserId(userId);
                photo.setUrl(url);
                photo.setThumbUrl(url);
                photo.setSortOrder(order);
                photo.setEntryId(entryId);
                photoMapper.insert(photo);
            } else {
                photoMapper.update(null, new LambdaUpdateWrapper<EntryPhoto>()
                        .eq(EntryPhoto::getId, photo.getId())
                        .set(EntryPhoto::getEntryId, entryId)
                        .set(EntryPhoto::getSortOrder, order)
                        .set(EntryPhoto::getDeleted, 0));
            }
            order++;
        }
    }
}
