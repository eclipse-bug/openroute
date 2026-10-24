package com.eclipse.tarjanapa.analysis;

import com.eclipse.tarjanapa.algorithms.Condensation;
import com.eclipse.tarjanapa.algorithms.SccResult;
import com.eclipse.tarjanapa.algorithms.TarjanBridges;
import com.eclipse.tarjanapa.graph.TransitGraph;
import com.eclipse.tarjanapa.model.Stop;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 * Analize de structură: punți / puncte de articulație și graful condensat,
 * traduse din indici în stații și rute.
 */
public final class StructureAnalysis {

    private StructureAnalysis() {
    }

    // ---------------------------------------------------------------- punți

    /** O punte: segmentul A — B, fără alternativă în rețeaua neorientată. */
    public record BridgeInfo(String fromId, String fromName, String toId, String toName,
                             double fromLat, double fromLon, double toLat, double toLon,
                             List<String> routes) {
    }

    public record BridgeReport(int bridgeCount, int articulationCount,
                               List<BridgeInfo> bridges, List<StopInfo> articulationPoints) {
    }

    public static BridgeReport bridges(TransitGraph g, SccResult scc) {
        TarjanBridges.Result r = TarjanBridges.compute(g.intGraph());

        List<BridgeInfo> bridges = new ArrayList<>();
        for (TarjanBridges.Bridge b : r.bridges()) {
            Stop a = g.stop(b.u());
            Stop c = g.stop(b.v());
            bridges.add(new BridgeInfo(a.id(), a.name(), c.id(), c.name(), a.lat(), a.lon(), c.lat(), c.lon(),
                    routesBetween(g, b.u(), b.v())));
        }
        bridges.sort(Comparator.comparing(BridgeInfo::fromName, Comparator.nullsLast(String::compareTo)));

        List<StopInfo> points = new ArrayList<>();
        for (int v : r.articulationPoints()) {
            points.add(StopInfo.of(g, scc, v));
        }
        points.sort(Comparator.comparing(StopInfo::name, Comparator.nullsLast(String::compareTo)));
        return new BridgeReport(bridges.size(), points.size(), List.copyOf(bridges), List.copyOf(points));
    }

    /** Rutele de pe muchia neorientată u — v (reuniunea arcelor u → v și v → u). */
    private static List<String> routesBetween(TransitGraph g, int u, int v) {
        TreeSet<String> labels = new TreeSet<>(CriticalRoutesAnalysis::compareLabels);
        for (int e : new int[]{g.edgeId(u, v), g.edgeId(v, u)}) {
            if (e >= 0) {
                for (int r : g.edgeRoutes(e)) {
                    labels.add(g.route(r).label());
                }
            }
        }
        return List.copyOf(labels);
    }

    // --------------------------------------------------------- condensare

    /** O componentă din graful condensat. */
    public record ComponentInfo(int id, int size, int inDegree, int outDegree,
                                boolean source, boolean sink, List<String> sampleStops) {
    }

    /**
     * @param minEdgesToAdd numărul minim de arce de adăugat ca rețeaua să devină tare conexă
     * @param components    componentele, descrescător după mărime
     */
    public record CondensationReport(int componentCount, int dagEdges, int sources, int sinks,
                                     int isolated, int minEdgesToAdd, List<ComponentInfo> components) {
    }

    public static CondensationReport condensation(TransitGraph g, SccResult scc) {
        Condensation c = Condensation.of(g.intGraph(), scc);
        List<ComponentInfo> comps = new ArrayList<>();
        int isolated = 0;
        for (int k = 0; k < c.componentCount(); k++) {
            int[] nodes = scc.components().get(k);
            List<String> sample = new ArrayList<>();
            for (int i = 0; i < Math.min(5, nodes.length); i++) {
                sample.add(g.stop(nodes[i]).name());
            }
            boolean src = c.inDegree(k) == 0;
            boolean snk = c.outDegree(k) == 0;
            if (src && snk) {
                isolated++;
            }
            comps.add(new ComponentInfo(k, nodes.length, c.inDegree(k), c.outDegree(k), src, snk, sample));
        }
        comps.sort(Comparator.comparingInt(ComponentInfo::size).reversed().thenComparingInt(ComponentInfo::id));
        return new CondensationReport(c.componentCount(), c.dag().edgeCount(), c.sources().size(),
                c.sinks().size(), isolated, c.minEdgesToStronglyConnect(), List.copyOf(comps));
    }
}
