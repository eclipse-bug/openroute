package com.eclipse.tarjanapa.service;

import com.eclipse.tarjanapa.analysis.Benchmark;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;
import java.util.List;

/** Configurarea implicită a benchmark-ului (secțiunea {@code benchmark:} din application.yml). */
@ConfigurationProperties(prefix = "benchmark")
public record BenchmarkProperties(
        @DefaultValue({"100", "1000", "10000", "100000"}) List<Integer> sizes,
        @DefaultValue("2.0") double avgDegree,
        @DefaultValue("42") long seed,
        @DefaultValue("5") int runs,
        @DefaultValue("2") int warmupRuns,
        @DefaultValue("10000") int naiveMaxNodes,
        @DefaultValue("data/results") Path resultsDir) {

    public Benchmark.Config toConfig() {
        return new Benchmark.Config(sizes, avgDegree, seed, runs, warmupRuns, naiveMaxNodes);
    }
}
