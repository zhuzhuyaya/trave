package com.travel.diary.controller;

import com.travel.diary.config.TileProperties;
import com.travel.diary.service.TileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Duration;

/**
 * 瓦片代理出口:/tiles/{z}/{x}/{y}.png → TileService(磁盘缓存 + 回源)。
 *
 * <p>刻意放在 /api 之外且不加 {@code @RequireLogin},理由与 /uploads 一致:
 * 瓦片由 Leaflet 用 {@code <img>} 请求,不会带 Authorization 头,
 * 挂到 JWT 拦截下地图就永远出不了图;而底图是公共地理数据,不含任何用户信息,公开无妨。
 *
 * <p>同样因为走 {@code <img>},这里的错误一律回纯 404 状态而非 R 信封 ——
 * 信封的 HTTP 恒 200 是为 axios 解析设计的,塞给 img 标签只会得到一张裂图。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class TileController {

    /** Web 地图最大缩放级别,超出即非法(z 越大 tile 越多,也是穿透目录的入口)。 */
    private static final int MAX_ZOOM = 19;

    private final TileService tileService;
    private final TileProperties properties;

    @GetMapping("/tiles/{z}/{x}/{y}")
    public ResponseEntity<byte[]> tile(@PathVariable String z,
                                       @PathVariable String x,
                                       @PathVariable String y) {
        int zoom;
        long px;
        long py;
        try {
            zoom = Integer.parseInt(z);
            px = Long.parseLong(x);
            String pyName = y.endsWith(".png") ? y.substring(0, y.length() - 4) : y;
            py = Long.parseLong(pyName);
        } catch (NumberFormatException badPath) {
            // 非数字一律 404:二进制端点不该吐 JSON 错误体
            return ResponseEntity.notFound().build();
        }
        if (zoom < 0 || zoom > MAX_ZOOM || px < 0 || py < 0 || px >= (1L << zoom) || py >= (1L << zoom)) {
            return ResponseEntity.notFound().build();
        }

        byte[] data;
        try {
            // 前面已用 1L<<zoom 校验过,这里强转 int 安全
            data = tileService.fetch(zoom, (int) px, py);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            // 读缓存半路炸了或回源网络故障:<img> 端点没有更好的归宿,404 一次到位
            log.warn("瓦片获取失败 z={} x={} y={}: {}", z, px, py, e.getMessage());
            return ResponseEntity.notFound().build();
        }
        if (data == null) {
            // 上游也拿不到:404 让浏览器按"缺瓦片"处理(留灰底、下轮再试),
            // 不缓存这个结果,给上游恢复后自动痊愈留了路
            return ResponseEntity.notFound().build();
        }

        // 浏览器侧的强缓存比服务端 TTL 略短,到期后会回来重新校验,
        // 正好轮到可能已被服务端淘汰/更新的版本
        long maxAgeSeconds = Math.min(properties.getTtlHours(), 72) * 3600;
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(maxAgeSeconds)).cachePublic())
                .body(data);
    }
}
