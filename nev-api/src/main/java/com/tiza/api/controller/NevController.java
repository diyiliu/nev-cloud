package com.tiza.api.controller;

import cn.com.tiza.tstar.datainterface.client.TStarSimpleClient;
import cn.com.tiza.tstar.datainterface.client.entity.ClientCmdSendResult;
import com.tiza.api.support.facade.InstructionJpa;
import com.tiza.api.support.facade.dto.Instruction;
import com.tiza.plugin.util.CommonUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Date;

/**
 * Description: NevController
 * Author: DIYILIU
 * Update: 2019-03-22 17:41
 */

@Slf4j
@RestController
public class NevController {

    @Resource
    private InstructionJpa instructionJpa;

    @Resource
    private TStarSimpleClient tStarClient;

    @Value("${tstar.terminal-type}")
    private String terminalType;

    @GetMapping("/send/{id}")
    public String toSend(@PathVariable Long id) throws Exception{
        Instruction instruction = instructionJpa.findById(id);

        if (instruction == null) {
            return "error";
        }

        int cmd = instruction.getCmdId();
        String vin = instruction.getTerminalId();

        byte[] content = null;
        switch (cmd) {
            case 0x82:
              content = remoteUpdate(instruction.getSendData());
                break;
            default:
                log.info("无效指令: {}", cmd);
        }

        if (content != null){
            // 生成下发指令
            byte[] bytes = CommonUtil.gb32960Response(vin, content, cmd, false);

            // TStar 指令下发
            ClientCmdSendResult sendResult = tStarClient.cmdSend(terminalType, vin, cmd, CommonUtil.getMsgSerial(), bytes, 1);
            if (sendResult.getIsSuccess()){
                log.info("TSTAR 执行结果: [成功]!");
                instruction.setStatus(1);
            }else {
                log.info("TSTAR 执行结果: [失败]!");
                instruction.setStatus(4);
                instruction.setErrorCode(sendResult.getErrorCode());
            }

            instructionJpa.save(instruction);
        }

        return "success";
    }

    private byte[]  remoteUpdate(String content) {
        byte[] bytes = content.getBytes();

        byte[] time = CommonUtil.dateToBytes(new Date());
        ByteBuf buf = Unpooled.buffer(7 + bytes.length);
        buf.writeBytes(time);
        buf.writeByte(0x01);
        buf.writeBytes(bytes);

        return  buf.array();
    }
}
