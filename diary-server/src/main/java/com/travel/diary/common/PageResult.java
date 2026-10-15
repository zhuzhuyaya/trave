package com.travel.diary.common;

import lombok.Getter;

import java.util.List;

/**
 * 分页结果。字段名与前端 {@code PageResult<T>} 一一对应。
 *
 * <p>用私有构造 + 静态工厂,避免调用方手工拼出 {@code pages} 与 {@code total} 不一致的对象。
 */
@Getter
public class PageResult<T> {

    /** 单页上限。与 MybatisPlusConfig 里 PaginationInnerInterceptor 的 maxLimit 保持一致。 */
    public static final int MAX_SIZE = 50;

    public static final int DEFAULT_SIZE = 10;

    private final List<T> list;
    private final long total;
    private final long page;
    private final long size;
    private final long pages;

    private PageResult(List<T> list, long total, long page, long size) {
        this.list = list == null ? List.of() : list;
        this.total = total;
        this.page = page;
        this.size = size;
        this.pages = size <= 0 ? 0 : (total + size - 1) / size;
    }

    public static <T> PageResult<T> of(List<T> list, long total, long page, long size) {
        return new PageResult<>(list, total, page, size);
    }

    /**
     * 页码越界时的空结果。
     *
     * <p>MySQL 分页拦截器在 {@code overflow=false} 下会返回空 list 但 total 仍为真实总数,
     * 那时直接用 {@link #of} 即可;这个方法用于"条件本身就不可能命中"的短路场景。
     */
    public static <T> PageResult<T> empty(long page, long size) {
        return new PageResult<>(List.of(), 0, page, size);
    }

    /** 请求页码兜底:null 或 &lt; 1 一律当第 1 页。 */
    public static long safePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    /** 请求页大小兜底:null 或 &lt; 1 取默认值,超过上限则截断,挡住 {@code size=999999}。 */
    public static long safeSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
