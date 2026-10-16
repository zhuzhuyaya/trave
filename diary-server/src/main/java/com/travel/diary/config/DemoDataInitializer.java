package com.travel.diary.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travel.diary.common.Markdown;
import com.travel.diary.entity.Entry;
import com.travel.diary.entity.EntryTag;
import com.travel.diary.entity.Tag;
import com.travel.diary.entity.Trip;
import com.travel.diary.entity.User;
import com.travel.diary.mapper.EntryMapper;
import com.travel.diary.mapper.EntryTagMapper;
import com.travel.diary.mapper.TagMapper;
import com.travel.diary.mapper.TripMapper;
import com.travel.diary.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 首次启动写入演示数据。
 *
 * <p>为什么用 Java 而不是 {@code data.sql}:密码必须由真实的
 * {@link PasswordEncoder} 生成 BCrypt 哈希,SQL 脚本里写不出可用的值
 * (硬编码一个哈希当然行,但演示账号的明文密码就没法改了)。
 *
 * <p>门控:users 表非空直接跳过 —— 于是重启不会产生重复数据,
 * 也不会覆盖你后来自己录的日记。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoDataInitializer implements ApplicationRunner {

    /** 所有演示账号统一密码。 */
    public static final String DEMO_PASSWORD = "diary123";

    private final UserMapper userMapper;
    private final TripMapper tripMapper;
    private final EntryMapper entryMapper;
    private final TagMapper tagMapper;
    private final EntryTagMapper entryTagMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${panel.seed.enabled:true}")
    private boolean enabled;

    /**
     * 固定随机种子 —— 同一个种子每次产出的"随机"浏览数完全一样,
     * 两次启动的数据可比对,排查问题时不会被随机值搅乱。
     */
    private final Random random = new Random(20261015L);

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("panel.seed.enabled=false,跳过演示数据写入");
            return;
        }

        long existing = userMapper.selectCount(null);
        if (existing > 0) {
            log.info("users 表已有 {} 条记录,跳过演示数据写入", existing);
            return;
        }

        Map<String, User> users = createUsers();
        Map<String, Trip> trips = createTrips(users);
        createEntries(users, trips);

        log.info("""

                ========================================================
                  旅行日记 · 演示数据已写入
                  账号:traveler / {}       (普通用户)
                  账号:admin    / {}       (管理员)
                  共 {} 段行程,{} 篇日记
                ========================================================
                """,
                DEMO_PASSWORD, DEMO_PASSWORD,
                tripMapper.selectCount(null), entryMapper.selectCount(null));
    }

    private Map<String, User> createUsers() {
        Map<String, User> users = new LinkedHashMap<>();
        users.put("traveler", user("traveler", "traveler@diary.local", "在路上", User.ROLE_USER,
                "把每一次出发都写成文字。"));
        users.put("admin", user("admin", "admin@diary.local", "管理员", User.ROLE_ADMIN,
                "维护这个小小的日记本。"));
        return users;
    }

    private User user(String username, String email, String nickname, String role, String bio) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setNickname(nickname);
        user.setBio(bio);
        user.setRole(role);
        user.setStatus(User.STATUS_ACTIVE);
        // 密码必须走真实编码器,这也是整个种子逻辑写在 Java 里的原因
        user.setPassword(passwordEncoder.encode(DEMO_PASSWORD));
        userMapper.insert(user);
        return user;
    }

    private Map<String, Trip> createTrips(Map<String, User> users) {
        Map<String, Trip> trips = new LinkedHashMap<>();
        trips.put("dali", trip(users.get("traveler"), "大理五日", "洱海边的慢日子,骑车、晒太阳、看云。"));
        trips.put("kyoto", trip(users.get("traveler"), "京都的秋", "红叶季的寺庙巡礼,雨里走了很多路。"));
        trips.put("qinghai", trip(users.get("traveler"), "环青海湖", "从西宁出发,一路向西到茶卡。"));
        trips.put("chengdu", trip(users.get("traveler"), "成都吃吃喝喝", "没做攻略的一周,主要任务是吃饭。"));
        trips.put("iceland", trip(users.get("admin"), "冰岛环岛", "十天的风、瀑布和极光。"));
        return trips;
    }

    private Trip trip(User owner, String title, String description) {
        Trip trip = new Trip();
        trip.setUserId(owner.getId());
        trip.setTitle(title);
        trip.setDescription(description);
        trip.setVisibility(Trip.VISIBILITY_PRIVATE);
        trip.setIsArchived(0);
        tripMapper.insert(trip);
        return trip;
    }

    private void createEntries(Map<String, User> users, Map<String, Trip> trips) {
        User traveler = users.get("traveler");
        User admin = users.get("admin");

        List<PostSpec> specs = List.of(
                // 大理
                new PostSpec(traveler, trips.get("dali"), "抵达大理的第一天",
                        "落地已经傍晚,机场大巴晃了一个多小时才到古城。\n\n"
                                + "放下行李就出门找吃的,在一家很小的店里点了饵丝。汤头清得能看见碗底,"
                                + "却一点都不寡淡。老板娘问我是不是第一次来,我说是,她就多给了一勺辣子。\n\n"
                                + "夜里沿着人民路走了很久。风凉,但不冷。",
                        "大理", 100.2676, 25.6065, "晴", "平静", 2024, 3, 12, 128, "大理", "云南", "古城"),
                new PostSpec(traveler, trips.get("dali"), "环洱海骑行,五十公里",
                        "租了一辆山地车,从才村码头出发,顺时针绕。\n\n"
                                + "前半程一直在拍照,后半程一直在后悔——屁股疼得厉害,"
                                + "而剩下的路还有二十多公里。\n\n"
                                + "海舌公园那一段最好看。水很静,天的蓝被压在水面上,"
                                + "远处苍山的雪线看得清清楚楚。\n\n"
                                + "晚上回到古城,吃了两碗米饭。",
                        "洱海", 100.1890, 25.8280, "晴", "疲惫但满足", 2024, 3, 14, 342, "骑行", "洱海", "户外"),
                new PostSpec(traveler, trips.get("dali"), "沙溪,一个安静得不像景点的地方",
                        "从大理坐了两个多小时车到沙溪。\n\n"
                                + "寺登街的青石板被磨得发亮,两旁的铺子大多关着门。"
                                + "下午坐在古戏台前的石阶上,看一个老人喂鸽子,看了快一个小时。\n\n"
                                + "这里没有商业街的喧闹,也没有网红打卡的队伍。"
                                + "只是有人在过日子,而我恰好路过。",
                        "沙溪", 99.8500, 26.3160, "多云", "安静", 2024, 3, 16, 96, "古镇", "大理", "慢生活"),

                // 京都
                new PostSpec(traveler, trips.get("kyoto"), "京都的雨",
                        "到京都第二天就下雨。\n\n"
                                + "原本计划的哲学之道走了一半,雨太大,躲进银阁寺旁边的茶屋。"
                                + "点了一碗抹茶,苦得让人清醒。\n\n"
                                + "雨里的苔藓是另一种绿色,湿漉漉的,像刚被谁擦过。"
                                + "忽然觉得下雨也不错 —— 至少人少。",
                        "京都", 135.7681, 35.0116, "雨", "惬意", 2024, 11, 8, 256, "京都", "日本", "雨天"),
                new PostSpec(traveler, trips.get("kyoto"), "清水寺的夜枫",
                        "赶上了夜间特别参拜。\n\n"
                                + "枫叶被灯打得透亮,一层层红从脚下铺到远处。"
                                + "人很多,但奇怪的是不吵——大家都举着手机,安静地往前走。\n\n"
                                + "在舞台上站了一会儿,想的是:这个角度,"
                                + "一千多年来有多少人站在同一个位置看过同一片山。",
                        "清水寺", 135.7850, 34.9949, "晴", "震撼", 2024, 11, 10, 512, "红叶", "寺庙", "京都"),
                new PostSpec(traveler, trips.get("kyoto"), "伏见稻荷,一千本鸟居",
                        "早上六点就到了,为的是避开人流。\n\n"
                                + "事实证明是对的:整条山路几乎只有我一个人。"
                                + "鸟居的红在晨光里偏橙,两侧的字被摸得发白。\n\n"
                                + "爬到半山腰就折返了。不是爬不动,"
                                + "是觉得够了——再多走一段,反而会冲淡刚才那阵安静。",
                        "伏见稻荷", 135.7727, 34.9671, "晴", "满足", 2024, 11, 12, 388, "徒步", "神社", "清晨"),

                // 青海
                new PostSpec(traveler, trips.get("qinghai"), "环湖第一天,从西宁到黑马河",
                        "出城之后风就大了起来。\n\n"
                                + "车窗外先是农田,然后是草原,再然后是那种"
                                + "一眼望不到边的、颜色深得发蓝的湖。\n\n"
                                + "在黑马河住下,晚上出门看星星。"
                                + "冷得发抖,但抬头的那一瞬间,什么都值了。",
                        "黑马河", 99.7833, 36.7333, "晴", "兴奋", 2025, 7, 20, 174, "青海湖", "自驾", "星空"),
                new PostSpec(traveler, trips.get("qinghai"), "茶卡盐湖,天空之镜",
                        "到的时候已经是下午。\n\n"
                                + "盐湖的水很浅,走过去大概到脚踝。"
                                + "云倒映在水面上,踩下去的时候会碎开,然后又慢慢合上。\n\n"
                                + "照片拍出来确实好看,但比照片更好的是那种"
                                + "站在天和地中间、脚下什么都没有的错觉。",
                        "茶卡盐湖", 99.0900, 36.7000, "晴", "失语", 2025, 7, 22, 602, "盐湖", "青海湖", "拍照"),

                // 成都
                new PostSpec(traveler, trips.get("chengdu"), "到成都的第一个晚上",
                        "本来想去看熊猫的,结果在酒店睡到了下午三点。\n\n"
                                + "出门直接去吃了火锅。微辣已经足够,吃到一半开始流泪,"
                                + "旁边桌的本地人看我一眼,又加了一勺辣椒面。\n\n"
                                + "这就是成都吧。",
                        "成都", 104.0665, 30.5728, "阴", "放松", 2026, 1, 3, 88, "火锅", "成都", "美食"),
                new PostSpec(traveler, trips.get("chengdu"), "人民公园的下午",
                        "在鹤鸣茶社坐了三个小时。\n\n"
                                + "一杯盖碗茶,一个竹椅,旁边桌上有人在打牌,"
                                + "再远一点有人在掏耳朵——那个工具叮叮当当的响声很有辨识度。\n\n"
                                + "什么都没干。但一点都不觉得浪费。",
                        "人民公园", 104.0630, 30.6550, "多云", "闲适", 2026, 1, 5, 64, "茶馆", "成都", "慢生活"),

                // 冰岛
                new PostSpec(admin, trips.get("iceland"), "黄金圈,第一天就被吹懵了",
                        "间歇泉喷发的那一刻,风把所有水汽都吹向人群。\n\n"
                                + "所有人都笑了,然后集体后退三步。\n\n"
                                + "黄金瀑布比照片上大得多。站在观景台上,"
                                + "水汽打在脸上,眼镜全花了。",
                        "黄金圈", -20.3020, 64.3271, "多云", "狼狈", 2025, 9, 18, 233, "冰岛", "瀑布", "自驾"),
                new PostSpec(admin, trips.get("iceland"), "在杰古沙龙湖等到极光",
                        "连着三晚都是云。\n\n"
                                + "第四晚十一点,把车停在路边,熄火。"
                                + "然后就看见天边那条淡绿色的带子慢慢亮起来。\n\n"
                                + "没有声音,但脑子里是轰的一声。",
                        "杰古沙龙", -16.1800, 64.0780, "晴", "难以形容", 2025, 9, 23, 741, "极光", "冰岛", "星空")
        );

        // 过去的日期需要显式写进 entry_date 与 created_at,否则时间线看起来像一天写完的
        for (PostSpec spec : specs) {
            Entry entry = new Entry();
            entry.setTripId(spec.trip().getId());
            entry.setUserId(spec.author().getId());
            entry.setTitle(spec.title());
            entry.setContent(spec.content());
            entry.setExcerpt(Markdown.toExcerpt(spec.content()));
            entry.setEntryDate(LocalDate.of(spec.year(), spec.month(), spec.day()));
            entry.setLocationName(spec.location());
            entry.setLatitude(BigDecimal.valueOf(spec.latitude()));
            entry.setLongitude(BigDecimal.valueOf(spec.longitude()));
            entry.setWeather(spec.weather());
            entry.setMood(spec.mood());
            // 浏览数用固定种子生成,让两次启动的数据完全一致
            entry.setViewCount(spec.baseViews() + random.nextInt(40));
            entryMapper.insert(entry);

            for (String tagName : spec.tags()) {
                attachTag(entry.getId(), tagName);
            }
        }
    }

    /**
     * 挂标签。
     *
     * <p>标签是全局共享的:多篇日记用同一个标签名时只应该有一条 tags 记录,
     * 因此先查后插,并把 use_count 累加。
     */
    private void attachTag(Long entryId, String name) {
        Tag tag = tagMapper.selectOne(new LambdaQueryWrapper<Tag>().eq(Tag::getName, name));
        if (tag == null) {
            Tag fresh = new Tag();
            fresh.setName(name);
            fresh.setUseCount(0);
            try {
                tagMapper.insert(fresh);
                tag = fresh;
            } catch (DuplicateKeyException e) {
                tag = tagMapper.selectOne(new LambdaQueryWrapper<Tag>().eq(Tag::getName, name));
                if (tag == null) {
                    return;
                }
            }
        }
        try {
            entryTagMapper.insert(EntryTag.of(entryId, tag.getId()));
            tagMapper.addUseCount(tag.getId(), 1);
        } catch (DuplicateKeyException e) {
            // 同一篇日记重复挂同一个标签,忽略
        }
    }

    /**
     * 一篇演示日记的规格。
     *
     * <p>用 record 而不是 Map:字段一多,字符串键的写法就没有编译器保护了,
     * 打错一个字要到运行时才发现。
     */
    private record PostSpec(
            User author,
            Trip trip,
            String title,
            String content,
            String location,
            double longitude,
            double latitude,
            String weather,
            String mood,
            int year,
            int month,
            int day,
            int baseViews,
            String... tags
    ) {
    }
}
