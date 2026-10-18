package io.aegis.inventory.application;

import io.aegis.inventory.domain.CatalogItem;
import io.aegis.inventory.infrastructure.persistence.CatalogRepository;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class CatalogService {
    private static final Pattern SKU = Pattern.compile("[A-Z0-9][A-Z0-9_-]{0,63}");
    private final CatalogRepository repository;
    public CatalogService(CatalogRepository repository) { this.repository = repository; }

    public CatalogItem find(String sku) {
        if (sku == null || !SKU.matcher(sku).matches()) { throw new InvalidSkuException(); }
        return repository.findBySku(sku).orElseThrow(ItemNotFoundException::new);
    }
}
