package com.travel.diary.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.travel.diary.common.BizException;
import com.travel.diary.common.ResultCode;
import com.travel.diary.config.UploadProperties;
import com.travel.diary.entity.EntryPhoto;
import com.travel.diary.mapper.EntryPhotoMapper;
import com.travel.diary.security.LoginUser;
import com.travel.diary.service.PhotoService;
import com.travel.diary.vo.PhotoVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 图片上传:落本地磁盘 + 静态映射访问。
 *
 * <p>类型判定用**文件头魔数**而不是 {@code MultipartFile.getContentType()} ——
 * 后者来自请求头,浏览器之外任何一个 HTTP 客户端都能随便写。
 * 只信任文件本身的字节,是这里唯一可靠的做法。
 *
 * <p>识别不出魔数时**直接拒绝,不看扩展名**。曾经写过"扩展名在白名单就放行"的兜底,
 * 结果一个文本文件改名成 .png 就能存进 uploads 目录 ——
 * 那目录是公开可访问的,等于给任意文件开了一扇门。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PhotoServiceImpl implements PhotoService {

    private static final DateTimeFormatter SUB_DIR = DateTimeFormatter.ofPattern("yyyy/MM");

    private final EntryPhotoMapper photoMapper;
    private final UploadProperties uploadProperties;

    @Override
    @Transactional
    public PhotoVO upload(MultipartFile file, LoginUser uploader) {
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("没有接收到图片文件");
        }
        if (file.getSize() > uploadProperties.getMaxSize()) {
            throw new BizException(ResultCode.FILE_TOO_LARGE);
        }

        String extension = detectExtension(file);
        if (extension == null) {
            throw new BizException(ResultCode.UNSUPPORTED_FILE);
        }

        String relativeDir = LocalDate.now().format(SUB_DIR);
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + extension;

        Path targetDir = Paths.get(uploadProperties.getDir()).toAbsolutePath().resolve(relativeDir);
        Path target = targetDir.resolve(fileName);

        try {
            Files.createDirectories(targetDir);
            file.transferTo(target);
        } catch (IOException e) {
            log.error("图片落盘失败:{}", target, e);
            throw new BizException(ResultCode.INTERNAL_ERROR, "图片保存失败,请重试");
        }

        String url = uploadProperties.getUrlPrefix() + "/" + relativeDir + "/" + fileName;
        int[] size = readDimensions(target);

        EntryPhoto photo = new EntryPhoto();
        photo.setUserId(uploader.id());
        photo.setUrl(url);
        // 本期不做服务端缩放,thumbUrl 与 url 同值 —— 前端用 object-fit + lazy 承担缩略
        photo.setThumbUrl(url);
        photo.setWidth(size[0] > 0 ? size[0] : null);
        photo.setHeight(size[1] > 0 ? size[1] : null);
        photo.setSortOrder(0);
        // entryId 留空:用户在编辑器里点"保存"时才会挂到具体日记上
        photoMapper.insert(photo);

        return toVO(photo);
    }

    @Override
    @Transactional
    public void delete(Long id, LoginUser operator) {
        EntryPhoto photo = photoMapper.selectById(id);
        if (photo == null) {
            throw BizException.notFound("图片");
        }
        if (!Objects.equals(photo.getUserId(), operator.id()) && !operator.isAdmin()) {
            throw BizException.forbidden("只能删除自己上传的图片");
        }

        photoMapper.deleteById(id);

        // 磁盘上的文件顺手删掉。删不掉只记日志不报错 ——
        // 数据库记录已经删了,不该因为一个孤儿文件让整个请求失败。
        try {
            String prefix = uploadProperties.getUrlPrefix() + "/";
            if (photo.getUrl().startsWith(prefix)) {
                String relative = photo.getUrl().substring(prefix.length());
                Files.deleteIfExists(Paths.get(uploadProperties.getDir()).toAbsolutePath().resolve(relative));
            }
        } catch (IOException e) {
            log.warn("图片记录已删除,但磁盘文件清理失败:{}", photo.getUrl(), e);
        }
    }

    @Override
    public void assertOwnedBy(List<String> urls, Long userId) {
        if (urls == null || urls.isEmpty()) {
            return;
        }
        List<String> distinct = urls.stream().filter(Objects::nonNull).distinct().toList();

        // 只防一件事:某个 url 在库里存在,但属于别人。
        // 库里查不到的 url 一律放行 —— 种子数据、外部图床地址都属于这一类,
        // 拦下来只会让用户没法粘贴一张网上的图。而"属于别人"才是真正要防的越权。
        long takenByOthers = photoMapper.selectCount(new LambdaQueryWrapper<EntryPhoto>()
                .ne(EntryPhoto::getUserId, userId)
                .in(EntryPhoto::getUrl, distinct));

        if (takenByOthers > 0) {
            throw BizException.forbidden("图片地址不属于当前账号");
        }
    }

    /* ---------------------------------------------------------------------
     *  内部工具
     * ------------------------------------------------------------------- */

    /**
     * 读文件头魔数判定真实类型。
     *
     * <ul>
     *   <li>JPEG: {@code FF D8 FF}</li>
     *   <li>PNG:  {@code 89 50 4E 47 0D 0A 1A 0A}</li>
     *   <li>WebP: {@code RIFF .... WEBP}(偏移 0 是 RIFF,偏移 8 是 WEBP)</li>
     * </ul>
     *
     * @return 扩展名,无法识别返回 null
     */
    private String detectExtension(MultipartFile file) {
        byte[] header = new byte[12];
        int read;
        try (InputStream in = file.getInputStream()) {
            read = in.read(header);
        } catch (IOException e) {
            log.warn("读取图片文件头失败", e);
            return null;
        }
        if (read < 12) {
            return null;
        }

        if (isJpeg(header)) {
            return "jpg";
        }
        if (isPng(header)) {
            return "png";
        }
        if (isWebp(header)) {
            return "webp";
        }
        return null;
    }

    private boolean isJpeg(byte[] h) {
        return (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF;
    }

    private boolean isPng(byte[] h) {
        return (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G';
    }

    private boolean isWebp(byte[] h) {
        return h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
                && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P';
    }

    /** 读出宽高。读了失败(格式特殊)就返回 0,交由上层写 null,不影响上传成功。 */
    private int[] readDimensions(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                return new int[]{0, 0};
            }
            return new int[]{image.getWidth(), image.getHeight()};
        } catch (IOException e) {
            log.debug("读取图片尺寸失败:{}", path, e);
            return new int[]{0, 0};
        }
    }

    private PhotoVO toVO(EntryPhoto photo) {
        PhotoVO vo = new PhotoVO();
        vo.setId(photo.getId());
        vo.setUrl(photo.getUrl());
        vo.setThumbUrl(photo.getThumbUrl());
        vo.setWidth(photo.getWidth());
        vo.setHeight(photo.getHeight());
        vo.setSortOrder(photo.getSortOrder());
        return vo;
    }
}
