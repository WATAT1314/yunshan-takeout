package com.yunshan.service;

import com.yunshan.dto.UserLoginDTO;
import com.yunshan.entity.User;

public interface UserService {

    /**
     * 微信登录
     * @param userLoginDTO
     * @return
     */
    User wxLogin(UserLoginDTO userLoginDTO);
}
