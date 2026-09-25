package com.autobook.controller;

import com.autobook.dto.HomeDto;
import com.autobook.service.HomeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {

    private final HomeService homeService;

    public HomeController(HomeService homeService) {
        this.homeService = homeService;
    }

    @GetMapping("/")
    public HomeDto home() {
        return homeService.getHomeSummary();
    }
}
