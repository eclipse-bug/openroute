package com.eclipse.tarjanapa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Serverul web: API REST + pagina cu harta (http://localhost:8080).
 * Pentru modul consolă vezi {@link com.eclipse.tarjanapa.console.ConsoleApp}.
 */
@SpringBootApplication
public class TarjanApaApplication {

    public static void main(String[] args) {
        SpringApplication.run(TarjanApaApplication.class, args);
    }

}
