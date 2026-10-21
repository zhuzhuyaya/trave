package com.travel.diary.service;

import com.travel.diary.config.TileProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.NoSuchFileException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 地图瓦片代理:先查本地磁盘缓存,未命中再回源 tile.openstreetmap.de 并落盘。
 *
 * <p>为什么由后端做:地图上唯一慢的是瓦片(实测单张 1.0~1.8s,本机业务接口只要 5ms)。
 * 瓦片经本服务缓存后,同一张瓦片整个部署只需回源一次,之后所有设备都吃本地磁盘,
 * 响应从 1s+ 降到毫秒级;浏览器自己有 1 天的 HTTP 缓存,但换设备 / 清缓存后
 * 那份缓存帮不上忙 —— 服务端缓存复用的是"整个部署"的流量。
 *
 * <p>为什么用磁盘而不是内存:PNG 瓦片单张 10~60KB,放大一次就涉及几十张,
 * 堆里留这个体积的二进制不如留给业务;磁盘缓存重启后依然命中,还能吃到 OS 文件缓存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TileService {

    /** 子域集合:与 OpenStreetMap 镜像约定一致,轮换以分散上游连接。 */
    private static final String[] SUBDOMAINS = {"a", "b", "c", "d"};

    /** 在途回源上限。HTTP/1.1 每子域只有一条连接,一次放大几十张瓦片一拥而上反而全体变慢。 */
    private static final int MAX_ACTIVE = 6;

    /** 累计写入超过这个量就扫一次盘做容量淘汰(单张几十 KB,32MB ≈ 上千张,足够稀疏)。 */
    private static final long EVICT_AFTER_BYTES = 32L * 1024 * 1024;

    private final TileProperties properties;
    private final HttpClient tileHttpClient;

    /** 在途回源登记表:key = z/x/y。并发打到同一张瓦片时只放一个去上游,其余等结果。 */
    private final ConcurrentHashMap<String, Object> inFlight = new ConcurrentHashMap<>();
    private static final Object CLAIMED = new Object();

    /** 等待者睡在这把锁上,回源方收工时 notifyAll。 */
    private final Object waitLock = new Object();

    /* 在途回源计数(限流),只锁 slotLock */
    private final Object slotLock = new Object();
    private int activeUpstream = 0;

    /* 淘汰的稀疏触发:累计写入量 + 防止并发双扫 */
    private final AtomicLong bytesSinceEvict = new AtomicLong();
    private final AtomicBoolean evicting = new AtomicBoolean(false);

    /**
     * 取一张瓦片:缓存命中直接返回;未命中认领回源资格,拉到就落盘。
     *
     * @return 瓦片字节;上游也拿不到时返回 null(调用方回 404,交由浏览器重试)
     */
    public byte[] fetch(int z, int x, long y) throws IOException, InterruptedException {
        String key = z + "/" + x + "/" + y;
        Path cached = cachePath(z, x, y);

        for (;;) {
            byte[] hit = readCache(cached);
            if (hit != null) {
                touch(cached);
                return hit;
            }

            if (inFlight.putIfAbsent(key, CLAIMED) == null) {
                // 认领成功:本线程负责回源
                try {
                    acquireSlot();
                    try {
                        byte[] fresh = download(z, x, y);
                        if (fresh != null) {
                            writeCache(cached, fresh);
                        }
                        // null 也直接返回:失败结果不落盘、不长期缓存,下次请求再试
                        return fresh;
                    } finally {
                        releaseSlot();
                    }
                } finally {
                    inFlight.remove(key);
                    synchronized (waitLock) {
                        waitLock.notifyAll();
                    }
                }
            }

            // 认领失败:同一张瓦片已有回源在途,等它收工后回到循环重读缓存;
            // 若持锁者失败了(没写出文件),循环会重新认领,由本线程再试一次
            waitFor(key);
        }
    }

    /** 缓存文件按 z/x/y 直接作目录结构,天然分片,淘汰整层扫起来也快。 */
    private Path cachePath(int z, int x, long y) {
        return Path.of(properties.getCacheDir(), String.valueOf(z), String.valueOf(x), y + ".png");
    }

    /** 过期或损坏的缓存一律按未命中处理(返回 null),让调用方走回源。 */
    private byte[] readCache(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            return null;
        }
        // TTL 按"最后一次使用"起算:touch() 在每次命中时刷新 mtime,
        // 热瓦片永远不会被 TTL 踢掉,只有真正冷掉的才过期
        Instant mtime = Files.getLastModifiedTime(path).toInstant();
        if (mtime.isBefore(Instant.now().minus(Duration.ofHours(properties.getTtlHours())))) {
            return null;
        }
        try {
            return Files.readAllBytes(path);
        } catch (NoSuchFileException vanished) {
            return null; // 并发中已被淘汰,当未命中
        } catch (IOException corrupted) {
            log.warn("瓦片缓存读取失败,按未命中处理: {}", path);
            Files.deleteIfExists(path);
            return null;
        }
    }

    /** 刷新 mtime,让容量淘汰"最久未优先"真的按最近使用算。 */
    private void touch(Path path) {
        try {
            Files.setLastModifiedTime(path, FileTime.from(Instant.now()));
        } catch (IOException ignored) {
            // 刷不上时间不影响出图
        }
    }

    /** 回源:子域轮换;超时、非 200、可疑的小体积占位图一律按失败返回 null。 */
    private byte[] download(int z, int x, long y) throws IOException, InterruptedException {
        String sub = SUBDOMAINS[(int) ((x + y) % SUBDOMAINS.length)];
        URI uri = URI.create(properties.getUpstream() + "/" + z + "/" + x + "/" + y + ".png");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .header("User-Agent", "travel-diary-tile-proxy/1.0")
                .GET()
                .build();
        HttpResponse<byte[]> response = tileHttpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            return null;
        }
        byte[] body = response.body();
        if (body.length < 100) {
            // 上游在故障时可能回 200 的占位小图,体积必然异常小 —— 拒收,不缓存错误图
            return null;
        }
        return body;
    }

    /** 落盘:先写临时文件再原子改名,避免并发读到半张图。完成后按累计量触发容量淘汰。 */
    private void writeCache(Path path, byte[] bytes) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
        Files.write(tmp, bytes);
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

        if (bytesSinceEvict.addAndGet(bytes.length) > EVICT_AFTER_BYTES
                && evicting.compareAndSet(false, true)) {
            bytesSinceEvict.set(0);
            try {
                evictIfNeeded();
            } finally {
                evicting.set(false);
            }
        }
    }

    /**
     * 容量淘汰:超过 maxMb 时按最近使用删最旧的瓦片;顺带清掉崩溃残留的老 .tmp 文件。
     *
     * <p>淘汰是 O(n) 遍历,靠 writeCache 里的稀疏触发(EVICT_AFTER_BYTES)控制频率,
     * 高频放大浏览不会每张瓦片都扫一次盘。
     */
    private void evictIfNeeded() {
        Path root = Path.of(properties.getCacheDir());
        if (!Files.isDirectory(root)) {
            return;
        }
        try {
            List<Path> tiles = new ArrayList<>();
            List<Path> staleTmp = new ArrayList<>();
            // lambda 里要累加,用单元素数组代替局部变量(保持 effectively final)
            long[] total = {0};
            Instant tmpCutoff = Instant.now().minus(Duration.ofHours(24));
            try (var walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile).forEach(p -> {
                    if (p.getFileName().toString().endsWith(".tmp")) {
                        try {
                            if (Files.getLastModifiedTime(p).toInstant().isBefore(tmpCutoff)) {
                                staleTmp.add(p);
                            }
                        } catch (IOException ignored) {
                            // 并发中消失了,不管
                        }
                        return;
                    }
                    try {
                        tiles.add(p);
                        total[0] += Files.size(p);
                    } catch (IOException ignored) {
                        // 并发中被删了就当没这个文件
                    }
                });
            }
            for (Path tmp : staleTmp) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // 并发中消失了,正好
                }
            }

            long maxBytes = properties.getMaxMb() * 1024 * 1024;
            if (total[0] <= maxBytes) {
                return;
            }
            List<Path> oldest = tiles.stream()
                    .sorted(Comparator.comparingLong(p -> {
                        try {
                            return Files.getLastModifiedTime(p).toMillis();
                        } catch (IOException e) {
                            return Long.MAX_VALUE; // 取不到时间的不动它
                        }
                    }))
                    .toList();
            long excess = total[0] - maxBytes;
            int deleted = 0;
            for (Path p : oldest) {
                if (excess <= 0) {
                    break;
                }
                try {
                    excess -= Files.size(p);
                    if (Files.deleteIfExists(p)) {
                        deleted++;
                    }
                } catch (IOException ignored) {
                    // 文件已消失,继续
                }
            }
            if (deleted > 0) {
                log.info("瓦片缓存超限,淘汰 {} 张最久未用的瓦片", deleted);
            }
        } catch (IOException e) {
            log.warn("瓦片缓存淘汰扫描失败: {}", e.getMessage());
        }
    }

    /** 在途回源限流:不同瓦片的并发也压在 MAX_ACTIVE 路,超出的排队等唤醒。 */
    private void acquireSlot() throws InterruptedException {
        synchronized (slotLock) {
            while (activeUpstream >= MAX_ACTIVE) {
                slotLock.wait();
            }
            activeUpstream++;
        }
    }

    private void releaseSlot() {
        synchronized (slotLock) {
            activeUpstream--;
            slotLock.notifyAll();
        }
    }

    /** 等在途回源收工:有人持锁就睡,持锁者消失(或超时)即返回,让调用方重试。 */
    private void waitFor(String key) throws InterruptedException {
        long deadline = System.currentTimeMillis() + properties.getTimeoutSeconds() * 2000L;
        synchronized (waitLock) {
            while (inFlight.containsKey(key) && System.currentTimeMillis() < deadline) {
                waitLock.wait(200);
            }
        }
    }
}
