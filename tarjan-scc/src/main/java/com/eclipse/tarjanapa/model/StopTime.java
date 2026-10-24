package com.eclipse.tarjanapa.model;

/** Trecerea unei curse printr-o stație (GTFS stop_times.txt). */
public record StopTime(String tripId, String stopId, int sequence) {
}
