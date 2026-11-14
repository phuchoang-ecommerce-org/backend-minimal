package com.phucle.ecommerce.identity.adapter.out.jdbc;

import com.phucle.ecommerce.identity.api.SellerShopDirectory;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSellerShopDirectory implements SellerShopDirectory {
    private final JdbcTemplate jdbc;
    JdbcSellerShopDirectory(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public Optional<UUID> shopIdForAccount(UUID accountId) {
        return jdbc.query("SELECT id FROM marketplace.seller_shop WHERE account_id=?",
                (rs, row) -> rs.getObject(1, UUID.class), accountId).stream().findFirst();
    }
    @Override public Optional<Shop> lockShopForStockWrite(UUID accountId) {
        return jdbc.query("SELECT id,status FROM marketplace.seller_shop WHERE account_id=? FOR SHARE",
                (rs, row) -> new Shop(rs.getObject("id", UUID.class), "ACTIVE".equals(rs.getString("status"))),
                accountId).stream().findFirst();
    }
}
