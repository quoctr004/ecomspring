package com.hieu.catalog_service.interfaces.rest;

import com.hieu.catalog_service.application.command.category.CreateCategoryCommand;
import com.hieu.catalog_service.application.command.category.DeleteCategoryCommand;
import com.hieu.catalog_service.application.command.category.UpdateCategoryCommand;
import com.hieu.catalog_service.application.common.CommandHandler;
import com.hieu.catalog_service.application.common.QueryHandler;
import com.hieu.catalog_service.application.dto.CategoryDTO;
import com.hieu.catalog_service.application.query.category.GetCategoryByIdQuery;
import com.hieu.catalog_service.application.query.category.ListCategoriesQuery;
import com.hieu.catalog_service.interfaces.rest.dto.CategoryRequest;
import com.hieu.common.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Category tree management.")
public class CategoryController {

    private static final String WRITE = "hasRole('ADMIN') or hasAuthority('catalog:write')";

    private final CommandHandler<CreateCategoryCommand, CategoryDTO> create;
    private final CommandHandler<UpdateCategoryCommand, CategoryDTO> update;
    private final CommandHandler<DeleteCategoryCommand, Void> delete;
    private final QueryHandler<GetCategoryByIdQuery, CategoryDTO> getById;
    private final QueryHandler<ListCategoriesQuery, List<CategoryDTO>> listAll;

    @Operation(summary = "List all active categories")
    @GetMapping
    public ResponseEntity<List<CategoryDTO>> list() {
        return ResponseEntity.ok(listAll.handle(new ListCategoriesQuery()));
    }

    @Operation(summary = "Get category by id")
    @GetMapping("/{id}")
    public ResponseEntity<CategoryDTO> byId(@PathVariable Long id) {
        return ResponseEntity.ok(getById.handle(new GetCategoryByIdQuery(id)));
    }

    @Operation(summary = "Create category (admin)")
    @PostMapping
    @PreAuthorize(WRITE)
    public ResponseEntity<CategoryDTO> create(@Valid @RequestBody CategoryRequest req,
                                                @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(create.handle(new CreateCategoryCommand(
            req.name(), req.description(), req.parentId(), req.sortOrder(), user.userId())));
    }

    @Operation(summary = "Update category (admin)")
    @PatchMapping("/{id}")
    @PreAuthorize(WRITE)
    public ResponseEntity<CategoryDTO> update(@PathVariable Long id,
                                                @Valid @RequestBody CategoryRequest req,
                                                @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(update.handle(new UpdateCategoryCommand(
            id, req.name(), req.description(), req.parentId(), req.sortOrder(), user.userId())));
    }

    @Operation(summary = "Soft-delete category (admin)")
    @DeleteMapping("/{id}")
    @PreAuthorize(WRITE)
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                        @AuthenticationPrincipal AuthenticatedUser user) {
        delete.handle(new DeleteCategoryCommand(id, user.userId()));
        return ResponseEntity.noContent().build();
    }
}
