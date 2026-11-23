package vn.edu.ptit.library.recommendation.event;

import vn.edu.ptit.library.recommendation.enums.InteractionEventType;

import java.util.UUID;

public final class InteractionRecordedEvent {

    private InteractionRecordedEvent() {
    }

    public record Sync(
            UUID userId,
            UUID documentId,
            InteractionEventType eventType,
            Integer ratingStars,
            boolean removeFavorite
    ) {
    }

    public record AsyncRead(
            UUID userId,
            UUID documentId,
            InteractionEventType eventType
    ) {
    }

    public static AsyncRead viewDetail(UUID userId, UUID documentId) {
        return new AsyncRead(userId, documentId, InteractionEventType.VIEW_DETAIL);
    }

    public static AsyncRead downloadOrRead(UUID userId, UUID documentId) {
        return new AsyncRead(userId, documentId, InteractionEventType.DOWNLOAD_OR_READ);
    }

    public static Sync favorite(UUID userId, UUID documentId, boolean isFavorite) {
        return new Sync(userId, documentId, InteractionEventType.FAVORITE, null, !isFavorite);
    }

    public static Sync rate(UUID userId, UUID documentId, int ratingStars) {
        return new Sync(userId, documentId, InteractionEventType.RATE, ratingStars, false);
    }

    public static Sync borrow(UUID userId, UUID documentId) {
        return new Sync(userId, documentId, InteractionEventType.BORROW, null, false);
    }
}
