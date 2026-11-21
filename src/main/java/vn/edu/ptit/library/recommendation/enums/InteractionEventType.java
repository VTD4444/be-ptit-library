package vn.edu.ptit.library.recommendation.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum InteractionEventType {

    VIEW_DETAIL(1.0, true),
    FAVORITE(3.0, false),
    DOWNLOAD_OR_READ(4.0, true),
    BORROW(5.0, false),
    RATE(0.0, false);

    private final double defaultWeight;
    private final boolean asyncReadEvent;
}
