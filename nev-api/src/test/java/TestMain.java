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

    @Test
    public void test4(){
        String str = "132|1,2";

        System.out.println(str.split("\\|")[0]);
    }

    @Test
    public void test5(){
        String str = "123";

        String str2 = new String(CommonUtil.str2Bytes(str, 17));
        System.out.println(str2 + "==");
        System.out.println(str.equals(str2));
    }

    @Test
    public void test6(){
        String value = "127.0.0.1";
        byte[] bytes = CommonUtil.str2Bytes(value.length() < 32 ? value + ";" : value, 32);

        System.out.println(CommonUtil.bytesToStr(bytes));

        value = new String(bytes);
        System.out.println(value + ":" + value.length());

        value = value.trim();
        System.out.println(value + ":" + value.length());
    }

    @Test
    public void test7(){

        String str = "0200\t0070224728\t66200\t64696464990\t064696464990\t2019-03-26 19:56:55\t2019-03-26 19:56:54\t1\t1\t1\t103.660920\t30.974106\t103.663032\t30.971570\t四川省\t成都市\t都江堰市\t510000\t510100\t510181\t0\t0.0\t133966.70\t\t591.000\t低速怠速调速器/无请求\t0\t6\t82\t\t248\t71274.0\t87.2\t\t\t\t\t\t\t\t\t0\t0\t0\t0\t0\t0\t0\t0\t0\t0\t1\t1\t0\t0\t0\t0\t0\t0\t1\t\t\t\t\t\t\t ";
        String[] strArr = str.split("\t");

        System.out.println(strArr.length);
        System.out.println(strArr[25]);
    }
}
