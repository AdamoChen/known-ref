package com.ccg.knowledgereference.com.ccg.controller;

import com.ccg.knowledgereference.com.ccg.controller.dto.CivilCodeItem;
import com.ccg.knowledgereference.servcie.CivilCodeManageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/kf/civil/code/")
public class CivilCodeController {

    @Autowired
    CivilCodeManageService civilCodeManageService;

    @GetMapping("/fullTextQuery")
    public List<CivilCodeItem> fullTextQuery(@RequestParam String content, @RequestParam(required = false) Integer from,
                                             @RequestParam(required = false) Integer size) throws IOException {
        return civilCodeManageService.fullTextQuery(content, from, size);
    }

    @GetMapping("/rangeByItemNum")
    public List<CivilCodeItem> rangeById(int start, int end) throws IOException {
        return civilCodeManageService.rangeById(start, end);
    }
}
