package com.phucle.ecommerce.cart.domain;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class Cart {
    private final UUID id;
    private final CartOwner owner;
    private final Map<UUID, CartLine> lines = new LinkedHashMap<>();
    private long version;
    private UUID mergedInto;
    public Cart(UUID id, CartOwner owner, long version, UUID mergedInto, Collection<CartLine> existing) {
        if (id == null || owner == null || version < 0) throw new IllegalArgumentException();
        this.id=id; this.owner=owner; this.version=version; this.mergedInto=mergedInto;
        for (CartLine line: existing) lines.put(line.variantId(), line);
    }
    public UUID id() { return id; }
    public CartOwner owner() { return owner; }
    public long version() { return version; }
    public UUID mergedInto() { return mergedInto; }
    public Collection<CartLine> lines() { return java.util.List.copyOf(lines.values()); }
    public Quantity quantity(UUID variantId) {
        CartLine line=lines.get(variantId); return line==null?null:line.quantity();
    }
    public void requireVersion(long expected) {
        if (expected < 0 || expected != version) throw new CartFailure(CartFailure.Code.STALE_VERSION);
    }
    public void setQuantity(UUID variantId, Quantity quantity) {
        requireEditable(); lines.put(variantId,new CartLine(variantId,quantity)); version++;
    }
    public void remove(UUID variantId) {
        requireEditable();
        if (lines.remove(variantId)==null) throw new CartFailure(CartFailure.Code.RESOURCE_NOT_FOUND);
        version++;
    }
    public void mergeLine(UUID variantId, Quantity quantity, int stock) {
        requireEditable();
        Quantity current=quantity(variantId);
        long combined=(long)(current==null?0:current.value())+quantity.value();
        if (combined>stock || combined>Integer.MAX_VALUE) throw new CartFailure(CartFailure.Code.QUANTITY_EXCEEDED);
        lines.put(variantId,new CartLine(variantId,new Quantity((int)combined)));
        version++;
    }
    public void consumeInto(UUID target) {
        if (!(owner instanceof CartOwner.Guest) || mergedInto!=null || id.equals(target))
            throw new IllegalStateException("Guest source cannot be consumed");
        mergedInto=target;
    }
    private void requireEditable() {
        if (mergedInto!=null) throw new CartFailure(CartFailure.Code.AUTHENTICATION_REQUIRED);
    }
}
