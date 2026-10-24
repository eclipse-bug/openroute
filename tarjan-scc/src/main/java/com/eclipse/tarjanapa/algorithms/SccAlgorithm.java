package com.eclipse.tarjanapa.algorithms;

/**
 * Interfața comună a algoritmilor de componente tare conexe.
 * <p>
 * Fiecare implementare scrie doar {@link #label}; măsurarea timpului și
 * construirea {@link SccResult} se fac uniform în {@link #run}.
 */
public interface SccAlgorithm {

    /** Numele afișat în rapoarte și în CSV. */
    String name();

    /**
     * Calculează componentele: completează {@code componentOf} cu etichete
     * 0..k-1 și întoarce k (numărul de componente).
     */
    int label(IntGraph g, int[] componentOf, OperationCounter ops);

    default SccResult run(IntGraph g) {
        OperationCounter ops = new OperationCounter();
        int[] componentOf = new int[g.nodeCount()];
        long start = System.nanoTime();
        int count = label(g, componentOf, ops);
        long elapsed = System.nanoTime() - start;
        return SccResult.fromLabels(name(), componentOf, count, elapsed, ops.total());
    }
}
