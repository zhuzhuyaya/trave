# geo-build · 点亮地图板块数据的生成工具

生成 `diary-web/public/geo/litmap-geo.json`:点亮地图叠在 Leaflet 上的板块多边形
(中国约 2800 个区县级板块 + 海外国家),以及国名中英映射 `country-name-cn.json`。

## 为什么工具化而不是手工放一份 JSON

板块边界是一份"死数据",但生成参数(简化容差、是否含南极、坐标系)一旦要对就要重跑;
留着脚本,下次要提精度或换数据源,改一行重跑即可。

## 板块颗粒度:为什么是区县不是省

点亮单元曾经是省 —— 结果"去过龙岩点亮了整个福建"。后来降到市,还是"去过新罗区
点亮了整个龙岩市"。区县才是用户实际活动的地方,中国的点亮单元因此是:

- 普通地级市 → **区县**(如龙岩市拆出 7 块);
- 直筒子市(东莞/中山/儋州等,无下层)→ **市面本身**一块;
- 直辖市(北上津渝)→ **市辖区**(16~38 块),不再往下拆街道;
- 三沙市 → 拆到**西沙区/南沙区**(DataV 已是此层);
- 台湾 / 香港 / 澳门 → **整域一块**(台湾没有市级子数据,港澳太碎);
- 海外不变,维持**国家级**。

## 数据来源

- **中国省界** `geo-data/china_full.json` —— DataV GeoAtlas(`geo.datav.aliyun.com/areas_v3/bound/100000_full.json`),
  高德坐标系(GCJ02),含 34 个省级面,提供台港澳的整域边界与九段线段。
  下载:`curl -o geo-data/china_full.json https://geo.datav.aliyun.com/areas_v3/bound/100000_full.json`
- **中国市级** `geo-data/china_cities.json` —— DataV 每省一份 `{省adcode}_full.json`
  (如 `350000_full.json` 是福建的 9 个市)由 `fetch-cities.mjs` 集中拉取后的缓存,
  key 是请求路径(如 `/350000_full.json`)。失败重跑可续。
- **中国县级** `geo-data/china_counties.json` —— DataV 每市一份 `{市adcode}_full.json`
  由 `fetch-counties.mjs` 集中拉取后的缓存(329 份,21MB 原始)。
  enrich 走省级缓存找有下层的市,只拉这些,串行 + 重试 + 间隔,失败重跑可续。
- **海外国界** `geo-data/ne50_admin0.json` —— Natural Earth 50m admin-0
  (`nvkelso/natural-earth-vector` 的 geojson),WGS84,
  新加坡等城邦国家也有。香港/澳门/台湾在这里是独立面,但已由 DataV 侧整域提供,生成时跳过。
  下载:`curl -L -o geo-data/ne50_admin0.json https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_50m_admin_0_countries.geojson`

## 坐标系的取舍

DataV 是 GCJ02、Natural Earth 是 WGS84、OSM 瓦片是 WGS84。
两套数据拼接在边界缝合处有数百米的缝隙/重叠,但点亮地图是区县/国家级的示意视图,
这个量级在缩放级别 3~7 不可见 —— 因此**不做** GCJ→WGS 纠偏,
省掉一份纠偏偏移表与一轮坐标变换。要建更精确的"真足迹边界"再回头做。

## 生成

```bash
cd tools/geo-build
node fetch-cities.mjs            # 拉取 31 份市级数据(有缓存,幂等)
node fetch-counties.mjs          # 拉取 329 份县级数据(有缓存,幂等,几分钟)
node build-country-name-cn.mjs   # 国名映射(可选,幂等)
node build-litmap.mjs            # 生成 ../out/litmap-geo.json
cp ../out/litmap-geo.json ../../diary-web/public/geo/litmap-geo.json
```

产物体积:约 3.2MB(gzip 约 850 KB),`public/` 下不进主 bundle,
浏览器缓存住,只在首次进入地图页拉一次。

## 简化容差

Douglas-Peucker,市界 1 公里、国界 8 公里,坐标保留 3 位小数(≈110 米)。
点亮判定用的是"点在多边形内",这个精度对判定结果的影响只发生在贴着县界/国界的少数点,
无感。要更准把 `build-litmap.mjs` 里的两个容差调小重跑
(县级面从 0.01 收紧会显著增大体积 —— 约 2800 块对共享边界的折线各存一份)。
