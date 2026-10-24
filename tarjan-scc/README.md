# Conectivitatea transportului public din Chișinău — componente tare conexe (Tarjan, Kosaraju, Gabow)

Proiect la „Analiza și proiectarea algoritmilor” (UTM). Aplicația descarcă rețeaua de transport
public din [Tranzy Open Data API](https://tranzy.ai), o modelează ca graf orientat și o analizează cu
algoritmi pentru componente tare conexe: unde se poate ajunge și de unde nu se mai
poate întoarce, ce se întâmplă dacă se închide o rută, ce rute și segmente sunt critice.

## Trei algoritmi la alegere

Toate analizele (harta, simularea, rutele critice, condensarea, rețeaua live, exportul CSV) pot fi
rulate cu oricare dintre cei trei algoritmi:

| Algoritm | Idee | Parcurgeri | Structuri auxiliare |
|---|---|---|---|
| **Tarjan** (1972) | DFS cu `index` și `lowlink`; rădăcina SCC are `lowlink = index` | 1 | stiva S |
| **Kosaraju** (1978) | DFS pe G (ordinea de terminare), apoi DFS pe Gᵀ în ordine inversă | 2 | stivă de ordine + graful transpus |
| **Gabow** (2000, path-based) | DFS cu numere de preordine; stiva P păstrează rădăcinile candidate | 1 | stivele S și P |

Algoritmul se alege astfel:
* **interfața web**: comutatorul „Algoritm: Tarjan | Kosaraju | Gabow” din antet. Tab-ul
  Benchmark are și butonul „Compară Tarjan · Kosaraju · Gabow”, care rulează toți trei pe rețeaua
  reală și verifică faptul că găsesc aceleași componente;
* **API**: parametrul `algorithm=tarjan|kosaraju|gabow` (implicit `tarjan`);
* **consolă**: opțiunea `A` (alegere) și `C` (comparație).

Pe rețeaua din Chișinău (V = 1 203, E = 1 461) toți trei găsesc 57 de SCC-uri, cu cea mai mare
de 1 147 de stații. Numărul de operații elementare: Tarjan 5 070, Gabow 7 476 (are o stivă în
plus, P), Kosaraju 9 195 (două parcurgeri plus construcția lui Gᵀ).

**Stack:** Java 25 · Spring Boot 3.5 · Maven · Leaflet + Chart.js (o pagină statică) · JUnit 5.
Fără bază de date: datele sunt ținute într-un cache JSON local.

## Pornire rapidă

```bash
cd tarjan-scc
export TRANZY_API_KEY=cheia-ta          # cheia se obține din contul de dezvoltator Tranzy
./mvnw spring-boot:run
```

1. La prima pornire, fără `agency-id`, logul afișează lista operatorilor Tranzy. Alegeți ID-ul
   pentru Chișinău.
2. Setați-l cu `export TRANZY_AGENCY_ID=<id>` sau în `src/main/resources/application.yml`
   (`tranzy.agency-id`), apoi reporniți. Pentru Chișinău ID-ul este **4** (RTEC&PUA Chisinau).
3. Deschideți <http://localhost:8080>.

Datele se salvează în `data/cache/{agencyId}/{endpoint}.json`. Cât timp cache-ul are mai puțin de
24h, nu se mai face nicio cerere. Dacă Tranzy nu răspunde sau lipsește cheia, se folosește
cache-ul existent, chiar dacă e mai vechi, deci **aplicația merge offline la prezentare**. Butonul
„Reîncarcă datele” (sau `POST /api/data/refresh`) forțează o descărcare nouă.

## Modul consolă

Meniu text (încărcare, sumar, închidere rută, rute critice, punți, condensare, benchmark,
export CSV), util pentru capturile din raport:

```bash
./mvnw spring-boot:run -Dspring-boot.run.main-class=com.eclipse.tarjanapa.console.ConsoleApp
# sau, din jar:
./mvnw package -DskipTests
java -Dloader.main=com.eclipse.tarjanapa.console.ConsoleApp \
     -cp target/tarjan-scc-0.0.1-SNAPSHOT.jar org.springframework.boot.loader.launch.PropertiesLauncher
```

## Structură

```
src/main/java/com/eclipse/tarjanapa/
├── algorithms/          independent de Spring, testabil separat
│   ├── IntGraph            graf orientat: liste de adiacență cu indici int
│   ├── SccAlgorithm        interfața comună → SccResult (componente, timp, operații)
│   ├── OperationCounter    contor de operații elementare (vizitare, arc, push, pop)
│   ├── TarjanRecursive     Tarjan clasic (index / lowlink / stivă)
│   ├── TarjanIterative     aceeași logică, cu stivă de apeluri explicită
│   ├── Kosaraju            două DFS-uri (G și Gᵀ)
│   ├── Gabow               DFS path-based cu stivele S și P
│   ├── SccChoice           cei 3 algoritmi selectabili în aplicație
│   ├── NaiveScc            BFS înainte ∩ BFS înapoi din fiecare nod, O(V·(V+E))
│   ├── Condensation        DAG-ul SCC-urilor, surse/destinații, max(surse, destinații)
│   ├── TarjanBridges       punți și puncte de articulație (graf neorientat)
│   └── RandomGraphGenerator  grafuri G(n, m) cu seed fix
├── model/               înregistrări GTFS: Route, Trip, Stop, StopTime, Vehicle, Agency
├── graph/               TransitGraph (stații ↔ indici, rute pe arce) și GraphBuilder
├── tranzy/              client HTTP (retry/backoff), cache JSON, parser GTFS tolerant
├── analysis/            starea rețelei, închidere rută, rute critice, live, punți/condensare,
│                        benchmark, export CSV — tot Java simplu, fără Spring
├── service/             NetworkService (încărcare + analize), configurare Spring
├── web/                 API REST
└── console/             ConsoleApp
src/main/resources/static/  index.html, app.js, style.css
```

## Modelul de graf

* **Nod** = stație (`stop_id`). Intră în graf doar stațiile deservite de cel puțin o cursă.
* **Arc** A → B = două stații consecutive (după `stop_sequence`) din aceeași cursă (`trip_id`).
* Fiecare arc reține mulțimea rutelor care trec pe el. `withoutRoutes(...)` scoate rutele de pe
  arce și elimină arcele rămase fără nicio rută, deci un segment deservit și de altă rută rămâne.

## Algoritmi și complexitate

| Algoritm | Timp | Memorie suplimentară | Observații |
|---|---|---|---|
| Tarjan recursiv | O(V + E) | O(V) + stiva de apeluri | o singură parcurgere DFS; StackOverflow la DFS adânc |
| Tarjan iterativ | O(V + E) | O(V) pe heap | aceeași logică și același număr de operații |
| Kosaraju | O(V + E) | O(V + E) pentru Gᵀ | două parcurgeri complete, ≈ 2× operații |
| Gabow | O(V + E) | O(V), două stive | fără `lowlink`; ciclurile se contopesc scoțând din stiva P |
| Naiv (BFS) | O(V·(V + E)) | O(V) | doar pentru comparație; rulează până la 10 000 de noduri |
| Punți / articulații | O(V + E) | O(V) | DFS iterativ pe graful neorientat |
| Condensare | O(V + E) | O(V + E) | arcele minime de adăugat = max(surse, destinații) dacă SCC > 1 |

Contorul de operații numără, la fel în toți algoritmii: vizitarea unui nod, examinarea unui arc
(inclusiv la construcția lui Gᵀ) și push/pop pe structura auxiliară (stiva S la Tarjan, stiva de
ordine la Kosaraju, stivele S și P la Gabow, coada BFS la varianta naivă). Stiva de apeluri a DFS-ului nu se numără, deci
Tarjan recursiv și iterativ dau exact același număr de operații: 5·V când E = 2V.

## API REST

| Metodă | Endpoint | Descriere |
|---|---|---|
| GET | `/api/algorithms` | cei 3 algoritmi selectabili |
| GET | `/api/network/summary` | V, E, rute, nr. SCC, SCC maximă, stațiile din SCC mici, timpul algoritmului |
| GET | `/api/network/stops` | stații cu coordonate și id-ul SCC |
| GET | `/api/network/edges` | arce cu rute |
| GET | `/api/routes` | lista rutelor |
| POST | `/api/simulate/close` | `{"routeIds": [...], "liveOnly": false, "algorithm": "gabow"}`: stațiile desprinse |
| GET | `/api/analysis/critical-routes?limit=20` | clasamentul rutelor critice |
| GET | `/api/analysis/bridges` | punți și puncte de articulație |
| GET | `/api/analysis/condensation` | graful condensat, surse, destinații, arce minime de adăugat |
| GET | `/api/analysis/live` | analiza doar pe rutele cu vehicule în circulație |
| | | Endpoint-urile de mai sus care depind de SCC acceptă `?algorithm=tarjan\|kosaraju\|gabow` |
| POST | `/api/benchmark` | rulează benchmark-ul (corp opțional: `sizes`, `avgDegree`, `seed`, `runs`, …) |
| POST | `/api/data/refresh?agencyId=` | reîncarcă datele din Tranzy |
| GET | `/api/status`, `/api/agencies` | starea încărcării; lista operatorilor |

## Benchmark

Rulează toți algoritmii (Tarjan recursiv și iterativ, Kosaraju, Gabow, naiv) pe rețeaua reală și pe grafuri aleatoare cu 100, 1 000, 10 000 și 100 000
de noduri (grad mediu configurabil, seed fix): 2 rulări de încălzire, apoi 5 rulări măsurate,
din care se raportează media timpului și a operațiilor. Benchmark-ul verifică faptul că toți
algoritmii găsesc exact aceleași componente; altfel se oprește cu eroare. Rezultatele se scriu în
`data/results/benchmark-<data>.csv` și `benchmark-latest.csv` (UTF-8 cu BOM, se deschide direct
în Excel/Word). Parametrii impliciți se schimbă în secțiunea `benchmark:` din `application.yml`.

## Teste

```bash
./mvnw test
```

* grafuri mici cu SCC-uri cunoscute (ciclu, lanț, două cicluri legate unidirecțional, nod izolat,
  graf gol, auto-buclă), pentru toți algoritmii;
* acordul tuturor algoritmilor (inclusiv Gabow) pe 100 de grafuri aleatoare;
* aceleași analize (închidere, rute critice, condensare) cu oricare dintre cei 3 algoritmi selectabili;
* `TarjanIterative`, `Kosaraju` și `Gabow` pe un lanț de 1 000 000 de noduri și `TarjanRecursive` care dă
  `StackOverflowError` pe același lanț;
* construirea grafului din fixture-ul GTFS fals `src/test/resources/fixtures/gtfs/`;
* analizele (închidere, rute critice, punți, condensare, live, benchmark + CSV) și politica de
  cache, fără rețea.
