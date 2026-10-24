package com.eclipse.tarjanapa.service;

import com.eclipse.tarjanapa.model.Agency;
import com.eclipse.tarjanapa.tranzy.TranzyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * La pornirea serverului: dacă agency ID-ul nu e setat, afișează lista
 * operatorilor Tranzy (ca să alegeți ID-ul pentru Chișinău); altfel încarcă
 * rețeaua. Erorile nu opresc serverul — interfața web le afișează.
 */
@Component
@ConditionalOnProperty(name = "app.load-on-startup", havingValue = "true", matchIfMissing = true)
public class StartupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupRunner.class);

    private final NetworkService service;

    public StartupRunner(NetworkService service) {
        this.service = service;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (service.agencyId() == null) {
            printAgencies();
            return;
        }
        try {
            service.load(false);
        } catch (TranzyException e) {
            log.error("Datele nu au putut fi încărcate: {}", e.getMessage());
        }
    }

    private void printAgencies() {
        log.warn("tranzy.agency-id nu este setat. Alegeți operatorul pentru Chișinău din lista de mai jos "
                + "și setați-l în application.yml sau în variabila de mediu TRANZY_AGENCY_ID.");
        try {
            List<Agency> agencies = service.agencies(false);
            StringBuilder sb = new StringBuilder("\nOperatori Tranzy disponibili:\n");
            for (Agency a : agencies) {
                sb.append(String.format("  %-6s %s%n", a.id(), a.name()));
            }
            log.warn(sb.toString());
        } catch (TranzyException e) {
            log.error("Lista operatorilor nu poate fi obținută: {}", e.getMessage());
        }
    }
}
