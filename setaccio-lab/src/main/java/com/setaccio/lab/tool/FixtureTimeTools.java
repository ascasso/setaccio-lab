package com.setaccio.lab.tool;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

public class FixtureTimeTools {

    public static final String FIXED_UTC_NOW_TOOL_NAME = "lab_fixed_utc_now";
    public static final String FIXED_TIME_FOR_ZONE_TOOL_NAME = "lab_fixed_time_for_zone";

    private final Clock clock;

    public FixtureTimeTools(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock is required");
    }

    @Tool(
            name = FIXED_UTC_NOW_TOOL_NAME,
            description = "Return the fixed UTC timestamp used by deterministic tool-calling benchmark fixtures."
    )
    public TimeSnapshot fixedUtcNow() {
        return snapshot(ZoneId.of("UTC"));
    }

    @Tool(
            name = FIXED_TIME_FOR_ZONE_TOOL_NAME,
            description = "Return the fixed benchmark timestamp converted to the requested IANA time zone."
    )
    public TimeSnapshot fixedTimeForZone(
            @ToolParam(required = true, description = "IANA time zone, such as America/Los_Angeles or UTC.")
                    String zoneId) {
        return snapshot(ZoneId.of(Objects.requireNonNull(zoneId, "zoneId is required")));
    }

    private TimeSnapshot snapshot(ZoneId zoneId) {
        Instant instant = clock.instant();
        ZonedDateTime zonedDateTime = instant.atZone(zoneId);
        return new TimeSnapshot(
                instant.toString(),
                zoneId.getId(),
                zonedDateTime.toLocalDate().toString(),
                zonedDateTime.toLocalTime().format(DateTimeFormatter.ISO_LOCAL_TIME),
                zonedDateTime.getDayOfWeek().name()
        );
    }

    public record TimeSnapshot(
            String instant,
            String zoneId,
            String localDate,
            String localTime,
            String dayOfWeek
    ) {}
}
