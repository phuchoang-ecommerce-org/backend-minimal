package com.phucle.ecommerce.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.phucle.ecommerce.identity.domain.Address;
import com.phucle.ecommerce.identity.domain.AddressId;
import com.phucle.ecommerce.identity.domain.AddressValue;
import com.phucle.ecommerce.identity.domain.MemberId;
import java.util.UUID;
import org.jmolecules.ddd.annotation.AggregateRoot;
import org.jmolecules.ddd.annotation.ValueObject;
import org.junit.jupiter.api.Test;

class AddressDomainTest {
    @Test void addressHasDistinctTypedIdsAndValueObject() {
        var member = new MemberId(UUID.randomUUID());
        var value = new AddressValue("Name", "Line", "City", "10000", "VN");
        var address = Address.create(member, value);
        assertThat(address.id()).isInstanceOf(AddressId.class);
        assertThat(address.memberId()).isEqualTo(member);
        assertThat(Address.class.isAnnotationPresent(AggregateRoot.class)).isTrue();
        assertThat(AddressValue.class.isAnnotationPresent(ValueObject.class)).isTrue();
        assertThatThrownBy(() -> new AddressId(null)).isInstanceOf(NullPointerException.class);
    }
}
