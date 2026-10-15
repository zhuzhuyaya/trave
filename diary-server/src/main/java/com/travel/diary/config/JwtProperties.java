package com.travel.diary.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "panel.jwt")
public class JwtProperties {

    /** HS256 密钥,至少 32 字节。生产环境务必用 JWT_SECRET 环境变量覆盖。 */
    private String secret;

    /** 有效期(小时)。168 = 7 天,足够覆盖一趟旅行,不用边玩边重新登录。 */
    private long expireHours = 168;
}
