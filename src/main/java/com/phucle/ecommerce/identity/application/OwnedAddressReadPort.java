package com.phucle.ecommerce.identity.application;

import com.phucle.ecommerce.identity.api.AddressView;
import com.phucle.ecommerce.identity.domain.AddressId;
import com.phucle.ecommerce.identity.domain.MemberId;
import java.util.List;
import java.util.Optional;

public interface OwnedAddressReadPort {
    List<AddressView> findOwnedBefore(MemberId owner, AddressId boundary, int fetchLimit);
    Optional<AddressView> findOwned(MemberId owner, AddressId addressId);
}
