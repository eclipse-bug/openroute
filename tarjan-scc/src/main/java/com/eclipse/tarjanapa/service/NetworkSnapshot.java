package com.eclipse.tarjanapa.service;

import com.eclipse.tarjanapa.algorithms.SccResult;
import com.eclipse.tarjanapa.analysis.CriticalRoutesAnalysis;
import com.eclipse.tarjanapa.analysis.NetworkState;
import com.eclipse.tarjanapa.analysis.StructureAnalysis;
import com.eclipse.tarjanapa.graph.GraphBuilder;
import com.eclipse.tarjanapa.graph.TransitGraph;
import com.eclipse.tarjanapa.tranzy.TranzyDataSource;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

/**
 * O încărcare completă a rețelei: graful, SCC-urile și analizele derivate.
 * Imutabilă; analizele costisitoare (rute critice, punți) se calculează o
 * singură dată, la prima cerere.
 */
public final class NetworkSnapshot {

    private final String agencyId;
    private final Instant loadedAt;
    private final TransitGraph graph;
    private final GraphBuilder.BuildReport buildReport;
    private final SccResult scc;
    private final NetworkState state;
    private final List<TranzyDataSource.Loaded> sources;
    private final List<String> warnings;

    private final Lazy<List<CriticalRoutesAnalysis.RouteImpact>> criticalRoutes;
    private final Lazy<StructureAnalysis.BridgeReport> bridges;
    private final Lazy<StructureAnalysis.CondensationReport> condensation;

    NetworkSnapshot(String agencyId, Instant loadedAt, TransitGraph graph, GraphBuilder.BuildReport buildReport,
                    SccResult scc, List<TranzyDataSource.Loaded> sources, List<String> warnings,
                    CriticalRoutesAnalysis critical) {
        this.agencyId = agencyId;
        this.loadedAt = loadedAt;
        this.graph = graph;
        this.buildReport = buildReport;
        this.scc = scc;
        this.state = NetworkState.of(graph, scc);
        this.sources = List.copyOf(sources);
        this.warnings = List.copyOf(warnings);
        this.criticalRoutes = new Lazy<>(() -> critical.rank(graph, scc));
        this.bridges = new Lazy<>(() -> StructureAnalysis.bridges(graph, scc));
        this.condensation = new Lazy<>(() -> StructureAnalysis.condensation(graph, scc));
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

    public SccResult scc() {
        return scc;
    }

    public NetworkState state() {
        return state;
    }

    public List<TranzyDataSource.Loaded> sources() {
        return sources;
    }

    public List<String> warnings() {
        return warnings;
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
