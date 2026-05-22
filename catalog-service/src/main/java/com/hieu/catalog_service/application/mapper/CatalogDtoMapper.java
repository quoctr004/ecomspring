package com.hieu.catalog_service.application.mapper;

import com.hieu.catalog_service.application.dto.AttrDTO;
import com.hieu.catalog_service.application.dto.AttrValDTO;
import com.hieu.catalog_service.application.dto.CategoryDTO;
import com.hieu.catalog_service.application.dto.ProductDTO;
import com.hieu.catalog_service.application.dto.ProductSummaryDTO;
import com.hieu.catalog_service.application.dto.VariantAttrDTO;
import com.hieu.catalog_service.application.dto.VariantDTO;
import com.hieu.catalog_service.domain.model.attribute.Attr;
import com.hieu.catalog_service.domain.model.attribute.AttrVal;
import com.hieu.catalog_service.domain.model.category.Category;
import com.hieu.catalog_service.domain.model.product.Product;
import com.hieu.catalog_service.domain.model.product.Variant;
import com.hieu.catalog_service.domain.model.product.VariantAttr;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/** Aggregates → DTOs. Centralised so the interfaces layer stays thin. */
@Component
public class CatalogDtoMapper {

    public ProductDTO toDto(Product p) {
        Product.PriceRange range = p.getPriceRange();
        List<VariantDTO> variants = p.getVariants().stream().map(this::toDto).toList();
        return new ProductDTO(
            p.getId() != null ? p.getId().value() : null,
            p.getName(),
            p.getSlug().value(),
            p.getDescription(),
            p.getCategoryId() != null ? p.getCategoryId().value() : null,
            p.getBrand(),
            p.getThumbnail(),
            p.getImages(),
            p.getStatus().name(),
            p.getMetaTitle(),
            p.getMetaDescription(),
            p.getMetaKeywords(),
            range.min(),
            range.max(),
            p.getTotalStock(),
            p.hasAvailableVariant(),
            variants,
            p.getCreatedAt(),
            p.getUpdatedAt(),
            p.getCreatedBy(),
            p.getUpdatedBy(),
            p.getVersion()
        );
    }

    public ProductSummaryDTO toSummary(Product p) {
        Product.PriceRange range = p.getPriceRange();
        // Distinct variant images so the storefront card can cycle through them.
        // Skip nulls + blanks; keep declaration order (no sort) so the first
        // photo stays the resting frame.
        java.util.List<String> variantImages = p.getVariants().stream()
                .map(Variant::getImage)
                .filter(s -> s != null && !s.isBlank())
                .distinct()
                .toList();
        return new ProductSummaryDTO(
            p.getId() != null ? p.getId().value() : null,
            p.getName(),
            p.getSlug().value(),
            p.getCategoryId() != null ? p.getCategoryId().value() : null,
            p.getBrand(),
            p.getThumbnail(),
            p.getStatus().name(),
            range.min(),
            range.max(),
            p.getTotalStock(),
            p.hasAvailableVariant(),
            variantImages,
            p.getCreatedAt()
        );
    }

    public VariantDTO toDto(Variant v) {
        BigDecimal salePrice = v.getSalePrice() != null ? v.getSalePrice().amount() : null;
        BigDecimal cost = v.getCost() != null ? v.getCost().amount() : null;
        return new VariantDTO(
            v.getId() != null ? v.getId().value() : null,
            v.getProductId() != null ? v.getProductId().value() : null,
            v.getSku().value(),
            v.getPrice().amount(),
            cost,
            salePrice,
            v.getEffectivePrice().amount(),
            v.getImage(),
            v.getWeight(),
            v.getQuantity().value(),
            v.getStatus().name(),
            v.isAvailable(),
            v.getAttrs().stream().map(this::toDto).toList()
        );
    }

    public VariantAttrDTO toDto(VariantAttr a) {
        return new VariantAttrDTO(
            a.getId(), a.getAttrId().value(), a.getAttrCode(), a.getAttrName(),
            a.getValId(), a.getValText()
        );
    }

    public CategoryDTO toDto(Category c) {
        return new CategoryDTO(
            c.getId() != null ? c.getId().value() : null,
            c.getName().value(),
            c.getDescription() != null ? c.getDescription().value() : null,
            c.getParentId() != null ? c.getParentId().value() : null,
            c.isActive(),
            c.getSortOrder(),
            c.getCreatedAt(),
            c.getUpdatedAt(),
            c.getCreatedBy(),
            c.getUpdatedBy()
        );
    }

    public AttrDTO toDto(Attr a) {
        return new AttrDTO(
            a.getId() != null ? a.getId().value() : null,
            a.getCode(), a.getName(), a.getType().name(), a.getSortOrder(),
            a.getValues().stream().map(this::toDto).toList()
        );
    }

    public AttrValDTO toDto(AttrVal v) {
        return new AttrValDTO(v.getId(), v.getAttrId(), v.getVal(), v.getCode(), v.getSortOrder());
    }
}
