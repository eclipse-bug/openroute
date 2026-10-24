package com.eclipse.tarjanapa.graph;

import com.eclipse.tarjanapa.model.GtfsDataset;
import com.eclipse.tarjanapa.model.Route;
import com.eclipse.tarjanapa.model.Stop;
import com.eclipse.tarjanapa.model.StopTime;
import com.eclipse.tarjanapa.model.Trip;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Construiește {@link TransitGraph} din datele GTFS.
 * <p>
 * Pași:
 * <ol>
 *   <li>Se grupează {@code stop_times} pe {@code trip_id} și se sortează după {@code stop_sequence}.</li>
 *   <li>Pentru fiecare pereche de stații consecutive A, B dintr-o cursă se adaugă
 *       arcul A → B, etichetat cu ruta cursei.</li>
 *   <li>Nodurile grafului sunt doar stațiile deservite de cel puțin o cursă;
 *       stațiile din {@code /stops} fără nicio trecere ar apărea altfel ca SCC-uri
 *       izolate artificiale.</li>
 * </ol>
 * Complexitate: O(S log S) pentru sortare, unde S = numărul de înregistrări stop_times.
 */
public final class GraphBuilder {

    private GraphBuilder() {
    }

    /** Graful și un raport despre datele ignorate (util pentru depanare și pentru raport). */
    public record BuildResult(TransitGraph graph, BuildReport report) {
    }

    /**
     * @param tripsUsed               curse care au produs cel puțin un arc
     * @param tripsWithoutRoute       curse al căror route_id lipsește din /routes (ignorate)
     * @param stopTimesWithoutTrip    stop_times cu trip_id necunoscut
     * @param stopTimesWithUnknownStop stop_times cu stop_id inexistent în /stops
     * @param unservedStops           stații fără nicio cursă (nu intră în graf)
     * @param selfLoopsSkipped        perechi consecutive cu aceeași stație (ignorate)
     */
    public record BuildReport(int tripsUsed, int tripsWithoutRoute, int stopTimesWithoutTrip,
                              int stopTimesWithUnknownStop, int unservedStops, int selfLoopsSkipped) {
    }

    public static BuildResult build(GtfsDataset data) {
        Map<String, Integer> routeIdx = new HashMap<>();
        List<Route> routes = data.routes();
        for (int i = 0; i < routes.size(); i++) {
            routeIdx.put(routes.get(i).id(), i);
        }

        Map<String, Integer> tripRoute = new HashMap<>();
        int tripsWithoutRoute = 0;
        for (Trip t : data.trips()) {
            Integer r = routeIdx.get(t.routeId());
            if (r == null) {
                tripsWithoutRoute++;
            } else {
                tripRoute.put(t.id(), r);
            }
        }

        Map<String, Stop> stopsById = new HashMap<>();
        for (Stop s : data.stops()) {
            stopsById.put(s.id(), s);
        }

        // 1. Gruparea pe curse.
        Map<String, List<StopTime>> byTrip = new LinkedHashMap<>();
        int withoutTrip = 0;
        int unknownStop = 0;
        for (StopTime st : data.stopTimes()) {
            if (!tripRoute.containsKey(st.tripId())) {
                withoutTrip++;
                continue;
            }
            if (!stopsById.containsKey(st.stopId())) {
                unknownStop++;
                continue;
            }
            byTrip.computeIfAbsent(st.tripId(), k -> new ArrayList<>()).add(st);
        }

        // 2. Arcele: cheie (A, B) → mulțimea rutelor. Indicii nodurilor se atribuie mai târziu.
        Map<String, Integer> tempIndex = new HashMap<>();
        List<String> tempIds = new ArrayList<>();
        Map<Long, BitSet> edges = new LinkedHashMap<>();
        int selfLoops = 0;
        int tripsUsed = 0;
        for (Map.Entry<String, List<StopTime>> entry : byTrip.entrySet()) {
            List<StopTime> seq = entry.getValue();
            seq.sort(Comparator.comparingInt(StopTime::sequence));
            int route = tripRoute.get(entry.getKey());
            boolean used = false;
            for (int i = 0; i + 1 < seq.size(); i++) {
                String a = seq.get(i).stopId();
                String b = seq.get(i + 1).stopId();
                if (a.equals(b)) {
                    selfLoops++;
                    continue;
                }
                int ia = tempIndex.computeIfAbsent(a, k -> { tempIds.add(k); return tempIds.size() - 1; });
                int ib = tempIndex.computeIfAbsent(b, k -> { tempIds.add(k); return tempIds.size() - 1; });
                edges.computeIfAbsent(((long) ia << 32) | ib, k -> new BitSet()).set(route);
                used = true;
            }
            if (used) {
                tripsUsed++;
            }
        }

        // 3. Nodurile finale: stațiile deservite, în ordinea din /stops (determinist).
        List<Stop> nodes = new ArrayList<>();
        Map<String, Integer> finalIndex = new HashMap<>();
        Set<String> served = new HashSet<>(tempIds);
        for (Stop s : data.stops()) {
            if (served.contains(s.id()) && !finalIndex.containsKey(s.id())) {
                finalIndex.put(s.id(), nodes.size());
                nodes.add(s);
            }
        }
        int unserved = (int) data.stops().stream().map(Stop::id).distinct().count() - nodes.size();

        int m = edges.size();
        int[] from = new int[m];
        int[] to = new int[m];
        int[][] edgeRoutes = new int[m][];
        int e = 0;
        for (Map.Entry<Long, BitSet> entry : edges.entrySet()) {
            long key = entry.getKey();
            from[e] = finalIndex.get(tempIds.get((int) (key >>> 32)));
            to[e] = finalIndex.get(tempIds.get((int) key));
            edgeRoutes[e] = entry.getValue().stream().toArray();
            e++;
        }

        TransitGraph graph = new TransitGraph(nodes, routes, from, to, edgeRoutes);
        BuildReport report = new BuildReport(tripsUsed, tripsWithoutRoute, withoutTrip, unknownStop,
                unserved, selfLoops);
        return new BuildResult(graph, report);
    }
}
