package com.eclipse.tarjanapa.algorithms;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Grafuri mici cu SCC-uri cunoscute, verificate pentru fiecare algoritm. */
class SccAlgorithmsTest {

    static Stream<SccAlgorithm> algorithms() {
        return SccAlgorithms.all().stream();
    }

    /** Componentele în formă canonică: fiecare sortată, apoi ordonate după primul nod. */
    static List<List<Integer>> canonical(SccResult r) {
        return r.components().stream()
                .map(c -> Arrays.stream(c).sorted().boxed().toList())
                .sorted(Comparator.comparing(c -> c.get(0)))
                .toList();
    }

    static IntGraph graph(int n, int[][] edges) {
        IntGraph.Builder b = IntGraph.builder(n);
        for (int[] e : edges) {
            b.addEdge(e[0], e[1]);
        }
        return b.build();
    }

    @ParameterizedTest
    @MethodSource("algorithms")
    void ciclu(SccAlgorithm alg) {
        SccResult r = alg.run(graph(4, new int[][]{{0, 1}, {1, 2}, {2, 3}, {3, 0}}));
        assertEquals(List.of(List.of(0, 1, 2, 3)), canonical(r));
    }

    @ParameterizedTest
    @MethodSource("algorithms")
    void lant(SccAlgorithm alg) {
        SccResult r = alg.run(graph(4, new int[][]{{0, 1}, {1, 2}, {2, 3}}));
        assertEquals(List.of(List.of(0), List.of(1), List.of(2), List.of(3)), canonical(r));
    }

    @ParameterizedTest
    @MethodSource("algorithms")
    void douaCicluriLegateUnidirectional(SccAlgorithm alg) {
        // {0,1,2} → {3,4}: un singur arc 2 → 3, fără întoarcere.
        SccResult r = alg.run(graph(5, new int[][]{{0, 1}, {1, 2}, {2, 0}, {2, 3}, {3, 4}, {4, 3}}));
        assertEquals(List.of(List.of(0, 1, 2), List.of(3, 4)), canonical(r));
        assertEquals(3, r.largestSize());
    }

    @ParameterizedTest
    @MethodSource("algorithms")
    void nodIzolat(SccAlgorithm alg) {
        SccResult r = alg.run(graph(3, new int[][]{{0, 1}, {1, 0}}));
        assertEquals(List.of(List.of(0, 1), List.of(2)), canonical(r));
    }

    @ParameterizedTest
    @MethodSource("algorithms")
    void grafGol(SccAlgorithm alg) {
        SccResult r = alg.run(graph(0, new int[][]{}));
        assertEquals(0, r.count());
        assertEquals(0, r.largestSize());
        assertEquals(-1, r.largestComponent());
    }

    @ParameterizedTest
    @MethodSource("algorithms")
    void autoBucla(SccAlgorithm alg) {
        SccResult r = alg.run(graph(2, new int[][]{{0, 0}, {0, 1}, {1, 1}}));
        assertEquals(List.of(List.of(0), List.of(1)), canonical(r));
    }

    @ParameterizedTest
    @MethodSource("algorithms")
    void arceMultipleSiCicluriImbricate(SccAlgorithm alg) {
        SccResult r = alg.run(graph(6, new int[][]{
                {0, 1}, {0, 1}, {1, 2}, {2, 0}, {1, 3}, {3, 4}, {4, 5}, {5, 3}, {5, 1}}));
        assertEquals(List.of(List.of(0, 1, 2, 3, 4, 5)), canonical(r));
    }

    @ParameterizedTest
    @MethodSource("algorithms")
    void contorulDeOperatiiEstePozitivSiTimpulMasurat(SccAlgorithm alg) {
        SccResult r = alg.run(graph(3, new int[][]{{0, 1}, {1, 2}}));
        assertEquals(alg.name(), r.algorithm());
        assertTrue(r.operations() > 0);
        assertTrue(r.elapsedNanos() >= 0);
    }
}
