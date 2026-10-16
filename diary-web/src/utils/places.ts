/**
 * 内置常用地点表。
 *
 * 为什么不接地理编码服务:那需要一个 API Key,还要处理配额与限流;
 * 而旅行日记的地点大多是"城市 / 知名景点",一张内置表就覆盖了绝大多数情况。
 * 这里只放真实经纬度 —— 后端地图页靠它打点,坐标错一点地图上就跑偏了。
 *
 * 用户想写表里没有的地方时,可以手填经纬度,或者只写地名(无坐标,
 * 不上地图,但仍能按地名搜索)。
 */

export interface Place {
  name: string
  region: string
  latitude: number
  longitude: number
}

export const PLACES: Place[] = [
  // 中国 · 云南
  { name: '大理', region: '云南', latitude: 25.6065, longitude: 100.2676 },
  { name: '洱海', region: '云南 · 大理', latitude: 25.7903, longitude: 100.1889 },
  { name: '双廊', region: '云南 · 大理', latitude: 25.8987, longitude: 100.1819 },
  { name: '沙溪古镇', region: '云南 · 大理', latitude: 26.3263, longitude: 99.8484 },
  { name: '丽江', region: '云南', latitude: 26.8721, longitude: 100.2299 },
  { name: '香格里拉', region: '云南', latitude: 27.8253, longitude: 99.7065 },
  { name: '昆明', region: '云南', latitude: 24.8801, longitude: 102.8329 },

  // 中国 · 四川 / 重庆
  { name: '成都', region: '四川', latitude: 30.5728, longitude: 104.0668 },
  { name: '人民公园', region: '四川 · 成都', latitude: 30.6648, longitude: 104.0553 },
  { name: '宽窄巷子', region: '四川 · 成都', latitude: 30.6698, longitude: 104.0593 },
  { name: '稻城亚丁', region: '四川 · 甘孜', latitude: 28.4356, longitude: 100.3314 },
  { name: '九寨沟', region: '四川 · 阿坝', latitude: 33.2603, longitude: 103.9182 },
  { name: '重庆', region: '重庆', latitude: 29.563, longitude: 106.5516 },

  // 中国 · 西北
  { name: '青海湖', region: '青海', latitude: 36.8944, longitude: 100.2269 },
  { name: '黑马河', region: '青海 · 共和', latitude: 36.7339, longitude: 99.7919 },
  { name: '茶卡盐湖', region: '青海 · 乌兰', latitude: 36.6997, longitude: 99.0894 },
  { name: '敦煌', region: '甘肃', latitude: 40.1421, longitude: 94.6618 },
  { name: '莫高窟', region: '甘肃 · 敦煌', latitude: 40.0358, longitude: 94.8097 },
  { name: '张掖丹霞', region: '甘肃 · 张掖', latitude: 38.9395, longitude: 100.1324 },
  { name: '乌鲁木齐', region: '新疆', latitude: 43.8256, longitude: 87.6168 },
  { name: '喀纳斯', region: '新疆 · 阿勒泰', latitude: 48.7139, longitude: 87.0019 },
  { name: '喀什', region: '新疆', latitude: 39.4704, longitude: 75.9898 },
  { name: '拉萨', region: '西藏', latitude: 29.652, longitude: 91.1721 },
  { name: '纳木错', region: '西藏 · 拉萨', latitude: 30.7464, longitude: 90.5918 },
  { name: '西安', region: '陕西', latitude: 34.3416, longitude: 108.9398 },

  // 中国 · 华东 / 华南 / 华北
  { name: '上海', region: '上海', latitude: 31.2304, longitude: 121.4737 },
  { name: '外滩', region: '上海 · 黄浦', latitude: 31.2397, longitude: 121.4901 },
  { name: '杭州', region: '浙江', latitude: 30.2741, longitude: 120.1551 },
  { name: '西湖', region: '浙江 · 杭州', latitude: 30.2465, longitude: 120.1476 },
  { name: '乌镇', region: '浙江 · 嘉兴', latitude: 30.7452, longitude: 120.4907 },
  { name: '厦门', region: '福建', latitude: 24.4798, longitude: 118.0894 },
  { name: '鼓浪屿', region: '福建 · 厦门', latitude: 24.4472, longitude: 118.0672 },
  { name: '南京', region: '江苏', latitude: 32.0603, longitude: 118.7969 },
  { name: '苏州', region: '江苏', latitude: 31.2989, longitude: 120.5853 },
  { name: '青岛', region: '山东', latitude: 36.0671, longitude: 120.3826 },
  { name: '北京', region: '北京', latitude: 39.9042, longitude: 116.4074 },
  { name: '故宫', region: '北京 · 东城', latitude: 39.9163, longitude: 116.3972 },
  { name: '广州', region: '广东', latitude: 23.1291, longitude: 113.2644 },
  { name: '深圳', region: '广东', latitude: 22.5431, longitude: 114.0579 },
  { name: '桂林', region: '广西', latitude: 25.2736, longitude: 110.29 },
  { name: '阳朔', region: '广西 · 桂林', latitude: 24.7785, longitude: 110.4966 },
  { name: '三亚', region: '海南', latitude: 18.2528, longitude: 109.512 },
  { name: '哈尔滨', region: '黑龙江', latitude: 45.8038, longitude: 126.5349 },
  { name: '呼伦贝尔', region: '内蒙古', latitude: 49.2122, longitude: 119.7658 },

  // 港澳台
  { name: '香港', region: '香港', latitude: 22.3193, longitude: 114.1694 },
  { name: '澳门', region: '澳门', latitude: 22.1987, longitude: 113.5439 },
  { name: '台北', region: '台湾', latitude: 25.033, longitude: 121.5654 },
  { name: '垦丁', region: '台湾 · 屏东', latitude: 21.9467, longitude: 120.8 },

  // 东亚
  { name: '东京', region: '日本', latitude: 35.6762, longitude: 139.6503 },
  { name: '京都', region: '日本', latitude: 35.0116, longitude: 135.7681 },
  { name: '清水寺', region: '日本 · 京都', latitude: 34.9949, longitude: 135.785 },
  { name: '伏见稻荷', region: '日本 · 京都', latitude: 34.9671, longitude: 135.7727 },
  { name: '平安神宫', region: '日本 · 京都', latitude: 35.0161, longitude: 135.7822 },
  { name: '岚山', region: '日本 · 京都', latitude: 35.0094, longitude: 135.6668 },
  { name: '大阪', region: '日本', latitude: 34.6937, longitude: 135.5023 },
  { name: '奈良', region: '日本', latitude: 34.6851, longitude: 135.8048 },
  { name: '北海道', region: '日本', latitude: 43.0621, longitude: 141.3544 },
  { name: '札幌', region: '日本 · 北海道', latitude: 43.0618, longitude: 141.3545 },
  { name: '首尔', region: '韩国', latitude: 37.5665, longitude: 126.978 },
  { name: '济州岛', region: '韩国', latitude: 33.4996, longitude: 126.5312 },
  { name: '香港离岛', region: '香港', latitude: 22.2619, longitude: 113.9416 },

  // 东南亚 / 南亚
  { name: '曼谷', region: '泰国', latitude: 13.7563, longitude: 100.5018 },
  { name: '清迈', region: '泰国', latitude: 18.7883, longitude: 98.9853 },
  { name: '普吉岛', region: '泰国', latitude: 7.8804, longitude: 98.3923 },
  { name: '巴厘岛', region: '印度尼西亚', latitude: -8.4095, longitude: 115.1889 },
  { name: '新加坡', region: '新加坡', latitude: 1.3521, longitude: 103.8198 },
  { name: '吉隆坡', region: '马来西亚', latitude: 3.139, longitude: 101.6869 },
  { name: '河内', region: '越南', latitude: 21.0278, longitude: 105.8342 },
  { name: '加德满都', region: '尼泊尔', latitude: 27.7172, longitude: 85.324 },

  // 欧洲
  { name: '巴黎', region: '法国', latitude: 48.8566, longitude: 2.3522 },
  { name: '埃菲尔铁塔', region: '法国 · 巴黎', latitude: 48.8584, longitude: 2.2945 },
  { name: '普罗旺斯', region: '法国', latitude: 43.9352, longitude: 5.0677 },
  { name: '伦敦', region: '英国', latitude: 51.5074, longitude: -0.1278 },
  { name: '爱丁堡', region: '英国 · 苏格兰', latitude: 55.9533, longitude: -3.1883 },
  { name: '罗马', region: '意大利', latitude: 41.9028, longitude: 12.4964 },
  { name: '佛罗伦萨', region: '意大利', latitude: 43.7696, longitude: 11.2558 },
  { name: '威尼斯', region: '意大利', latitude: 45.4408, longitude: 12.3155 },
  { name: '巴塞罗那', region: '西班牙', latitude: 41.3874, longitude: 2.1686 },
  { name: '马德里', region: '西班牙', latitude: 40.4168, longitude: -3.7038 },
  { name: '圣托里尼', region: '希腊', latitude: 36.3932, longitude: 25.4615 },
  { name: '雅典', region: '希腊', latitude: 37.9838, longitude: 23.7275 },
  { name: '布拉格', region: '捷克', latitude: 50.0755, longitude: 14.4378 },
  { name: '维也纳', region: '奥地利', latitude: 48.2082, longitude: 16.3738 },
  { name: '阿姆斯特丹', region: '荷兰', latitude: 52.3676, longitude: 4.9041 },
  { name: '苏黎世', region: '瑞士', latitude: 47.3769, longitude: 8.5417 },
  { name: '因特拉肯', region: '瑞士', latitude: 46.6863, longitude: 7.8632 },
  { name: '少女峰', region: '瑞士', latitude: 46.5374, longitude: 7.9624 },
  { name: '柏林', region: '德国', latitude: 52.52, longitude: 13.405 },
  { name: '慕尼黑', region: '德国', latitude: 48.1351, longitude: 11.582 },
  { name: '新天鹅堡', region: '德国 · 巴伐利亚', latitude: 47.5576, longitude: 10.7498 },
  { name: '莫斯科', region: '俄罗斯', latitude: 55.7558, longitude: 37.6173 },
  { name: '圣彼得堡', region: '俄罗斯', latitude: 59.9311, longitude: 30.3609 },
  { name: '雷克雅未克', region: '冰岛', latitude: 64.1466, longitude: -21.9426 },
  { name: '黄金圈', region: '冰岛', latitude: 64.3271, longitude: -20.1219 },
  { name: '杰古沙龙冰河湖', region: '冰岛', latitude: 64.0784, longitude: -16.2306 },
  { name: '维克黑沙滩', region: '冰岛', latitude: 63.4194, longitude: -19.0062 },

  // 北美
  { name: '纽约', region: '美国 · 纽约州', latitude: 40.7128, longitude: -74.006 },
  { name: '时代广场', region: '美国 · 纽约', latitude: 40.758, longitude: -73.9855 },
  { name: '旧金山', region: '美国 · 加州', latitude: 37.7749, longitude: -122.4194 },
  { name: '金门大桥', region: '美国 · 旧金山', latitude: 37.8199, longitude: -122.4783 },
  { name: '洛杉矶', region: '美国 · 加州', latitude: 34.0522, longitude: -118.2437 },
  { name: '黄石公园', region: '美国 · 怀俄明', latitude: 44.428, longitude: -110.5885 },
  { name: '温哥华', region: '加拿大 · 不列颠哥伦比亚', latitude: 49.2827, longitude: -123.1207 },
  { name: '班夫', region: '加拿大 · 阿尔伯塔', latitude: 51.1784, longitude: -115.5708 },
  { name: '墨西哥城', region: '墨西哥', latitude: 19.4326, longitude: -99.1332 },

  // 大洋洲 / 非洲 / 南美
  { name: '悉尼', region: '澳大利亚', latitude: -33.8688, longitude: 151.2093 },
  { name: '墨尔本', region: '澳大利亚', latitude: -37.8136, longitude: 144.9631 },
  { name: '大堡礁', region: '澳大利亚 · 昆士兰', latitude: -18.2871, longitude: 147.6992 },
  { name: '皇后镇', region: '新西兰', latitude: -45.0312, longitude: 168.6626 },
  { name: '开罗', region: '埃及', latitude: 30.0444, longitude: 31.2357 },
  { name: '开普敦', region: '南非', latitude: -33.9249, longitude: 18.4241 },
  { name: '马拉喀什', region: '摩洛哥', latitude: 31.6295, longitude: -7.9811 },
  { name: '里约热内卢', region: '巴西', latitude: -22.9068, longitude: -43.1729 },
  { name: '库斯科', region: '秘鲁', latitude: -13.5319, longitude: -71.9675 },
  { name: '马丘比丘', region: '秘鲁', latitude: -13.1631, longitude: -72.545 },
]
