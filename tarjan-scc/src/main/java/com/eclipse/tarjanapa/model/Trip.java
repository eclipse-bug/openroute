package com.eclipse.tarjanapa.model;

/**
 * Cursă (GTFS trips.txt).
 *
 * @param directionId 0/1 (tur/retur); -1 dacă lipsește
 */
public record Trip(String id, String routeId, int directionId, String headsign) {
}
