package com.tiza.rp.handler;

import cn.com.tiza.earth4j.LocationParser;
import cn.com.tiza.tstar.common.process.BaseHandle;
import cn.com.tiza.tstar.common.process.RPTuple;
import com.tiza.plugin.cache.ICache;
import com.tiza.plugin.model.Gb32960Header;
import com.tiza.plugin.protocol.gb32960.Gb32960DataProcess;
import com.tiza.plugin.util.CommonUtil;
import com.tiza.plugin.util.JacksonUtil;
import com.tiza.plugin.util.SpringUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * Description: Gb32960ParseHandler
 * Author: DIYILIU
 * Update: 2018-12-06 10:18
 */

@Slf4j
public class Gb32960ParseHandler extends BaseHandle {

    @Override
    public RPTuple handle(RPTuple rpTuple) throws Exception {
        log.info("终端[{}], 指令[{}]...", rpTuple.getTerminalID(), CommonUtil.toHex(rpTuple.getCmdID(), 4));

        ICache cmdCacheProvider = SpringUtil.getBean("cmdCacheProvider");
        Gb32960DataProcess process = (Gb32960DataProcess) cmdCacheProvider.get(rpTuple.getCmdID());
        if (process == null) {
            log.warn("CMD {} ,不到指令[{}]解析器!", JacksonUtil.toJson(cmdCacheProvider.getKeys()), CommonUtil.toHex(rpTuple.getCmdID(), 4));
            return null;
        }

        // 解析消息头
        Gb32960Header header = (Gb32960Header) process.parseHeader(rpTuple.getMsgBody());
        if (header == null) {

            return null;
        }

        String terminalId = header.getTerminalId();
        ICache vehicleInfoProvider = SpringUtil.getBean("vehicleInfoProvider");

        //log.info("设备缓存: {}", JacksonUtil.toJson(vehicleInfoProvider.getKeys()));
        // 验证设备是否绑定车辆
        if (!vehicleInfoProvider.containsKey(terminalId)) {
            log.warn("设备[{}]未绑定车辆信息!", terminalId);

            return null;
        }

        header.setGwTime(rpTuple.getTime());
        // 指令解析
        process.parse(header.getContent(), header);

        return rpTuple;
    }

    @Override
    public void init() throws Exception {
        // 加载地图数据，解析省市区
        LocationParser.getInstance().init();

        // 装载 Spring 容器
        SpringUtil.init();
    }
}
