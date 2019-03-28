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
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 指令下发接口
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
                if (paramStr.startsWith("132|") || paramStr.startsWith("130|")) {
                    bytes = queryExtra(paramStr);
                } else {
                    bytes = queryParam(paramStr);
                }

                break;
            case 0x81:
                if (paramStr.startsWith("132|") || paramStr.startsWith("130|")) {
                    bytes = setExtra(paramStr);
                } else {
                    bytes = setParam(paramStr);
                }
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
            log.info("设备[{}]原始指令下发: [{}]", vin, CommonUtil.bytesToStr(content));

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
            instruction.setSendTime(now);
            instructionJpa.save(instruction);
        }

        return "success";
    }

    /**
     * 远程升级 0x82
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
     * 参数查询 0x80 (不包含0x84)
     *
     * @param content
     * @return
     */
    private byte[] queryParam(String content) {
        String[] paramIds = content.split(",");
        int n = 0;
        ByteBuf buf = Unpooled.buffer();
        for (int i = 0; i < paramIds.length; i++) {
            int id = Integer.valueOf(paramIds[i]);
            if (0x05 == id || 0x0E == id) {
                buf.writeByte(id - 1);
                n++;
            }
            buf.writeByte(id);
            n++;
        }

        return combine(n, buf);
    }

    /**
     * 参数设置 0x81 (不包含0x84)
     *
     * @param content
     * @return
     */
    private byte[] setParam(String content) throws Exception {
        Map map = JacksonUtil.toObject(content, HashMap.class);

        int i = 0;
        ByteBuf buf = Unpooled.buffer();
        for (Iterator iterator = map.keySet().iterator(); iterator.hasNext(); ) {
            Object key = iterator.next();
            int id = Integer.parseInt(String.valueOf(key));
            String value = String.valueOf(map.get(key));
            byte[] bytes = CommonUtil.gb32960SetParam(id, value);
            buf.writeBytes(bytes);
            i++;
            if (0x05 == id || 0x0E == id) {
                i++;
            }
        }

        return combine(i, buf);
    }

    /**
     * 第一个字节 添加数量
     *
     * @param count
     * @param buf
     * @return
     */
    private byte[] combine(int count, ByteBuf buf) {
        int length = buf.writerIndex();
        byte[] bytes = new byte[length + 1];
        bytes[0] = (byte) count;

        buf.getBytes(0, bytes, 1, length);

        return bytes;
    }

    /**
     * 扩展协议查询
     *
     * @param content
     * @return
     */
    private byte[] queryExtra(String content) {
        // 0x84
        if (content.startsWith("132|")) {
            String[] strArr = content.split("\\|");
            int id = Integer.parseInt(strArr[0]);
            String params = strArr[1];

            String[] paramIds = params.split(",");
            int count = paramIds.length;
            ByteBuf buf = Unpooled.buffer(3 + count);
            buf.writeByte(1);
            buf.writeByte(id);
            buf.writeByte(count);
            for (String p : paramIds) {
                buf.writeByte(Integer.parseInt(p));
            }

            return buf.array();
        }

        return new byte[0];
    }

    /**
     * 扩展协议设置
     *
     * @param content
     * @return
     */
    private byte[] setExtra(String content) {
        // 0x84
        if (content.startsWith("132|")) {
            String[] strArr = content.split("\\|");
            int id = Integer.parseInt(strArr[0]);
            String params = strArr[1];

            ByteBuf buf = Unpooled.buffer();
            buf.writeByte(1);
            buf.writeByte(id);
            try {
                Map<String, Object> paramMap = JacksonUtil.toObject(params, HashMap.class);
                buf.writeByte(paramMap.size());
                for (Iterator<String> iterator = paramMap.keySet().iterator(); iterator.hasNext(); ) {
                    String key = iterator.next();
                    String value = String.valueOf(paramMap.get(key));

                    int option = Integer.valueOf(key);
                    buf.writeByte(option);
                    if (1 == option) {
                        buf.writeBytes(CommonUtil.str2Bytes(value, 17));
                    } else if (2 == option || 4 == option) {

                        buf.writeByte(Integer.valueOf(value));
                    } else if (3 == option || 5 == option) {
                        buf.writeInt(Integer.valueOf(value));
                    } else if (6 == option || 7 == option || 8 == option) {
                        String[] items = value.split(",");

                        buf.writeByte(Integer.valueOf(items[0]));
                        buf.writeShort(Integer.valueOf(items[1]));
                        String host = items[2];
                        buf.writeBytes(CommonUtil.str2Bytes(host.length() < 32 ? host + ";" : host, 32));
                    } else {
                        log.warn("0x84 选项[{}]内容未知!", option);
                    }
                }

                int length = buf.writerIndex();
                byte[] bytes = new byte[length];
                buf.getBytes(0, bytes);

                return bytes;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // 0x82 CAN 数据透传
        if (content.startsWith("130|")) {
            String[] strArr = content.split("\\|");
            int id = Integer.parseInt(strArr[0]);
            String params = strArr[1];

            String[] items = params.split(",");

            int count1 = Integer.valueOf(items[0]);
            int count2 = Integer.valueOf(items[1]);
            int len = Integer.valueOf(items[2]);
            // 帧ID
            String[] ids = items[3].split("-");

            ByteBuf buf = Unpooled.buffer(4 + len * ids.length);
            buf.writeByte(1);
            buf.writeByte(id);
            buf.writeByte(count1);
            buf.writeByte(count2);
            for (String str : ids) {
                buf.writeBytes(CommonUtil.hexStringToBytes(str));
            }

            return buf.array();
        }


        return new byte[0];
    }
}
