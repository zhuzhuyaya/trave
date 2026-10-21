package com.travel.diary.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "panel.tiles")
public class TileProperties {

    /**
     * 瓦片上游。OSM 德国镜像(FOSSGIS 运营,OSM 官方认可的基础设施)——
     * 候选源逐一实测的取舍过程写在 diary-web 的 MapView.vue 注释里:
     * 主站多数网络连不通,CARTO 要 key,ArcGIS 中国区域高缩放不可用,高德有 GCJ02 偏移。
     */
    private String upstream = "https://tile.openstreetmap.de";

    /** 瓦片缓存落盘目录。相对路径以应用启动目录为基准,与 uploads 同一套约定。 */
    private String cacheDir = "./tile-cache";

    /** 磁盘缓存有效期(小时)。浏览器侧的实际生效值还会与上游 max-age 取更小者。 */
    private long ttlHours = 168;

    /** 缓存目录总容量上限(MB),超出后按文件新旧淘汰,防止目录无上限膨胀。 */
    private long maxMb = 512;

    /** 回源超时(秒)。上游实测单张 1~2s,放宽一点免得高峰误伤;超时按回源失败处理。 */
    private int timeoutSeconds = 10;
}
