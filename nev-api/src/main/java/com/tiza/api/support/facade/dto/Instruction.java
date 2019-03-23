package com.tiza.api.support.facade.dto;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Date;

/**
 * Description: Instruction
 * Author: DIYILIU
 * Update: 2019-03-22 17:50
 */

@Data
@Entity
@Table(name = "BS_INSTRUCTIONLOG")
public class Instruction {

    @Id
    private Long id;

    private Long vehicleId;

    private String terminalId;

    private Integer cmdId;

    private String type;

    private String sendData;

    private Date sendTime;

    private String responseData;

    private Date responseTime;

    private Integer errorCode;

    private Integer serialNo;

    @Column(name = "SENDSTATUS")
    private Integer status;
}
