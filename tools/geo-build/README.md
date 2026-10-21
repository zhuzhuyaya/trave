# geo-build · 点亮地图板块数据的生成工具

生成 `diary-web/public/geo/litmap-geo.json`:点亮地图叠在 Leaflet 上的板块多边形
(中国 34 个省级板块 + 海外国家),以及国名中英映射 `country-name-cn.json`。

## 为什么工具化而不是手工放一份 JSON

板块边界是一份"死数据",但生成参数(简化容差、是否含南极、坐标系)一旦要对就要重跑;
留着脚本,下次要提精度或换数据源,改一行重跑即可。

## 数据来源

- **中国省界** `geo-data/china_full.json` —— DataV GeoAtlas(`geo.datav.aliyun.com/areas_v3/bound/100000_full.json`),
  高德坐标系(GCJ02),含 34 个省级面。
  下载:`curl -o geo-data/china_full.json https://geo.datav.aliyun.com/areas_v3/bound/100000_full.json`
- **海外国界** `geo-data/ne50_admin0.json` —— Natural Earth 50m admin-0
  (`nvkelso/natural-earth-vector` 的 geojson),WGS84,
  香港作为独立面(`Hong Kong S.A.R.`),新加坡等城邦国家也有。
  DataV 的 `world.json` 没有这些,所以不用它。
  下载:`curl -L -o geo-data/ne50_admin0.json https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_50m_admin_0_countries.geojson`

## 坐标系的取舍

DataV 是 GCJ02、Natural Earth 是 WGS84、OSM 瓦片是 WGS84。
两套数据拼接在边界缝合处有数百米的缝隙/重叠,但点亮地图是省/国家级的示意视图,
这个量级在缩放级别 3~7 不可见 —— 因此**不做** GCJ→WGS 纠偏,
省掉一份纠偏偏移表与一轮坐标变换。要建更精确的"真足迹边界"再回头做。

## 生成

```bash
cd tools/geo-build
node build-country-name-cn.mjs   # 国名映射(可选,幂等)
node build-litmap.mjs            # 生成 ../out/litmap-geo.json
cp ../out/litmap-geo.json ../../diary-web/public/geo/litmap-geo.json
```

产物体积:约 760 KB(gzip 约 250 KB),`public/` 下不进主 bundle,
浏览器缓存住,只在首次进入地图页拉一次。

## 简化容差

Douglas-Peucker,省界 600 米、国界 8 公里,坐标保留 3 位小数(≈110 米)。
点亮判定用的是"点在多边形内",这个精度对判定结果的影响只发生在贴着省界/国界的少数点,
无感。要更准把 `build-litmap.mjs` 里的两个容差调小重跑。
