package com.tiza.rp.support.service.base;

import com.tiza.plugin.bean.VehicleInfo;
import com.tiza.plugin.model.facade.IExecutor;

/**
 * Description: VehicleBaseService
 * Author: DIYILIU
 * Update: 2019-03-21 13:56
 */
public class VehicleBaseService implements IExecutor {

    private VehicleInfo vehicleInfo;

    @Override
    public void run() {

    }

    public VehicleInfo getVehicleInfo() {
        return vehicleInfo;
    }

    public void setVehicleInfo(VehicleInfo vehicleInfo) {
        this.vehicleInfo = vehicleInfo;
    }
}
