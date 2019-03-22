package com.tiza.gw.gb32960;

import cn.com.tiza.tstar.common.entity.TStarData;
import cn.com.tiza.tstar.gateway.handler.BaseUserDefinedHandler;
import com.tiza.plugin.util.CommonUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;

import java.util.Date;

/**
 * Description: Gb32960Handler
 * Author: Wangw
 * Update: 2019-03-18 11:41
 */

public class Gb32960Handler extends BaseUserDefinedHandler {

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

        TStarData tStarData = new TStarData();
        tStarData.setMsgBody(msgBody);
        tStarData.setCmdID(cmd);
        tStarData.setTerminalID(vin);
        tStarData.setTime(System.currentTimeMillis());

        // 需要应答
        if (resp == 0xFE) {
            doResponse(channelHandlerContext, tStarData, length);
        }

        return tStarData;
    }

    /**
     * 指令应答
     *
     * @param ctx
     * @param tStarData
     * @param length
     */
    private void doResponse(ChannelHandlerContext ctx, TStarData tStarData, int length) {
        byte[] bytes = packResp(tStarData, length > 0 ? true : false);

        TStarData respData = new TStarData();
        respData.setTerminalID(tStarData.getTerminalID());
        respData.setCmdID(tStarData.getCmdID());
        respData.setMsgBody(bytes);
        respData.setTime(System.currentTimeMillis());
        ctx.channel().writeAndFlush(respData);
    }


    private byte[] packResp(TStarData tStarData, boolean dateFlag) {
        int cmd = tStarData.getCmdID();

        int length = dateFlag ? 31 : 25;
        ByteBuf buf = Unpooled.buffer(length);
        buf.writeByte(0x23);
        buf.writeByte(0x23);
        buf.writeByte(cmd);
        buf.writeByte(0x01);
        // VIN
        buf.writeBytes(tStarData.getTerminalID().getBytes());
        // 不加密
        buf.writeByte(0x01);
        buf.writeShort(dateFlag ? 6 : 0);
        // 是否包含时间
        if (dateFlag) {

            // 时间
            byte[] dateArray = CommonUtil.dateToBytes(new Date());
            buf.writeBytes(dateArray);
        }
        // 获取校验位
        byte[] content = new byte[length - 3];
        buf.getBytes(2, content);
        int check = CommonUtil.getCheck(content);
        buf.writeByte(check);
        return buf.array();
    }
}
