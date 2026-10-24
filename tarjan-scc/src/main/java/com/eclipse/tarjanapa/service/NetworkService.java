package com.eclipse.tarjanapa.service;

import com.eclipse.tarjanapa.algorithms.SccChoice;
import com.eclipse.tarjanapa.analysis.Benchmark;
import com.eclipse.tarjanapa.analysis.ClosureAnalysis;
import com.eclipse.tarjanapa.analysis.ConnectionAnalysis;
import com.eclipse.tarjanapa.analysis.CsvExporter;
import com.eclipse.tarjanapa.analysis.LiveAnalysis;
import com.eclipse.tarjanapa.analysis.NetworkState;
import com.eclipse.tarjanapa.graph.GraphBuilder;
import com.eclipse.tarjanapa.graph.TransitGraph;
import com.eclipse.tarjanapa.model.Agency;
import com.eclipse.tarjanapa.model.Vehicle;
import com.eclipse.tarjanapa.tranzy.TranzyDataSource;
import com.eclipse.tarjanapa.tranzy.TranzyException;
import com.eclipse.tarjanapa.tranzy.TranzyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Punctul central al aplicației: încarcă datele, construiește graful și expune
 * analizele, calculate cu algoritmul SCC ales (Tarjan, Kosaraju sau Gabow).
 * Folosit atât de API-ul REST, cât și de {@code ConsoleApp}.
 */
@Service
public class NetworkService {

    private static final Logger log = LoggerFactory.getLogger(NetworkService.class);

    private final TranzyDataSource dataSource;
    private final CsvExporter exporter;
    private final BenchmarkProperties benchmarkProps;

    private volatile String agencyId;
    private volatile NetworkSnapshot snapshot;
    private volatile String lastError = "Datele nu au fost încă încărcate.";
    private volatile Benchmark.Result lastBenchmark;

    public NetworkService(TranzyProperties props, TranzyDataSource dataSource, CsvExporter exporter,
                          BenchmarkProperties benchmarkProps) {
        this.dataSource = dataSource;
        this.exporter = exporter;
        this.benchmarkProps = benchmarkProps;
        this.agencyId = props.hasAgencyId() ? props.agencyId().trim() : null;
    }

    public String agencyId() {
        return agencyId;
    }

    /** Schimbă operatorul (de ex. ales din consolă); datele trebuie reîncărcate după. */
    public void setAgencyId(String agencyId) {
        this.agencyId = agencyId == null || agencyId.isBlank() ? null : agencyId.trim();
    }

    public List<Agency> agencies(boolean forceRefresh) {
        return dataSource.agencies(forceRefresh);
    }

    /**
     * Încarcă (sau reîncarcă) rețeaua. Componentele se calculează imediat cu
     * Tarjan; ceilalți algoritmi rulează la prima cerere.
     *
     * @param forceRefresh true = ignoră cache-ul și descarcă din Tranzy
     */
    public synchronized NetworkSnapshot load(boolean forceRefresh) {
        try {
            TranzyDataSource.DatasetLoad dl = dataSource.dataset(agencyId, forceRefresh);
            GraphBuilder.BuildResult built = GraphBuilder.build(dl.dataset());

            List<String> warnings = new ArrayList<>(dl.warnings());
            if (built.graph().stopCount() == 0) {
                warnings.add("Graful rezultat este gol: verificați că /stop_times conține date pentru acest operator.");
            }
            NetworkSnapshot s = new NetworkSnapshot(agencyId, Instant.now(), built.graph(), built.report(),
                    dl.sources(), warnings);
            NetworkSnapshot.AlgorithmView v = s.view(SccChoice.TARJAN);
            snapshot = s;
            lastError = null;
            NetworkState st = v.state();
            log.info("Rețea încărcată (agency {}): V={}, E={}, SCC={}, cea mai mare SCC={} ({}%), Tarjan {} ms",
                    agencyId, st.stops(), st.edges(), st.sccCount(), st.largestSize(),
                    String.format("%.1f", st.largestPercent()), String.format("%.2f", v.scc().elapsedMillis()));
            return s;
        } catch (TranzyException e) {
            lastError = e.getMessage();
            throw e;
        }
    }

    /** Ultima rețea încărcată; aruncă {@link NoDataException} dacă nu există. */
    public NetworkSnapshot snapshot() {
        NetworkSnapshot s = snapshot;
        if (s == null) {
            throw new NoDataException(lastError);
        }
        return s;
    }

    /** Rezultatele algoritmului ales pe rețeaua curentă. */
    public NetworkSnapshot.AlgorithmView view(SccChoice choice) {
        return snapshot().view(choice);
    }

    public boolean isLoaded() {
        return snapshot != null;
    }

    public ClosureAnalysis.ClosureResult simulateClosure(Set<String> routeIds, SccChoice choice) {
        NetworkSnapshot s = snapshot();
        return new ClosureAnalysis(choice.create()).simulate(s.graph(), s.view(choice).scc(), routeIds);
    }

    /**
     * Conexiunea dintre două stații, cu algoritmul ales; dacă {@code closedRoutes}
     * nu e gol, se caută în rețeaua fără acele rute (ca în simulare).
     */
    public ConnectionAnalysis.Connection connection(String fromStopId, String toStopId, Set<String> closedRoutes,
                                                    SccChoice choice, ConnectionAnalysis.Mode mode) {
        NetworkSnapshot s = snapshot();
        if (closedRoutes == null || closedRoutes.isEmpty()) {
            return ConnectionAnalysis.find(s.graph(), s.view(choice).scc(), fromStopId, toStopId, mode);
        }
        TransitGraph g = s.graph().withoutRoutes(closedRoutes);
        return ConnectionAnalysis.find(g, choice.create().run(g.intGraph()), fromStopId, toStopId, mode);
    }

    public LiveAnalysis.LiveResult live(boolean forceRefresh, SccChoice choice) {
        NetworkSnapshot s = snapshot();
        List<String> warnings = new ArrayList<>();
        List<Vehicle> vehicles = dataSource.vehicles(s.agencyId(), forceRefresh, warnings);
        warnings.forEach(log::warn);
        return new LiveAnalysis(choice.create()).analyze(s.graph(), vehicles);
    }

    /** Rulează benchmark-ul; rețeaua reală se include dacă e încărcată. Rezultatul se exportă în CSV. */
    public BenchmarkRun benchmark(Benchmark.Config config) {
        NetworkSnapshot s = snapshot;
        Benchmark.Result result = new Benchmark().run(config, s == null ? null : s.graph().intGraph());
        lastBenchmark = result;
        Path csv = exporter.benchmark(result);
        return new BenchmarkRun(result, csv.toString());
    }

    public record BenchmarkRun(Benchmark.Result result, String csvFile) {
    }

    public Benchmark.Config defaultBenchmarkConfig() {
        return benchmarkProps.toConfig();
    }

    public Benchmark.Result lastBenchmark() {
        return lastBenchmark;
    }

    /** Exportă în CSV tot ce e disponibil, cu algoritmul ales; întoarce fișierele scrise. */
    public List<Path> exportAll(SccChoice choice) {
        NetworkSnapshot.AlgorithmView v = view(choice);
        List<Path> files = new ArrayList<>();
        files.add(exporter.smallComponents(v.state()));
        files.add(exporter.criticalRoutes(v.criticalRoutes()));
        files.add(exporter.bridges(v.bridges()));
        if (lastBenchmark != null) {
            files.add(exporter.benchmark(lastBenchmark));
        }
        return files;
    }
}
