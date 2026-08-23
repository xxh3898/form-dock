package com.formdock.response;

import java.time.Instant;
import java.util.List;

public record CreatorResponsePageResponse(
        List<Item> items,
        long page,
        int size,
        long totalElements,
        long totalPages) {

    public CreatorResponsePageResponse {
        items = List.copyOf(items);
    }

    public record Item(Long responseId, Instant submittedAt) {
    }
}
