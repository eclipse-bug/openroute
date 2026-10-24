package com.eclipse.tarjanapa.analysis;

import com.eclipse.tarjanapa.algorithms.SccAlgorithm;
import com.eclipse.tarjanapa.algorithms.SccResult;
import com.eclipse.tarjanapa.algorithms.TarjanIterative;
import com.eclipse.tarjanapa.graph.TransitGraph;
import com.eclipse.tarjanapa.model.Route;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Simularea închiderii uneia sau mai multor rute.
 * <p>
 * Se elimină rutele din graf ({@link TransitGraph#withoutRoutes}), se recalculează
 * SCC-urile și se compară cu starea inițială: o stație s-a „desprins” dacă era
 * în cea mai mare SCC înainte și nu mai este în cea mai mare SCC după închidere.
 * Indicii stațiilor rămân aceiași, deci diferența se face direct pe indici.
 * <p>
 * Cost: un Tarjan, O(V + E), per simulare.
 */
public final class ClosureAnalysis {

    private final SccAlgorithm algorithm;

    public ClosureAnalysis() {
        this(new TarjanIterative());
    }

    public ClosureAnalysis(SccAlgorithm algorithm) {
        this.algorithm = algorithm;
    }

    /**
     * @param closedRoutes   rutele închise efectiv (cele recunoscute)
     * @param unknownRoutes  id-uri primite care nu există în date
     * @param before         starea înainte (doar cifrele, fără lista stațiilor)
     * @param after          starea după închidere
     * @param detachedStops  stațiile care au ieșit din componenta mare
     */
    public record ClosureResult(List<RouteRef> closedRoutes, List<String> unknownRoutes,
                                Summary before, Summary after, List<StopInfo> detachedStops,
                                SccResult sccAfter, TransitGraph graphAfter) {

        public int detachedCount() {
            return detachedStops.size();
        }
    }

    /** Referință scurtă la o rută. */
    public record RouteRef(String id, String label, String type) {
        static RouteRef of(Route r) {
            return new RouteRef(r.id(), r.label(), r.typeName());
        }
    }

    /** Cifrele principale ale unei stări. */
    public record Summary(int edges, int sccCount, int largestSize, double largestPercent) {
        static Summary of(TransitGraph g, SccResult scc) {
            double pct = g.stopCount() == 0 ? 0 : 100.0 * scc.largestSize() / g.stopCount();
            return new Summary(g.edgeCount(), scc.count(), scc.largestSize(), pct);
        }
    }

    public ClosureResult simulate(TransitGraph base, SccResult baseScc, Set<String> routeIds) {
        List<RouteRef> closed = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        Set<String> valid = new LinkedHashSet<>();
        for (String id : routeIds) {
            int r = base.routeIndexOf(id);
            if (r < 0) {
                unknown.add(id);
            } else if (valid.add(id)) {
                closed.add(RouteRef.of(base.route(r)));
            }
        }

        TransitGraph after = base.withoutRoutes(valid);
        SccResult afterScc = algorithm.run(after.intGraph());

        int giantBefore = baseScc.largestComponent();
        // Dacă după închidere toate componentele au o singură stație, nu mai există
        // o „componentă mare”: toate stațiile ei de dinainte s-au desprins.
        int giantAfter = afterScc.largestSize() > 1 ? afterScc.largestComponent() : -1;
        List<StopInfo> detached = new ArrayList<>();
        for (int v = 0; v < base.stopCount(); v++) {
            boolean wasInGiant = baseScc.componentOf()[v] == giantBefore;
            boolean isInGiant = afterScc.componentOf()[v] == giantAfter;
            if (wasInGiant && !isInGiant) {
                // Rutele afișate sunt cele din graful inițial: ce deservea stația înainte.
                StopInfo now = StopInfo.of(after, afterScc, v);
                detached.add(new StopInfo(now.id(), now.name(), now.lat(), now.lon(), now.component(),
                        now.componentSize(), base.routeLabelsAtStop(v)));
            }
        }
        detached.sort(Comparator.comparing(StopInfo::name, Comparator.nullsLast(String::compareTo)));

        return new ClosureResult(closed, unknown, Summary.of(base, baseScc), Summary.of(after, afterScc),
                List.copyOf(detached), afterScc, after);
    }
}
