package com.phucle.ecommerce.identity.adapter.out.jdbc;

import com.phucle.ecommerce.identity.api.AddressView;
import com.phucle.ecommerce.identity.application.OwnedAddressReadPort;
import com.phucle.ecommerce.identity.domain.AddressId;
import com.phucle.ecommerce.identity.domain.MemberId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcOwnedAddressReadAdapter implements OwnedAddressReadPort {
    private final NamedParameterJdbcTemplate jdbc;
    JdbcOwnedAddressReadAdapter(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public List<AddressView> findOwnedBefore(MemberId owner, AddressId boundary, int fetchLimit) {
        String sql = """
                SELECT id, recipient, address_line, locality, postal_code, country
                FROM marketplace.address WHERE member_id=:memberId
                """ + (boundary == null ? "" : " AND id < :boundary ") + " ORDER BY id DESC LIMIT :fetchLimit";
        var params = new HashMap<String, Object>();
        params.put("memberId", owner.value()); params.put("fetchLimit", fetchLimit);
        if (boundary != null) params.put("boundary", boundary.value());
        return jdbc.query(sql, params, (row, n) -> new AddressView(row.getObject("id", UUID.class),
                row.getString("recipient"), row.getString("address_line"), row.getString("locality"),
                row.getString("postal_code"), row.getString("country")));
    }
    @Override public Optional<AddressView> findOwned(MemberId owner, AddressId id) {
        return jdbc.query("""
                SELECT id, recipient, address_line, locality, postal_code, country FROM marketplace.address
                WHERE member_id=:memberId AND id=:id
                """, Map.of("memberId", owner.value(), "id", id.value()),
                (row, n) -> new AddressView(row.getObject("id", UUID.class), row.getString("recipient"),
                row.getString("address_line"), row.getString("locality"), row.getString("postal_code"),
                row.getString("country"))).stream().findFirst();
    }
}
