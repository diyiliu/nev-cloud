package com.tiza.rp.support.config;

import cn.com.tiza.tstar.common.utils.JedisUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.annotation.Resource;

/**
 * Description: NevConfig
 * Author: DIYILIU
 * Update: 2019-03-22 16:23
 */

@Configuration
public class NevConfig {

    @Resource
    private Environment environment;

    @Bean
    public JedisUtil jedisUtil() {
        String host = environment.getProperty("redis.host");
        int port = environment.getProperty("redis.port", Integer.class);
        int database = environment.getProperty("redis.database", Integer.class);
        String password = environment.getProperty("redis.password");
        String pwd = StringUtils.isEmpty(password) ? null : password;

        return new JedisUtil(host, port, database, pwd);
    }
}
