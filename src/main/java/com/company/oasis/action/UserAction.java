package com.company.oasis.action;

import com.company.framework.annotation.Autowired;
import com.company.framework.annotation.Controller;
import com.company.framework.annotation.RequestMapping;
import com.company.oasis.service.iservice.UserService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Controller
@RequestMapping("user")
public class UserAction {
    @Autowired
    UserService userService;

    @RequestMapping("welcome.do")
    public void welcome(String username, HttpServletRequest request, HttpServletResponse response) {
        System.out.println("Welcome " + userService.getUsername(username));
    }
}
