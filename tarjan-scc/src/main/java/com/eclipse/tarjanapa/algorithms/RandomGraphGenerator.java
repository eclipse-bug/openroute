package com.eclipse.tarjanapa.algorithms;

import java.util.SplittableRandom;

/**
 * Generator de grafuri orientate aleatoare pentru benchmark (model G(n, m)).
 * <p>
 * Se generează m = round(n · gradMediu) arce cu capete alese uniform; se evită
 * auto-buclele, dar se pot repeta arce (efect neglijabil pentru n mare).
 * Cu un seed fix rezultatul este reproductibil, deci tabelele din raport pot fi
 * regenerate exact.
 */
public final class RandomGraphGenerator {

    private RandomGraphGenerator() {
    }

    /**
     * @param n          numărul de noduri
     * @param avgDegree  gradul mediu de ieșire (densitatea), de ex. 2.0
     * @param seed       sămânța generatorului
     */
    public static IntGraph generate(int n, double avgDegree, long seed) {
        if (avgDegree < 0) {
            throw new IllegalArgumentException("Gradul mediu trebuie să fie ≥ 0");
        }
        IntGraph.Builder b = IntGraph.builder(n);
        if (n < 2) {
            return b.build();
        }
        SplittableRandom rnd = new SplittableRandom(seed);
        long m = Math.round(n * avgDegree);
        for (long i = 0; i < m; i++) {
            int u = rnd.nextInt(n);
            int v = rnd.nextInt(n - 1);
            if (v >= u) {
                v++;   // v uniform în {0..n-1} \ {u}
            }
            b.addEdge(u, v);
        }
        return b.build();
    }
}
