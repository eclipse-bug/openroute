package com.eclipse.tarjanapa.algorithms;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Alegerea algoritmului din aplicație: tarjan, kosaraju, gabow. */
class SccChoiceTest {

    @Test
    void numeleSeInterpreteazaFaraMajuscule() {
        assertEquals(SccChoice.TARJAN, SccChoice.parse("tarjan"));
        assertEquals(SccChoice.KOSARAJU, SccChoice.parse("Kosaraju"));
        assertEquals(SccChoice.GABOW, SccChoice.parse(" GABOW "));
    }

    @Test
    void implicitEsteTarjan() {
        assertEquals(SccChoice.TARJAN, SccChoice.parse(null));
        assertEquals(SccChoice.TARJAN, SccChoice.parse(""));
    }

    @Test
    void numeNecunoscutEsteRefuzat() {
        assertThrows(IllegalArgumentException.class, () -> SccChoice.parse("dijkstra"));
    }

    @Test
    void fiecareAlegereCreeazaAlgoritmulCorect() {
        assertInstanceOf(TarjanIterative.class, SccChoice.TARJAN.create());
        assertInstanceOf(Kosaraju.class, SccChoice.KOSARAJU.create());
        assertInstanceOf(Gabow.class, SccChoice.GABOW.create());
    }

    @Test
    void gabowFolosesteStivaPSuplimentara() {
        // Lanț 0 → 1 → 2: Tarjan = 3 vizite + 2 arce + 3 push + 3 pop = 11;
        // Gabow are în plus 3 push + 3 pop pe stiva P = 17.
        IntGraph g = IntGraph.builder(3).addEdge(0, 1).addEdge(1, 2).build();
        assertEquals(11, new TarjanIterative().run(g).operations());
        assertEquals(17, new Gabow().run(g).operations());
    }
}
