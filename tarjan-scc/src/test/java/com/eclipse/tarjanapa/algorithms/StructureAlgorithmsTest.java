package com.eclipse.tarjanapa.algorithms;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Condensarea, numărul minim de arce de adăugat și punțile. */
class StructureAlgorithmsTest {

    private static IntGraph graph(int n, int[][] edges) {
        IntGraph.Builder b = IntGraph.builder(n);
        for (int[] e : edges) {
            b.addEdge(e[0], e[1]);
        }
        return b.build();
    }

    @Test
    void condensareaDouaCicluriLegate() {
        IntGraph g = graph(5, new int[][]{{0, 1}, {1, 2}, {2, 0}, {2, 3}, {1, 3}, {3, 4}, {4, 3}});
        SccResult scc = new TarjanIterative().run(g);
        Condensation c = Condensation.of(g, scc);
        assertEquals(2, c.componentCount());
        assertEquals(1, c.dag().edgeCount(), "arcele duplicate dintre componente se elimină");
        assertEquals(1, c.sources().size());
        assertEquals(1, c.sinks().size());
        assertEquals(1, c.minEdgesToStronglyConnect());
    }

    @Test
    void minimArceDeAdaugatPentruStea() {
        // 0 → 1, 0 → 2, 0 → 3: o sursă, trei destinații ⇒ max(1, 3) = 3.
        IntGraph g = graph(4, new int[][]{{0, 1}, {0, 2}, {0, 3}});
        Condensation c = Condensation.of(g, new TarjanIterative().run(g));
        assertEquals(3, c.minEdgesToStronglyConnect());
    }

    @Test
    void minimArcePentruNoduriIzolateSiGrafTareConex() {
        IntGraph izolate = graph(3, new int[][]{});
        assertEquals(3, Condensation.of(izolate, new TarjanIterative().run(izolate)).minEdgesToStronglyConnect());

        IntGraph ciclu = graph(3, new int[][]{{0, 1}, {1, 2}, {2, 0}});
        assertEquals(0, Condensation.of(ciclu, new TarjanIterative().run(ciclu)).minEdgesToStronglyConnect());
    }

    @Test
    void puntiSiPuncteDeArticulatie() {
        // Triunghi 0-1-2 legat prin puntea 2-3 de lanțul 3-4.
        IntGraph g = graph(5, new int[][]{{0, 1}, {1, 2}, {2, 0}, {2, 3}, {3, 4}, {4, 3}});
        TarjanBridges.Result r = TarjanBridges.compute(g);
        assertEquals(List.of(new TarjanBridges.Bridge(2, 3), new TarjanBridges.Bridge(3, 4)),
                r.bridges().stream().sorted((a, b) -> a.u() != b.u() ? a.u() - b.u() : a.v() - b.v()).toList());
        assertEquals(List.of(2, 3), r.articulationPoints());
    }

    @Test
    void arcDusIntorsNuEstePunteDubla() {
        // A ⇄ B este o singură muchie neorientată, deci e punte.
        IntGraph g = graph(2, new int[][]{{0, 1}, {1, 0}});
        assertEquals(1, TarjanBridges.compute(g).bridges().size());
    }

    @Test
    void cicluFaraPunti() {
        IntGraph g = graph(4, new int[][]{{0, 1}, {1, 2}, {2, 3}, {3, 0}});
        TarjanBridges.Result r = TarjanBridges.compute(g);
        assertEquals(0, r.bridges().size());
        assertEquals(0, r.articulationPoints().size());
    }
}
