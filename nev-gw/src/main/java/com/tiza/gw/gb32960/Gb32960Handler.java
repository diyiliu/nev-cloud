package com.tiza.gw.gb32960;

import cn.com.tiza.tstar.common.entity.TStarData;
import cn.com.tiza.tstar.gateway.handler.BaseUserDefinedHandler;
import com.tiza.plugin.util.CommonUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;

/**
 * Description: Gb32960Handler
 * Author: Wangw
 * Update: 2019-03-18 11:41
 */

public class Gb32960Handler extends BaseUserDefinedHandler {

    /**
     * 0: 平台转发数据; 1: 车载终端数据
     **/
    private final static int ORIGIN_FROM = 0;

    public TStarData handleRecvMessage(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf) {
        byte[] msgBody = new byte[byteBuf.readableBytes()];
        byteBuf.getBytes(0, msgBody);

        // 协议头
        byteBuf.readShort();
        // 命令标识
        int cmd = byteBuf.readUnsignedByte();
        // 应答标识
        int resp = byteBuf.readUnsignedByte();

        // VIN码
        byte[] vinArray = new byte[17];
        byteBuf.readBytes(vinArray);
        String vin = new String(vinArray);

        // 加密方式
        byteBuf.readByte();
        // 数据单元长度
        int length = byteBuf.readUnsignedShort();
        // 指令内容
        byte[] bytes = new byte[length];
        byteBuf.readBytes(bytes);

        TStarData tStarData = new TStarData();
        tStarData.setMsgBody(msgBody);
        tStarData.setCmdID(cmd);
        tStarData.setTerminalID(vin);
        tStarData.setTime(System.currentTimeMillis());

        // 需要应答
        if (resp == 0xFE) {
            doResponse(channelHandlerContext, vin, cmd, bytes);
        }

        return tStarData;
    }

    /**
     * 指令应答
     *
     * @param ctx
     * @param terminal
     * @param cmd
     * @param bytes
     */
    private void doResponse(ChannelHandlerContext ctx, String terminal, int cmd, byte[] bytes) {
        // 平台数据
        if (ORIGIN_FROM == 0 && bytes.length > 5) {
            byte[] dateArray = new byte[6];
            System.arraycopy(bytes, 0, dateArray, 0, 6);
            bytes = dateArray;
        }

        // 应答内容
        byte[] content = packResp(terminal, cmd, bytes);

        TStarData respData = new TStarData();
        respData.setTerminalID(terminal);
        respData.setCmdID(cmd);
        respData.setMsgBody(content);
        respData.setTime(System.currentTimeMillis());
        ctx.channel().writeAndFlush(respData);
    }

    /**
     * 生成应答数据
     *
     * @param terminal
     * @param cmd
     * @param bytes
     * @return
     */
    private byte[] packResp(String terminal, int cmd, byte[] bytes) {
        int length = bytes.length;
        ByteBuf buf = Unpooled.buffer(25 + length);
        buf.writeByte(0x23);
        buf.writeByte(0x23);
        buf.writeByte(cmd);
        buf.writeByte(0x01);
        // VIN
        buf.writeBytes(terminal.getBytes());
        // 不加密
        buf.writeByte(0x01);
        buf.writeShort(length);
        // 返回数据
        buf.writeBytes(bytes);

        // 获取校验位
        byte[] content = new byte[22 + length];
        buf.getBytes(2, content);
        int check = CommonUtil.getCheck(content);
        buf.writeByte(check);

        return buf.array();
    }
}
