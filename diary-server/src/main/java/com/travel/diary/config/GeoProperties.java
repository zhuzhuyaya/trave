package com.travel.diary.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 在线地名检索(OSM Nominatim)代理的配置。 */
@Data
@Component
@ConfigurationProperties(prefix = "panel.geo")
public class GeoProperties {

    /**
     * Nominatim 基址。官方公共实例(operations.osm.org 运营),无需 API Key;
     * 使用条款要求自报应用名(UA 里带了)且限速 1 req/s,见 service 里的限速实现。
     */
    private String upstream = "https://nominatim.openstreetmap.org";

    /** 检索结果缓存落盘目录,与 tiles/uploads 同一套相对路径约定。 */
    private String cacheDir = "./geo-cache";

    /** 缓存有效期(小时):地名库变化很慢,7 天足够;过期后回 404 让下次重新拉。 */
    private long ttlHours = 168;

    /** 单次请求最多返回几条(也会传给上游 limit,省流量也省配额)。 */
    private int limit = 8;

    /** 回源超时(秒)。Nominatim 冷查询可能要 1~2s,放宽避免高峰误伤。 */
    private int timeoutSeconds = 8;

    /** 两次回源之间的最小间隔(毫秒)。Nominatim 使用条款要求 ≤1 req/s,取 1050 留余量。 */
    private long minIntervalMs = 1050;
}
