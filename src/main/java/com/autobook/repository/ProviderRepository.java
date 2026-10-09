package com.autobook.repository;

import com.autobook.dto.ProviderDto;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ProviderRepository {

    private static final String SELECT_PROVIDER = """
            SELECT provider_id, user_id, name, email, phone
            FROM providers
            """;

    private static final RowMapper<ProviderDto> PROVIDER_ROW_MAPPER = (resultSet, rowNum) -> new ProviderDto(
            resultSet.getLong("provider_id"),
            resultSet.getObject("user_id", Long.class),
            resultSet.getString("name"),
            resultSet.getString("email"),
            resultSet.getString("phone")
    );

    private final JdbcTemplate jdbcTemplate;

    public ProviderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ProviderDto> findAll() {
        return jdbcTemplate.query(SELECT_PROVIDER + " ORDER BY name", PROVIDER_ROW_MAPPER);
    }

    public Optional<ProviderDto> findById(long providerId) {
        return jdbcTemplate.query(SELECT_PROVIDER + " WHERE provider_id = ?", PROVIDER_ROW_MAPPER, providerId)
                .stream()
                .findFirst();
    }

    public Optional<ProviderDto> findByUserId(long userId) {
        return jdbcTemplate.query(SELECT_PROVIDER + " WHERE user_id = ?", PROVIDER_ROW_MAPPER, userId)
                .stream()
                .findFirst();
    }

    /**
     * Locks the provider row for the rest of the current transaction. Used to serialize
     * schedule changes for a single provider so overlapping slots cannot be inserted concurrently.
     */
    public void lockProvider(long providerId) {
        jdbcTemplate.queryForList("SELECT provider_id FROM providers WHERE provider_id = ? FOR UPDATE", providerId);
    }

    public int updateContactInfo(long providerId, String name, String phone) {
        return jdbcTemplate.update(
                "UPDATE providers SET name = ?, phone = ? WHERE provider_id = ?",
                name,
                phone,
                providerId
        );
    }
}
