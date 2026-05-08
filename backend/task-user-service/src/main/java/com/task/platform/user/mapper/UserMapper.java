package com.task.platform.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.task.platform.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 根据手机号查询用户
     * 注意：手机号已建唯一索引，结果最多1条
     */
    default User selectByPhone(String phone) {
        return selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getPhone, phone));
    }

    /**
     * 根据邀请码查询用户
     */
    default User selectByInviteCode(String inviteCode) {
        return selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getInviteCode, inviteCode));
    }
}
