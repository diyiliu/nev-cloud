package com.tiza.rp.support.parse;

import cn.com.tiza.tstar.common.process.BaseHandle;
import cn.com.tiza.tstar.common.process.RPTuple;
import com.tiza.plugin.bean.VehicleInfo;
import com.tiza.plugin.cache.ICache;
import com.tiza.plugin.model.DeviceData;
import com.tiza.plugin.model.Position;
import com.tiza.plugin.model.adapter.DataParseAdapter;
import com.tiza.plugin.util.DateUtil;
import com.tiza.plugin.util.JacksonUtil;
import com.tiza.rp.support.config.NevConstant;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;

import javax.annotation.Resource;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Description: NevDataParse
 * Author: DIYILIU
 * Update: 2019-03-22 16:21
 */

@Slf4j
@Service
public class NevDataParse extends DataParseAdapter {

    /**
     * 任务调度器
     **/
    private ExecutorService executorService = Executors.newFixedThreadPool(2);

    @Resource
    private ICache vehicleInfoProvider;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Value("${}")
    private String vehicleTrackTopic;

    @Value("${}")
    private String vehicleMoveChannel;

    @Override
    public void dealWithTStar(DeviceData deviceData, BaseHandle handle) {
        List<Map> paramValues = (List<Map>) deviceData.getDataBody();

        int cmd = deviceData.getCmdId();
        String vin = deviceData.getDeviceId();
        if (!vehicleInfoProvider.containsKey(vin)) {
            log.warn("[{}] 车辆列表不存在!", vin);
            return;
        }
        VehicleInfo vehicleInfo = (VehicleInfo) vehicleInfoProvider.get(vin);

        Map kafkaMap = new HashMap();

        Date gpsTime = null;
        Double speed = null;
        List list = new ArrayList();
        StringBuilder str = new StringBuilder("update BS_VEHICLEGPSINFO set ");
        for (int i = 0; i < paramValues.size(); i++) {
            Map map = paramValues.get(i);
            for (Iterator iterator = map.keySet().iterator(); iterator.hasNext(); ) {
                String key = (String) iterator.next();
                Object value = map.get(key);

                if (key.equalsIgnoreCase("GpsTime")) {
                    gpsTime = (Date) value;
                    vehicleInfo.setDatetime(gpsTime.getTime());
                }

                if (key.equalsIgnoreCase("Speed")) {
                    speed = (Double) value;
                }

                if (key.equalsIgnoreCase("VehicleStatus")) {
                    vehicleInfo.setStatus((Integer) value);
                }

                if (key.equalsIgnoreCase("position")) {
                    Position position = (Position) value;
                    position.setTime(gpsTime.getTime());
                    position.setSpeed(speed);

                    str.append("LocationStatus").append("=?, ");
                    list.add(position.getStatus());
                    kafkaMap.put("LocationStatus", position.getStatus());

                    // 有效定位
                    if (position.getStatus() == 0) {

                        str.append("WGS84LAT").append("=?, ");
                        str.append("WGS84LNG").append("=?, ");
                        str.append("GCJ02LAT").append("=?, ");
                        str.append("GCJ02LNG").append("=?, ");
                        list.add(position.getLat());
                        list.add(position.getLng());
                        list.add(position.getEnLat());
                        list.add(position.getEnLng());

                        kafkaMap.put("WGS84LAT", position.getLat());
                        kafkaMap.put("WGS84LNG", position.getLng());
                        kafkaMap.put("GCJ02LAT", position.getEnLat());
                        kafkaMap.put("GCJ02LNG", position.getEnLng());

                        if (StringUtils.isNotEmpty(position.getProvince())) {
                            str.append("PROVINCE").append("=?, ");
                            str.append("CITY").append("=?, ");
                            str.append("DISTRICT").append("=?, ");
                            list.add(position.getProvince());
                            list.add(position.getCity());
                            list.add(position.getArea());

                            kafkaMap.put("PROVINCE", position.getProvince());
                            kafkaMap.put("CITY", position.getCity());
                            kafkaMap.put("DISTRICT", position.getArea());
                        }

                        // 发布redis
                        if (0x02 == cmd) {
                            toRedis(vehicleInfo, position, handle.getJedis());
                        }
                    }
                    continue;
                }
                str.append(key).append("=?, ");
                list.add(formatValue(value));
            }

            if (!map.containsKey("position")) {
                kafkaMap.putAll(map);
            }
        }

        // 写入kafka
        toKafka(deviceData, vehicleInfo, kafkaMap, handle);

        // 更新当前位置信息
        if (0x02 == cmd) {
            String sql = str.substring(0, str.length() - 2) + " where VEHICLEID=" + vehicleInfo.getId();
            jdbcTemplate.update(sql, list.toArray());
        }
    }

    @Override
    public void dealData(DeviceData deviceData, Map param, String type) {


    }


    private void toKafka(DeviceData deviceData, VehicleInfo vehicle, Map paramValues, BaseHandle handle) {
        paramValues.put(NevConstant.Location.VEHICLE_ID, vehicle.getId());

        RPTuple rpTuple = new RPTuple();
        rpTuple.setCmdID(deviceData.getCmdId());
        rpTuple.setTerminalID(String.valueOf(vehicle.getId()));

        String msgBody = JacksonUtil.toJson(paramValues);
        rpTuple.setMsgBody(msgBody.getBytes(Charset.forName(NevConstant.JSON_CHARSET)));
        rpTuple.setTime(vehicle.getDatetime());

        log.info("终端[{}]写入Kafka位置信息...", deviceData.getDeviceId());
        handle.storeInKafka(rpTuple, vehicleTrackTopic);
    }

    private void toRedis(VehicleInfo vehicle, Position position, Jedis jedis) {
        Map posMap = new HashMap();
        posMap.put(NevConstant.Location.GPS_TIME, DateUtil.dateToString(new Date(position.getTime())));
        posMap.put(NevConstant.Location.LAT, position.getEnLat());
        posMap.put(NevConstant.Location.LNG, position.getEnLng());
        posMap.put(NevConstant.Location.SPEED, position.getSpeed());
        posMap.put(NevConstant.Location.VEHICLE_ID, vehicle.getId());

        posMap.put(NevConstant.Location.STATUS, vehicle.getStatus() == null ? "" : vehicle.getStatus());


        log.info("车辆[{}]发布Redis位置信息...", vehicle.getId());
        try {
            jedis.publish(vehicleMoveChannel, JacksonUtil.toJson(posMap));
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (jedis != null) {
                jedis.close();
            }
        }
    }

    public Object formatValue(Object obj) {
        if (obj instanceof Map ||
                obj instanceof Collection) {

            return JacksonUtil.toJson(obj);
        }

        return obj;
    }
}
