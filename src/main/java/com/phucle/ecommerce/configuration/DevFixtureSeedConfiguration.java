package com.phucle.ecommerce.configuration;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("dev")
@ConditionalOnProperty(prefix = "marketplace.dev-fixtures", name = "enabled", havingValue = "true")
class DevFixtureSeedConfiguration {

    static final UUID MEMBER_ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    static final UUID SELLER_ACCOUNT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    static final UUID SELLER_SHOP_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");
    static final UUID ADMIN_ACCOUNT_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;

    DevFixtureSeedConfiguration(JdbcTemplate jdbc, PasswordEncoder passwords) {
        this.jdbc = jdbc;
        this.passwords = passwords;
    }

    @Transactional
    public void seed(String memberPassword, String sellerPassword, String adminPassword, DevSeedDataset data) {
        insertAccount(jdbc, MEMBER_ACCOUNT_ID, "synthetic.member@example.test", passwords.encode(memberPassword), "MEMBER");
        insertAccount(jdbc, SELLER_ACCOUNT_ID, "synthetic.seller@example.test", passwords.encode(sellerPassword), "SELLER");
        insertAccount(jdbc, ADMIN_ACCOUNT_ID, "synthetic.admin@example.test", passwords.encode(adminPassword), "ADMIN");
        seedSellerShop(jdbc);
        if (data != null) {
            seedGenerated(data);
        }
    }

    private void seedGenerated(DevSeedDataset data) {
        if (data.categories() == null || data.products() == null || data.addresses() == null
                || data.orders() == null || data.reviews() == null
                || data.categories().size() > 20 || data.products().size() > 500
                || data.addresses().size() > 10 || data.orders().size() > 500
                || data.reviews().size() > 500) {
            throw new IllegalArgumentException("Invalid dev seed dataset size");
        }
        for (var category : data.categories()) {
            jdbc.update("INSERT INTO marketplace.category (id,name) VALUES (?,?) ON CONFLICT DO NOTHING",
                    category.id(), category.name());
        }
        for (var product : data.products()) {
            jdbc.update("""
                    INSERT INTO marketplace.product (id,seller_id,category_id,title,description,published)
                    VALUES (?,?,?,?,'Synthetic generated product',true) ON CONFLICT DO NOTHING
                    """, product.id(), SELLER_SHOP_ID, product.categoryId(), product.title());
            jdbc.update("""
                    INSERT INTO marketplace.variant (id,product_id,sku,label,price_usd)
                    VALUES (?,?,?,'Standard',?) ON CONFLICT DO NOTHING
                    """, product.variantId(), product.id(), product.sku(), product.priceUsd());
            jdbc.update("INSERT INTO marketplace.stock (variant_id,on_hand) VALUES (?,?) ON CONFLICT DO NOTHING",
                    product.variantId(), product.stock());
            if (product.image() != null) {
                var image = product.image();
                jdbc.update("""
                        INSERT INTO marketplace.product_image
                          (id,product_id,mode,source_url,source_name,license,sort_order,alt_text)
                        VALUES (?,?,'LINK',?,?,?,0,?) ON CONFLICT DO NOTHING
                        """, image.id(), product.id(), image.url(), image.sourceName(), image.license(),
                        image.altText());
            }
        }
        for (var address : data.addresses()) {
            jdbc.update("""
                    INSERT INTO marketplace.address (id,member_id,recipient,address_line,locality,postal_code,country)
                    VALUES (?,?,?,?,?,?,?) ON CONFLICT DO NOTHING
                    """, address.id(), MEMBER_ACCOUNT_ID, address.recipient(), address.addressLine(),
                    address.locality(), address.postalCode(), address.country());
        }
        for (var order : data.orders()) {
            if (order.quantity() < 1 || !java.util.Set.of("PENDING_PAYMENT", "PAID").contains(order.paymentStatus())) {
                throw new IllegalArgumentException("Invalid generated order");
            }
            jdbc.update("""
                    INSERT INTO marketplace.customer_order
                      (id,member_id,idempotency_key,request_fingerprint,status,total_usd,currency)
                    SELECT ?, ?, ?, 'synthetic-generated', 'PLACED', v.price_usd * ?, 'USD'
                    FROM marketplace.variant v WHERE v.id=? ON CONFLICT DO NOTHING
                    """, order.id(), MEMBER_ACCOUNT_ID, "dev-seed-" + order.id(), order.quantity(), order.variantId());
            jdbc.update("""
                    INSERT INTO marketplace.seller_order
                      (id,customer_order_id,seller_id,payment_status,total_usd,paid_at)
                    SELECT ?, ?, ?, ?, total_usd,
                      CASE WHEN ?='PAID' THEN CURRENT_TIMESTAMP ELSE NULL END
                    FROM marketplace.customer_order WHERE id=? ON CONFLICT DO NOTHING
                    """, order.sellerOrderId(), order.id(), SELLER_SHOP_ID, order.paymentStatus(),
                    order.paymentStatus(), order.id());
            jdbc.update("""
                    INSERT INTO marketplace.order_line
                      (id,seller_order_id,variant_id,product_id,product_title_snapshot,sku_snapshot,
                       quantity,unit_price_usd,discount_usd,line_total_usd)
                    SELECT ?, ?, v.id, p.id, p.title, v.sku, ?, v.price_usd, 0, v.price_usd * ?
                    FROM marketplace.variant v JOIN marketplace.product p ON p.id=v.product_id
                    WHERE v.id=? ON CONFLICT DO NOTHING
                    """, order.lineId(), order.sellerOrderId(), order.quantity(), order.quantity(), order.variantId());
            jdbc.update("""
                    INSERT INTO marketplace.order_address_snapshot
                      (customer_order_id,recipient,address_line,locality,postal_code,country)
                    SELECT ?, recipient,address_line,locality,postal_code,country
                    FROM marketplace.address WHERE member_id=? ORDER BY id LIMIT 1
                    ON CONFLICT DO NOTHING
                    """, order.id(), MEMBER_ACCOUNT_ID);
        }
        for (var review : data.reviews()) {
            jdbc.update("""
                    INSERT INTO marketplace.review (id,member_id,product_id,rating,body,visible)
                    VALUES (?,?,?,?,?,true) ON CONFLICT DO NOTHING
                    """, review.id(), MEMBER_ACCOUNT_ID, review.productId(), review.rating(), review.body());
        }
    }

    private void seedSellerShop(JdbcTemplate jdbc) {
        jdbc.update("""
                INSERT INTO marketplace.seller_shop (id, account_id, name, status)
                VALUES (?, ?, 'Synthetic Seller Shop', 'ACTIVE')
                ON CONFLICT DO NOTHING
                """, SELLER_SHOP_ID, SELLER_ACCOUNT_ID);
    }

    private void insertAccount(JdbcTemplate jdbc, UUID id, String login, String passwordHash, String role) {
        int changed = jdbc.update("""
                INSERT INTO marketplace.account (id, normalized_login, password_hash, role, status, display_name)
                VALUES (?, ?, ?, ?, 'ACTIVE', 'Synthetic ' || ?)
                ON CONFLICT (id) DO UPDATE SET password_hash=EXCLUDED.password_hash
                WHERE marketplace.account.normalized_login=EXCLUDED.normalized_login
                  AND marketplace.account.role=EXCLUDED.role
                """, id, login, passwordHash, role, role);
        if (changed != 1) {
            throw new org.springframework.dao.DataIntegrityViolationException("Synthetic fixture identity conflicts with existing data");
        }
    }
}
