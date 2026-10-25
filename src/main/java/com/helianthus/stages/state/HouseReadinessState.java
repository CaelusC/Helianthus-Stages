package com.helianthus.stages.state;

/**
 * Readiness flag for a single house. {@code via} is one of "quest", "command" or "admin".
 */
public record HouseReadinessState(boolean ready, long atEpochMs, String via)
{
}
