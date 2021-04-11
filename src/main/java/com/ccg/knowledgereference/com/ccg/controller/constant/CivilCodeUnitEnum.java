package com.ccg.knowledgereference.com.ccg.controller.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum CivilCodeUnitEnum {
    CODIFICATION(1,"编"),
    SUB_CODIFICATION( 2,"分编"),
    CHAPTER( 3,"章"),
    SECTION(4,"节"),
    ITEM(5, "条"),
    ;
    int level;
    String name;

    public static CivilCodeUnitEnum find(String str){
        for (CivilCodeUnitEnum e : CivilCodeUnitEnum.values()) {
            if (e.name.equals(str)) {
                return e;
            }
        }
        return null;
    }

}
