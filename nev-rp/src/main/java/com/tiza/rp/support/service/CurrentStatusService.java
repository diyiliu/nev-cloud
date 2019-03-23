package com.tiza.rp.support.service;

import com.tiza.plugin.bean.VehicleInfo;
import com.tiza.plugin.util.JacksonUtil;
import com.tiza.rp.support.config.NevConstant;
import com.tiza.rp.support.service.base.VehicleBaseService;
import redis.clients.jedis.Jedis;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Description: CurrentStatusService
 * Author: DIYILIU
 * Update: 2019-03-21 13:54
 */
public class CurrentStatusService extends VehicleBaseService {

    private String redisChannel;

    @Override
    public void run() {
        dealRealModel(getDataMap(), getVehicleInfo());
    }

    /**
     * 处理车辆实时状态
     *
     * @param modelMap
     * @param vehicleInfo
     */
    public void dealRealModel(Map modelMap, VehicleInfo vehicleInfo) {
        String prefix = "model:gb32960:";
        Jedis jedis = getJedis();
        try {
            for (Iterator iterator = modelMap.keySet().iterator(); iterator.hasNext(); ) {
                String key = (String) iterator.next();
                int value = (int) modelMap.get(key);

                String redisKey = prefix + key + vehicleInfo.getId();
                if (!jedis.exists(redisKey)) {

                    createMessage(key, value, vehicleInfo);
                    jedis.set(redisKey, String.valueOf(value));
                } else {
                    int last = Integer.valueOf(jedis.get(redisKey));

                    // 状态发生变化
                    if (value != last) {

                        createMessage(key, value, vehicleInfo);
                        jedis.set(redisKey, String.valueOf(value));
                    }
                }
            }
        } catch (NumberFormatException e) {
            e.printStackTrace();
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
    }

    public void createMessage(String key, int value, VehicleInfo vehicleInfo) {
        Map message = new HashMap();
        message.put("vehicleId", vehicleInfo.getId());
        message.put("license", vehicleInfo.getLicense());
        message.put("vin", vehicleInfo.getTerminalId());
        message.put("time", vehicleInfo.getDatetime());

        String category = "";
        String content = "";
        switch (key) {
            case NevConstant.RealMode.IN_OUT:
                category = "vehicle";
                if (1 == value) {
                    content = "车辆登入";
                } else if (0 == value) {
                    content = "车辆登出";
                }
                break;
            case NevConstant.RealMode.ON_OFF:
                category = "vehicle";
                if (1 == value) {
                    content = "车辆启动";
                } else if (2 == value) {
                    content = "车辆熄火";
                }
                break;
            case NevConstant.RealMode.ALARM_LEVEL:
                category = "fault";
                content = "车辆故障，当前故障等级为" + value + "级";
                break;
            case NevConstant.RealMode.TOP_OFF:
                category = "charge";
                if (1 == value) {
                    content = "停车充电";
                } else if (4 == value) {
                    content = "充电完成";
                }
                break;
            default:
                break;
        }
        message.put("category", category);
        message.put("message", content);

        // 消息发布到redis
        Jedis jedis = getJedis();
        try {
            jedis.publish(redisChannel, JacksonUtil.toJson(message));
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
    }

    public void setRedisChannel(String redisChannel) {
        this.redisChannel = redisChannel;
    }
}
