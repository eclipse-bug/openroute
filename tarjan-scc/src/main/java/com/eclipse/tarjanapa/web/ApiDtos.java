package com.eclipse.tarjanapa.web;

import com.eclipse.tarjanapa.analysis.ClosureAnalysis;
import com.eclipse.tarjanapa.analysis.StopInfo;

import java.time.Instant;
import java.util.List;

/** Obiectele JSON întoarse de API (separate de modelul intern, care conține grafuri și tablouri). */
public final class ApiDtos {

    private ApiDtos() {
    }

    public record Status(boolean loaded, String agencyId, boolean apiKeyConfigured, String error,
                         Instant loadedAt, List<String> warnings, List<Source> sources) {
    }

    public record Source(String endpoint, int records, String origin, Instant fetchedAt) {
    }

    public record Summary(String agencyId, Instant loadedAt, int stops, int edges, double averageDegree,
                          int routes, int activeRoutes, int sccCount, int largestSccSize, double largestSccPercent,
                          int smallComponentCount, int unservedStops, double tarjanMillis,
                          List<StopInfo> smallComponentStops) {
    }

    public record StopDto(String id, String name, double lat, double lon, int scc, int sccSize,
                          boolean inLargest, List<String> routes) {
    }

    public record EdgeDto(String from, String to, List<String> routes) {
    }

    public record RouteDto(String id, String label, String longName, String type, int edges) {
    }

    public record CloseRequest(List<String> routeIds, Boolean liveOnly) {
    }

    public record ClosureDto(List<ClosureAnalysis.RouteRef> closedRoutes, List<String> unknownRoutes,
                             boolean liveOnly, ClosureAnalysis.Summary before, ClosureAnalysis.Summary after,
                             int detachedCount, List<StopInfo> detachedStops) {
    }

    public record LiveDto(int vehicles, int vehiclesOnRoutes, List<String> activeRouteIds,
                          List<String> activeRouteLabels, int inactiveStops, int stops, int edges, int sccCount,
                          int largestSccSize, double largestSccPercent, List<StopInfo> smallComponentStops,
                          List<StopDto> stopComponents) {
    }

    public record BenchmarkRequest(List<Integer> sizes, Double avgDegree, Long seed, Integer runs,
                                   Integer warmupRuns, Integer naiveMaxNodes) {
    }

    public record ErrorDto(String error, String kind) {
    }
}
