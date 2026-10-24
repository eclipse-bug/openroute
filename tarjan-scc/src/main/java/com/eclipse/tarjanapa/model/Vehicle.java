package com.eclipse.tarjanapa.model;

/** Poziția în timp real a unui vehicul (Tranzy /vehicles). routeId poate fi null. */
public record Vehicle(String id, String label, double lat, double lon, String routeId, String tripId,
                      String timestamp) {
}
