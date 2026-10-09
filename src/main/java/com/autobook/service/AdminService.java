package com.autobook.service;

import com.autobook.dto.ProviderDto;
import com.autobook.exception.InvalidRequestException;
import com.autobook.exception.ResourceNotFoundException;
import com.autobook.model.AppUser;
import com.autobook.repository.ProviderRepository;
import com.autobook.repository.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {

    private final ProviderRepository providerRepository;
    private final UserRepository userRepository;

    public AdminService(ProviderRepository providerRepository, UserRepository userRepository) {
        this.providerRepository = providerRepository;
        this.userRepository = userRepository;
    }

    public List<ProviderDto> getProviders() {
        return providerRepository.findAll();
    }

    public List<AppUser> getUsers() {
        return userRepository.findAll();
    }

    @Transactional
    public ProviderDto updateProvider(long providerId, String name, String phone) {
        if (name == null || name.isBlank()) {
            throw new InvalidRequestException("Provider name is required.");
        }
        String trimmedPhone = phone == null || phone.isBlank() ? null : phone.trim();
        if (providerRepository.updateContactInfo(providerId, name.trim(), trimmedPhone) == 0) {
            throw new ResourceNotFoundException("Provider " + providerId + " was not found.");
        }
        return providerRepository.findById(providerId).orElseThrow();
    }
}
