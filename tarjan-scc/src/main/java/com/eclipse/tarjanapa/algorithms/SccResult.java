package com.eclipse.tarjanapa.algorithms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Rezultatul comun al tuturor algoritmilor SCC.
 *
 * @param algorithm     numele algoritmului care a produs rezultatul
 * @param components    lista componentelor; fiecare componentă = nodurile ei
 * @param componentOf   componentOf[v] = indicele componentei nodului v în {@code components}
 * @param elapsedNanos  timpul de execuție al algoritmului propriu-zis
 * @param operations    numărul total de operații elementare (vezi {@link OperationCounter})
 */
public record SccResult(String algorithm,
                        List<int[]> components,
                        int[] componentOf,
                        long elapsedNanos,
                        long operations) {

    /**
     * Construiește rezultatul dintr-un vector de etichete 0..count-1.
     * Gruparea pe componente se face aici, în afara măsurării timpului.
     */
    public static SccResult fromLabels(String algorithm, int[] componentOf, int count,
                                       long elapsedNanos, long operations) {
        int[] sizes = new int[count];
        for (int c : componentOf) {
            sizes[c]++;
        }
        List<int[]> comps = new ArrayList<>(count);
        for (int c = 0; c < count; c++) {
            comps.add(new int[sizes[c]]);
        }
        int[] fill = new int[count];
        for (int v = 0; v < componentOf.length; v++) {
            int c = componentOf[v];
            comps.get(c)[fill[c]++] = v;
        }
        return new SccResult(algorithm, Collections.unmodifiableList(comps), componentOf,
                elapsedNanos, operations);
    }

    /** Numărul de componente tare conexe. */
    public int count() {
        return components.size();
    }

    /** Indicele celei mai mari componente (-1 pentru graful gol). */
    public int largestComponent() {
        int best = -1;
        for (int c = 0; c < components.size(); c++) {
            if (best == -1 || components.get(c).length > components.get(best).length) {
                best = c;
            }
        }
        return best;
    }

    /** Dimensiunea celei mai mari componente (0 pentru graful gol). */
    public int largestSize() {
        int c = largestComponent();
        return c < 0 ? 0 : components.get(c).length;
    }

    public double elapsedMillis() {
        return elapsedNanos / 1_000_000.0;
    }

    /**
     * Forma canonică a partiției: fiecare nod primește ca etichetă cel mai mic
     * nod din componenta sa. Doi algoritmi au găsit aceleași componente
     * dacă și numai dacă formele canonice sunt egale (numerotarea internă diferă).
     */
    public int[] canonicalLabels() {
        int[] minOf = new int[components.size()];
        Arrays.fill(minOf, Integer.MAX_VALUE);
        for (int v = 0; v < componentOf.length; v++) {
            minOf[componentOf[v]] = Math.min(minOf[componentOf[v]], v);
        }
        int[] labels = new int[componentOf.length];
        for (int v = 0; v < componentOf.length; v++) {
            labels[v] = minOf[componentOf[v]];
        }
        return labels;
    }

    /** Verifică dacă celălalt rezultat descrie exact aceeași partiție în SCC-uri. */
    public boolean samePartitionAs(SccResult other) {
        return Arrays.equals(canonicalLabels(), other.canonicalLabels());
    }
}
