# Prova tecnica – Piattaforma di emissione documenti commerciali

Sistema a microservizi che emette scontrini per conto di organizzazioni clienti, controlla che
l'organizzazione sia abilitata al prodotto, trasmette le chiusure giornaliere a un ente esterno
simulato e rende disponibili gli acquisti al CRM tramite eventi Kafka.

Stack: Java 21, Spring Boot 3.5, MySQL 8.4, MongoDB 7, Kafka 3.9 (KRaft), Maven, Docker Compose,
Testcontainers per i test di integrazione, springdoc per la documentazione OpenAPI.

## Avvio

Serve solo Docker (con Docker Compose v2).

```bash
docker compose up --build
```

La prima volta ci mette qualche minuto perché Maven scarica le dipendenze dentro la build.

| Servizio           | Porta | Swagger UI                              |
|--------------------|-------|-----------------------------------------|
| account-service    | 8081  | http://localhost:8081/swagger-ui.html   |
| receipts-service   | 8082  | http://localhost:8082/swagger-ui.html   |
| crm-service        | 8083  | http://localhost:8083/swagger-ui.html   |
| transmission-stub  | 8090  | –                                       |
| MySQL              | 3306  |                                         |
| MongoDB            | 27017 |                                         |
| Kafka              | 29092 | (listener per l'host)                   |

Per lanciare un servizio dall'IDE basta avviare solo l'infrastruttura (`docker compose up mysql mongo kafka`):
i valori di default in `application.yml` puntano a localhost.

## Prova veloce con curl

```bash
# 1. creo un'organizzazione (mi segno l'id)
curl -s -X POST localhost:8081/organizations -H 'Content-Type: application/json' \
  -d '{"name":"Bar Sport srl"}'
ORG=<id restituito>

# 2. la abilito a SCONTRINA e CRM (ripetere la stessa richiesta non cambia nulla)
curl -s -X PUT localhost:8081/organizations/$ORG/enablements/SCONTRINA -H 'Content-Type: application/json' \
  -d '{"status":"ENABLED","startDate":"2026-01-01"}'
curl -s -X PUT localhost:8081/organizations/$ORG/enablements/CRM -H 'Content-Type: application/json' \
  -d '{"status":"ENABLED","startDate":"2026-01-01"}'

# 3. emetto uno scontrino (rilanciando con la stessa Idempotency-Key torna lo stesso documento con 200)
curl -s -i -X POST localhost:8082/receipts -H 'Content-Type: application/json' \
  -H 'Idempotency-Key: ordine-1' -H 'X-Correlation-Id: prova-123' \
  -d '{"organizationId":"'$ORG'","cashRegisterId":"CASSA-1","paymentType":"CARD","customerId":"mario.rossi",
       "lines":[{"description":"caffè","quantity":2,"unitPrice":1.10,"vatRate":10},
                {"description":"acqua","quantity":1,"unitPrice":1.22,"vatRate":22}]}'

# 4. annullo (crea un nuovo documento collegato, il secondo tentativo dà 409)
curl -s -X POST localhost:8082/receipts/<id>/cancellation

# 5. storico acquisti del cliente nel CRM
curl -s localhost:8083/organizations/$ORG/customers/mario.rossi/summary

# 6. chiusura di un giorno passato ed esito della trasmissione
curl -s -X POST localhost:8082/closures -H 'Content-Type: application/json' \
  -d '{"organizationId":"'$ORG'","cashRegisterId":"CASSA-1","businessDate":"<data di ieri>"}'
curl -s localhost:8082/closures/<id>
curl -s localhost:8090/transmissions      # cosa ha ricevuto l'ente simulato e quante volte
```

La chiusura si può fare solo per giorni già conclusi (vedi sotto), quindi provandola a mano sul giorno
precedente si ottiene una chiusura con zero documenti: basta comunque per vedere stati, errori e retry
verso lo stub. Il flusso completo con documenti è coperto da `ClosureIntegrationTest`.

## Architettura

```
                         REST: posso emettere?
   client ──REST──► receipts-service ────────────────────► account-service
                     │  MySQL receipts_db                   │  MySQL account_db
                     │                                      │
                     │ REST (chiusure)          outbox      │ outbox
                     ▼                            │         │
              transmission-stub                   ▼         ▼
                                       ┌──────────── Kafka ────────────┐
                                       │ receipts.receipt-events       │
                                       │ account.enablement-events     │
                                       └──────────────┬────────────────┘
                                                      ▼
                                                 crm-service ── MongoDB
```

Moduli Maven:

- **account-service** – organizzazioni (attiva/sospesa) e abilitazioni ai prodotti con data di inizio e fine.
  Espone anche `GET /organizations/{id}/access/{product}`, usato da Receipts per sapere se un'organizzazione
  può emettere. Ogni modifica di un'abilitazione genera un evento `ENABLEMENT_CHANGED`.
- **receipts-service** – emissione e annullo dei documenti, numerazione per punto cassa, chiusura giornaliera
  e trasmissione all'ente esterno con retry. Pubblica `RECEIPT_ISSUED` e `RECEIPT_CANCELLED`.
- **crm-service** – consuma gli eventi e tiene lo storico acquisti per cliente (numero acquisti, totale speso,
  ultimo acquisto), solo per le organizzazioni abilitate a CRM.
- **transmission-stub** – finto ente esterno: il 30% delle volte risponde 503, il 20% risponde dopo 5 secondi
  (oltre il timeout di 3s di Receipts). È idempotente sul `closureId`.
- **common** – poche classi tecniche condivise: formato degli errori, `ApiException`, filtro per il correlation id.

Ogni servizio ha il proprio database e nessuno legge il database di un altro. In docker compose c'è una sola
istanza MySQL con due database e due utenti diversi (ognuno vede solo il suo): in produzione sarebbero istanze
separate, qui mi sembrava inutile appesantire l'ambiente.

## Scelte progettuali

### Cosa va su MySQL e cosa su MongoDB

- **MySQL** per Account e Receipts. Qui servono transazioni, vincoli `UNIQUE` e lock di riga: sono proprio
  gli strumenti che uso per numerazione, idempotenza, una sola chiusura per giorno e outbox nella stessa
  transazione del dato. I documenti fiscali poi hanno una struttura fissa e devono essere consistenti.
- **MongoDB** per il CRM. È un modello di lettura costruito dagli eventi: un documento per acquisto con upsert
  per id, nessuna relazione da mantenere, e se servisse lo si può ricostruire rileggendo gli eventi.
  Gli upsert atomici di Mongo rendono semplice gestire eventi duplicati o fuori ordine.

### Controllo dell'abilitazione: sincrono

Receipts, prima di emettere, chiama Account (`GET /organizations/{id}/access/SCONTRINA`) con timeout brevi.

Ho scelto la chiamata sincrona perché è la soluzione più semplice e il dato è sempre aggiornato: se
un'organizzazione viene sospesa, dal momento dopo non può più emettere. Con una copia locale costruita dagli
eventi ci sarebbe una finestra (il ritardo di Kafka) in cui un'organizzazione appena sospesa potrebbe ancora
emettere, e in più dovrei pubblicare anche gli eventi di cambio stato dell'organizzazione.

**Se Account non è raggiungibile** (timeout, connessione rifiutata, 5xx) Receipts risponde `503 ACCOUNT_UNAVAILABLE`
e il documento non viene emesso. È una scelta "fail closed": preferisco non emettere piuttosto che emettere per
un'organizzazione che magari non è più abilitata. Il prezzo è che Receipts dipende dalla disponibilità di Account;
con più tempo aggiungerei una cache con scadenza breve o la copia locale come fallback.

Nel CRM invece ho fatto il contrario: tiene una copia locale delle abilitazioni CRM costruita dagli eventi di
Account, perché lavora già in modo asincrono e non avrebbe senso bloccare il consumo dei messaggi se Account è giù.

### Spring MVC invece di Project Reactor

Reactor era indicato come preferenza non vincolante. Ho usato Spring MVC classico con JPA perché la parte critica
della prova è transazionale (lock di riga, vincoli, outbox nella stessa transazione) e con JDBC/JPA bloccanti è
molto più lineare da scrivere, testare e spiegare. Con Reactor avrei dovuto usare R2DBC e gestire le transazioni
reattive, aggiungendo complessità senza un vantaggio reale per questi volumi.

### Eventi

- Payload JSON (stringa), serializzato e letto con l'`ObjectMapper` di Spring: niente type header di Spring Kafka,
  così producer e consumer non condividono classi Java.
- La chiave del messaggio è l'id dell'organizzazione: gli eventi di una stessa organizzazione finiscono nella
  stessa partizione e mantengono l'ordine.
- Il correlation id viaggia nell'header `X-Correlation-Id` del messaggio.
- Gli eventi contengono lo stato completo (non solo "cosa è cambiato"), così il consumer non deve richiamare il produttore.

## Punti critici

### Numerazione concorrente

Tabella `cash_register_counter` con chiave (organizzazione, punto cassa) e l'ultimo numero usato.
Dentro la transazione di emissione (`ReceiptNumberGenerator`):

```sql
INSERT INTO cash_register_counter (organization_id, cash_register_id, last_number) VALUES (?, ?, 1)
ON DUPLICATE KEY UPDATE last_number = last_number + 1;
SELECT last_number FROM cash_register_counter WHERE organization_id = ? AND cash_register_id = ?;
```

- L'upsert crea il contatore la prima volta e lo incrementa le volte successive, in modo atomico.
- MySQL tiene un lock esclusivo sulla riga fino al commit: una seconda emissione sullo stesso punto cassa aspetta
  che la prima finisca, quindi **niente duplicati**.
- Se la transazione fallisce (es. errore nel salvataggio), il rollback annulla anche l'incremento, quindi
  **niente buchi**.
- Punti cassa diversi hanno righe diverse e non si bloccano tra loro.
- C'è comunque un vincolo `UNIQUE (organization_id, cash_register_id, receipt_number)` come rete di sicurezza.

Ho scartato `AUTO_INCREMENT` (unico per tabella e lascia buchi in caso di rollback) e il `MAX(numero)+1`
(due transazioni possono leggere lo stesso valore). Il test `concurrentRequestsGetConsecutiveNumbersWithoutGapsOrDuplicates`
lancia 30 emissioni in parallelo e verifica di ottenere esattamente i numeri da 1 a 30.

Il limite di questa soluzione è che le emissioni sullo stesso punto cassa sono serializzate: per un punto
cassa fisico va benissimo (non emette centinaia di scontrini al secondo).

### Idempotenza

- **Emissione**: l'header `Idempotency-Key` è obbligatorio. C'è un vincolo `UNIQUE (organization_id, idempotency_key)`.
  1. Se la chiave esiste già restituisco il documento già emesso con `200` (invece di `201`).
  2. Se due richieste con la stessa chiave arrivano insieme, entrambe passano il controllo ma il vincolo unique
     ne fa vincere una sola: l'altra va in rollback (contatore compreso) e restituisce il documento della prima.

  Per questo in `ReceiptService` uso `TransactionTemplate`: mi serve gestire l'eccezione del vincolo **fuori**
  dalla transazione fallita.
- **Annullo**: vincolo `UNIQUE (original_receipt_id)`, un documento si può annullare una sola volta. Il secondo
  tentativo (anche concorrente) riceve `409 RECEIPT_ALREADY_CANCELLED`.
- **Provisioning delle abilitazioni**: è un `PUT` che descrive lo stato finale. Se l'abilitazione è già in quello
  stato non faccio nulla: nessun errore e nessun evento. Per le richieste concorrenti sulla stessa organizzazione
  faccio `SELECT ... FOR UPDATE` sulla riga dell'organizzazione, così vengono eseguite una dopo l'altra.
- **Consumer CRM**: vedi sotto, eventi duplicati non cambiano il risultato.

### Doppia trasmissione

1. Una sola chiusura per (organizzazione, punto cassa, giorno) grazie a un vincolo unique: se il job notturno
   parte due volte o su due istanze, la seconda creazione trova quella esistente.
2. Prima di inviare, il job "prenota" la chiusura con un update condizionato:

   ```sql
   UPDATE daily_closure SET status = 'IN_PROGRESS', attempts = attempts + 1
   WHERE id = ? AND status = 'TO_SEND'
   ```

   Se due esecuzioni ci provano insieme, solo una ottiene "1 riga aggiornata" e invia; l'altra trova 0 e passa oltre.
   L'update ha una sua transazione breve, quindi lo stato `IN_PROGRESS` è subito visibile alle altre istanze e
   non tengo transazioni aperte durante la chiamata HTTP.
3. Esito: `SENT` con numero di protocollo, oppure di nuovo `TO_SEND` con `nextAttemptAt` calcolato con backoff
   esponenziale (30s, 1m, 2m, 4m...) e, superati i 5 tentativi, `FAILED`. Lo stato si consulta con `GET /closures/{id}`.
4. Resta il caso del timeout: l'ente ha ricevuto la chiusura ma noi non abbiamo avuto la risposta. Per questo
   mando il `closureId` che fa da chiave di idempotenza anche per l'ente (lo stub restituisce lo stesso protocollo).

Il test `sameClosureIsNeverTransmittedTwiceEvenIfTheJobRunsInParallel` lancia 5 esecuzioni in parallelo sulla stessa
chiusura e verifica che il client di trasmissione venga chiamato una sola volta.

La chiusura si può creare solo per giorni già conclusi: così l'insieme dei documenti non cambia più e non ci sono
corse tra emissione e chiusura. Viene creata da un job alle 00:30 per il giorno prima, oppure a mano con `POST /closures`.

### Coerenza tra database ed evento pubblicato (outbox)

Account e Receipts non pubblicano direttamente su Kafka. Nella stessa transazione in cui salvano il dato scrivono
l'evento nella tabella `outbox_event` (`OutboxService` ha `Propagation.MANDATORY` proprio per obbligare a chiamarlo
dentro una transazione). Quindi o si salvano entrambi o nessuno dei due.

Un job (`OutboxPublisher`, ogni secondo) legge gli eventi non pubblicati con `SELECT ... FOR UPDATE SKIP LOCKED`,
li invia a Kafka aspettando la conferma del broker e li segna come pubblicati. `SKIP LOCKED` fa sì che con più istanze
ognuna prenda righe diverse. Se un invio fallisce mi fermo, così non cambio l'ordine degli eventi, e riprovo al giro dopo.

La garanzia è **at least once**: se il servizio muore dopo l'invio ma prima del commit, l'evento viene ripubblicato.
Per questo i consumer devono essere idempotenti.

### CRM: eventi duplicati e fuori ordine

Ogni scontrino è un documento Mongo con `_id` = id dello scontrino, scritto con un upsert:

- `RECEIPT_ISSUED` imposta importo, cliente e data, senza toccare il flag `cancelled`;
- `RECEIPT_CANCELLED` imposta `cancelled = true` sull'acquisto originale, creandolo se l'emissione non è ancora arrivata.

Ripetere un evento non cambia nulla e l'ordine di arrivo non conta. Numero acquisti, totale e ultimo acquisto si
calcolano in lettura sugli acquisti emessi e non annullati.

Per le abilitazioni il CRM salva l'`occurredAt` dell'ultimo evento applicato e scarta quelli più vecchi o uguali.

## Regola di arrotondamento

- I prezzi unitari sono **IVA inclusa** con massimo 2 decimali (come su uno scontrino).
- Totale riga = quantità × prezzo unitario.
- L'IVA si calcola **per aliquota**: sommo i totali delle righe con la stessa aliquota e scorporo
  `imponibile = lordo × 100 / (100 + aliquota)`, arrotondato a 2 decimali `HALF_UP`; `IVA = lordo − imponibile`.
- Totale documento = somma dei lordi; IVA documento = somma delle IVA per aliquota.

Calcolando l'IVA come differenza, imponibile + IVA torna sempre al centesimo con il totale. Raggruppare per aliquota
evita che gli arrotondamenti riga per riga si sommino (esempio nei test: tre righe da 0,10 € al 22% danno IVA 0,05
e non 0,06). Codice in `ReceiptCalculator`, test in `ReceiptCalculatorTest`.

Aliquote ammesse: 4, 10, 22. Quantità intere ≥ 1.

## Errori, log e tracciabilità

Tutti i servizi rispondono agli errori con lo stesso formato (`ErrorResponse` + `GlobalExceptionHandler` nel modulo common):

```json
{
  "timestamp": "2026-11-12T20:03:25.770Z",
  "status": 409,
  "code": "RECEIPT_ALREADY_CANCELLED",
  "message": "Il documento 2 è già stato annullato",
  "path": "/receipts/2/cancellation",
  "correlationId": "5806a077-1a4d-4f64-9e21-9b4480dd27ba",
  "details": []
}
```

`code` è un identificativo stabile pensato per chi integra le API; in `details` finiscono gli errori di validazione campo per campo.

Codici principali: `VALIDATION_ERROR`, `ORGANIZATION_NOT_FOUND`, `ORGANIZATION_SUSPENDED`, `PRODUCT_NOT_ENABLED`,
`ACCOUNT_UNAVAILABLE` (503), `INVALID_VAT_RATE`, `RECEIPT_NOT_FOUND`, `RECEIPT_ALREADY_CANCELLED`,
`CLOSURE_DATE_NOT_PAST`, `INTERNAL_ERROR`.

**Log JSON**: uso il logging strutturato nativo di Spring Boot (`logging.structured.format.console: ecs`), quindi
nessuna libreria in più.

**Correlation id**: `CorrelationIdFilter` legge l'header `X-Correlation-Id` (o ne genera uno), lo mette nell'MDC
(e quindi in ogni riga di log) e lo rimanda nella risposta. Viene propagato:
- nelle chiamate REST tra servizi, con un interceptor del `RestClient`;
- negli eventi Kafka: viene salvato nella riga di outbox e inviato come header; il CRM lo rimette nell'MDC quando consuma.

Così filtrando i log per un id si segue una richiesta da Receipts ad Account e fino al CRM.

## Test

```bash
mvn test
```

Serve Docker in esecuzione: i test di integrazione avviano MySQL, Kafka e MongoDB veri con Testcontainers.

- **account-service**: abilitazioni (idempotenza anche con 8 richieste parallele, un evento per ogni cambio di stato,
  verifica accesso), pubblicazione dell'outbox su Kafka con correlation id, formato errori via MockMvc.
- **receipts-service**: calcolo IVA e arrotondamenti, numerazione con 30 richieste concorrenti, idempotenza
  (sequenziale e concorrente, senza buchi nel numero), annullo (anche concorrente), client verso Account
  (allowed / non abilitata / 404 / Account giù) con `MockRestServiceServer`, chiusura, retry/backoff
  e assenza di doppia trasmissione con 5 esecuzioni parallele.
- **crm-service**: duplicati, annullo arrivato prima dell'emissione, organizzazioni non abilitate, eventi di
  abilitazione fuori ordine e un test end-to-end che passa davvero da Kafka.

Nei test di Receipts, Account e l'ente esterno sono sostituiti da mock (`@MockitoBean`).

## Semplificazioni e cosa non ho fatto

- **Nessuna autenticazione/autorizzazione**: l'organizzazione arriva nel body della richiesta. In un sistema vero
  verrebbe dal token del chiamante.
- **Chiusura solo per giorni passati**: niente chiusura "a metà giornata".
- **Chiusura bloccata in `IN_PROGRESS`**: se l'istanza muore durante l'invio la chiusura resta in quello stato.
  Andrebbe aggiunto un recupero che dopo un timeout la rimette in `TO_SEND` (l'invio doppio è comunque gestito
  dall'idempotenza sul `closureId` lato ente).
- **Idempotency-Key**: non verifico che la seconda richiesta abbia lo stesso body della prima (andrebbe salvato
  un hash della richiesta e restituito un errore se diverso).
- **CRM**: l'ordine tra topic diversi non è garantito; se un'abilitazione CRM arriva dopo gli scontrini di quel
  periodo, quegli scontrini vengono ignorati. Totali calcolati in lettura (per molti acquisti per cliente userei
  un'aggregazione Mongo o un documento riepilogo aggiornato in modo idempotente).
- **Nessun dead letter topic**: un messaggio che continua a fallire viene loggato e scartato dopo 3 tentativi.
- **Eventi di cambio stato dell'organizzazione** (attiva/sospesa) non pubblicati: non servono a nessun consumer
  con il controllo sincrono.
- Il codice dell'outbox è duplicato in Account e Receipts: sono poche classi e ho preferito non creare
  una libreria condivisa con dipendenze JPA/Kafka solo per questo.
- Clienti identificati solo da un id, nessuna anagrafica.

## Con più tempo

- Recupero delle chiusure rimaste in `IN_PROGRESS` e alert sulle chiusure `FAILED`.
- Dead letter topic e un endpoint per rilanciare i messaggi.
- Cache breve (o copia locale da eventi) per l'abilitazione, per non dipendere del tutto dalla disponibilità di Account.
- Controllo dell'hash del body per le chiavi di idempotenza e scadenza delle chiavi.
- Pulizia periodica della tabella outbox.
- Autenticazione (es. OAuth2/JWT) con l'organizzazione presa dal token.
- Metriche (Micrometer/Prometheus) e tracing distribuito (OpenTelemetry) al posto del solo correlation id.
- Healthcheck applicativi (Actuator) in docker compose.

## Uso dell'intelligenza artificiale

Ho usato **Claude Code** (Anthropic) come assistente durante lo sviluppo, in particolare per:

- la prima stesura del codice dei servizi e dei test;
- la configurazione di Docker Compose e Testcontainers;
- la stesura di questo README.

<!-- Da completare con quello che hai fatto tu: cosa hai rivisto, modificato o riscritto e come hai verificato il funzionamento. -->
