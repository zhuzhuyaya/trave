package com.travel.diary.controller;

import com.travel.diary.common.R;
import com.travel.diary.security.RequireLogin;
import com.travel.diary.service.NominatimService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 在线地名检索代理:/api/geo/search?q=… → NominatimService。
 *
 * <p>挂 RequireLogin:与业务 API 一致,检索是给登录用户写日记用的;
 * 同时挡住匿名滥用把整个部署共享的 1 req/s 上游配额烧光。
 * 失败(上游挂/超时)不抛错,返回空列表 —— 前端本地搜索照常可用,
 * 在线这路是增量,不能因为它把搜索框拖垮。
 */
@RestController
@RequestMapping("/api/geo")
@RequiredArgsConstructor
public class GeoController {

    private final NominatimService nominatimService;

    @GetMapping("/search")
    @RequireLogin
    public R<List<NominatimService.GeoHit>> search(
            @RequestParam String q,
            @RequestParam(required = false) Double west,
            @RequestParam(required = false) Double north,
            @RequestParam(required = false) Double east,
            @RequestParam(required = false) Double south) {
        // 四角齐了才算有效视野;缺一个就无界搜(重名时优先级差些,但结果照样有)
        Double[] viewbox = west != null && north != null && east != null && south != null
                ? new Double[]{west, north, east, south}
                : null;
        return R.ok(nominatimService.search(q, viewbox));
    }
}
