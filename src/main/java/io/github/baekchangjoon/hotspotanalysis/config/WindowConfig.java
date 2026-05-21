package io.github.baekchangjoon.hotspotanalysis.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;

import java.time.LocalDate;

/**
 * Time window over which commits are considered for hotspot scoring.
 *
 * <p>Exactly one of the two modes must be specified:
 * <ul>
 *   <li>Absolute range: both {@link #since()} and {@link #until()}.</li>
 *   <li>Relative range: {@link #days()} (number of days back from "now").</li>
 * </ul>
 * </p>
 */
public record WindowConfig(
        LocalDate since,
        LocalDate until,
        @Min(value = 1, message = "window.days must be >= 1") Integer days
) {

    @AssertTrue(message = "window requires either (since,until) or days")
    public boolean hasEitherRangeOrDays() {
        boolean hasAbsoluteRange = since != null && until != null;
        boolean hasRelativeRange = days != null;
        return hasAbsoluteRange || hasRelativeRange;
    }

    @AssertTrue(message = "window.since must not be after window.until")
    public boolean isSinceNotAfterUntil() {
        if (since == null || until == null) {
            return true;
        }
        return !since.isAfter(until);
    }
}
