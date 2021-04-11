package com.ccg.knowledgereference.servcie;

import com.ccg.knowledgereference.com.ccg.controller.dto.CivilCodeItem;

import java.io.IOException;
import java.util.List;

public interface CivilCodeManageService {

    boolean importData(String path);

    List<CivilCodeItem> fullTextQuery(String content, Integer from, Integer size) throws IOException;

    List<CivilCodeItem> rangeById(Integer start, Integer end) throws IOException;
}
