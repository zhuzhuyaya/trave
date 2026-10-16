package com.travel.diary.controller;

import com.travel.diary.common.R;
import com.travel.diary.security.RequireLogin;
import com.travel.diary.security.UserContext;
import com.travel.diary.service.PhotoService;
import com.travel.diary.vo.PhotoVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/photos")
@RequiredArgsConstructor
public class PhotoController {

    private final PhotoService photoService;

    /**
     * 上传单张图片。
     *
     * <p>刻意做成"一次一张"而不是一次多张:多文件接口在部分移动端浏览器上
     * 拿不到可靠的单张进度与失败重试粒度,一张失败整批回滚的体验更糟。
     * 前端选多张时并发发几个请求,由浏览器自己排队,反而更简单。
     *
     * <p>参数名 {@code file} 与前端 FormData 的字段名必须一致。
     */
    @PostMapping
    @RequireLogin
    public R<PhotoVO> upload(@RequestParam("file") MultipartFile file) {
        return R.ok(photoService.upload(file, UserContext.require()));
    }

    @DeleteMapping("/{id}")
    @RequireLogin
    public R<Void> delete(@PathVariable Long id) {
        photoService.delete(id, UserContext.require());
        return R.ok();
    }
}
