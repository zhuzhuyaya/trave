package com.travel.diary;

import com.travel.diary.common.Markdown;
import com.travel.diary.common.PageResult;
import com.travel.diary.mapper.EntryMapper;
import com.travel.diary.mapper.UserMapper;
import com.travel.diary.security.JwtUtil;
import com.travel.diary.security.LoginUser;
import com.travel.diary.service.EntryService;
import com.travel.diary.service.InsightService;
import com.travel.diary.service.PhotoService;
import com.travel.diary.service.TripService;
import com.travel.diary.service.ViewAssembler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 上下文装配 + 通用层单测。
 *
 * <p>跑在 H2 上(见 application-test.yml),因此不需要 MySQL 密码 ——
 * 这条特性是"改完代码随手跑一下"的前提,否则没人会真的跑测试。
 */
@SpringBootTest
@ActiveProfiles("test")
class DiaryApplicationTests {

    @Autowired
    private org.springframework.context.ApplicationContext context;

    @Test
    @DisplayName("Spring 上下文能装配起来")
    void contextLoads() {
        assertThat(context).isNotNull();
    }

    @Test
    @DisplayName("Mapper 都被扫描到了")
    void mappersAreScanned() {
        assertThat(context.getBean(EntryMapper.class)).isNotNull();
        assertThat(context.getBean(UserMapper.class)).isNotNull();
    }

    @Test
    @DisplayName("分页拦截器已注册")
    void paginationInterceptorIsRegistered() {
        // 少了这个 Bean,分页会静默退化成"查出全部再内存分页",不报错但会拖垮服务
        assertThat(context.getBean(MybatisPlusInterceptor.class)).isNotNull();
    }

    @Test
    @DisplayName("Service 与装配器都已注入")
    void servicesAreWired() {
        assertThat(context.getBean(EntryService.class)).isNotNull();
        assertThat(context.getBean(TripService.class)).isNotNull();
        assertThat(context.getBean(PhotoService.class)).isNotNull();
        assertThat(context.getBean(InsightService.class)).isNotNull();
        assertThat(context.getBean(ViewAssembler.class)).isNotNull();
    }

    @Test
    @DisplayName("AI 未启用时语义搜索返回 enabled=false,而不是报错")
    void semanticSearchDegradesWhenAiDisabled() {
        var result = context.getBean(EntryService.class)
                .semanticSearch("京都的秋天", new LoginUser(1L, "traveler", "USER"));

        // 没有嵌入模型 Bean 时必须短路由,不能去查向量表或调 Ollama
        assertThat(result.isEnabled()).isFalse();
        assertThat(result.getHits()).isEmpty();
    }

    @Test
    @DisplayName("JWT 签发后能原样解析回来")
    void jwtRoundTrip() {
        JwtUtil jwtUtil = context.getBean(JwtUtil.class);
        String token = jwtUtil.generate(42L, "traveler", "USER");

        LoginUser parsed = jwtUtil.parse(token);
        assertThat(parsed.id()).isEqualTo(42L);
        assertThat(parsed.username()).isEqualTo("traveler");
        assertThat(parsed.isAdmin()).isFalse();
    }

    @Test
    @DisplayName("ADMIN 角色能被识别")
    void adminRoleIsRecognised() {
        JwtUtil jwtUtil = context.getBean(JwtUtil.class);
        LoginUser parsed = jwtUtil.parse(jwtUtil.generate(1L, "admin", "ADMIN"));
        assertThat(parsed.isAdmin()).isTrue();
    }

    @Test
    @DisplayName("BCrypt 编码与校验")
    void passwordEncoderRoundTrip() {
        PasswordEncoder encoder = context.getBean(PasswordEncoder.class);
        String hash = encoder.encode("diary123");

        assertThat(hash).startsWith("$2a$");
        assertThat(encoder.matches("diary123", hash)).isTrue();
        assertThat(encoder.matches("wrong-password", hash)).isFalse();
        // 每次编码都要带新的 salt,否则哈希会被彩虹表一把梭
        assertThat(encoder.encode("diary123")).isNotEqualTo(hash);
    }

    @Test
    @DisplayName("分页参数会被夹到合理区间")
    void pageParamsAreClamped() {
        assertThat(PageResult.safePage(null)).isEqualTo(1);
        assertThat(PageResult.safePage(0)).isEqualTo(1);
        assertThat(PageResult.safePage(-5)).isEqualTo(1);
        assertThat(PageResult.safePage(3)).isEqualTo(3);

        assertThat(PageResult.safeSize(null)).isEqualTo(PageResult.DEFAULT_SIZE);
        assertThat(PageResult.safeSize(0)).isEqualTo(PageResult.DEFAULT_SIZE);
        assertThat(PageResult.safeSize(15)).isEqualTo(15);
        // 恶意的 size=999999 不能真的拉 99 万行回去
        assertThat(PageResult.safeSize(999_999)).isEqualTo(PageResult.MAX_SIZE);
    }

    @Test
    @DisplayName("分页结果的页数计算")
    void pageCountIsComputed() {
        assertThat(PageResult.of(java.util.List.of(), 0, 1, 10).getPages()).isZero();
        assertThat(PageResult.of(java.util.List.of(), 1, 1, 10).getPages()).isEqualTo(1);
        assertThat(PageResult.of(java.util.List.of(), 10, 1, 10).getPages()).isEqualTo(1);
        // 21 条 / 每页 10 条 = 3 页(向上取整,不能漏掉尾巴那一页)
        assertThat(PageResult.of(java.util.List.of(), 21, 1, 10).getPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("摘要生成:剥掉 Markdown 记号并截断")
    void markdownExcerpt() {
        assertThat(Markdown.toExcerpt(null)).isEmpty();
        assertThat(Markdown.toExcerpt("   ")).isEmpty();
        assertThat(Markdown.toExcerpt("# 标题\n\n正文内容"))
                .isEqualTo("标题 正文内容");
        assertThat(Markdown.toExcerpt("**加粗** 和 `代码`"))
                .isEqualTo("加粗 和 代码");
        assertThat(Markdown.toExcerpt("[链接文字](https://example.com)"))
                .isEqualTo("链接文字");
        assertThat(Markdown.toExcerpt("看这张 ![图](a.png) 照片"))
                .isEqualTo("看这张 照片");
        // 代码块整块丢掉:它在卡片上既读不懂也占地方
        assertThat(Markdown.toExcerpt("前\n```java\nint a = 1;\n```\n后"))
                .isEqualTo("前 后");

        String longText = "很".repeat(300);
        String excerpt = Markdown.toExcerpt(longText);
        assertThat(excerpt).endsWith("…");
        // 120 个正文 + 1 个省略号
        assertThat(excerpt.length()).isEqualTo(121);
    }
}
