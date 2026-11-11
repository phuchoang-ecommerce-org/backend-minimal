package com.phucle.ecommerce.identity.application;

import com.phucle.ecommerce.identity.api.AddressView;
import com.phucle.ecommerce.identity.api.OwnedAddressQueries;
import com.phucle.ecommerce.identity.api.TrustedActor;
import com.phucle.ecommerce.identity.domain.AddressId;
import com.phucle.ecommerce.identity.domain.MemberId;
import com.phucle.ecommerce.shared.CursorCodec;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AddressQueryService implements OwnedAddressQueries {
    private final OwnedAddressReadPort read;
    private final CursorCodec cursors;
    AddressQueryService(OwnedAddressReadPort read, CursorCodec cursors) { this.read = read; this.cursors = cursors; }
    @Override @Transactional(readOnly = true) public Page list(TrustedActor actor, String cursor, int limit) {
        AddressAuthorization.requireMember(actor);
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Invalid limit");
        UUID boundary = cursor == null ? null : cursors.decode(cursor, "addresses", actor.accountId().toString());
        var rows = read.findOwnedBefore(new MemberId(actor.accountId()), boundary == null ? null : new AddressId(boundary), limit + 1);
        boolean more = rows.size() > limit;
        var selected = more ? rows.subList(0, limit) : rows;
        return new Page(selected, more ? cursors.encode("addresses", actor.accountId().toString(), selected.getLast().id()) : null, more);
    }
    @Override @Transactional(readOnly = true) public AddressView findOwned(TrustedActor actor, UUID addressId) {
        AddressAuthorization.requireMember(actor);
        return read.findOwned(new MemberId(actor.accountId()), new AddressId(addressId))
                .orElseThrow(() -> new OwnedAddressNotFoundException());
    }
}
