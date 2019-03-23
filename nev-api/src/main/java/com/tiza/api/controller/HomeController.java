package com.tiza.api.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;


/**
 * Description: HomeController
 * Author: DIYILIU
 * Update: 2019-03-20 16:58
 */

@Slf4j
@RestController
public class HomeController {

    @GetMapping
    public String index() {


        return "Hello, World!";
    }

    @PostMapping("/test")
    public String testPost(HttpServletRequest request) {

        return "ok";
    }
}
