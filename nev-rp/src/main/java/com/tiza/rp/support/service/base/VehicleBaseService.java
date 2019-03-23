package com.tiza.rp.support.service.base;

import com.tiza.plugin.bean.VehicleInfo;
import com.tiza.plugin.model.facade.IExecutor;
import redis.clients.jedis.Jedis;

import java.util.Map;

/**
 * Description: VehicleBaseService
 * Author: DIYILIU
 * Update: 2019-03-21 13:56
 */
public class VehicleBaseService implements IExecutor {

    private VehicleInfo vehicleInfo;

    private Jedis jedis;

    private Map dataMap;


    @Override
    public void run() {

    }

    public VehicleInfo getVehicleInfo() {
        return vehicleInfo;
    }

    public void setVehicleInfo(VehicleInfo vehicleInfo) {
        this.vehicleInfo = vehicleInfo;
    }

    public Jedis getJedis() {
        return jedis;
    }

    public void setJedis(Jedis jedis) {
        this.jedis = jedis;
    }

    public Map getDataMap() {
        return dataMap;
    }

    public void setDataMap(Map dataMap) {
        this.dataMap = dataMap;
    }
}
