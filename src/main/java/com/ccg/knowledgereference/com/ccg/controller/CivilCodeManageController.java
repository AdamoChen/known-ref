package com.ccg.knowledgereference.com.ccg.controller;

import com.ccg.knowledgereference.com.ccg.controller.dto.CivilCodeItem;
import com.ccg.knowledgereference.servcie.CivilCodeManageService;
import org.omg.PortableInterceptor.INACTIVE;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/kf/civil/code/manage")
public class CivilCodeManageController {

    @Autowired
    CivilCodeManageService civilCodeManageService;

    @GetMapping("/import")
    public String importCivilCode(@RequestParam String path){
//         path = "C:\\Users\\Adamo_chen\\Desktop\\民法典.md";
        boolean r = civilCodeManageService.importData(path);
        return "success";
    }


}
