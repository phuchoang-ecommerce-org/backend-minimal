package com.phucle.ecommerce.configuration;

import java.util.Arrays;
import java.util.Set;

final class ProfilePolicy {

    private static final Set<String> SUPPORTED = Set.of("dev", "prod");

    private ProfilePolicy() {
    }

    static void requireValid(String[] activeProfiles, String[] defaultProfiles, boolean devFixturesEnabled) {
        String[] effectiveProfiles = activeProfiles.length == 0 ? defaultProfiles : activeProfiles;
        if (effectiveProfiles.length != 1 || !SUPPORTED.contains(effectiveProfiles[0])) {
            throw new IllegalStateException("Exactly one supported profile is required: dev or prod; got "
                    + Arrays.toString(effectiveProfiles));
        }
        if ("prod".equals(effectiveProfiles[0]) && devFixturesEnabled) {
            throw new IllegalStateException("Dev fixtures cannot be enabled with the prod profile");
        }
    }
}
