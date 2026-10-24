package com.eclipse.tarjanapa.service;

import com.eclipse.tarjanapa.analysis.ClosureAnalysis;
import com.eclipse.tarjanapa.analysis.CriticalRoutesAnalysis;
import com.eclipse.tarjanapa.analysis.CsvExporter;
import com.eclipse.tarjanapa.analysis.LiveAnalysis;
import com.eclipse.tarjanapa.tranzy.TranzyClient;
import com.eclipse.tarjanapa.tranzy.TranzyDataSource;
import com.eclipse.tarjanapa.tranzy.TranzyProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Leagă clasele independente de Spring (client Tranzy, analize) ca bean-uri.
 * Pachetele {@code algorithms} și {@code analysis} nu depind de Spring.
 */
@Configuration
@EnableConfigurationProperties({TranzyProperties.class, BenchmarkProperties.class})
public class AppConfig {

    @Bean
    TranzyClient tranzyClient(TranzyProperties props) {
        return new TranzyClient(props);
    }

    @Bean
    TranzyDataSource tranzyDataSource(TranzyProperties props, TranzyClient client, ObjectMapper mapper) {
        return new TranzyDataSource(props, client, mapper);
    }

    @Bean
    ClosureAnalysis closureAnalysis() {
        return new ClosureAnalysis();
    }

    @Bean
    CriticalRoutesAnalysis criticalRoutesAnalysis(ClosureAnalysis closure) {
        return new CriticalRoutesAnalysis(closure);
    }

    @Bean
    LiveAnalysis liveAnalysis() {
        return new LiveAnalysis();
    }

    @Bean
    CsvExporter csvExporter(BenchmarkProperties props) {
        return new CsvExporter(props.resultsDir());
    }
}
