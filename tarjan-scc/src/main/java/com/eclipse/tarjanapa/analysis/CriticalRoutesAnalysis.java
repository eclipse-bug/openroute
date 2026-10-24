package com.eclipse.tarjanapa.analysis;

import com.eclipse.tarjanapa.algorithms.SccResult;
import com.eclipse.tarjanapa.graph.TransitGraph;
import com.eclipse.tarjanapa.model.Route;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Clasamentul rutelor critice: pentru fiecare rută se simulează închiderea ei
 * (doar a ei) și se numără stațiile care ies din componenta mare.
 * <p>
 * Cost: R simulări × O(V + E), unde R = numărul de rute. Pentru Chișinău
 * (câteva sute de rute, câteva mii de stații) durează sub o secundă.
 */
public final class CriticalRoutesAnalysis {

    private final ClosureAnalysis closure;

    public CriticalRoutesAnalysis(ClosureAnalysis closure) {
        this.closure = closure;
    }

    /**
     * @param lostStops     stații care ies din componenta mare la închiderea rutei
     * @param sccAfter      numărul de SCC-uri după închidere
     * @param largestAfter  dimensiunea celei mai mari SCC după închidere
     */
    public record RouteImpact(int rank, String routeId, String label, String type, String longName,
                              int lostStops, int sccAfter, int largestAfter) {
    }

    /** Toate rutele cu cel puțin un arc, sortate descrescător după stațiile pierdute. */
    public List<RouteImpact> rank(TransitGraph base, SccResult baseScc) {
        boolean[] hasEdges = new boolean[base.routes().size()];
        for (int e = 0; e < base.edgeCount(); e++) {
            for (int r : base.edgeRoutes(e)) {
                hasEdges[r] = true;
            }
        }

        List<RouteImpact> impacts = new ArrayList<>();
        for (int r = 0; r < hasEdges.length; r++) {
            if (!hasEdges[r]) {
                continue;
            }
            Route route = base.route(r);
            ClosureAnalysis.ClosureResult res = closure.simulate(base, baseScc, Set.of(route.id()));
            impacts.add(new RouteImpact(0, route.id(), route.label(), route.typeName(), route.longName(),
                    res.detachedCount(), res.after().sccCount(), res.after().largestSize()));
        }
        impacts.sort(Comparator.comparingInt(RouteImpact::lostStops).reversed()
                .thenComparing(RouteImpact::label, CriticalRoutesAnalysis::compareLabels));

        List<RouteImpact> ranked = new ArrayList<>(impacts.size());
        for (int i = 0; i < impacts.size(); i++) {
            RouteImpact x = impacts.get(i);
            ranked.add(new RouteImpact(i + 1, x.routeId(), x.label(), x.type(), x.longName(),
                    x.lostStops(), x.sccAfter(), x.largestAfter()));
        }
        return List.copyOf(ranked);
    }

    /** Ordonare „naturală” a numerelor de rută: 2 < 10 < 10A. */
    public static int compareLabels(String a, String b) {
        int na = leadingNumber(a);
        int nb = leadingNumber(b);
        if (na != nb) {
            return Integer.compare(na, nb);
        }
        return a.compareTo(b);
    }

    private static int leadingNumber(String s) {
        int i = 0;
        while (i < s.length() && Character.isDigit(s.charAt(i))) {
            i++;
        }
        if (i == 0 || i > 9) {
            return Integer.MAX_VALUE;
        }
        return Integer.parseInt(s.substring(0, i));
    }
}
