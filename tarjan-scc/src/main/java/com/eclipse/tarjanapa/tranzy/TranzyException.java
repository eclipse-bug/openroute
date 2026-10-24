package com.eclipse.tarjanapa.tranzy;

/** Eroare la obținerea datelor din Tranzy, cu un tip care permite mesaje clare pentru utilizator. */
public class TranzyException extends RuntimeException {

    public enum Kind {
        /** Lipsește variabila de mediu TRANZY_API_KEY. */
        MISSING_API_KEY,
        /** Nu este configurat operatorul (X-Agency-Id). */
        MISSING_AGENCY,
        /** 401 / 403: cheie invalidă sau fără drepturi pentru operator. */
        UNAUTHORIZED,
        /** 429 după epuizarea reîncercărilor. */
        RATE_LIMITED,
        /** Răspuns gol sau listă vidă. */
        EMPTY_RESPONSE,
        /** Alt cod HTTP de eroare. */
        HTTP_ERROR,
        /** Eroare de rețea / timeout / JSON invalid. */
        NETWORK
    }

    private final Kind kind;

    public TranzyException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public TranzyException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
