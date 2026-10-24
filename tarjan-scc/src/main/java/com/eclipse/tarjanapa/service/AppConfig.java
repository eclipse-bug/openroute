package com.eclipse.tarjanapa.service;

import com.eclipse.tarjanapa.analysis.CsvExporter;
import com.eclipse.tarjanapa.tranzy.TranzyClient;
import com.eclipse.tarjanapa.tranzy.TranzyDataSource;
import com.eclipse.tarjanapa.tranzy.TranzyProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Leagă clasele independente de Spring (client Tranzy, export CSV) ca bean-uri.
 * Analizele se creează per cerere, cu algoritmul SCC ales.
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
    CsvExporter csvExporter(BenchmarkProperties props) {
        return new CsvExporter(props.resultsDir());
    }
}
