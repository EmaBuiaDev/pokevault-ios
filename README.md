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

## Cosa resta da fare

### Fase 1: la filiera (gratis)
- [x] Scheletro KMP: Android, desktop, iOS
- [x] Prima schermata vera: espansioni italiane da `/v1/expansions`, con i loghi
- [x] Repo su GitHub (pubblico) e prima build iOS verde, 04/10/2026
- [ ] Caricare lo zip del simulatore su appetize.io e guardare l'app

### Fase 2: il catalogo (gratis)
- [x] Carte di un'espansione (`/v1/expansions/{id}/cards`) e immagini `/images/it/...`
- [x] Prezzi (`/ita/prices/{code}.json`), mostrando sempre il minimo (`low`) come su Android
- [x] Cache locale su file (kotlinx-io): espansioni subito all'avvio, carte 24h, prezzi 12h, offline con quello gia' visto
- [x] Tema, colori, simboli di rarita' e bottom bar presi dall'app Android; Home con MenuGrid

### Fase 3: account e collezione
- [x] App iOS registrata nello stesso progetto Firebase; plist nel segreto `FIREBASE_IOS_PLIST`, mai nel repo
- [x] Login con email e password (REST), provato su Appetize con due account veri il 04/10/2026
- [ ] Login con Google **e Sign in with Apple** (obbligatorio sull'App Store se c'è Google)
- [ ] Refresh token nel Portachiavi iOS invece che in un file
- [x] Collezione in sola lettura da Firestore: divisa per espansione, totali, stampe raggruppate come su Android (collectionCardKey), offline
- [ ] Aggiungere, modificare e togliere carte (scritture su Firestore, con test prima di toccare dati veri)
- [ ] Eliminazione dell'account dall'app (obbligatoria per Apple)

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
- [ ] Scanner: fotocamera + Apple Vision
- [ ] TradeRadar, per ultimo: la moderazione e l'età (18+) vanno spiegate bene alla revisione

### Fase 6: revisione Apple
- [ ] Icona senza loghi ufficiali, dicitura "non affiliato a Nintendo / The Pokémon Company"
- [ ] Privacy label in App Store Connect, privacy policy sul sito aggiornata per citare iOS
- [ ] Questionario sull'età, screenshot (dal simulatore in CI), account demo per i revisori
- [ ] Note per la revisione e un nome di riserva se "PokeVault" viene contestato
