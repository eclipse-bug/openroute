package com.eclipse.tarjanapa.algorithms;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/** Utilitare comune: lista algoritmilor și rularea pe un fir cu stivă mare. */
public final class SccAlgorithms {

    /** Mărimea stivei pentru firul care rulează varianta recursivă pe grafuri mari. */
    public static final long BIG_STACK_BYTES = 1L << 30; // 1 GiB

    private SccAlgorithms() {
    }

    /** Cei patru algoritmi comparați în benchmark, în ordinea din raport. */
    public static List<SccAlgorithm> all() {
        return List.of(new TarjanRecursive(), new TarjanIterative(), new Kosaraju(), new NaiveScc());
    }

    /**
     * Rulează sarcina pe un fir nou cu stivă de {@link #BIG_STACK_BYTES}.
     * Necesar pentru {@link TarjanRecursive} pe grafuri aleatoare mari, unde
     * adâncimea DFS depășește stiva implicită a firului principal.
     */
    public static <T> T withBigStack(Supplier<T> task) {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread t = new Thread(null, () -> {
            try {
                result.set(task.get());
            } catch (Throwable e) {
                error.set(e);
            }
        }, "scc-big-stack", BIG_STACK_BYTES);
        t.start();
        try {
            t.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Rulare întreruptă", e);
        }
        Throwable e = error.get();
        if (e instanceof RuntimeException re) {
            throw re;
        }
        if (e instanceof Error err) {
            throw err;
        }
        if (e != null) {
            throw new IllegalStateException(e);
        }
        return result.get();
    }
}
