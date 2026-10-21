package com.travel.diary.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.travel.diary.config.GeoProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 在线地名检索:代理 OSM Nominatim,给地图选点的搜索框补"景区/POI 级"的结果。
 *
 * <p>为什么要代理而不是前端直接请求:Nominatim 的使用条款把 key 排除在外,
 * 但要求限速(1 req/s)并自报应用名 —— 这两条只有服务端能可靠执行;
 * 且浏览器的 CORS 也会拦直连。代理同时给结果落一层磁盘缓存,
 * 同一个地名整个部署只回源一次(板块数据是死数据,景区名更慢变,7 天 TTL 足够)。
 *
 * <p>降级口径:上游挂了/超时一律返回空列表,不抛错 ——
 * 搜索框里本地(内置表 + 板块索引)结果永远可用,在线这路只是"锦上添花"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NominatimService {

    private final GeoProperties properties;
    private final HttpClient tileHttpClient; // 复用回源客户端:连接池常驻,与业务互不抢连接
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Nominatim 要求逐次串行限速;这把锁同时保证"读 mtime → 休息"的原子性 */
    private final Object rateLock = new Object();
    private long lastFetchMs = 0;

    /** 一条检索结果,给 VO 的字段(不直接吐 Nominatim 原始 JSON,字段太肥)。
     *  bbox 是 Nominatim 的 boundingbox 南北西东四界,前端拿它做"像导航那样"的取景飞行;
     *  没有(纯点位)时为 null,前端自动退回点飞。 */
    public record GeoHit(String name, String region, double latitude, double longitude, String[] bbox) {}

    /**
     * 按关键词检索。先查磁盘缓存,未命中限速回源;nominatim 挂了回空列表。
     *
     * @param viewbox 西南、东北角限界(可选):地图选点场景先飞到了某处,
     *                带上当前视野能把"重名裁剪"做得更聪明(如搜"西湖"优先视野内的)
     */
    public List<GeoHit> search(String query, Double[] viewbox) {
        String key = cacheKey(query, viewbox);
        Path cached = cachePath(key);

        byte[] hit = readCache(cached);
        if (hit == null) {
            hit = fetchUpstream(query, viewbox);
            if (hit == null) {
                return List.of(); // 上游失败:不缓存空结果,下次请求再试
            }
            writeCache(cached, hit);
        }
        return parse(hit);
    }

    /* ---------------- 缓存 ---------------- */

    /** 缓存文件名:query + viewbox 摘要的可打印指纹,避免文件名非法字符 */
    private String cacheKey(String query, Double[] viewbox) {
        String raw = query + (viewbox == null ? "" : "|" + String.join(",", stream(viewbox)));
        StringBuilder sb = new StringBuilder();
        for (byte b : raw.getBytes(StandardCharsets.UTF_8)) {
            if (b >= '0' && b <= '9' || b >= 'a' && b <= 'z' || b >= 'A' && b <= 'Z') {
                sb.append((char) b);
            } else {
                sb.append(String.format("%02x", b & 0xff));
            }
        }
        // 截断防超长文件名;前缀放查询原文片段,扫缓存目录时人能看懂
        String head = query.replaceAll("[^\\w\\u4e00-\\u9fa5]", "").substring(0, Math.min(16, query.length()));
        return (head.isEmpty() ? "q" : head) + "-" + sb.substring(0, Math.min(24, sb.length())) + ".json";
    }

    private static List<String> stream(Double[] viewbox) {
        List<String> parts = new ArrayList<>();
        for (Double d : viewbox) {
            parts.add(d == null ? "" : d.toString());
        }
        return parts;
    }

    private Path cachePath(String key) {
        return Path.of(properties.getCacheDir(), key);
    }

    /** 过期或损坏的缓存按未命中处理;与 TileService.readCache 同一套口径 */
    private byte[] readCache(Path path) {
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try {
            Instant mtime = Files.getLastModifiedTime(path).toInstant();
            if (mtime.isBefore(Instant.now().minus(Duration.ofHours(properties.getTtlHours())))) {
                return null;
            }
            return Files.readAllBytes(path);
        } catch (NoSuchFileException vanished) {
            return null; // 并发中已被淘汰
        } catch (IOException corrupted) {
            log.warn("地名缓存读取失败,按未命中处理: {}", path);
            return null;
        }
    }

    private void writeCache(Path path, byte[] bytes) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.write(tmp, bytes);
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.warn("地名缓存写入失败(不影响本次结果): {}", e.getMessage());
        }
    }

    /* ---------------- 回源 ---------------- */

    /** 限速回源:同一个部署所有用户共享 1 req/s 预算,等锁的排成一路。 */
    private byte[] fetchUpstream(String query, Double[] viewbox) {
        synchronized (rateLock) {
            long waitMs = properties.getMinIntervalMs() - (System.currentTimeMillis() - lastFetchMs);
            if (waitMs > 0) {
                try {
                    Thread.sleep(waitMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
            lastFetchMs = System.currentTimeMillis();

            try {
                URI uri = buildUri(query, viewbox);
                HttpRequest request = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                        // Nominatim 使用条款:必须自报应用身份;空 UA 会被直接封掉
                        .header("User-Agent", "travel-diary/1.0 (self-hosted travel diary)")
                        .header("Accept-Language", "zh-CN,zh;q=0.9")
                        .GET()
                        .build();
                HttpResponse<byte[]> response = tileHttpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() != 200) {
                    log.info("Nominatim 回源非 200: {} {}", response.statusCode(), query);
                    return null;
                }
                return response.body();
            } catch (IOException e) {
                log.info("Nominatim 回源失败(按空结果处理): {} - {}", query, e.getMessage());
                return null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
    }

    private URI buildUri(String query, Double[] viewbox) {
        StringBuilder url = new StringBuilder(properties.getUpstream())
                .append("/search?q=").append(URLEncoder.encode(query, StandardCharsets.UTF_8))
                .append("&format=jsonv2&addressdetails=0&limit=").append(properties.getLimit());
        if (viewbox != null && viewbox.length == 4) {
            // viewbox=<x1>,<y1>,<x2>,<y2>(左上,右上,左下,右下的 x4 个经度 y4 个纬度)
            url.append("&viewbox=").append(viewbox[0]).append(',').append(viewbox[1])
                    .append(',').append(viewbox[2]).append(',').append(viewbox[3]);
        }
        return URI.create(url.toString());
    }

    /* ---------------- 解析 ---------------- */

    /** Nominatim 返回数组;只取展示需要的三样:名字、父子链、坐标。 */
    private List<GeoHit> parse(byte[] body) {
        List<GeoHit> hits = new ArrayList<>();
        try {
            JsonNode array = objectMapper.readTree(body);
            if (!array.isArray()) {
                return hits;
            }
            for (JsonNode item : array) {
                String name = itemName(item);
                if (name == null) {
                    continue;
                }
                double lat;
                double lon;
                try {
                    lat = Double.parseDouble(item.path("lat").asText());
                    lon = Double.parseDouble(item.path("lon").asText());
                } catch (NumberFormatException badCoord) {
                    continue;
                }
                hits.add(new GeoHit(name, subtitle(item, name), lat, lon, boundingbox(item)));
            }
        } catch (IOException e) {
            log.warn("Nominatim 结果解析失败(按空结果处理): {}", e.getMessage());
        }
        return hits;
    }

    /** boundingbox 原样透传([南, 北, 西, 东] 字符串):顺序语义由前端文档约定,这里不重排。 */
    private String[] boundingbox(JsonNode item) {
        JsonNode box = item.path("boundingbox");
        if (!box.isArray() || box.size() != 4) {
            return null;
        }
        String[] parts = new String[4];
        for (int i = 0; i < 4; i++) {
            parts[i] = box.get(i).asText();
        }
        return parts;
    }

    /**
     * 找展示名:优先 name(Nominatim 的 jsonv2 已给本语种名),否则从
     * display_name 抠主名(它是"主名, 区, 市, 省, 国家"链,第一段即主名)。
     */
    private String itemName(JsonNode item) {
        String simple = item.path("name").asText("");
        if (!simple.isBlank()) {
            return simple;
        }
        String display = item.path("display_name").asText("");
        int comma = display.indexOf(',');
        if (!display.isBlank() && comma > 0) {
            return display.substring(0, comma).trim();
        }
        return display.isBlank() ? null : display.trim();
    }

    /**
     * 副标题:display_name 去掉与主名重复的第一段,再把", "分隔链压成「A · B · C」。
     * 全造不出(没有父子链)时回空串,前端自然就不显示副标题。
     */
    private String subtitle(JsonNode item, String name) {
        String display = item.path("display_name").asText("");
        if (display.isBlank()) {
            return "";
        }
        String[] segments = display.split(",\\s*");
        List<String> rest = new ArrayList<>();
        for (String segment : segments) {
            if (!segment.isBlank() && !segment.equals(name)) {
                rest.add(segment);
            }
        }
        return String.join(" · ", rest);
    }
}
