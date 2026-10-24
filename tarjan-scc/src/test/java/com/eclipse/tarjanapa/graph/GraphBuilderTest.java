package com.eclipse.tarjanapa.graph;

import com.eclipse.tarjanapa.algorithms.SccResult;
import com.eclipse.tarjanapa.algorithms.TarjanIterative;
import com.eclipse.tarjanapa.model.GtfsDataset;
import com.eclipse.tarjanapa.tranzy.GtfsParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Construirea grafului din fixture-ul GTFS fals din {@code fixtures/gtfs}.
 * <pre>
 * ruta 1:  A ⇄ B ⇄ C      ruta 2:  C ⇄ D ⇄ E      ruta 3:  E → F (sens unic)
 * G nu e deservită de nicio cursă; H nu are coordonate.
 * </pre>
 */
public class GraphBuilderTest {

    static GtfsDataset dataset;
    static GraphBuilder.BuildResult built;

    static JsonNode fixture(String name) throws IOException {
        try (InputStream in = GraphBuilderTest.class.getResourceAsStream("/fixtures/gtfs/" + name + ".json")) {
            return new ObjectMapper().readTree(in);
        }
    }

    public static GtfsDataset loadFixture() throws IOException {
        return new GtfsDataset(GtfsParser.routes(fixture("routes")), GtfsParser.trips(fixture("trips")),
                GtfsParser.stops(fixture("stops")), GtfsParser.stopTimes(fixture("stop_times")));
    }

    @BeforeAll
    static void build() throws IOException {
        dataset = loadFixture();
        built = GraphBuilder.build(dataset);
    }

    @Test
    void parserulIgnoraInregistrarileIncompleteSiCampurileNecunoscute() {
        assertEquals(3, dataset.routes().size());
        assertEquals(6, dataset.trips().size(), "cursa fără route_id se ignoră");
        assertEquals(7, dataset.stops().size(), "stația fără coordonate se ignoră");
        assertEquals(16, dataset.stopTimes().size(), "stop_time fără stop_sequence se ignoră");
        assertEquals("1", dataset.routes().get(0).id(), "id-urile numerice devin text");
    }

    @Test
    void noduriSiArce() {
        TransitGraph g = built.graph();
        assertEquals(6, g.stopCount(), "G (nedeservită) nu intră în graf");
        assertEquals(-1, g.indexOf("G"));
        assertEquals(9, g.edgeCount());
        assertEquals(1, built.report().unservedStops());
        assertEquals(1, built.report().selfLoopsSkipped(), "F → F se ignoră");
        assertEquals(1, built.report().tripsWithoutRoute());
        assertEquals(1, built.report().stopTimesWithoutTrip());
    }

    @Test
    void arceleSuntOrientateDupaStopSequence() {
        TransitGraph g = built.graph();
        // T1 vine nesortat în fixture (B, A, C), deci arcul corect este A → B.
        assertTrue(g.edgeId(g.indexOf("A"), g.indexOf("B")) >= 0);
        assertTrue(g.edgeId(g.indexOf("E"), g.indexOf("F")) >= 0);
        assertEquals(-1, g.edgeId(g.indexOf("F"), g.indexOf("E")), "ruta 3 e cu sens unic");
    }

    @Test
    void fiecareArcRetineRutele() {
        TransitGraph g = built.graph();
        int e = g.edgeId(g.indexOf("C"), g.indexOf("D"));
        assertArrayEquals(new int[]{g.routeIndexOf("2")}, g.edgeRoutes(e));
        assertEquals(List.of("1", "2"), g.routeLabelsAtStop(g.indexOf("C")));
    }

    @Test
    void sccPeReteaFixture() {
        TransitGraph g = built.graph();
        SccResult scc = new TarjanIterative().run(g.intGraph());
        assertEquals(2, scc.count());
        assertEquals(5, scc.largestSize());
        assertTrue(scc.componentOf()[g.indexOf("F")] != scc.largestComponent());
    }

    @Test
    void faraRutaEliminaDoarArceleRamaseFaraRute() {
        TransitGraph g = built.graph();
        TransitGraph closed = g.withoutRoutes(Set.of("2"));
        assertEquals(g.stopCount(), closed.stopCount(), "stațiile se păstrează, cu aceiași indici");
        assertEquals(5, closed.edgeCount(), "dispar cele 4 arce ale rutei 2");
        SccResult scc = new TarjanIterative().run(closed.intGraph());
        assertEquals(4, scc.count());
        assertEquals(3, scc.largestSize());
    }

    @Test
    void faraStatiiRenumeroteaza() {
        TransitGraph g = built.graph().withoutStops(Set.of("C"));
        assertEquals(5, g.stopCount());
        assertEquals(-1, g.indexOf("C"));
        assertEquals(5, g.edgeCount(), "dispar cele 4 arce incidente lui C");
        assertTrue(Arrays.stream(new String[]{"A", "B", "D", "E", "F"}).allMatch(id -> g.indexOf(id) >= 0));
    }

    @Test
    void statistici() {
        TransitGraph.GraphStats st = built.graph().stats();
        assertEquals(6, st.stops());
        assertEquals(9, st.edges());
        assertEquals(1.5, st.averageDegree(), 1e-9);
        assertEquals(3, st.activeRoutes());
    }
}
