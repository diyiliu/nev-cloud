package com.tiza.api.controller;

import cn.com.tiza.tstar.datainterface.client.TStarSimpleClient;
import cn.com.tiza.tstar.datainterface.client.entity.ClientCmdSendResult;
import com.tiza.plugin.util.CommonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;


/**
 * Description: HomeController
 * Author: DIYILIU
 * Update: 2019-03-20 16:58
 */

@Slf4j
@RestController
public class HomeController {

    @Resource
    private TStarSimpleClient tStarClient;

    @GetMapping
    public String index(HttpServletRequest request) throws Exception {
        String terminal = "00000000000000000";
        String terminalType = "trash_gb32960";

        String str = "23230701303030303030303030303030303030303001000037";
        byte[] bytes = CommonUtil.hexStringToBytes(str);

        // TStar 指令下发
        ClientCmdSendResult sendResult = tStarClient.cmdSend(terminalType, terminal, 0x07, 1, bytes, 1);
        log.info("TSTAR 执行结果: [{}]", sendResult.getIsSuccess() ? "成功" : "失败");

        return "Hello, World!";
    }

    @PostMapping("/test")
    public String testPost(HttpServletRequest request) {

        return "ok";
    }
}
