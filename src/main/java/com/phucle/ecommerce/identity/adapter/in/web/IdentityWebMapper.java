package com.phucle.ecommerce.identity.adapter.in.web;

import com.phucle.ecommerce.identity.api.AccountView;
import com.phucle.ecommerce.identity.api.LoginCommand;
import com.phucle.ecommerce.identity.api.RegisterMemberCommand;
import org.mapstruct.Builder;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        injectionStrategy = InjectionStrategy.CONSTRUCTOR,
        unmappedTargetPolicy = ReportingPolicy.ERROR,
        typeConversionPolicy = ReportingPolicy.ERROR,
        builder = @Builder(disableBuilder = true))
interface IdentityWebMapper {

    RegisterMemberCommand toCommand(RegisterMemberRequest request);

    LoginCommand toCommand(LoginRequest request);

    AccountResponse toResponse(AccountView account);
}
