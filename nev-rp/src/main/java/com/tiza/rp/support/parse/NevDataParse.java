package com.tiza.rp.support.parse;

import cn.com.tiza.tstar.common.process.BaseHandle;
import cn.com.tiza.tstar.common.process.RPTuple;
import cn.com.tiza.tstar.common.utils.JedisUtil;
import com.tiza.plugin.bean.VehicleInfo;
import com.tiza.plugin.cache.ICache;
import com.tiza.plugin.model.DeviceData;
import com.tiza.plugin.model.Position;
import com.tiza.plugin.model.adapter.DataParseAdapter;
import com.tiza.plugin.util.DateUtil;
import com.tiza.plugin.util.JacksonUtil;
import com.tiza.rp.support.config.NevConstant;
import com.tiza.rp.support.service.CurrentStatusService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
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

    @Resource
    private JedisUtil jedisUtil;

    @Value("${kafka.vehicle-track}")
    private String vehicleTrackTopic;

    @Value("${redis.vehicle-move}")
    private String vehicleMoveChannel;

    @Value("${redis.vehicle-event}")
    private String vehicleEventChannel;

    @Override
    public void detach(DeviceData deviceData) {
        VehicleInfo vehicleInfo = (VehicleInfo) vehicleInfoProvider.get(deviceData.getDeviceId());
        String terminal = vehicleInfo.getTerminalId();

        if ("cmdResp".equals(deviceData.getDataType())) {
            int cmd = deviceData.getCmdId();
            int resp = deviceData.getDataStatus();
            String hms = DateUtil.dateToString(new Date(deviceData.getTime()), "%1$tH%1$tM%1$tS");

            String sql = "UPDATE bs_instructionlog t" +
                    "   SET t.responsetime = ?, t.responsedata = ?, t.sendstatus = ?" +
                    " WHERE t.terminalid = ?" +
                    "   AND t.cmdid = ?" +
                    "   AND t.serialno = ?";

            // 3成功;4失败
            int status = 4;
            String respData = null;
            if (resp == 1) {
                status = 3;
                Object object = deviceData.getDataBody();
                if (object != null) {
                    respData = JacksonUtil.toJson(object);
                }
            }

            Object[] param = new Object[]{new Date(), respData, status, terminal, cmd, hms};
            jdbcTemplate.update(sql, param);

            log.info("指令应答[{}, {}, {}, {}]", vehicleInfo.getTerminalId(), cmd, hms, respData);
            return;
        }

        // 车辆实时状态处理
        Map realMode = (Map) deviceData.getDataBody();
        if (MapUtils.isEmpty(realMode)) {
            return;
        }

        CurrentStatusService statusService = new CurrentStatusService();
        statusService.setRedisChannel(vehicleEventChannel);
        statusService.setJedis(jedisUtil.getJedis());
        statusService.setDataMap(realMode);
        statusService.setVehicleInfo(vehicleInfo);
        executorService.execute(statusService);
    }

    @Override
    public void dealWithTStar(DeviceData deviceData, BaseHandle handle) {
        int cmd = deviceData.getCmdId();
        VehicleInfo vehicleInfo = (VehicleInfo) vehicleInfoProvider.get(deviceData.getDeviceId());

        List<Map> paramValues = (List<Map>) deviceData.getDataBody();
        if (CollectionUtils.isEmpty(paramValues)) {

            return;
        }

        Map kafkaMap = new HashMap();
        List list = new ArrayList();

        Date gpsTime = null;
        Double speed = null;
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
                            Jedis jedis = jedisUtil.getJedis();
                            try {
                                toRedis(vehicleInfo, position, jedis);
                            } finally {
                                if (jedis != null) {
                                    jedis.close();
                                }
                            }
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

    /**
     * 写入 Kafka
     *
     * @param deviceData
     * @param vehicle
     * @param paramValues
     * @param handle
     */
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

    /**
     * 发布 Redis
     *
     * @param vehicle
     * @param position
     * @param jedis
     */
    private void toRedis(VehicleInfo vehicle, Position position, Jedis jedis) {
        Map posMap = new HashMap();
        posMap.put(NevConstant.Location.GPS_TIME, DateUtil.dateToString(new Date(position.getTime())));
        posMap.put(NevConstant.Location.LAT, position.getEnLat());
        posMap.put(NevConstant.Location.LNG, position.getEnLng());
        posMap.put(NevConstant.Location.SPEED, position.getSpeed());
        posMap.put(NevConstant.Location.VEHICLE_ID, vehicle.getId());

        posMap.put(NevConstant.Location.STATUS, vehicle.getStatus() == null ? "" : vehicle.getStatus());

        log.info("车辆[{}]发布Redis位置信息...", vehicle.getId());
        jedis.publish(vehicleMoveChannel, JacksonUtil.toJson(posMap));
    }

    public Object formatValue(Object obj) {
        if (obj instanceof Map || obj instanceof Collection) {

            return JacksonUtil.toJson(obj);
        }

        return obj;
    }
}
