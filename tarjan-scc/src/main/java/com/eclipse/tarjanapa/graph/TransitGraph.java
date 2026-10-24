package com.eclipse.tarjanapa.graph;

import com.eclipse.tarjanapa.algorithms.IntGraph;
import com.eclipse.tarjanapa.model.Route;
import com.eclipse.tarjanapa.model.Stop;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * Graful rețelei de transport.
 * <ul>
 *   <li>Nod = stație ({@code stop_id}), identificată intern printr-un indice 0..V-1.</li>
 *   <li>Arc orientat A → B = două stații consecutive din aceeași cursă.</li>
 *   <li>Fiecare arc reține mulțimea rutelor care trec pe el (ca indici de rută, sortați).</li>
 * </ul>
 * Arcele sunt unice: dacă mai multe curse/rute trec pe A → B, există un singur
 * arc, cu mulțimea reunită a rutelor. Structura e imutabilă; operațiile de
 * „închidere” întorc un graf nou.
 */
public final class TransitGraph {

    private final List<Stop> stops;
    private final Map<String, Integer> stopIndex;
    private final List<Route> routes;
    private final Map<String, Integer> routeIndex;

    // Arcele, ca tablouri paralele: arcul e este edgeFrom[e] → edgeTo[e].
    private final int[] edgeFrom;
    private final int[] edgeTo;
    private final int[][] edgeRoutes;

    private final IntGraph graph;
    private final Map<Long, Integer> edgeIdByPair;
    private int[][] routesAtStop; // calculat la cerere

    TransitGraph(List<Stop> stops, List<Route> routes, int[] edgeFrom, int[] edgeTo, int[][] edgeRoutes) {
        this.stops = List.copyOf(stops);
        this.routes = List.copyOf(routes);
        this.edgeFrom = edgeFrom;
        this.edgeTo = edgeTo;
        this.edgeRoutes = edgeRoutes;

        this.stopIndex = new HashMap<>(stops.size() * 2);
        for (int i = 0; i < this.stops.size(); i++) {
            stopIndex.put(this.stops.get(i).id(), i);
        }
        this.routeIndex = new HashMap<>(routes.size() * 2);
        for (int i = 0; i < this.routes.size(); i++) {
            routeIndex.put(this.routes.get(i).id(), i);
        }

        IntGraph.Builder b = IntGraph.builder(this.stops.size());
        this.edgeIdByPair = new HashMap<>(edgeFrom.length * 2);
        for (int e = 0; e < edgeFrom.length; e++) {
            b.addEdge(edgeFrom[e], edgeTo[e]);
            edgeIdByPair.put(pairKey(edgeFrom[e], edgeTo[e]), e);
        }
        this.graph = b.build();
    }

    private static long pairKey(int u, int v) {
        return ((long) u << 32) | (v & 0xffffffffL);
    }

    // ---------------------------------------------------------------- acces

    /** Graful „gol” (indici întregi) pe care rulează algoritmii. */
    public IntGraph intGraph() {
        return graph;
    }

    public int stopCount() {
        return stops.size();
    }

    public int edgeCount() {
        return edgeFrom.length;
    }

    public List<Stop> stops() {
        return stops;
    }

    public Stop stop(int index) {
        return stops.get(index);
    }

    /** Indicele stației cu id-ul dat, sau -1. */
    public int indexOf(String stopId) {
        return stopIndex.getOrDefault(stopId, -1);
    }

    /** Toate rutele din setul de date (inclusiv cele fără arce). */
    public List<Route> routes() {
        return routes;
    }

    public Route route(int routeIdx) {
        return routes.get(routeIdx);
    }

    /** Indicele rutei cu id-ul dat, sau -1. */
    public int routeIndexOf(String routeId) {
        return routeIndex.getOrDefault(routeId, -1);
    }

    public int edgeFrom(int e) {
        return edgeFrom[e];
    }

    public int edgeTo(int e) {
        return edgeTo[e];
    }

    /** Indicii rutelor care trec pe arcul e (tabloul nu trebuie modificat). */
    public int[] edgeRoutes(int e) {
        return edgeRoutes[e];
    }

    /** Id-ul arcului u → v, sau -1 dacă nu există. */
    public int edgeId(int u, int v) {
        return edgeIdByPair.getOrDefault(pairKey(u, v), -1);
    }

    /** Indicii rutelor care deservesc stația v (din arcele incidente, intrare și ieșire). */
    public int[] routesAtStop(int v) {
        if (routesAtStop == null) {
            computeRoutesAtStop();
        }
        return routesAtStop[v];
    }

    /** Etichetele (numerele) rutelor care deservesc stația v. */
    public List<String> routeLabelsAtStop(int v) {
        List<String> labels = new ArrayList<>();
        for (int r : routesAtStop(v)) {
            labels.add(routes.get(r).label());
        }
        return labels;
    }

    private synchronized void computeRoutesAtStop() {
        if (routesAtStop != null) {
            return;
        }
        List<BitSet> sets = new ArrayList<>(stops.size());
        for (int i = 0; i < stops.size(); i++) {
            sets.add(new BitSet());
        }
        for (int e = 0; e < edgeFrom.length; e++) {
            for (int r : edgeRoutes[e]) {
                sets.get(edgeFrom[e]).set(r);
                sets.get(edgeTo[e]).set(r);
            }
        }
        int[][] result = new int[stops.size()][];
        for (int i = 0; i < stops.size(); i++) {
            result[i] = sets.get(i).stream().toArray();
        }
        routesAtStop = result;
    }

    /** Numărul de rute care au cel puțin un arc în graf. */
    public int activeRouteCount() {
        BitSet used = new BitSet(routes.size());
        for (int[] rs : edgeRoutes) {
            for (int r : rs) {
                used.set(r);
            }
        }
        return used.cardinality();
    }

    /** Statistici de bază: V, E, grad mediu de ieșire (= E / V). */
    public GraphStats stats() {
        int v = stopCount();
        int e = edgeCount();
        return new GraphStats(v, e, v == 0 ? 0.0 : (double) e / v, activeRouteCount());
    }

    /**
     * @param stops         V
     * @param edges         E
     * @param averageDegree gradul mediu de ieșire E/V (egal cu cel de intrare)
     * @param activeRoutes  rute care au cel puțin un arc
     */
    public record GraphStats(int stops, int edges, double averageDegree, int activeRoutes) {
    }

    // ------------------------------------------------------- grafuri derivate

    /**
     * Graful fără rutele date: din fiecare arc se scot aceste rute; arcele care
     * rămân fără nicio rută dispar. Stațiile se păstrează toate (cu aceiași
     * indici), ca rezultatele să poată fi comparate direct cu graful inițial.
     */
    public TransitGraph withoutRoutes(Set<String> routeIds) {
        BitSet removed = new BitSet(routes.size());
        for (String id : routeIds) {
            int r = routeIndexOf(id);
            if (r >= 0) {
                removed.set(r);
            }
        }
        return filterRoutes(r -> !removed.get(r));
    }

    /** Graful care păstrează doar rutele date (de ex. rutele cu vehicule active). */
    public TransitGraph onlyRoutes(Set<String> routeIds) {
        BitSet kept = new BitSet(routes.size());
        for (String id : routeIds) {
            int r = routeIndexOf(id);
            if (r >= 0) {
                kept.set(r);
            }
        }
        return filterRoutes(kept::get);
    }

    private TransitGraph filterRoutes(IntPredicate keepRoute) {
        int m = edgeFrom.length;
        int[] from = new int[m];
        int[] to = new int[m];
        int[][] rs = new int[m][];
        int count = 0;
        for (int e = 0; e < m; e++) {
            int[] remaining = Arrays.stream(edgeRoutes[e]).filter(keepRoute).toArray();
            if (remaining.length > 0) {
                from[count] = edgeFrom[e];
                to[count] = edgeTo[e];
                rs[count] = remaining;
                count++;
            }
        }
        return new TransitGraph(stops, routes,
                Arrays.copyOf(from, count), Arrays.copyOf(to, count), Arrays.copyOf(rs, count));
    }

    /**
     * Graful fără stațiile date: stațiile se elimină cu totul (împreună cu toate
     * arcele incidente), iar nodurile rămase se renumerotează.
     */
    public TransitGraph withoutStops(Set<String> stopIds) {
        int[] newIndex = new int[stops.size()];
        List<Stop> kept = new ArrayList<>();
        for (int i = 0; i < stops.size(); i++) {
            if (stopIds.contains(stops.get(i).id())) {
                newIndex[i] = -1;
            } else {
                newIndex[i] = kept.size();
                kept.add(stops.get(i));
            }
        }
        int m = edgeFrom.length;
        int[] from = new int[m];
        int[] to = new int[m];
        int[][] rs = new int[m][];
        int count = 0;
        for (int e = 0; e < m; e++) {
            int a = newIndex[edgeFrom[e]];
            int b = newIndex[edgeTo[e]];
            if (a >= 0 && b >= 0) {
                from[count] = a;
                to[count] = b;
                rs[count] = edgeRoutes[e];
                count++;
            }
        }
        return new TransitGraph(kept, routes,
                Arrays.copyOf(from, count), Arrays.copyOf(to, count), Arrays.copyOf(rs, count));
    }
}
