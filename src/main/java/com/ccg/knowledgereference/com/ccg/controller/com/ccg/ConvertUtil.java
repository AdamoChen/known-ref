package com.ccg.knowledgereference.com.ccg.controller.com.ccg;

import java.util.HashMap;
import java.util.Map;

/**
 * 转换工具类
 */
public class ConvertUtil {
    private static Map<Character, Integer> NUM_MAP = new HashMap<>(10);
    private static Map<Character, Integer> UNIT_MAP = new HashMap<>(8);

    static {
        //char[] numArr = {'零', '一', '二', '三', '四', '五', '六', '七', '八', '九'};
        NUM_MAP.put('零', 0);
        NUM_MAP.put('一', 1);
        NUM_MAP.put('二', 2);
        NUM_MAP.put('三', 3);
        NUM_MAP.put('四', 4);
        NUM_MAP.put('五', 5);
        NUM_MAP.put('六', 6);
        NUM_MAP.put('七', 7);
        NUM_MAP.put('八', 8);
        NUM_MAP.put('九', 9);

        UNIT_MAP.put('十', 10);
        UNIT_MAP.put('百', 100);
        UNIT_MAP.put('千', 1000);
        UNIT_MAP.put('万', 10000);
        UNIT_MAP.put('亿', 100000000);
    }


    /**
     * 目前只能在十万以下的正确转换
     *
     * @param numStr
     * @return
     */
    public static int covertToInt(String numStr) {
        int num = 0;
        int maxUnit = 0;
        for (int i = 0; i < numStr.length(); ) {
            char c1 = numStr.charAt(i++);
            char c2 = 0;
            if (i < numStr.length()) {
                c2 = numStr.charAt(i++);
            } else {
                c2 = 11;
            }
            if (NUM_MAP.containsKey(c1) && UNIT_MAP.containsKey(c2)) {
                // 处理数字 + 单位
                num += NUM_MAP.get(c1) * UNIT_MAP.get(c2);
            } else if (NUM_MAP.containsKey(c1) && c2 == 11) {
                // 处理最后个位数
                num += NUM_MAP.get(c1);
            } else if (NUM_MAP.containsKey(c1) && '零' == c1) {
                // 处理零数  比如 一千零二十一
                i--;
                continue;
            } else if ('十' == c1) {
                // 处理十 十一 十二
                if(NUM_MAP.containsKey(c2)){
                    return 10 + NUM_MAP.get(c2);
                }else{
                    return 10;
                }
            } else {
                // 处理 十万 百万 千万
                num *= UNIT_MAP.get(c1);
                i--;
            }

        }

        return num;
    }

    public static void main(String[] args) {
        String s = "一千三百零二";
        s = "一千零二十一";
        // s = "三千二百零三万零一";
        System.out.println(covertToInt(s));
    }

}
