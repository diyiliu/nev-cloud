package com.tiza.api.controller;

import cn.com.tiza.tstar.datainterface.client.TStarSimpleClient;
import cn.com.tiza.tstar.datainterface.client.entity.ClientCmdSendResult;
import com.tiza.api.support.facade.InstructionJpa;
import com.tiza.api.support.facade.dto.Instruction;
import com.tiza.plugin.util.CommonUtil;
import com.tiza.plugin.util.DateUtil;
import com.tiza.plugin.util.JacksonUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

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
    public String toSend(@PathVariable Long id) throws Exception {
        Instruction instruction = instructionJpa.findById(id).get();

        if (instruction == null) {
            return "error";
        }

        int cmd = instruction.getCmdId();
        String vin = instruction.getTerminalId();
        String paramStr = instruction.getSendData();
        byte[] bytes = null;
        switch (cmd) {
            case 0x80:
                bytes = queryParam(paramStr);
                break;
            case 0x81:
                bytes = setParam(paramStr);
                break;
            case 0x82:
                bytes = remoteUpdate(paramStr);
                break;
            default:
                log.info("无效指令: {}", cmd);
        }

        if (bytes != null) {
            Date now = new Date();
            // 时间
            byte[] time = CommonUtil.dateToBytes(now);
            // 生成下发指令
            byte[] content = CommonUtil.gb32960Response(vin, Unpooled.copiedBuffer(time, bytes).array(), cmd, false);
            // TStar 指令下发
            ClientCmdSendResult sendResult = tStarClient.cmdSend(terminalType, vin, cmd, CommonUtil.getMsgSerial(), content, 1);

            if (sendResult.getIsSuccess()) {
                log.info("TSTAR 执行结果: [成功]!");
                instruction.setStatus(1);
            } else {
                log.info("TSTAR 执行结果: [失败]!");
                instruction.setStatus(4);
                instruction.setErrorCode(sendResult.getErrorCode());
            }

            String hms = DateUtil.dateToString(now, "%1$tH%1$tM%1$tS");
            instruction.setSerialNo(Integer.parseInt(hms));
            instructionJpa.save(instruction);
        }

        return "success";
    }

    /**
     * 远程升级
     *
     * @param content
     * @return
     */
    private byte[] remoteUpdate(String content) {
        byte[] bytes = content.getBytes();

        ByteBuf buf = Unpooled.buffer(1 + bytes.length);
        buf.writeByte(0x01);
        buf.writeBytes(bytes);
        return buf.array();
    }

    /**
     * 参数查询 (不包含0x84)
     *
     * @param content
     * @return
     */
    private byte[] queryParam(String content) {
        String[] paramIds = content.split(",");
        int length = paramIds.length;
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[i] = Byte.valueOf(paramIds[i]);
        }

        return bytes;
    }

    /**
     * 参数设置 (不包含0x84)
     *
     * @param content
     * @return
     */
    private byte[] setParam(String content) throws Exception {
        Map map = JacksonUtil.toObject(content, HashMap.class);

        ByteBuf buf = Unpooled.buffer();
        for (Iterator iterator = map.keySet().iterator(); iterator.hasNext(); ) {
            Object key = iterator.next();
            int id = Integer.parseInt(String.valueOf(key));
            String value = String.valueOf(map.get(key));

            byte[] bytes = CommonUtil.gb32960SetParam(id, value);
            buf.writeBytes(bytes);
        }

        byte[] byteArr = new byte[buf.writerIndex()];
        buf.getBytes(0, byteArr);

        return byteArr;
    }

}
