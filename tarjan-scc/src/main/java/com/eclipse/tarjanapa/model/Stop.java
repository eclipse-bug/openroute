package com.eclipse.tarjanapa.model;

/** Stație (GTFS stops.txt). */
public record Stop(String id, String name, double lat, double lon) {
}
