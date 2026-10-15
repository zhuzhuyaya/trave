package com.travel.diary.service;

import com.travel.diary.security.LoginUser;
import com.travel.diary.vo.PhotoVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface PhotoService {

    /** 上传单张图片,落盘后返回可直接访问的 URL。 */
    PhotoVO upload(MultipartFile file, LoginUser uploader);

    /** 删除附件。会校验归属,顺手把磁盘上的文件也删掉。 */
    void delete(Long id, LoginUser operator);

    /**
     * 校验一组 URL 是否都归属于该用户。
     *
     * <p>保存日记时调用 —— 防止把别人上传的图片地址粘进自己的日记里。
     */
    void assertOwnedBy(List<String> urls, Long userId);
}
