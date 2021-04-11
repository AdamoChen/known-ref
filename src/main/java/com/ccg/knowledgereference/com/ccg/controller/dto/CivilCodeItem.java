package com.ccg.knowledgereference.com.ccg.controller.dto;

import lombok.Data;

@Data
public class CivilCodeItem {
    /**
     * 编
     */
    private CivilCodeNode codification;

    /**
     * 分编
     */
    private CivilCodeNode subCodification;

    /**
     * 章
     */
    private CivilCodeNode chapter;

    /**
     * 节
     */
    private CivilCodeNode secction;

    /**
     * 条
     */
    private CivilCodeNode item;

}
