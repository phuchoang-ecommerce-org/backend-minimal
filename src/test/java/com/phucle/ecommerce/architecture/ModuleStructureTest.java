package com.phucle.ecommerce.architecture;

import com.phucle.ecommerce.MarketplaceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModuleStructureTest {

    @Test
    void modulesRespectBoundaries() {
        ApplicationModules.of(MarketplaceApplication.class).verify();
    }
}
