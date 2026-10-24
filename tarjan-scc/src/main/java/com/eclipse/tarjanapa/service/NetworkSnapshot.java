package com.eclipse.tarjanapa.service;

import com.eclipse.tarjanapa.algorithms.SccChoice;
import com.eclipse.tarjanapa.algorithms.SccResult;
import com.eclipse.tarjanapa.analysis.ClosureAnalysis;
import com.eclipse.tarjanapa.analysis.CriticalRoutesAnalysis;
import com.eclipse.tarjanapa.analysis.NetworkState;
import com.eclipse.tarjanapa.analysis.StructureAnalysis;
import com.eclipse.tarjanapa.graph.GraphBuilder;
import com.eclipse.tarjanapa.graph.TransitGraph;
import com.eclipse.tarjanapa.tranzy.TranzyDataSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * O încărcare completă a rețelei: graful și, pentru fiecare algoritm SCC ales
 * (Tarjan, Kosaraju, Gabow), rezultatul și analizele derivate.
 * <p>
 * Imutabilă. Rezultatul unui algoritm se calculează la prima cerere și se
 * păstrează; analizele costisitoare (rute critice, punți) la fel.
 */
public final class NetworkSnapshot {

    private final String agencyId;
    private final Instant loadedAt;
    private final TransitGraph graph;
    private final GraphBuilder.BuildReport buildReport;
    private final List<TranzyDataSource.Loaded> sources;
    private final List<String> warnings;
    private final Map<SccChoice, AlgorithmView> views = new ConcurrentHashMap<>();

    NetworkSnapshot(String agencyId, Instant loadedAt, TransitGraph graph, GraphBuilder.BuildReport buildReport,
                    List<TranzyDataSource.Loaded> sources, List<String> warnings) {
        this.agencyId = agencyId;
        this.loadedAt = loadedAt;
        this.graph = graph;
        this.buildReport = buildReport;
        this.sources = List.copyOf(sources);
        this.warnings = List.copyOf(warnings);
    }

    public String agencyId() {
        return agencyId;
    }

    public Instant loadedAt() {
        return loadedAt;
    }

    public TransitGraph graph() {
        return graph;
    }

    public GraphBuilder.BuildReport buildReport() {
        return buildReport;
    }

    public List<TranzyDataSource.Loaded> sources() {
        return sources;
    }

    public List<String> warnings() {
        return warnings;
    }

    /** Rezultatele obținute cu algoritmul dat (calculate la prima cerere). */
    public AlgorithmView view(SccChoice choice) {
        return views.computeIfAbsent(choice, AlgorithmView::new);
    }

    /** Rețeaua văzută prin unul dintre cei trei algoritmi SCC. */
    public final class AlgorithmView {

        private final SccChoice choice;
        private final SccResult scc;
        private final NetworkState state;
        private final Lazy<List<CriticalRoutesAnalysis.RouteImpact>> criticalRoutes;
        private final Lazy<StructureAnalysis.BridgeReport> bridges;
        private final Lazy<StructureAnalysis.CondensationReport> condensation;

        private AlgorithmView(SccChoice choice) {
            this.choice = choice;
            this.scc = choice.create().run(graph.intGraph());
            this.state = NetworkState.of(graph, scc);
            ClosureAnalysis closure = new ClosureAnalysis(choice.create());
            this.criticalRoutes = new Lazy<>(() -> new CriticalRoutesAnalysis(closure).rank(graph, scc));
            this.bridges = new Lazy<>(() -> StructureAnalysis.bridges(graph, scc));
            this.condensation = new Lazy<>(() -> StructureAnalysis.condensation(graph, scc));
        }

        public SccChoice choice() {
            return choice;
        }

        public SccResult scc() {
            return scc;
        }

        public NetworkState state() {
            return state;
        }

        public List<CriticalRoutesAnalysis.RouteImpact> criticalRoutes() {
            return criticalRoutes.get();
        }

        public StructureAnalysis.BridgeReport bridges() {
            return bridges.get();
        }

        public StructureAnalysis.CondensationReport condensation() {
            return condensation.get();
        }
    }

    /** Valoare calculată o singură dată, thread-safe. */
    private static final class Lazy<T> {
        private final Supplier<T> supplier;
        private volatile T value;

        Lazy(Supplier<T> supplier) {
            this.supplier = supplier;
        }

        T get() {
            T v = value;
            if (v == null) {
                synchronized (this) {
                    v = value;
                    if (v == null) {
                        v = supplier.get();
                        value = v;
                    }
                }
            }
            return v;
        }
    }
}
