package com.eclipse.tarjanapa.model;

import java.util.List;

/** Setul de date static GTFS necesar construirii grafului. */
public record GtfsDataset(List<Route> routes, List<Trip> trips, List<Stop> stops, List<StopTime> stopTimes) {

    public GtfsDataset {
        routes = List.copyOf(routes);
        trips = List.copyOf(trips);
        stops = List.copyOf(stops);
        stopTimes = List.copyOf(stopTimes);
    }
}
