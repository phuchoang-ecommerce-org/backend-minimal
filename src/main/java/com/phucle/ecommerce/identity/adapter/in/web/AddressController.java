package com.phucle.ecommerce.identity.adapter.in.web;

import com.phucle.ecommerce.identity.api.AddressCommands;
import com.phucle.ecommerce.identity.api.OwnedAddressQueries;
import com.phucle.ecommerce.identity.api.CurrentActor;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
final class AddressController {
    private final AddressCommands commands;
    private final OwnedAddressQueries queries;
    private final CurrentActor actor;
    private final AddressWebMapper mapper;
    AddressController(AddressCommands commands, OwnedAddressQueries queries, CurrentActor actor, AddressWebMapper mapper) {
        this.commands = commands; this.queries = queries; this.actor = actor; this.mapper = mapper;
    }
    @PostMapping("/me/addresses") @ResponseStatus(HttpStatus.CREATED)
    AddressResponse create(@Valid @RequestBody AddressRequest request) {
        return mapper.toResponse(commands.create(actor.require(), mapper.toPayload(request)));
    }
    @GetMapping("/me/addresses")
    AddressList list(@RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") int limit) {
        var page = queries.list(actor.require(), cursor, limit);
        return new AddressList(page.items().stream().map(mapper::toResponse).toList(), page.nextCursor(), page.hasNext());
    }
    record AddressList(List<AddressResponse> items, String nextCursor, boolean hasNext) {}
}
