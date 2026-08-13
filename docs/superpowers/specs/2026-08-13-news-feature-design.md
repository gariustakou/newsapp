# Spec — Feature News (NewsApp KMP)

- **Date** : 2026-08-13
- **Projet** : NewsApp (Kotlin Multiplatform — Android, iOS, Desktop/JVM, Web js+wasmJs)
- **Statut** : Design validé (brainstorm) — prêt pour le plan d'implémentation

---

## 1. Contexte & objectif

NewsApp est un projet KMP en **mono-module** (pas de multi-modules) : `sharedLogic` (logique partagée, toutes cibles), `sharedUI` (Compose partagé Desktop+Web), `androidApp` (Compose natif AndroidX), `iosApp` (SwiftUI natif), `desktopApp`, `webApp`.

Objectif : implémenter une **feature News** (fil d'actualités + catégories + détail) en **Clean Architecture par packages** (mirroir de la structure en couches de `ido_app`, sans nouveaux modules Gradle), avec les bonnes pratiques KMP : coroutines/`Flow`, Repository comme source de vérité, DI Koin, ViewModel multiplateforme partagé, gestion d'erreurs typée.

### Stratégie de partage (rappel, déjà en place)
| Plateforme | UI | Logique |
|-----------|-----|---------|
| Android | Compose natif (`androidApp`) | `sharedLogic` |
| Desktop + Web | Compose partagé (`sharedUI`) | `sharedLogic` |
| iOS | SwiftUI natif (`iosApp`, plus tard) | `sharedLogic` (framework `SharedLogic`) |

---

## 2. Périmètre

### v1 (cette spec)
- Fil d'actualités **par catégorie/rubrique** (chips horizontales).
- **Défaut** : « À la une », langue **français**, pays **Cameroun**.
- **Sélecteur de langue**.
- **Détail article** natif + bouton « Lire l'article » (**lien externe**).
- **Pagination infinite scroll** (~20/page, loader en bas).
- **Offline-first léger** : cache Room (Android/iOS/Desktop), réseau seul sur Web.
- **Pull-to-refresh**.

### Hors-scope (v2)
Recherche par mots-clés · Favoris/bookmarks persistés · WebView interne · Combinaison de filtres (catégorie + pays) · Cache Web (OPFS) · Wrapper iOS avancé (SKIE) · Sélecteur de pays complet avec tous les pays.

---

## 3. Décisions verrouillées (récap brainstorm)

| # | Décision |
|---|----------|
| API | **Currents API** (`currentsapi.services`) — CORS OK (Web), pagination `page_number`, multi-langue. Isolée dans `news/data/remote` → **swappable** (GNews/NewsAPI plus tard). |
| Web / DB | **Pas de base locale sur Web** (réseau seul). Room sur Android/iOS/Desktop. |
| Pagination | **Infinite scroll, ~20/page**, loader en bas. |
| Langue/Pays | Défaut **`fr` + Cameroun**. Sélecteur de langue. Priorité pays : Cameroun → Afrique → France → Canada → USA. **Fallback** si contenu mince : CM → Afrique francophone → monde `fr`. |
| Catégories | Jeu restreint : **À la une · Cameroun · Afrique · Business · Tech · Sport · Santé · Divertissement**. Défaut « À la une ». Chips horizontales. **Une seule chip active** en v1. |
| Détail | Détail natif + **lien externe**. WebView en v2. |
| ViewModel | **Un seul ViewModel multiplateforme** (`org.jetbrains.androidx.lifecycle`). iOS l'observe (wrapper plus tard). |
| Clé API | **BuildKonfig** (`local.properties` → `CURRENTS_API_KEY`). |
| Offline | **Offline-first léger** : cache par `(filtre+langue+pays)`, affiche cache puis refresh page 1, pull-to-refresh remplace, scroll ajoute. Web = réseau seul. |

---

## 4. Architecture — packages & placement

```
sharedLogic/src/commonMain/kotlin/com/ggdevhub/newsapp/news/
├── domain/
│   ├── model/         Article, NewsFilter, NewsLanguage, DataError
│   ├── repository/    NewsRepository (interface)
│   └── source/        NewsLocalDataSource (interface)   ← abstraction cache
├── data/
│   ├── remote/        CurrentsApi, dto/*, NewsRemoteMapper, HttpClientFactory
│   ├── local/         (voir §7 — placement source set non-Web)
│   ├── NewsRepositoryImpl
│   └── mapper/        DTO ↔ domain, Entity ↔ domain
├── presentation/
│   ├── list/          NewsListViewModel + NewsListContract (State/Action/Event)
│   └── detail/        ArticleDetailViewModel + ArticleDetailContract (State/Action)
├── di/                networkModule, databaseModule, newsModule (Koin)
└── util/              Result<D,E>, Paginator, openUrl (expect/actual)

sharedUI/src/commonMain/.../news/     écrans Compose Desktop+Web (NewsListScreen, ArticleDetailScreen, chips, cards) + Coil
androidApp/src/main/.../news/         écrans Compose Android natif + Coil (réutilisent le ViewModel partagé)
iosApp/                               SwiftUI (v2) observant le ViewModel partagé
```

**Règle de dépendances** : `presentation → domain ← data`. Le `domain` ne dépend de rien (ni Ktor, ni Room, ni Coil).

---

## 5. Modèle de domaine (commonMain)

```kotlin
data class Article(
    val id: String,
    val title: String,
    val description: String?,
    val url: String,
    val imageUrl: String?,
    val author: String?,
    val sourceName: String?,      // dérivé du domaine de l'URL ou de l'auteur
    val categories: List<String>,
    val publishedAt: Instant?,    // kotlinx-datetime
)

/** Filtre actif = une chip. Mappé vers les paramètres Currents dans la couche data. */
enum class NewsFilter { TOP, CAMEROON, AFRICA, BUSINESS, TECH, SPORT, HEALTH, ENTERTAINMENT }

enum class NewsLanguage(val code: String) { FR("fr"), EN("en") /* extensible */ }

/** Erreurs typées (pas d'exception qui remonte à l'UI). */
sealed interface DataError {
    enum class Remote : DataError { NO_INTERNET, TIMEOUT, RATE_LIMIT, SERVER, SERIALIZATION, UNKNOWN }
    enum class Local  : DataError { DISK_FULL, UNKNOWN }
}

sealed interface Result<out D, out E : DataError> {
    data class Success<D>(val data: D) : Result<D, Nothing>
    data class Error<E : DataError>(val error: E) : Result<Nothing, E>
}
```

### Contrat Repository
```kotlin
interface NewsRepository {
    /** Flux observable de la catégorie courante (source de vérité : cache si dispo, sinon mémoire). */
    fun observeArticles(filter: NewsFilter, language: NewsLanguage, country: String?): Flow<List<Article>>

    /** Recharge la page 1 (remplace le cache de la clé). Utilisé au lancement + pull-to-refresh. */
    suspend fun refresh(filter: NewsFilter, language: NewsLanguage, country: String?): Result<Unit, DataError>

    /** Charge la page suivante (infinite scroll) et l'ajoute au cache. Retourne s'il reste des pages. */
    suspend fun loadNextPage(filter: NewsFilter, language: NewsLanguage, country: String?): Result<Boolean, DataError>

    suspend fun getArticle(id: String): Article?
}
```

---

## 6. Intégration API Currents (`news/data/remote`)

- **Base URL** : `https://api.currentsapi.services/v1/`
- **Endpoints v1** : `latest-news` (fil), `search` (utilisé pour filtres/pagination avancés ; base v2 pour la recherche).
- **Auth** : paramètre/entête `apiKey` (injecté via BuildKonfig).
- **Paramètres** : `language`, `country`, `category`, `page_number` (pagination), `keywords` (v2).

### Mapping chip → paramètres Currents
| Chip | Paramètres |
|------|-----------|
| À la une | `latest-news?language=<lang>&country=<pays>` |
| Cameroun | `country=CM&language=<lang>` |
| Afrique | ⚠️ Currents n'a pas de « région ». **Impl. pragmatique** : liste tournante de pays africains francophones OU `language=fr` sans `country`. À ajuster avec la vraie clé. |
| Business/Tech/Sport/Santé/Divertissement | `category=business|technology|sports|health|entertainment&language=<lang>` |

### Fallback contenu (pays)
`refresh()` applique une cascade si la réponse est vide/insuffisante : **Cameroun → Afrique francophone → monde `fr`**. La cascade vit dans `NewsRepositoryImpl`, pas dans l'UI.

### DTO (kotlinx-serialization)
```kotlin
@Serializable data class CurrentsResponseDto(
    val status: String? = null,
    val news: List<CurrentsArticleDto> = emptyList(),
    val page: Int? = null,
)
@Serializable data class CurrentsArticleDto(
    val id: String,
    val title: String,
    val description: String? = null,
    val url: String,
    val author: String? = null,
    val image: String? = null,          // "None" possible → null-safe
    val language: String? = null,
    val category: List<String> = emptyList(),
    val published: String? = null,      // parse → Instant
)
```

### HttpClientFactory
Client Ktor commun : `ContentNegotiation` (json, `ignoreUnknownKeys=true`), `Logging`, timeouts, injection de `apiKey`. Le **moteur** est fourni par plateforme (déjà câblé : okhttp Android/Desktop, darwin iOS, js Web).

---

## 7. Cache local Room (`news/data/local`) — Android/iOS/Desktop uniquement

Room **ne compile pas pour js/wasmJs**. On isole donc le code Room dans un **source set intermédiaire non-Web**.

### Source sets
```
commonMain          → NewsLocalDataSource (interface)   [toutes cibles]
nonWebMain          → Room: NewsEntity, NewsDao, NewsDatabase, RoomNewsLocalDataSource, getDatabaseBuilder (expect)
  ├─ androidMain    → getDatabaseBuilder(context) actual
  ├─ iosMain        → getDatabaseBuilder() actual (NSHomeDirectory)
  └─ jvmMain        → getDatabaseBuilder() actual (java.io.tmpdir / user home)
webMain (js+wasmJs) → InMemoryNewsLocalDataSource (no-op persistant : cache en mémoire de session)
```
`nonWebMain.dependsOn(commonMain)` ; `androidMain/iosMain/jvmMain.dependsOn(nonWebMain)`. `webMain.dependsOn(commonMain)` ; `jsMain/wasmJsMain.dependsOn(webMain)`.

### Room (dans nonWebMain)
```kotlin
@Entity(tableName = "articles")
data class NewsEntity(
    @PrimaryKey val id: String,
    val cacheKey: String,          // "<filter>|<lang>|<country>" — permet le cache par catégorie
    val page: Int,
    val title: String, val description: String?, val url: String,
    val imageUrl: String?, val author: String?, val sourceName: String?,
    val categories: String,        // CSV
    val publishedAtEpochMs: Long?,
    val insertedAt: Long,
)

@Dao interface NewsDao {
    @Query("SELECT * FROM articles WHERE cacheKey = :key ORDER BY page, insertedAt")
    fun observeByKey(key: String): Flow<List<NewsEntity>>
    @Upsert suspend fun upsertAll(items: List<NewsEntity>)
    @Query("DELETE FROM articles WHERE cacheKey = :key") suspend fun clearKey(key: String)
}

@Database(entities = [NewsEntity::class], version = 1)
@ConstructedBy(NewsDatabaseConstructor::class)
abstract class NewsDatabase : RoomDatabase() { abstract fun newsDao(): NewsDao }

expect object NewsDatabaseConstructor : RoomDatabaseConstructor<NewsDatabase>
expect fun getDatabaseBuilder(/* context sur Android */): RoomDatabase.Builder<NewsDatabase>
```
Driver : `BundledSQLiteDriver()`, `setQueryCoroutineContext(Dispatchers.IO)`.

### NewsLocalDataSource (interface, commonMain)
```kotlin
interface NewsLocalDataSource {
    fun observe(cacheKey: String): Flow<List<Article>>
    suspend fun replacePage1(cacheKey: String, articles: List<Article>)
    suspend fun appendPage(cacheKey: String, page: Int, articles: List<Article>)
    suspend fun getById(id: String): Article?
}
```
Impl : `RoomNewsLocalDataSource` (nonWeb) / `InMemoryNewsLocalDataSource` (web). Liées par Koin par plateforme.

---

## 8. Repository offline-first + pagination (`NewsRepositoryImpl`)

- `cacheKey = "${filter.name}|${language.code}|${country ?: ""}"`.
- `observeArticles()` → `local.observe(cacheKey)` (Flow réactif ; sur Web = flux mémoire).
- `refresh()` (lancement + pull-to-refresh) : appelle Currents page 1 (avec cascade fallback pays) ; en cas de succès → `local.replacePage1(cacheKey, articles)` ; erreur silencieuse si cache non vide, sinon `Result.Error`.
- `loadNextPage()` : maintient un `currentPage` par `cacheKey` (via un `Paginator`/map en mémoire) ; fetch `page_number = currentPage+1` ; `local.appendPage(...)` ; retourne `hasMore` (false si page vide).
- Toutes les I/O sur `Dispatchers.IO` ; exceptions Ktor → `DataError` typée.

---

## 9. Présentation (`news/presentation`) — ViewModel partagé (MVI : State / Action / Event)

On applique le pattern **MVI / UDF** : l'UI n'expose qu'un **`State`** immuable, envoie toutes les intentions via **un seul `onAction(Action)`**, et reçoit les effets one-shot (navigation, ouverture de lien, erreur) via un flux d'**`Event`**. Cohérent avec `ido_app`/Chirp et idéal pour le pont iOS SwiftUI.

```kotlin
// STATE — snapshot immuable de l'écran liste
data class NewsListState(
    val activeFilter: NewsFilter = NewsFilter.TOP,
    val language: NewsLanguage = NewsLanguage.FR,
    val country: String? = "CM",
    val articles: List<Article> = emptyList(),
    val isLoading: Boolean = false,        // refresh page 1 / changement de filtre
    val isPaginating: Boolean = false,     // loader du bas
    val isRefreshing: Boolean = false,     // pull-to-refresh
    val endReached: Boolean = false,
    val error: DataError? = null,
    val availableFilters: List<NewsFilter> = NewsFilter.entries,
    val availableLanguages: List<NewsLanguage> = NewsLanguage.entries,
)

// ACTION — toute intention utilisateur (un seul point d'entrée onAction)
sealed interface NewsListAction {
    data class SelectFilter(val filter: NewsFilter) : NewsListAction
    data class SelectLanguage(val language: NewsLanguage) : NewsListAction
    data object Refresh : NewsListAction            // pull-to-refresh
    data object ScrolledToEnd : NewsListAction      // infinite scroll
    data class OpenArticle(val article: Article) : NewsListAction
    data object Retry : NewsListAction
}

// EVENT — effets one-shot, consommés une seule fois par le Root
sealed interface NewsListEvent {
    data class NavigateToDetail(val articleId: String) : NewsListEvent
    data class ShowError(val error: DataError) : NewsListEvent
}

class NewsListViewModel(private val repo: NewsRepository) : ViewModel() {
    val state: StateFlow<NewsListState>           // combine repo.observeArticles(...) + flags UI (stateIn)
    val events: Flow<NewsListEvent>               // Channel(...).receiveAsFlow()
    fun onAction(action: NewsListAction)          // POINT D'ENTRÉE UNIQUE
}
```
- `state` combine `repo.observeArticles(...)` (Flow) + les flags UI, via `stateIn(viewModelScope, WhileSubscribed, initial)`.
- Les effets one-shot passent par `events` (ex. `OpenArticle` → `NavigateToDetail`), jamais par l'état → pas de re-déclenchement à la recomposition.
- **Écran détail** : `ArticleDetailState(article, notFound)` + `sealed interface ArticleDetailAction { data object OpenLink; data class Load(id) }`. `OpenLink` appelle `openUrl(article.url)`.
- **Ouverture lien externe** : `expect fun openUrl(url: String)` → actual Android (Intent), iOS (UIApplication), Desktop (Desktop.browse), Web (window.open). Placé dans `util/`.
- **Côté UI** : `Screen(state, onAction)` **stateless** (testable/preview) ; un `Root` collecte `state` et **observe `events`** pour naviguer. iOS SwiftUI fait de même (observe `state`, dispatche des `Action`, réagit aux `events`).

---

## 10. Injection de dépendances (Koin)

```kotlin
val networkModule = module { single { HttpClientFactory.create() }; single { CurrentsApi(get()) } }
val databaseModule = module { /* NewsDatabase + RoomNewsLocalDataSource (nonWeb) / InMemory (web) via modules plateforme */ }
val newsModule = module {
    single<NewsRepository> { NewsRepositoryImpl(get(), get()) }
    viewModel { NewsListViewModel(get()) }
    viewModel { ArticleDetailViewModel(get()) }
}
```
- `initKoin()` commun (dans `sharedLogic/di`) fusionne les modules ; point d'entrée appelé par chaque app (Android `Application`, Desktop `main`, Web `main`, iOS `initKoin` Swift).
- Le binding `NewsLocalDataSource` est fourni par un **module Koin plateforme** (expect/actual `val platformDataModule: Module`) : Room côté nonWeb, InMemory côté web.

---

## 11. UI par plateforme

**Composants partagés (sharedUI, Desktop+Web)** : `NewsListScreen` (chips + `LazyColumn` d'`ArticleCard` + loader bas + pull-to-refresh), `ArticleDetailScreen`. Images via **Coil3** (`AsyncImage` + `coil-network-ktor3`).

**Android (androidApp)** : mêmes écrans réécrits en **Compose AndroidX natif** (le ViewModel partagé est réutilisé via `koinViewModel`). Navigation feed→détail : **navigation-compose** (à ajouter côté Android) ou état simple.

**Desktop/Web (sharedUI)** : navigation via un état/`Navigator` Compose partagé (nav lib différée si trop lourde).

**iOS (v2)** : SwiftUI observe `NewsListViewModel.state` (wrapper Flow→Swift).

> Note : la lib de **navigation** est **différée** — on commence par une navigation à état minimal (liste ↔ détail), on branchera une vraie nav-compose au besoin.

---

## 12. États & erreurs (UX)
- **Loading** initial : placeholder/skeleton ou spinner centré.
- **Empty** : message « Aucun article » + bouton Réessayer (après fallback pays épuisé).
- **Error** : bannière + Réessayer ; en offline avec cache → on affiche le cache + petit bandeau « hors-ligne ».
- **Pagination** : loader en bas ; « Fin des résultats » quand `endReached`.
- **Rate limit (429)** : message clair « quota atteint, réessayez plus tard ».

---

## 13. Dépendances à ajouter

### Déjà présentes ✅
Ktor (core/negotiation/json/logging + moteurs okhttp/darwin/js), kotlinx-serialization(+plugin), coroutines, kotlinx-datetime, Koin (core/compose/android), Coil3.

### À ajouter au catalogue `libs.versions.toml`
```toml
[versions]
room = "2.8.4"
sqlite = "2.7.0"
# KSP DOIT correspondre exactement à la version de Kotlin (format "<kotlin>-<ksp>", ex. "2.4.10-2.0.x").
# À récupérer sur https://github.com/google/ksp/releases pour Kotlin 2.4.10 au moment de l'implémentation.
ksp = "2.4.10-<à-fixer-phase-1>"
buildkonfig = "0.22.0"
# lifecycle-viewmodel réutilise la version androidx-lifecycle déjà présente (2.11.0)

[libraries]
androidx-room-runtime  = { module = "androidx.room:room-runtime",  version.ref = "room" }
androidx-room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
androidx-sqlite-bundled = { module = "androidx.sqlite:sqlite-bundled", version.ref = "sqlite" }
jetbrains-lifecycle-viewmodel = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel", version.ref = "androidx-lifecycle" }

[plugins]
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
room = { id = "androidx.room", version.ref = "room" }
buildkonfig = { id = "com.codingfeline.buildkonfig", version.ref = "buildkonfig" }
```

### Câblage `sharedLogic/build.gradle.kts`
- Plugins : `+ ksp`, `+ room`, `+ buildkonfig`.
- `room { schemaDirectory("$projectDir/schemas") }`.
- `commonMain` : `+ room-runtime`, `+ sqlite-bundled`, `+ jetbrains-lifecycle-viewmodel`.
- Bloc KSP par cible non-Web :
  ```kotlin
  dependencies {
      add("kspAndroid", libs.androidx.room.compiler)
      add("kspIosArm64", libs.androidx.room.compiler)
      add("kspIosSimulatorArm64", libs.androidx.room.compiler)
      add("kspJvm", libs.androidx.room.compiler)   // PAS de kspJs / kspWasmJs
  }
  ```
- Source sets `nonWebMain` / `webMain` (cf. §7).
- `gradle.properties` : `kotlin.native.disableCompilerDaemon=true` (recommandé par la doc Room KMP pour éviter un crash natif).

### BuildKonfig
```kotlin
buildkonfig {
    packageName = "com.ggdevhub.newsapp"
    defaultConfigs {
        buildConfigField(STRING, "CURRENTS_API_KEY", localProperty("CURRENTS_API_KEY"))
    }
}
```
`local.properties` (hors git) : `CURRENTS_API_KEY=xxxxx`.

---

## 14. Plan d'implémentation (phases)

1. **Infra deps** : ajouter Room + KSP + BuildKonfig + lifecycle-viewmodel au catalogue et à `sharedLogic` ; source sets nonWeb/web ; `local.properties` + clé. → compile toutes cibles.
2. **Domain** : models, `NewsFilter`, `Result`/`DataError`, interfaces `NewsRepository` / `NewsLocalDataSource`.
3. **Remote** : `HttpClientFactory`, `CurrentsApi`, DTOs, mapper, mapping chip→params + fallback pays.
4. **Local** : Room (entity/dao/db + expect/actual builders) sur nonWeb ; InMemory sur web.
5. **Repository** : `NewsRepositoryImpl` offline-first + pagination.
6. **DI** : modules Koin + `initKoin()` + module plateforme.
7. **Presentation** : `NewsListViewModel` / `ArticleDetailViewModel` + `openUrl` expect/actual.
8. **UI Desktop/Web** (sharedUI) : écrans Compose + Coil + chips + infinite scroll + pull-to-refresh.
9. **UI Android** (androidApp) : écrans Compose natifs réutilisant le ViewModel.
10. **Vérif** : compile + run Android & Desktop ; smoke test Web.
11. *(v2)* iOS SwiftUI, recherche, favoris, WebView.

---

## 15. Risques & limites connues
- **Quota Currents gratuit serré** → dev prudent ; le fallback pays peut consommer plusieurs requêtes (mitigé : s'arrêter à la 1ʳᵉ réponse non vide).
- **Couverture Cameroun/Afrique** possiblement mince → fallback prévu ; « Afrique » sans param région dédié (impl. pragmatique à ajuster).
- **Room + source sets intermédiaires + KSP** = setup un peu avancé ; risque de config Gradle → traité en phase 1 isolée avec vérif de compilation.
- **iOS Flow→Swift** = friction connue, repoussée en v2.
- **Web** : pas de persistance (cache mémoire de session) — assumé.

---

## 16. Questions ouvertes (non bloquantes)
- Mapping exact « Afrique » (liste de pays vs langue seule) — à trancher avec la vraie clé Currents.
- Taille de page réelle imposée par Currents (viser ~20, s'adapter).
- Navigation : état minimal en v1, vraie lib nav plus tard.
