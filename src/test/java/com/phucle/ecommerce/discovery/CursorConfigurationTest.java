package com.phucle.ecommerce.discovery;

import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import org.junit.jupiter.api.Test;

class CursorConfigurationTest {
    @Test void startupRejectsMissingOrShortMacKey() {
        assertThatIllegalStateException().isThrownBy(() -> new CursorCodec(""));
        assertThatIllegalStateException().isThrownBy(() -> new CursorCodec("short"));
    }
}
