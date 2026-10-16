package com.travel.diary.controller;

import com.travel.diary.common.PageResult;
import com.travel.diary.common.R;
import com.travel.diary.dto.TripRequest;
import com.travel.diary.security.RequireLogin;
import com.travel.diary.security.UserContext;
import com.travel.diary.service.TripService;
import com.travel.diary.vo.TripDetailVO;
import com.travel.diary.vo.TripVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripService tripService;

    @GetMapping
    @RequireLogin
    public R<PageResult<TripVO>> list(@RequestParam(required = false) Integer page,
                                      @RequestParam(required = false) Integer size,
                                      @RequestParam(required = false) Boolean includeArchived) {
        return R.ok(tripService.list(UserContext.require(), page, size, includeArchived));
    }

    @GetMapping("/{id}")
    @RequireLogin
    public R<TripDetailVO> detail(@PathVariable Long id) {
        return R.ok(tripService.detail(id, UserContext.require()));
    }

    @PostMapping
    @RequireLogin
    public R<TripVO> create(@Valid @RequestBody TripRequest request) {
        return R.ok(tripService.create(request, UserContext.require()));
    }

    @PutMapping("/{id}")
    @RequireLogin
    public R<TripVO> update(@PathVariable Long id, @Valid @RequestBody TripRequest request) {
        return R.ok(tripService.update(id, request, UserContext.require()));
    }

    @DeleteMapping("/{id}")
    @RequireLogin
    public R<Void> delete(@PathVariable Long id) {
        tripService.delete(id, UserContext.require());
        return R.ok();
    }
}
