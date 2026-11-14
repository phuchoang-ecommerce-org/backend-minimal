package com.phucle.ecommerce.configuration;

import com.phucle.ecommerce.cart.api.GuestCartMergeCommand;
import com.phucle.ecommerce.cart.api.GuestCartMergeResult;
import com.phucle.ecommerce.cart.api.GuestOriginPolicy;
import com.phucle.ecommerce.cart.api.MergeGuestCartOnLogin;
import com.phucle.ecommerce.identity.api.CompleteLogin;
import com.phucle.ecommerce.identity.api.CompletedLogin;
import com.phucle.ecommerce.identity.api.LogIn;
import com.phucle.ecommerce.identity.api.LoginCommand;
import com.phucle.ecommerce.identity.api.LoginResult;
import com.phucle.ecommerce.identity.api.TrustedActor;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
final class LoginCompositionService implements CompleteLogin {

    private final LogIn logIn;
    private final MergeGuestCartOnLogin cartMerge;
    private final GuestOriginPolicy guestOrigin;

    LoginCompositionService(LogIn logIn, MergeGuestCartOnLogin cartMerge, GuestOriginPolicy guestOrigin) {
        this.logIn = logIn;
        this.cartMerge = cartMerge;
        this.guestOrigin = guestOrigin;
    }

    @Override
    public CompletedLogin complete(LoginCommand command, String guestProof, String origin) {
        if (guestProof != null) {
            guestOrigin.requireAllowed(origin);
        }
        var login = logIn.logIn(command);
        GuestCartMergeResult merge = mergeGuestCart(login.account().id(), login.account().role(), guestProof);
        return completedLogin(login, merge);
    }

    private GuestCartMergeResult mergeGuestCart(UUID accountId, String role, String guestProof) {
        if (guestProof == null || !"MEMBER".equals(role)) {
            return new GuestCartMergeResult(false, false, List.of());
        }
        return cartMerge.merge(new GuestCartMergeCommand(
                new TrustedActor(accountId, role, UUID.randomUUID().toString()), guestProof));
    }

    private CompletedLogin completedLogin(LoginResult login, GuestCartMergeResult merge) {
        return new CompletedLogin(login, merge.merged(), merge.sourceConsumed(), guestOrigin.secureCookie(),
                merge.rejectedLines().stream()
                        .map(line -> new CompletedLogin.RejectedLine(line.variantId(), line.code()))
                        .toList());
    }
}
