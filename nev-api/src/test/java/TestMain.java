import com.tiza.plugin.util.CommonUtil;
import com.tiza.plugin.util.DateUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import java.util.Date;

/**
 * Description: TestMain
 * Author: DIYILIU
 * Update: 2019-03-22 19:05
 */
public class TestMain {

    @Test
    public void test(){

        System.out.println(CommonUtil.bytesToStr("123".getBytes()));
    }


    @Test
    public void test1(){

        String str = DateUtil.dateToString(new Date(), "%1$tH%1$tM%1$tS");
        System.out.println(str);
        System.out.println(Long.MAX_VALUE - str.hashCode());
    }

    @Test
    public void test2(){

        ByteBuf buf = Unpooled.buffer();
        buf.writeBytes(new byte[2]);
        buf.writeBytes(new byte[12]);

        int length = buf.writerIndex();
        System.out.println(length);

        byte[] bytes = new byte[length];
        buf.getBytes(0, bytes);

        System.out.println(CommonUtil.bytesToStr(bytes));
    }

    @Test
    public void test3(){
        String str = "123456";

        byte[] bytes = CommonUtil.longToBytes(Long.valueOf(str), 2);

    }
}
