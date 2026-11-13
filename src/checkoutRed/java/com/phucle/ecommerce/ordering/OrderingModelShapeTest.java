package com.phucle.ecommerce.ordering;

import static org.assertj.core.api.Assertions.assertThat;

import com.phucle.ecommerce.ordering.domain.OrderFactory;
import com.phucle.ecommerce.ordering.domain.AddressSnapshot;
import org.junit.jupiter.api.Test;

class OrderingModelShapeTest {
    @Test void factoryBuildsTopLevelAggregateWithEntityChildren() throws Exception {
        var root = OrderFactory.class.getMethod("place", java.util.UUID.class,
                AddressSnapshot.class, java.util.List.class).getReturnType();
        assertThat(root.getName()).isEqualTo("com.phucle.ecommerce.ordering.domain.CustomerOrder");
        assertThat(root.isAnnotationPresent(org.jmolecules.ddd.annotation.AggregateRoot.class)).isTrue();
        assertThat(classOrNull("com.phucle.ecommerce.ordering.domain.SellerOrder")).isNotNull();
        assertThat(classOrNull("com.phucle.ecommerce.ordering.domain.OrderLine")).isNotNull();
    }
    private Class<?> classOrNull(String name) {
        try { return Class.forName(name); } catch (ClassNotFoundException exception) { return null; }
    }
}
