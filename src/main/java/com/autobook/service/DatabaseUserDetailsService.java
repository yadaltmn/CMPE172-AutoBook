package com.autobook.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseUserDetailsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        return jdbcTemplate.query(
                "SELECT email, password, role FROM users WHERE email = ?",
                rs -> {
                    if (!rs.next()) {
                        throw new UsernameNotFoundException(
                                "User not found"
                        );
                    }

                    return User.withUsername(rs.getString("email"))
                            .password(rs.getString("password"))
                            .roles(rs.getString("role"))
                            .build();
                },
                email
        );
    }
}