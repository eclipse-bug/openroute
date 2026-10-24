package com.eclipse.tarjanapa.algorithms;

import org.junit.jupiter.api.Test;

import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Toți algoritmii SCC trebuie să găsească aceeași partiție pe 100 de grafuri aleatoare. */
class RandomAgreementTest {

    @Test
    void totiAlgoritmiiDauAcelasiRezultatPe100DeGrafuriAleatoare() {
        SplittableRandom rnd = new SplittableRandom(2026);
        for (int i = 0; i < 100; i++) {
            int n = 1 + rnd.nextInt(300);
            double degree = rnd.nextDouble() * 3.0;   // de la foarte rar la dens
            IntGraph g = RandomGraphGenerator.generate(n, degree, i);

            SccResult reference = new TarjanIterative().run(g);
            for (SccAlgorithm alg : SccAlgorithms.all()) {
                SccResult r = alg.run(g);
                assertTrue(reference.samePartitionAs(r),
                        "Graful #" + i + " (n=" + n + "): " + alg.name() + " diferă de TarjanIterative");
                assertEquals(reference.count(), r.count());
            }
        }
    }

    @Test
    void variantaRecursivaSiIterativaFacAcelasiNumarDeOperatii() {
        IntGraph g = RandomGraphGenerator.generate(5_000, 2.0, 7);
        assertEquals(new TarjanRecursive().run(g).operations(), new TarjanIterative().run(g).operations());
    }

    @Test
    void generatorulEsteReproductibil() {
        IntGraph a = RandomGraphGenerator.generate(1_000, 2.0, 42);
        IntGraph b = RandomGraphGenerator.generate(1_000, 2.0, 42);
        assertEquals(a.edgeCount(), b.edgeCount());
        for (int u = 0; u < a.nodeCount(); u++) {
            assertArrayEquals(a.neighbors(u), b.neighbors(u));
        }
    }
}
