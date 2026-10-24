package com.eclipse.tarjanapa.analysis;

import com.eclipse.tarjanapa.algorithms.SccChoice;
import com.eclipse.tarjanapa.algorithms.SccResult;
import com.eclipse.tarjanapa.algorithms.TarjanIterative;
import com.eclipse.tarjanapa.graph.GraphBuilder;
import com.eclipse.tarjanapa.graph.GraphBuilderTest;
import com.eclipse.tarjanapa.graph.TransitGraph;
import com.eclipse.tarjanapa.model.Vehicle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Analizele pe rețeaua din fixture (vezi {@link GraphBuilderTest}). */
class AnalysisTest {

    static TransitGraph g;
    static SccResult scc;

    @BeforeAll
    static void setUp() throws IOException {
        g = GraphBuilder.build(GraphBuilderTest.loadFixture()).graph();
        scc = new TarjanIterative().run(g.intGraph());
    }

    @ParameterizedTest
    @EnumSource(SccChoice.class)
    void ceiTreiAlgoritmiDauAceleasiAnalize(SccChoice choice) {
        SccResult r = choice.create().run(g.intGraph());
        assertTrue(scc.samePartitionAs(r));
        NetworkState st = NetworkState.of(g, r);
        assertEquals(2, st.sccCount());
        assertEquals(5, st.largestSize());

        ClosureAnalysis.ClosureResult closed = new ClosureAnalysis(choice.create()).simulate(g, r, Set.of("2"));
        assertEquals(Set.of("D", "E"), Set.copyOf(closed.detachedStops().stream().map(StopInfo::id).toList()));

        List<CriticalRoutesAnalysis.RouteImpact> ranking =
                new CriticalRoutesAnalysis(new ClosureAnalysis(choice.create())).rank(g, r);
        assertEquals(List.of(2, 2, 0), ranking.stream().map(CriticalRoutesAnalysis.RouteImpact::lostStops).toList());
        assertEquals(1, StructureAnalysis.condensation(g, r).minEdgesToAdd());
    }

    @Test
    void stareaRetelei() {
        NetworkState st = NetworkState.of(g, scc);
        assertEquals(2, st.sccCount());
        assertEquals(5, st.largestSize());
        assertEquals(100.0 * 5 / 6, st.largestPercent(), 1e-9);
        assertEquals(1, st.smallComponentStops().size());
        assertEquals("Depou", st.smallComponentStops().get(0).name());
        assertEquals(List.of("3"), st.smallComponentStops().get(0).routes());
    }

    @Test
    void inchidereaRuteiDesprindeStatiile() {
        ClosureAnalysis.ClosureResult r = new ClosureAnalysis().simulate(g, scc, Set.of("2", "nu-exista"));
        assertEquals(List.of("nu-exista"), r.unknownRoutes());
        assertEquals(1, r.closedRoutes().size());
        assertEquals(2, r.detachedCount());
        assertEquals(Set.of("D", "E"), Set.copyOf(r.detachedStops().stream().map(StopInfo::id).toList()));
        assertEquals(3, r.after().largestSize());
    }

    @Test
    void inchidereaUneiRuteFaraImpact() {
        ClosureAnalysis.ClosureResult r = new ClosureAnalysis().simulate(g, scc, Set.of("3"));
        assertEquals(0, r.detachedCount());
        assertEquals(5, r.after().largestSize());
    }

    @Test
    void clasamentRuteCritice() {
        List<CriticalRoutesAnalysis.RouteImpact> ranking = new CriticalRoutesAnalysis(new ClosureAnalysis()).rank(g, scc);
        assertEquals(3, ranking.size());
        // Ruta 1: componenta mare devine {C, D, E} (3) ⇒ A, B se pierd; ruta 2: D, E se pierd.
        assertEquals(2, ranking.get(0).lostStops());
        assertEquals(2, ranking.get(1).lostStops());
        assertEquals(0, ranking.get(2).lostStops());
        assertEquals("3", ranking.get(2).label());
        assertEquals(1, ranking.get(0).rank());
    }

    @Test
    void puntiInFixture() {
        StructureAnalysis.BridgeReport r = StructureAnalysis.bridges(g, scc);
        assertEquals(5, r.bridgeCount(), "rețeaua neorientată A-B-C-D-E-F este un lanț");
        assertEquals(4, r.articulationCount());
    }

    @Test
    void condensareInFixture() {
        StructureAnalysis.CondensationReport c = StructureAnalysis.condensation(g, scc);
        assertEquals(2, c.componentCount());
        assertEquals(1, c.dagEdges());
        assertEquals(1, c.minEdgesToAdd(), "un arc F → rețea face graful tare conex");
    }

    @Test
    void reteaLiveDoarCuRuteleCuVehicule() {
        List<Vehicle> vehicles = List.of(
                new Vehicle("101", "1234", 47.02, 28.84, "1", "T1", null),
                new Vehicle("102", "5678", 47.03, 28.89, null, null, null));
        LiveAnalysis.LiveResult r = new LiveAnalysis().analyze(g, vehicles);
        assertEquals(2, r.vehicles());
        assertEquals(1, r.vehiclesOnRoutes());
        assertEquals(List.of("1"), r.activeRouteLabels());
        assertEquals(3, r.inactiveStops(), "D, E, F nu sunt deservite de ruta 1");
        assertEquals(3, r.state().stops());
        assertEquals(1, r.state().sccCount());
    }

    @Test
    void benchmarkVerificaAcordulSiExportaCsv(@TempDir Path dir) throws IOException {
        Benchmark.Config config = new Benchmark.Config(List.of(100, 1_000), 2.0, 42L, 2, 1, 1_000);
        Benchmark.Result r = new Benchmark().run(config, g.intGraph());
        assertEquals(3 * 5, r.rows().size(), "3 grafuri × 5 algoritmi");
        assertTrue(r.verified());

        Path csv = new CsvExporter(dir).benchmark(r);
        List<String> lines = Files.readAllLines(csv);
        assertEquals(16, lines.size());
        assertTrue(lines.get(1).startsWith("retea-reala,6,9,TarjanRecursive"));
        assertTrue(Files.exists(dir.resolve("benchmark-latest.csv")));
    }

    @Test
    void naiveSccSeOmitePesteLimita() {
        Benchmark.Config config = new Benchmark.Config(List.of(2_000), 2.0, 1L, 1, 0, 1_000);
        Benchmark.Result r = new Benchmark().run(config, null);
        assertEquals(4, r.rows().size());
        assertTrue(r.rows().stream().noneMatch(row -> row.algorithm().equals("NaiveScc")));
    }
}
