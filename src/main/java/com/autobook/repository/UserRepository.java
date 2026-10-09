package com.autobook.repository;

import com.autobook.model.AppUser;
import com.autobook.model.UserRole;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private static final RowMapper<AppUser> USER_ROW_MAPPER = (resultSet, rowNum) -> new AppUser(
            resultSet.getLong("user_id"),
            resultSet.getString("first_name"),
            resultSet.getString("last_name"),
            resultSet.getString("email"),
            UserRole.valueOf(resultSet.getString("role"))
    );

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<AppUser> findByEmail(String email) {
        List<AppUser> users = jdbcTemplate.query(
                "SELECT user_id, first_name, last_name, email, role FROM users WHERE email = ?",
                USER_ROW_MAPPER,
                email
        );
        return users.stream().findFirst();
    }

    public List<AppUser> findAll() {
        return jdbcTemplate.query(
                "SELECT user_id, first_name, last_name, email, role FROM users ORDER BY role, last_name, first_name",
                USER_ROW_MAPPER
        );
    }
}
