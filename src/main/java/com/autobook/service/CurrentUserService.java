package com.autobook.service;

import com.autobook.dto.ProviderDto;
import com.autobook.exception.ForbiddenOperationException;
import com.autobook.model.AppUser;
import com.autobook.repository.ProviderRepository;
import com.autobook.repository.UserRepository;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Resolves the signed-in user from the Spring Security session. Controllers never accept
 * a user or provider id from the request, so a client cannot act on behalf of someone else.
 */
@Service
public class CurrentUserService {

    private final UserRepository userRepository;
    private final ProviderRepository providerRepository;

    public CurrentUserService(UserRepository userRepository, ProviderRepository providerRepository) {
        this.userRepository = userRepository;
        this.providerRepository = providerRepository;
    }

    public AppUser requireUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required.");
        }
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Signed-in user no longer exists."));
    }

    public ProviderDto requireProvider(Authentication authentication) {
        AppUser user = requireUser(authentication);
        return providerRepository.findByUserId(user.userId())
                .orElseThrow(() -> new ForbiddenOperationException("This account is not linked to a provider."));
    }
}
