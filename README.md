# PokeVault per iOS (Kotlin Multiplatform)

Repo separato da `pokevault`. L'app Android in produzione non dipende da qui
e qui non si modifica nulla di quel repo: il codice si copia e si adatta.

- `shared/`: codice e UI comuni (Compose Multiplatform). Quasi tutto il lavoro sta qui.
- `iosApp/`: il guscio Xcode che ospita la UI condivisa. Bundle ID e nome in `iosApp/Configuration/Config.xcconfig`.
- `androidApp/`: serve solo a provare il codice condiviso su Android. `applicationId`
  `com.emabuia.pokevault.kmp`, quindi convive con la PokeVault di Play senza toccarla.
- `desktopApp/`: per vedere la UI su Windows senza telefono.

Il backend è lo stesso dell'app Android (Worker di produzione, solo GET pubbliche).

## Lavorare da Windows (senza Mac)

```sh
export JAVA_HOME="C:/Users/ASUS/.jdks/jbr-21.0.11"   # JDK 21: con la 25 Gradle/Kotlin si rompono
./gradlew :shared:jvmTest              # test del codice comune
./gradlew :desktopApp:run              # la UI in una finestra su Windows
./gradlew :androidApp:assembleDebug    # APK di prova
```

Da Windows i target iOS non si compilano: ci pensa la CI su macOS.
`gradlew` può uscire 0 anche quando fallisce: va letta la riga `BUILD`.

## CI (GitHub Actions)

`.github/workflows/build.yml`, a ogni push su `main`:
- **Android + test** su Linux;
- **iOS** su macOS: compila per il simulatore senza firma e pubblica `PokeVault-simulator.zip`
  tra gli artifact della run, da caricare su appetize.io per vedere l'app nel browser.

Con il repo pubblico i minuti macOS sono gratis. Con il repo privato ne restano circa 200 al mese.

## Installare su un iPhone senza App Store

`.github/workflows/iphone.yml` parte solo a mano (Actions → **iPhone (ipa da firmare)** → Run workflow,
anche dall'app GitHub sul telefono) e pubblica `PokeVault.ipa` senza firma: build Release per iPhone vero.

1. Sul PC: iTunes (quello del sito Apple, non del Microsoft Store) e [Sideloadly](https://sideloadly.io).
2. Scarica l'artifact `PokeVault-iphone` della run, estrai `PokeVault.ipa`.
3. iPhone collegato col cavo, trascina l'ipa in Sideloadly, inserisci un Apple ID (meglio uno fatto apposta) e Start.
4. Sull'iPhone: Impostazioni → Privacy e sicurezza → **Modalità sviluppatore** (si riavvia), poi
   Impostazioni → Generali → VPN e gestione dispositivi → autorizza il profilo.

Con un Apple ID gratuito l'app dura **7 giorni** (poi si reinstalla, i dati restano) e se ne tengono al massimo 3.
Niente push ne' Sign in with Apple. TradeRadar con un account vero lavora sul server di **produzione**.

## Cosa resta da fare

### Fase 1: la filiera (gratis)
- [x] Scheletro KMP: Android, desktop, iOS
- [x] Prima schermata vera: espansioni italiane da `/v1/expansions`, con i loghi
- [x] Repo su GitHub (pubblico) e prima build iOS verde, 04/10/2026
- [x] Caricare lo zip del simulatore su appetize.io e guardare l'app

### Fase 2: il catalogo (gratis)
- [x] Carte di un'espansione (`/v1/expansions/{id}/cards`) e immagini `/images/it/...`
- [x] Prezzi (`/ita/prices/{code}.json`), mostrando sempre il minimo (`low`) come su Android
- [x] Cache locale su file (kotlinx-io): espansioni subito all'avvio, carte 24h, prezzi 12h, offline con quello gia' visto
- [x] Tema, colori, simboli di rarita' e bottom bar presi dall'app Android; Home con MenuGrid

### Fase 3: account e collezione
- [x] App iOS registrata nello stesso progetto Firebase; plist nel segreto `FIREBASE_IOS_PLIST`, mai nel repo
- [x] Login con email e password (REST), provato su Appetize con due account veri il 04/10/2026
- [x] Login con Google (OAuth con PKCE in ASWebAuthenticationSession, poi signInWithIdp): stesso account di Android
- [ ] Sign in with Apple (obbligatorio sull'App Store se c'è Google): serve l'account sviluppatore
- [x] Sessione (refresh token compreso) nel Portachiavi iOS; quella vecchia su file si sposta da sola, e dopo una reinstallazione non si rientra con l'account di prima
- [x] Collezione in sola lettura da Firestore: divisa per espansione, totali, stampe raggruppate come su Android (collectionCardKey), offline
- [x] Aggiungere carte dal dettaglio (stampa, copie, condizione, lingua), cambiare copie e togliere stampe dalla collezione: stessi documenti di Android, provati contro un Firestore finto
- [x] Eliminazione dell'account dall'app (obbligatoria per Apple): nello stesso ordine di Android, da provare con un account usa e getta
- [x] Collezione completa: ricerca, filtri, ordinamenti, viste come su Android
- [x] Wishlist modificabili: crea, modifica, elimina, carte dal dettaglio (cuore); limite di 1 lista senza Premium, Premium letto dal Worker
- [x] Illustratori: elenco, pagina artista, segui (stessi documenti di Android)
- [x] Carte gradate, e il voto (ente, voto) si mette dalla finestra delle copie in Carte
- [ ] Provare su Appetize wishlist, illustratori e gradate
- [x] Collector Lab completo: hub, Album (griglia e raccoglitore) e Chase per set
- [ ] Provare su Appetize album e chase
- [x] Competitive, parte 1: hub, Match log (tornei, partite, statistiche e matchup) sugli stessi documenti di Android; limite di 1 torneo senza Premium
- [x] Competitive, parte 2: Hand Simulator (Prova e Analisi), mani salvate e prova gratuita sul telefono come su Android
- [x] Deck Lab 3a: elenco con i filtri, dettaglio, elimina (con le carte solo-deck rimaste orfane), duplica, esporta la decklist (copia e condividi)
- [x] Deck Lab 3b: editor, crea e modifica (carte della collezione e cercate nel catalogo, in collezione o solo nel deck di prova), con limiti di 4 copie e 60 carte, annulla, copertine
- [x] Deck Lab 3c-1: import da testo (PTCG Live, Limitless, CSV), con le carte possedute subito e le mancanti dal catalogo italiano, in collezione o solo nel deck; i test Android dell'import girano anche qui
- [x] Deck Lab 3c-2: schede Meta Deck e Win Tournament da Limitless (stessa cache, stesso limite di 50 richieste ogni 5 minuti, 10 decklist gratis senza Premium), import nel Deck Lab
- [ ] Provare su Appetize Competitive: Match log, Hand Simulator, Deck Lab (mazzi, editor, import, Meta Deck)

### Fase 4: TestFlight (qui si pagano i 99 $/anno)
- [ ] Iscrizione all'Apple Developer Program, poi la richiesta per lo Small Business Program (commissione al 15%)
- [ ] Registrare il Bundle ID `com.emabuia.pokevault` e creare l'app in App Store Connect (prenota il nome)
- [ ] Chiave API di App Store Connect + certificato di distribuzione (si fanno da Windows con openssl)
- [ ] Job CI di firma e upload su TestFlight (fastlane)
- [ ] Invitare l'amico con l'iPhone come tester esterno

### Fase 5: funzioni legate alla piattaforma
- [ ] Premium con StoreKit 2 (o RevenueCat) + endpoint di verifica Apple nel Worker
      (l'unica modifica nel repo `pokevault`, da fare a parte e solo quando serve)
- [ ] Notifiche: chiave APNs caricata su Firebase
- [x] Scanner: fotocamera (AVFoundation) + Apple Vision, stessa logica di Android (consenso fra fotogrammi, carta da confermare o rosa di candidati, modalita' continua, annulla)
- [ ] Provare lo Scanner su un iPhone vero (su Appetize la fotocamera non c'e')
- [x] TradeRadar: match, proposte, appuntamenti (Apple Mappe), riepilogo della collezione, voti, classifica, segnala e blocca; posizione con CoreLocation al chilometro, tasto Radar al centro della barra. Senza notifiche push (arrivano con la chiave APNs) e con gli sprite del podio fermi (Coil anima le GIF solo su Android)
- [ ] Provare TradeRadar su un iPhone vero: posizione, mappe, condivisione. Con un account vero si lavora sul server di produzione
- [ ] Alla revisione spiegare bene la moderazione e l'età (18+) di TradeRadar

### Fase 6: revisione Apple
- [ ] Icona senza loghi ufficiali, dicitura "non affiliato a Nintendo / The Pokémon Company"
- [ ] Privacy label in App Store Connect, privacy policy sul sito aggiornata per citare iOS
- [ ] Questionario sull'età, screenshot (dal simulatore in CI), account demo per i revisori
- [ ] Note per la revisione e un nome di riserva se "PokeVault" viene contestato

## Verifica prima di ogni push

```sh
./gradlew :shared:jvmTest :shared:compileCommonMainKotlinMetadata :shared:compileIosMainKotlinMetadata \
  :androidApp:assembleDebug :desktopApp:compileKotlin
```

Le due compilazioni *Metadata* contano: JVM e Android non si accorgono delle
API solo-JVM usate nel codice comune (`Math`, `putIfAbsent`, `String.format`),
e `compileIosMainKotlinMetadata` controlla gia' da Windows anche il codice iOS
contro i simboli veri di UIKit. Solo il link finale resta alla CI su macOS.
