package com.autobook.service;

import com.autobook.dto.HomeDto;
import com.autobook.repository.HomeRepository;
import org.springframework.stereotype.Service;

@Service
public class HomeService {

    private final HomeRepository homeRepository;

    public HomeService(HomeRepository homeRepository) {
        this.homeRepository = homeRepository;
    }

    public HomeDto getHomeSummary() {
        return homeRepository.getHomeSummary();
    }
}
