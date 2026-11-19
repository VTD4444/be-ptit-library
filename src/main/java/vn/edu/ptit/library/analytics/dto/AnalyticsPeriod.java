package vn.edu.ptit.library.analytics.dto;

import vn.edu.ptit.library.common.exception.AppException;
import vn.edu.ptit.library.common.exception.ErrorCode;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

public enum AnalyticsPeriod {
    TODAY,
    LAST_7_DAYS,
    LAST_30_DAYS,
    LAST_90_DAYS,
    THIS_MONTH,
    THIS_YEAR,
    ALL;

    public record PeriodWindow(
            String periodName,
            OffsetDateTime start,
            OffsetDateTime end,
            boolean groupByMonth
    ) {}

    public static PeriodWindow resolve(String periodParam, OffsetDateTime fromDate, OffsetDateTime toDate) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        if (fromDate != null || toDate != null) {
            OffsetDateTime end = toDate != null ? toDate : now;
            OffsetDateTime start = fromDate != null ? fromDate : end.minusDays(30);
            if (start.isAfter(end)) {
                throw new AppException(ErrorCode.INVALID_REQUEST, "fromDate phai nho hon hoac bang toDate");
            }
            boolean byMonth = Duration.between(start, end).toDays() > 92;
            return new PeriodWindow("CUSTOM", start, end, byMonth);
        }

        AnalyticsPeriod period = parse(periodParam);
        return switch (period) {
            case TODAY -> new PeriodWindow(period.name(), now.truncatedTo(ChronoUnit.DAYS), now, false);
            case LAST_7_DAYS -> new PeriodWindow(period.name(), now.minusDays(6).truncatedTo(ChronoUnit.DAYS), now, false);
            case LAST_30_DAYS -> new PeriodWindow(period.name(), now.minusDays(29).truncatedTo(ChronoUnit.DAYS), now, false);
            case LAST_90_DAYS -> new PeriodWindow(period.name(), now.minusDays(89).truncatedTo(ChronoUnit.DAYS), now, false);
            case THIS_MONTH -> new PeriodWindow(period.name(), now.withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS), now, false);
            case THIS_YEAR -> new PeriodWindow(period.name(), now.withDayOfYear(1).truncatedTo(ChronoUnit.DAYS), now, true);
            case ALL -> new PeriodWindow(period.name(), null, now, true);
        };
    }

    public static AnalyticsPeriod parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return LAST_30_DAYS;
        }
        try {
            return AnalyticsPeriod.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.INVALID_REQUEST,
                    "Khoang thoi gian khong hop le. Ho tro: TODAY, LAST_7_DAYS, LAST_30_DAYS, LAST_90_DAYS, THIS_MONTH, THIS_YEAR, ALL");
        }
    }
}
