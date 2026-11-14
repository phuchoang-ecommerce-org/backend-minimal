package com.phucle.ecommerce.identity.adapter.out.jdbc;

import com.phucle.ecommerce.identity.api.AddressView;
import com.phucle.ecommerce.identity.application.OwnedAddressReadPort;
import com.phucle.ecommerce.identity.domain.AddressId;
import com.phucle.ecommerce.identity.domain.MemberId;
import java.sql.ResultSet;
import java.sql.SQLException;
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
        return jdbc.query(pageSql(boundary), pageParams(owner, boundary, fetchLimit),
                (row, n) -> addressView(row));
    }
    @Override public Optional<AddressView> findOwned(MemberId owner, AddressId id) {
        return jdbc.query("""
                SELECT id, recipient, address_line, locality, postal_code, country FROM marketplace.address
                WHERE member_id=:memberId AND id=:id
                """, Map.of("memberId", owner.value(), "id", id.value()),
                (row, n) -> addressView(row)).stream().findFirst();
    }

    private String pageSql(AddressId boundary) {
        return """
                SELECT id, recipient, address_line, locality, postal_code, country
                FROM marketplace.address WHERE member_id=:memberId
                """ + (boundary == null ? "" : " AND id < :boundary ") + " ORDER BY id DESC LIMIT :fetchLimit";
    }

    private Map<String, Object> pageParams(MemberId owner, AddressId boundary, int fetchLimit) {
        var params = new HashMap<String, Object>();
        params.put("memberId", owner.value()); params.put("fetchLimit", fetchLimit);
        if (boundary != null) params.put("boundary", boundary.value());
        return params;
    }

    private AddressView addressView(ResultSet row) throws SQLException {
        return new AddressView(row.getObject("id", UUID.class), row.getString("recipient"),
                row.getString("address_line"), row.getString("locality"), row.getString("postal_code"),
                row.getString("country"));
    }
}
