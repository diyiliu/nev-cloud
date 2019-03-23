package com.tiza.rp.support.task;

import com.tiza.plugin.bean.VehicleInfo;
import com.tiza.plugin.cache.ICache;
import com.tiza.plugin.model.facade.ITask;
import com.tiza.plugin.util.CommonUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Description: VehicleInfoTask
 * Author: DIYILIU
 * Update: 2018-01-30 11:07
 */

@Slf4j
@Service
public class VehicleInfoTask implements ITask {

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private ICache vehicleInfoProvider;

    @Scheduled(fixedRate = 3 * 60 * 1000, initialDelay = 2 * 1000)
    public void execute() {
        log.info("刷新车辆列表 ...");

        String sql = "SELECT t.id, t.vin, t.iccid, t.license FROM bs_vehicle t";

        List<VehicleInfo> vehicleInfos = jdbcTemplate.query(sql, new RowMapper<VehicleInfo>() {
            @Override
            public VehicleInfo mapRow(ResultSet rs, int rowNum) throws SQLException {
                VehicleInfo vehicleInfo = new VehicleInfo();
                vehicleInfo.setId(rs.getLong("id"));
                vehicleInfo.setTerminalId(rs.getString("vin"));
                vehicleInfo.setIccid(rs.getString("iccid"));
                vehicleInfo.setLicense(rs.getString("license"));

                return vehicleInfo;
            }
        });

        refresh(vehicleInfos, vehicleInfoProvider);
    }

    private void refresh(List<VehicleInfo> vehicleInfos, ICache vehicleCache) {
        if (vehicleInfos == null || vehicleInfos.size() < 1) {
            log.warn("无车辆信息！");
            return;
        }

        Set oldKeys = vehicleCache.getKeys();
        Set tempKeys = new HashSet(vehicleInfos.size());

        for (VehicleInfo vehicle : vehicleInfos) {
            vehicleCache.put(vehicle.getTerminalId(), vehicle);
            tempKeys.add(vehicle.getTerminalId());
        }
        CommonUtil.refreshCache(oldKeys, tempKeys, vehicleCache);
    }
}
