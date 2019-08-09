package com.company.oasis.service.impl;

import com.company.framework.annotation.Service;
import com.company.oasis.service.iservice.UserService;
@Service("userService")
public class UserServiceImpl implements UserService {
    @Override
    public String getUsername(String username) {
        System.out.println(username.toUpperCase());
        return username.toUpperCase();
    }
}
