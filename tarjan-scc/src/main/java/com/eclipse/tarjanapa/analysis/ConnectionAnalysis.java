package com.eclipse.tarjanapa.analysis;

import com.eclipse.tarjanapa.algorithms.SccResult;
import com.eclipse.tarjanapa.graph.TransitGraph;
import com.eclipse.tarjanapa.model.Route;
import com.eclipse.tarjanapa.model.Stop;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;

/**
 * Conexiunea dintre două stații A și B.
 * <p>
 * Legătura cu SCC-urile: A și B sunt în aceeași componentă tare conexă exact
 * când se poate ajunge și din A în B, și din B înapoi în A. Dacă sunt în
 * componente diferite, drumul există cel mult într-un singur sens.
 * <p>
 * Pentru fiecare sens se caută un drum după unul dintre criterii ({@link Mode}):
 * <ul>
 *   <li>{@link Mode#STOPS} — cele mai puține stații: BFS pe graful stațiilor,
 *       O(V + E); drumul se împarte apoi greedy în tronsoane pe rute;</li>
 *   <li>{@link Mode#TRANSFERS} — cele mai puține schimbări de rută, iar la
 *       egalitate cele mai puține stații: Dijkstra pe graful extins cu stările
 *       (stație, ruta în care ești), O((V·R + E·R) log(V·R)).</li>
 * </ul>
 */
public final class ConnectionAnalysis {

    /** Criteriul după care se alege drumul. */
    public enum Mode {
        /** Cele mai puține stații (BFS). */
        STOPS,
        /** Cele mai puține schimbări de rută, apoi cele mai puține stații (Dijkstra). */
        TRANSFERS;

        /** „stops” / „transfers”; gol ⇒ TRANSFERS. */
        public static Mode parse(String s) {
            if (s == null || s.isBlank()) {
                return TRANSFERS;
            }
            return switch (s.trim().toLowerCase(Locale.ROOT)) {
                case "stops", "statii" -> STOPS;
                case "transfers", "schimbari" -> TRANSFERS;
                default -> throw new IllegalArgumentException(
                        "Criteriu necunoscut: „" + s + "”. Valori posibile: transfers, stops.");
            };
        }
    }

    private ConnectionAnalysis() {
    }

    /** O stație de pe drum. */
    public record StopRef(String id, String name, double lat, double lon) {
        static StopRef of(Stop s) {
            return new StopRef(s.id(), s.name(), s.lat(), s.lon());
        }
    }

    /** Un tronson parcurs cu aceeași rută; {@code segments} = numărul de segmente parcurse. */
    public record Leg(String routeId, String routeLabel, String routeType, int segments, List<StopRef> stops) {
    }

    /**
     * Drumul într-un sens; {@code found = false} dacă destinația nu e accesibilă.
     *
     * @param segments  numărul de segmente (stații parcurse)
     * @param transfers numărul de schimbări de rută (= tronsoane − 1)
     * @param stops     toate stațiile, de la plecare la sosire
     * @param legs      tronsoanele pe rute
     */
    public record Path(boolean found, int segments, int transfers, List<StopRef> stops, List<Leg> legs) {
        static final Path NONE = new Path(false, 0, 0, List.of(), List.of());
    }

    /**
     * @param mode          criteriul folosit
     * @param sameComponent A și B sunt în aceeași SCC (ambele sensuri posibile)
     * @param forward       drumul A → B
     * @param backward      drumul B → A
     * @param explanation   concluzia, în română
     */
    public record Connection(StopRef from, StopRef to, Mode mode, int fromComponent, int toComponent,
                             boolean sameComponent, Path forward, Path backward, String explanation) {
    }

    /** Conexiunea cu criteriul implicit (cele mai puține schimbări). */
    public static Connection find(TransitGraph g, SccResult scc, String fromId, String toId) {
        return find(g, scc, fromId, toId, Mode.TRANSFERS);
    }

    /**
     * @throws IllegalArgumentException dacă una dintre stații nu este în graf
     */
    public static Connection find(TransitGraph g, SccResult scc, String fromId, String toId, Mode mode) {
        int a = g.indexOf(fromId);
        int b = g.indexOf(toId);
        if (a < 0) {
            throw new IllegalArgumentException("Stația de plecare „" + fromId + "” nu este în rețea.");
        }
        if (b < 0) {
            throw new IllegalArgumentException("Stația de sosire „" + toId + "” nu este în rețea.");
        }
        int ca = scc.componentOf()[a];
        int cb = scc.componentOf()[b];
        Path forward = mode == Mode.STOPS ? fewestStops(g, a, b) : fewestTransfers(g, a, b);
        Path backward = mode == Mode.STOPS ? fewestStops(g, b, a) : fewestTransfers(g, b, a);
        boolean same = ca == cb;

        // Verificare de consistență: SCC-urile și căutarea drumului trebuie să spună același lucru.
        if (same != (forward.found() && backward.found())) {
            throw new IllegalStateException("Rezultatul SCC nu corespunde accesibilității calculate direct.");
        }

        return new Connection(StopRef.of(g.stop(a)), StopRef.of(g.stop(b)), mode, ca, cb, same, forward, backward,
                explain(g.stop(a).name(), g.stop(b).name(), a == b, same, forward, backward));
    }

    private static String explain(String from, String to, boolean identical, boolean same, Path fwd, Path back) {
        if (identical) {
            return "Plecarea și sosirea sunt aceeași stație.";
        }
        if (same) {
            return "„" + from + "” și „" + to + "” sunt în aceeași componentă tare conexă: "
                    + "se poate ajunge în ambele sensuri.";
        }
        if (fwd.found()) {
            return "Se poate ajunge din „" + from + "” în „" + to + "”, dar nu și înapoi: "
                    + "stațiile sunt în componente tare conexe diferite.";
        }
        if (back.found()) {
            return "Nu se poate ajunge din „" + from + "” în „" + to + "”, ci doar invers: "
                    + "stațiile sunt în componente tare conexe diferite.";
        }
        return "Nu există drum între „" + from + "” și „" + to + "” în niciun sens.";
    }

    private static Path sameStop(TransitGraph g, int s) {
        return new Path(true, 0, 0, List.of(StopRef.of(g.stop(s))), List.of());
    }

    // ------------------------------------------------ cele mai puține stații

    /** BFS: drumul cu cele mai puține segmente de la s la t. */
    static Path fewestStops(TransitGraph g, int s, int t) {
        if (s == t) {
            return sameStop(g, s);
        }
        int n = g.stopCount();
        int[] parent = new int[n];
        Arrays.fill(parent, -1);
        parent[s] = s;
        int[] queue = new int[n];
        int head = 0;
        int tail = 0;
        queue[tail++] = s;
        while (head < tail && parent[t] == -1) {
            int u = queue[head++];
            for (int w : g.intGraph().neighbors(u)) {
                if (parent[w] == -1) {
                    parent[w] = u;
                    queue[tail++] = w;
                }
            }
        }
        if (parent[t] == -1) {
            return Path.NONE;
        }

        List<Integer> nodes = new ArrayList<>();
        for (int v = t; v != s; v = parent[v]) {
            nodes.add(v);
        }
        nodes.add(s);
        Collections.reverse(nodes);
        return buildPath(g, nodes, greedyRoutes(g, nodes));
    }

    /**
     * Alege ruta fiecărui segment pe un drum fixat: de fiecare dată ruta care
     * continuă cele mai multe segmente consecutive.
     */
    private static int[] greedyRoutes(TransitGraph g, List<Integer> nodes) {
        int segments = nodes.size() - 1;
        int[] edge = new int[segments];
        for (int i = 0; i < segments; i++) {
            edge[i] = g.edgeId(nodes.get(i), nodes.get(i + 1));
        }
        int[] routeOf = new int[segments];
        int i = 0;
        while (i < segments) {
            int bestRoute = -1;
            int bestEnd = i;
            for (int r : g.edgeRoutes(edge[i])) {
                int end = i;
                while (end < segments && contains(g.edgeRoutes(edge[end]), r)) {
                    end++;
                }
                if (end > bestEnd) {
                    bestEnd = end;
                    bestRoute = r;
                }
            }
            Arrays.fill(routeOf, i, bestEnd, bestRoute);
            i = bestEnd;
        }
        return routeOf;
    }

    // ---------------------------------------------- cele mai puține schimbări

    /**
     * Dijkstra pe stările (stație, rută curentă). Trecerea pe un segment cu
     * aceeași rută costă 1; cu altă rută costă 1 + PENALTY, unde PENALTY > numărul
     * maxim de segmente — astfel costul ordonează lexicografic (schimbări, stații).
     * Prima urcare (din stația de plecare) nu este o schimbare.
     */
    static Path fewestTransfers(TransitGraph g, int s, int t) {
        if (s == t) {
            return sameStop(g, s);
        }
        int n = g.stopCount();
        int routes = g.routes().size();
        long penalty = n + 1L;
        int states = n * routes;          // starea (u, r) = u * routes + r

        long[] dist = new long[states];
        int[] prev = new int[states];     // starea anterioară; -1 = plecare din s
        Arrays.fill(dist, Long.MAX_VALUE);
        PriorityQueue<long[]> pq = new PriorityQueue<>((x, y) -> Long.compare(x[0], y[0]));

        // Plecarea: urcarea în oricare rută care pleacă din s.
        for (int w : g.intGraph().neighbors(s)) {
            int e = g.edgeId(s, w);
            for (int r : g.edgeRoutes(e)) {
                int st = w * routes + r;
                if (1 < dist[st]) {
                    dist[st] = 1;
                    prev[st] = -1;
                    pq.add(new long[]{1, st});
                }
            }
        }

        int best = -1;
        while (!pq.isEmpty()) {
            long[] top = pq.poll();
            int st = (int) top[1];
            if (top[0] > dist[st]) {
                continue;
            }
            int u = st / routes;
            int r = st % routes;
            if (u == t) {
                best = st;   // prima stare scoasă cu stația t are costul minim
                break;
            }
            for (int w : g.intGraph().neighbors(u)) {
                int e = g.edgeId(u, w);
                for (int r2 : g.edgeRoutes(e)) {
                    long nd = top[0] + 1 + (r2 == r ? 0 : penalty);
                    int st2 = w * routes + r2;
                    if (nd < dist[st2]) {
                        dist[st2] = nd;
                        prev[st2] = st;
                        pq.add(new long[]{nd, st2});
                    }
                }
            }
        }
        if (best < 0) {
            return Path.NONE;
        }

        // Reconstrucția: stațiile și ruta fiecărui segment.
        List<Integer> nodes = new ArrayList<>();
        List<Integer> segRoutes = new ArrayList<>();
        for (int st = best; st != -1; st = prev[st]) {
            nodes.add(st / routes);
            segRoutes.add(st % routes);
        }
        nodes.add(s);
        Collections.reverse(nodes);
        Collections.reverse(segRoutes);
        return buildPath(g, nodes, segRoutes.stream().mapToInt(Integer::intValue).toArray());
    }

    // ------------------------------------------------------------- comun

    /** Grupează segmentele consecutive cu aceeași rută în tronsoane. */
    private static Path buildPath(TransitGraph g, List<Integer> nodes, int[] routeOf) {
        List<StopRef> stops = nodes.stream().map(v -> StopRef.of(g.stop(v))).toList();
        List<Leg> legs = new ArrayList<>();
        int i = 0;
        while (i < routeOf.length) {
            int end = i;
            while (end < routeOf.length && routeOf[end] == routeOf[i]) {
                end++;
            }
            Route route = g.route(routeOf[i]);
            legs.add(new Leg(route.id(), route.label(), route.typeName(), end - i, stops.subList(i, end + 1)));
            i = end;
        }
        return new Path(true, stops.size() - 1, Math.max(0, legs.size() - 1), stops, List.copyOf(legs));
    }

    /** Rutele unui arc sunt sortate, deci căutarea e binară. */
    private static boolean contains(int[] sortedRoutes, int r) {
        return Arrays.binarySearch(sortedRoutes, r) >= 0;
    }
}
