package com.eclipse.tarjanapa.algorithms;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/** Grafuri adânci: varianta iterativă trebuie să meargă acolo unde recursivitatea crapă. */
class DeepGraphTest {

    private static final int N = 1_000_000;

    /** Lanțul 0 → 1 → … → N-1: adâncimea DFS este N. */
    private static IntGraph chain(int n) {
        IntGraph.Builder b = IntGraph.builder(n);
        for (int i = 0; i + 1 < n; i++) {
            b.addEdge(i, i + 1);
        }
        return b.build();
    }

    @Test
    void tarjanIterativMergePeUnLantDeUnMilionDeNoduri() {
        SccResult r = new TarjanIterative().run(chain(N));
        assertEquals(N, r.count());
        assertEquals(1, r.largestSize());
    }

    @Test
    void tarjanIterativPeUnCicluDeUnMilionDeNoduri() {
        IntGraph.Builder b = IntGraph.builder(N);
        for (int i = 0; i < N; i++) {
            b.addEdge(i, (i + 1) % N);
        }
        SccResult r = new TarjanIterative().run(b.build());
        assertEquals(1, r.count());
        assertEquals(N, r.largestSize());
    }

    @Test
    void tarjanRecursivDepasesteStivaPeAcelasiLant() throws InterruptedException {
        IntGraph g = chain(N);
        AtomicReference<Throwable> error = new AtomicReference<>();
        // Fir cu stivă de 512 KiB (ordinul de mărime al stivei implicite).
        Thread t = new Thread(null, () -> {
            try {
                new TarjanRecursive().run(g);
            } catch (Throwable e) {
                error.set(e);
            }
        }, "stiva-mica", 512 * 1024);
        t.start();
        t.join();
        assertInstanceOf(StackOverflowError.class, error.get());
    }

    @Test
    void kosarajuIterativMergeSiElPeLant() {
        assertEquals(N, new Kosaraju().run(chain(N)).count());
    }
}
