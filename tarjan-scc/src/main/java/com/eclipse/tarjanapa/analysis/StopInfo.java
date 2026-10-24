package com.eclipse.tarjanapa.analysis;

import com.eclipse.tarjanapa.algorithms.SccResult;
import com.eclipse.tarjanapa.graph.TransitGraph;
import com.eclipse.tarjanapa.model.Stop;

import java.util.List;

/**
 * O stație, cu componenta ei tare conexă și rutele care o deservesc — forma
 * folosită în rapoarte, în consolă și în API.
 */
public record StopInfo(String id, String name, double lat, double lon,
                       int component, int componentSize, List<String> routes) {

    public static StopInfo of(TransitGraph g, SccResult scc, int v) {
        Stop s = g.stop(v);
        int c = scc.componentOf()[v];
        return new StopInfo(s.id(), s.name(), s.lat(), s.lon(), c,
                scc.components().get(c).length, g.routeLabelsAtStop(v));
    }
}
