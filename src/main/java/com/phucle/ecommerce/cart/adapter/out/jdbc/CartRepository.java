package com.phucle.ecommerce.cart.adapter.out.jdbc;

import com.phucle.ecommerce.cart.domain.Cart;
import com.phucle.ecommerce.cart.application.port.out.CartRepositoryPort;
import com.phucle.ecommerce.cart.domain.CartLine;
import com.phucle.ecommerce.cart.domain.CartOwner;
import com.phucle.ecommerce.cart.domain.Quantity;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class CartRepository implements CartRepositoryPort {
    private final JdbcTemplate jdbc;
    public CartRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Override public Cart guest(String hash, boolean lock) {
        if (hash==null) return null;
        List<Cart> found=jdbc.query("SELECT id,version,merged_into_cart_id FROM marketplace.cart "
                + "WHERE guest_session_hash=? AND expires_at>now()"+(lock?" FOR UPDATE":""),
                (r,n)->load(r.getObject(1,UUID.class),new CartOwner.Guest(hash),r.getLong(2),
                        r.getObject(3,UUID.class)),hash);
        return found.isEmpty()?null:found.getFirst();
    }
    @Override public Cart member(UUID member, boolean lock) {
        List<Cart> found=jdbc.query("SELECT id,version,merged_into_cart_id FROM marketplace.cart WHERE member_id=?"
                +(lock?" FOR UPDATE":""),
                (r,n)->load(r.getObject(1,UUID.class),new CartOwner.Member(member),r.getLong(2),
                        r.getObject(3,UUID.class)),member);
        return found.isEmpty()?null:found.getFirst();
    }
    @Override public Cart createGuest(String hash, Instant expiry) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO marketplace.cart(id,guest_session_hash,expires_at) VALUES (?,?,?)",
                id,hash,java.sql.Timestamp.from(expiry));
        return new Cart(id,new CartOwner.Guest(hash),0,null,List.of());
    }
    @Override public Cart createMember(UUID member) {
        jdbc.update("INSERT INTO marketplace.cart(id,member_id) VALUES (?,?) ON CONFLICT (member_id) DO NOTHING",
                UUID.randomUUID(),member);
        return member(member,true);
    }
    @Override public void save(Cart cart, long previousVersion) {
        int updated = jdbc.update("UPDATE marketplace.cart SET version=?,merged_into_cart_id=?,merged_at="
                + "CASE WHEN ?::uuid IS NULL THEN NULL ELSE now() END WHERE id=? AND version=?",
                cart.version(),cart.mergedInto(),cart.mergedInto(),cart.id(),previousVersion);
        if (updated != 1) throw new com.phucle.ecommerce.cart.domain.CartFailure(
                com.phucle.ecommerce.cart.domain.CartFailure.Code.STALE_VERSION);
        jdbc.update("DELETE FROM marketplace.cart_line WHERE cart_id=?",cart.id());
        for (CartLine line:cart.lines()) jdbc.update(
                "INSERT INTO marketplace.cart_line(cart_id,variant_id,quantity) VALUES (?,?,?)",
                cart.id(),line.variantId(),line.quantity().value());
    }
    private Cart load(UUID id,CartOwner owner,long version,UUID mergedInto) {
        List<CartLine> lines=new ArrayList<>();
        jdbc.query("SELECT variant_id,quantity FROM marketplace.cart_line WHERE cart_id=? ORDER BY variant_id",
                (org.springframework.jdbc.core.RowCallbackHandler) r->lines.add(
                        new CartLine(r.getObject(1,UUID.class),new Quantity(r.getInt(2)))),id);
        return new Cart(id,owner,version,mergedInto,lines);
    }
}
