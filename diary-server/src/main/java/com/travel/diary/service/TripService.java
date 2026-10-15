package com.travel.diary.service;

import com.travel.diary.common.PageResult;
import com.travel.diary.dto.TripRequest;
import com.travel.diary.entity.Trip;
import com.travel.diary.security.LoginUser;
import com.travel.diary.vo.TripDetailVO;
import com.travel.diary.vo.TripVO;

public interface TripService {

    PageResult<TripVO> list(LoginUser owner, Integer page, Integer size, Boolean includeArchived);

    TripDetailVO detail(Long id, LoginUser viewer);

    TripVO create(TripRequest request, LoginUser owner);

    TripVO update(Long id, TripRequest request, LoginUser editor);

    /** 删除行程:行程本身删除,名下的日记一并逻辑删除(不连坐图片文件,本地磁盘场景可接受)。 */
    void delete(Long id, LoginUser operator);

    /** 取实体并做读权限校验。 */
    Trip requireReadable(Long id, LoginUser viewer);

    /** 取实体并做写权限校验(仅作者本人或管理员)。 */
    Trip requireOwned(Long id, LoginUser editor);
}
