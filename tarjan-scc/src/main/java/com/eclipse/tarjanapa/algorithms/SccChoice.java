package com.eclipse.tarjanapa.algorithms;

import java.util.Locale;
import java.util.function.Supplier;

/**
 * Cei trei algoritmi SCC pe care utilizatorul îi poate alege în aplicație
 * (interfața web, API, consolă). Toate analizele rețelei rulează cu algoritmul ales.
 * <p>
 * Pentru Tarjan se folosește varianta iterativă: dă exact aceleași componente
 * ca cea recursivă, dar nu riscă {@link StackOverflowError}.
 */
public enum SccChoice {

    TARJAN("tarjan", "Tarjan", "un DFS, index + lowlink, o stivă", TarjanIterative::new),
    KOSARAJU("kosaraju", "Kosaraju", "două DFS-uri: pe G și pe graful transpus", Kosaraju::new),
    GABOW("gabow", "Gabow", "un DFS path-based, două stive (S și P)", Gabow::new);

    private final String id;
    private final String label;
    private final String description;
    private final Supplier<SccAlgorithm> factory;

    SccChoice(String id, String label, String description, Supplier<SccAlgorithm> factory) {
        this.id = id;
        this.label = label;
        this.description = description;
        this.factory = factory;
    }

    /** Identificatorul folosit în URL-uri și în consolă: tarjan, kosaraju, gabow. */
    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    /** O instanță nouă a algoritmului. */
    public SccAlgorithm create() {
        return factory.get();
    }

    /**
     * Interpretează numele primit (fără diferență între majuscule); gol ⇒ Tarjan.
     *
     * @throws IllegalArgumentException pentru un nume necunoscut
     */
    public static SccChoice parse(String name) {
        if (name == null || name.isBlank()) {
            return TARJAN;
        }
        String key = name.trim().toLowerCase(Locale.ROOT);
        for (SccChoice c : values()) {
            if (c.id.equals(key) || c.name().equalsIgnoreCase(key)) {
                return c;
            }
        }
        throw new IllegalArgumentException("Algoritm necunoscut: „" + name
                + "”. Valori posibile: tarjan, kosaraju, gabow.");
    }
}
