package com.travel.diary.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "panel.upload")
public class UploadProperties {

    /** 图片落盘根目录。相对路径以应用启动目录为基准。 */
    private String dir = "./uploads";

    /** 单张图片上限(字节)。与 spring.servlet.multipart.max-file-size 保持一致。 */
    private long maxSize = 10L * 1024 * 1024;

    /** 对外访问前缀。url 存的是这个前缀拼上相对路径,换域名不用改数据。 */
    private String urlPrefix = "/uploads";
}
