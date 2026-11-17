package com.example.securityagent.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class SpaForwardController {

    @RequestMapping({
        "/",
        "/login",
        "/register",
        "/resident/**",
        "/guard/**",
        "/admin/**",
        "/monitor/**",
        "/human-security/**",
        "/common/**"
    })
    public String forward() {
        return "forward:/index.html";
    }
}