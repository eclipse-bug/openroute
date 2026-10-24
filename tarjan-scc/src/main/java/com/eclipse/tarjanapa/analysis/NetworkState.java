package com.eclipse.tarjanapa.analysis;

import com.eclipse.tarjanapa.algorithms.SccResult;
import com.eclipse.tarjanapa.graph.TransitGraph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Starea conectivității rețelei.
 *
 * @param stops                 V
 * @param edges                 E
 * @param averageDegree         E / V
 * @param activeRoutes          rute cu cel puțin un arc
 * @param sccCount              numărul de componente tare conexe
 * @param largestComponent      indicele celei mai mari SCC (-1 la graf gol)
 * @param largestSize           dimensiunea celei mai mari SCC
 * @param largestPercent        % din stații aflate în cea mai mare SCC
 * @param smallComponentCount   numărul celorlalte SCC-uri
 * @param smallComponentStops   stațiile din afara celei mai mari SCC, grupate pe componente
 */
public record NetworkState(int stops, int edges, double averageDegree, int activeRoutes,
                           int sccCount, int largestComponent, int largestSize, double largestPercent,
                           int smallComponentCount, List<StopInfo> smallComponentStops) {

    /**
     * Calculează starea din graf și rezultatul SCC deja calculat.
     * Stațiile din SCC-uri mici sunt sortate după mărimea componentei, apoi după nume.
     */
    public static NetworkState of(TransitGraph g, SccResult scc) {
        int largest = scc.largestComponent();
        int largestSize = scc.largestSize();
        List<StopInfo> small = new ArrayList<>();
        for (int v = 0; v < g.stopCount(); v++) {
            if (scc.componentOf()[v] != largest) {
                small.add(StopInfo.of(g, scc, v));
            }
        }
        small.sort(Comparator.comparingInt(StopInfo::componentSize).reversed()
                .thenComparingInt(StopInfo::component)
                .thenComparing(StopInfo::name, Comparator.nullsLast(String::compareTo)));
        TransitGraph.GraphStats st = g.stats();
        double pct = g.stopCount() == 0 ? 0.0 : 100.0 * largestSize / g.stopCount();
        return new NetworkState(st.stops(), st.edges(), st.averageDegree(), st.activeRoutes(),
                scc.count(), largest, largestSize, pct, Math.max(0, scc.count() - 1), List.copyOf(small));
    }
}
