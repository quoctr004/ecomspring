package com.hieu.catalog_service.interfaces.rest;

import com.hieu.catalog_service.application.command.attr.AddAttrValueCommand;
import com.hieu.catalog_service.application.command.attr.CreateAttrCommand;
import com.hieu.catalog_service.application.command.attr.DeleteAttrCommand;
import com.hieu.catalog_service.application.command.attr.RemoveAttrValueCommand;
import com.hieu.catalog_service.application.command.attr.UpdateAttrCommand;
import com.hieu.catalog_service.application.common.CommandHandler;
import com.hieu.catalog_service.application.common.QueryHandler;
import com.hieu.catalog_service.application.dto.AttrDTO;
import com.hieu.catalog_service.application.query.attr.GetAttrByIdQuery;
import com.hieu.catalog_service.application.query.attr.ListAttrsQuery;
import com.hieu.catalog_service.interfaces.rest.dto.AddAttrValueRequest;
import com.hieu.catalog_service.interfaces.rest.dto.CreateAttrRequest;
import com.hieu.catalog_service.interfaces.rest.dto.UpdateAttrRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/attrs")
@RequiredArgsConstructor
@Tag(name = "Attributes", description = "Attribute definitions used by product variants.")
public class AttrController {

    private static final String WRITE = "hasRole('ADMIN') or hasAuthority('catalog:write')";

    private final CommandHandler<CreateAttrCommand, AttrDTO> create;
    private final CommandHandler<UpdateAttrCommand, AttrDTO> update;
    private final CommandHandler<DeleteAttrCommand, Void> delete;
    private final CommandHandler<AddAttrValueCommand, AttrDTO> addValue;
    private final CommandHandler<RemoveAttrValueCommand, Void> removeValue;
    private final QueryHandler<GetAttrByIdQuery, AttrDTO> getById;
    private final QueryHandler<ListAttrsQuery, List<AttrDTO>> listAll;

    @Operation(summary = "List all attributes")
    @GetMapping
    public ResponseEntity<List<AttrDTO>> list() {
        return ResponseEntity.ok(listAll.handle(new ListAttrsQuery()));
    }

    @Operation(summary = "Get attribute by id")
    @GetMapping("/{id}")
    public ResponseEntity<AttrDTO> byId(@PathVariable Long id) {
        return ResponseEntity.ok(getById.handle(new GetAttrByIdQuery(id)));
    }

    @Operation(summary = "Create attribute (admin)")
    @PostMapping
    @PreAuthorize(WRITE)
    public ResponseEntity<AttrDTO> create(@Valid @RequestBody CreateAttrRequest req) {
        var values = req.values() == null ? List.<CreateAttrCommand.ValueCmd>of()
            : req.values().stream().map(v -> new CreateAttrCommand.ValueCmd(v.val(), v.code())).toList();
        return ResponseEntity.ok(create.handle(new CreateAttrCommand(req.code(), req.name(), req.type(), values)));
    }

    @Operation(summary = "Update attribute (admin)")
    @PatchMapping("/{id}")
    @PreAuthorize(WRITE)
    public ResponseEntity<AttrDTO> update(@PathVariable Long id, @Valid @RequestBody UpdateAttrRequest req) {
        return ResponseEntity.ok(update.handle(new UpdateAttrCommand(id, req.name(), req.type(), req.sortOrder())));
    }

    @Operation(summary = "Delete attribute (admin)")
    @DeleteMapping("/{id}")
    @PreAuthorize(WRITE)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        delete.handle(new DeleteAttrCommand(id));
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Add attribute value (admin)")
    @PostMapping("/{id}/values")
    @PreAuthorize(WRITE)
    public ResponseEntity<AttrDTO> addValue(@PathVariable Long id, @Valid @RequestBody AddAttrValueRequest req) {
        return ResponseEntity.ok(addValue.handle(new AddAttrValueCommand(id, req.val(), req.code())));
    }

    @Operation(summary = "Remove attribute value (admin)")
    @DeleteMapping("/{id}/values/{valId}")
    @PreAuthorize(WRITE)
    public ResponseEntity<Void> removeValue(@PathVariable Long id, @PathVariable Long valId) {
        removeValue.handle(new RemoveAttrValueCommand(id, valId));
        return ResponseEntity.noContent().build();
    }
}
