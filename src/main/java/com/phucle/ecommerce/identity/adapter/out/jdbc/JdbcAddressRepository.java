package com.phucle.ecommerce.identity.adapter.out.jdbc;

import com.phucle.ecommerce.identity.application.AddressRepositoryPort;
import com.phucle.ecommerce.identity.domain.Address;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAddressRepository implements AddressRepositoryPort {
    private final NamedParameterJdbcTemplate jdbc;
    JdbcAddressRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public void insert(Address address) {
        var v = address.value();
        jdbc.update("""
                INSERT INTO marketplace.address (id, member_id, recipient, address_line, locality, postal_code, country)
                VALUES (:id, :memberId, :recipient, :addressLine, :locality, :postalCode, :country)
                """, Map.of("id", address.id().value(), "memberId", address.memberId().value(),
                "recipient", v.recipient(), "addressLine", v.addressLine(), "locality", v.locality(),
                "postalCode", v.postalCode(), "country", v.country()));
    }
}
