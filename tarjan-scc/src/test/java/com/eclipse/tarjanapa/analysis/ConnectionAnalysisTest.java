package com.eclipse.tarjanapa.analysis;

import com.eclipse.tarjanapa.algorithms.SccChoice;
import com.eclipse.tarjanapa.algorithms.SccResult;
import com.eclipse.tarjanapa.graph.GraphBuilder;
import com.eclipse.tarjanapa.graph.GraphBuilderTest;
import com.eclipse.tarjanapa.graph.TransitGraph;
import com.eclipse.tarjanapa.model.GtfsDataset;
import com.eclipse.tarjanapa.model.Route;
import com.eclipse.tarjanapa.model.Stop;
import com.eclipse.tarjanapa.model.StopTime;
import com.eclipse.tarjanapa.model.Trip;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Conexiunea dintre stații pe fixture:
 * ruta 1 A ⇄ B ⇄ C, ruta 2 C ⇄ D ⇄ E, ruta 3 E → F (sens unic).
 */
class ConnectionAnalysisTest {

    static TransitGraph g;

    @BeforeAll
    static void setUp() throws IOException {
        g = GraphBuilder.build(GraphBuilderTest.loadFixture()).graph();
    }

    private static List<String> ids(ConnectionAnalysis.Path p) {
        return p.stops().stream().map(ConnectionAnalysis.StopRef::id).toList();
    }

    @ParameterizedTest
    @EnumSource(SccChoice.class)
    void aceeasiComponentaAmbeleSensuri(SccChoice choice) {
        SccResult scc = choice.create().run(g.intGraph());
        ConnectionAnalysis.Connection c = ConnectionAnalysis.find(g, scc, "A", "E");
        assertTrue(c.sameComponent());
        assertEquals(List.of("A", "B", "C", "D", "E"), ids(c.forward()));
        assertEquals(List.of("E", "D", "C", "B", "A"), ids(c.backward()));
        assertEquals(1, c.forward().transfers(), "ruta 1 până la C, apoi ruta 2");
        assertEquals(List.of("1", "2"), c.forward().legs().stream().map(ConnectionAnalysis.Leg::routeLabel).toList());
    }

    @ParameterizedTest
    @EnumSource(SccChoice.class)
    void doarIntrUnSensSpreDepou(SccChoice choice) {
        SccResult scc = choice.create().run(g.intGraph());
        ConnectionAnalysis.Connection c = ConnectionAnalysis.find(g, scc, "A", "F");
        assertFalse(c.sameComponent());
        assertTrue(c.forward().found());
        assertFalse(c.backward().found());
        assertEquals(5, c.forward().segments());
        assertEquals(2, c.forward().transfers());
        assertTrue(c.explanation().contains("nu și înapoi"));
    }

    @Test
    void sensulInversEsteExplicat() {
        SccResult scc = SccChoice.GABOW.create().run(g.intGraph());
        ConnectionAnalysis.Connection c = ConnectionAnalysis.find(g, scc, "F", "A");
        assertFalse(c.forward().found());
        assertTrue(c.backward().found());
        assertTrue(c.explanation().contains("doar invers"));
    }

    @Test
    void ruteInchiseRupConexiunea() {
        TransitGraph closed = g.withoutRoutes(Set.of("2"));
        SccResult scc = SccChoice.KOSARAJU.create().run(closed.intGraph());
        ConnectionAnalysis.Connection c = ConnectionAnalysis.find(closed, scc, "A", "E");
        assertFalse(c.forward().found());
        assertFalse(c.backward().found());
        assertTrue(c.explanation().startsWith("Nu există drum"));
    }

    @Test
    void aceeasiStatie() {
        SccResult scc = SccChoice.TARJAN.create().run(g.intGraph());
        ConnectionAnalysis.Connection c = ConnectionAnalysis.find(g, scc, "B", "B");
        assertTrue(c.sameComponent());
        assertEquals(0, c.forward().segments());
    }

    @Test
    void ceiDoiCriteriiDauDrumuriDiferite() {
        // Ruta 1: A → B, ruta 2: B → C (scurt, dar cu schimbare); ruta 3: A → X → Y → C (lung, direct).
        List<Stop> stops = List.of(new Stop("A", "A", 0, 0), new Stop("B", "B", 0, 1), new Stop("C", "C", 0, 2),
                new Stop("X", "X", 1, 0), new Stop("Y", "Y", 1, 1));
        List<Route> routes = List.of(new Route("1", "1", null, 3), new Route("2", "2", null, 3),
                new Route("3", "3", null, 3));
        List<Trip> trips = List.of(new Trip("t1", "1", 0, null), new Trip("t2", "2", 0, null),
                new Trip("t3", "3", 0, null));
        List<StopTime> times = List.of(
                new StopTime("t1", "A", 1), new StopTime("t1", "B", 2),
                new StopTime("t2", "B", 1), new StopTime("t2", "C", 2),
                new StopTime("t3", "A", 1), new StopTime("t3", "X", 2), new StopTime("t3", "Y", 3),
                new StopTime("t3", "C", 4));
        TransitGraph small = GraphBuilder.build(new GtfsDataset(routes, trips, stops, times)).graph();
        SccResult scc = SccChoice.TARJAN.create().run(small.intGraph());

        ConnectionAnalysis.Path byStops =
                ConnectionAnalysis.find(small, scc, "A", "C", ConnectionAnalysis.Mode.STOPS).forward();
        assertEquals(List.of("A", "B", "C"), ids(byStops));
        assertEquals(1, byStops.transfers());

        ConnectionAnalysis.Path byTransfers =
                ConnectionAnalysis.find(small, scc, "A", "C", ConnectionAnalysis.Mode.TRANSFERS).forward();
        assertEquals(List.of("A", "X", "Y", "C"), ids(byTransfers));
        assertEquals(0, byTransfers.transfers());
        assertEquals(3, byTransfers.legs().get(0).segments());
    }

    @Test
    void criteriulSeInterpreteaza() {
        assertEquals(ConnectionAnalysis.Mode.TRANSFERS, ConnectionAnalysis.Mode.parse(null));
        assertEquals(ConnectionAnalysis.Mode.STOPS, ConnectionAnalysis.Mode.parse("stops"));
        assertThrows(IllegalArgumentException.class, () -> ConnectionAnalysis.Mode.parse("rapid"));
    }

    @Test
    void statieNecunoscuta() {
        SccResult scc = SccChoice.TARJAN.create().run(g.intGraph());
        assertThrows(IllegalArgumentException.class, () -> ConnectionAnalysis.find(g, scc, "A", "G"));
    }
}
