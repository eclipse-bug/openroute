package com.eclipse.tarjanapa.analysis;

import com.eclipse.tarjanapa.algorithms.SccAlgorithm;
import com.eclipse.tarjanapa.algorithms.SccResult;
import com.eclipse.tarjanapa.algorithms.TarjanIterative;
import com.eclipse.tarjanapa.graph.TransitGraph;
import com.eclipse.tarjanapa.model.Vehicle;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Rețeaua „live”: se consideră active doar rutele cu cel puțin un vehicul în
 * circulație (din /vehicles), iar analiza SCC se rulează pe graful restrâns la ele.
 * Stațiile care nu sunt deservite de nicio rută activă se scot din graf: altfel
 * ar apărea ca sute de SCC-uri izolate artificiale.
 */
public final class LiveAnalysis {

    private final SccAlgorithm algorithm;

    public LiveAnalysis() {
        this(new TarjanIterative());
    }

    public LiveAnalysis(SccAlgorithm algorithm) {
        this.algorithm = algorithm;
    }

    /**
     * @param vehicles          vehicule raportate
     * @param vehiclesOnRoutes  vehicule asociate unei rute cunoscute
     * @param activeRouteIds    rutele active
     * @param activeRouteLabels numerele rutelor active
     * @param inactiveStops     stații fără nicio rută activă (excluse din analiză)
     */
    public record LiveResult(int vehicles, int vehiclesOnRoutes, List<String> activeRouteIds,
                             List<String> activeRouteLabels, int inactiveStops, NetworkState state,
                             TransitGraph graph, SccResult scc) {
    }

    public LiveResult analyze(TransitGraph base, List<Vehicle> vehicles) {
        Set<String> active = new LinkedHashSet<>();
        int onRoutes = 0;
        for (Vehicle v : vehicles) {
            if (v.routeId() != null && base.routeIndexOf(v.routeId()) >= 0) {
                active.add(v.routeId());
                onRoutes++;
            }
        }
        TransitGraph restricted = base.onlyRoutes(active);
        Set<String> inactive = new HashSet<>();
        for (int v = 0; v < restricted.stopCount(); v++) {
            if (restricted.routesAtStop(v).length == 0) {
                inactive.add(restricted.stop(v).id());
            }
        }
        TransitGraph live = restricted.withoutStops(inactive);
        SccResult scc = algorithm.run(live.intGraph());

        List<String> labels = new ArrayList<>();
        for (String id : active) {
            labels.add(base.route(base.routeIndexOf(id)).label());
        }
        labels.sort(CriticalRoutesAnalysis::compareLabels);
        return new LiveResult(vehicles.size(), onRoutes, List.copyOf(active), List.copyOf(labels), inactive.size(),
                NetworkState.of(live, scc), live, scc);
    }
}
