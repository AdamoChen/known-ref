package com.ccg.knowledgereference.com.ccg.controller.dto;

import com.ccg.knowledgereference.com.ccg.controller.constant.CivilCodeUnitEnum;
import lombok.Data;

@Data
public class CivilCodeNode {
    /**
     * 单元
     */
    private CivilCodeUnitEnum levelUnit;
    /**
     * 编号
     */
    private int num;
    /**
     * 原始节点编号
     */
    private String originalValue;

    //private String content;

}
