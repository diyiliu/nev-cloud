package com.tiza.api.support.config;

import cn.com.tiza.tstar.datainterface.client.TStarSimpleClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.annotation.Resource;

/**
 * Description: SpringConfig
 * Author: DIYILIU
 * Update: 2019-03-22 16:08
 */

@Configuration
public class SpringConfig {

    @Resource
    private Environment environment;

    @Bean
    public TStarSimpleClient tStarClient() throws Exception{
        String username = environment.getProperty("tstar.username");
        String password = environment.getProperty("tstar.password");

        return new TStarSimpleClient(username, password);
    }
}
