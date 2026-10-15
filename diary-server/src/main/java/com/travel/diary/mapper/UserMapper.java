package com.travel.diary.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.travel.diary.entity.User;

/**
 * 用户。全部走 BaseMapper 的通用方法,没有自定义 SQL。
 */
public interface UserMapper extends BaseMapper<User> {
}
