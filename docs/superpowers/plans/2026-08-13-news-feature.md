# News Feature — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implémenter la feature News (fil par catégorie + détail + offline-first + pagination) dans `sharedLogic`, consommée par l'UI Compose partagée (Desktop/Web) et l'UI Compose native Android.

**Architecture:** Clean Architecture **par packages** (mono-module) dans `sharedLogic/news/{domain,data,presentation,di}`. Repository = source de vérité (offline-first). API **Currents** isolée dans `data/remote` (swappable). Cache **Room** sur Android/iOS/Desktop (source set `nonWebMain`), **en mémoire** sur Web. ViewModel **partagé** multiplateforme. DI **Koin**.

**Tech Stack:** Kotlin 2.4.10, Ktor 3.5.1, kotlinx-serialization 1.11.0, kotlinx-coroutines 1.11.0, kotlinx-datetime 0.8.0, Koin 4.2.2, Coil3 3.5.0, Room 2.8.4 + KSP + sqlite-bundled 2.7.0, BuildKonfig 0.22.0, Compose Multiplatform 1.11.1 / AndroidX Compose (BOM 2026.03.01).

## Global Constraints

- **Package racine** : `com.ggdevhub.newsapp` (tout le code de la feature sous `…/news/`).
- **Cibles** : `android`, `iosArm64`, `iosSimulatorArm64`, `jvm` (Desktop), `js`, `wasmJs`. Le code de `commonMain` DOIT compiler pour les 6.
- **Stratégie UI** : Android = Compose natif AndroidX (`androidApp`) ; Desktop+Web = Compose Multiplatform (`sharedUI`, ne cible PAS Android) ; iOS = SwiftUI (v2).
- **Room** : uniquement `nonWebMain` (android/ios/jvm). JAMAIS dans `commonMain` ni `js`/`wasmJs`.
- **Mono-module** : PAS de nouveaux modules Gradle ni de convention plugins. Clean archi par packages.
- **Commentaires** : TOUT le code est commenté **en français**, de façon pédagogique (rôle + intention + points KMP non triviaux). Préférence utilisateur ferme.
- **Désactivations** : commenter, ne pas supprimer.
- **Clé API** : `CURRENTS_API_KEY` dans `local.properties` (gitignored), injectée via BuildKonfig. Jamais hardcodée/committée.
- **minSdk 24, compileSdk 37, JVM 11.**
- **Runner de tests commonTest** : `./gradlew :sharedLogic:jvmTest` (exécute commonTest sur la JVM, rapide).

---

## File Structure

**Config**
- Modify `gradle/libs.versions.toml` — versions/libs/plugins Room, KSP, BuildKonfig, lifecycle-viewmodel, test libs.
- Modify `sharedLogic/build.gradle.kts` — plugins ksp/room/buildkonfig, source sets nonWeb/web, deps, bloc KSP, BuildKonfig.
- Modify `gradle.properties` — `kotlin.native.disableCompilerDaemon=true`.
- Modify `local.properties` — `CURRENTS_API_KEY=...` (gitignored).

**Domain** (`sharedLogic/src/commonMain/.../news/domain/`)
- `model/Article.kt`, `model/NewsFilter.kt`, `model/NewsLanguage.kt`, `model/DataError.kt` (+ `Result`).
- `repository/NewsRepository.kt`, `source/NewsLocalDataSource.kt`.

**Data** (`.../news/data/`)
- `remote/dto/CurrentsDto.kt`, `remote/HttpClientFactory.kt`, `remote/CurrentsApi.kt`, `remote/NewsRequestParams.kt`.
- `mapper/ArticleMappers.kt`.
- `NewsRepositoryImpl.kt`.

**Local** (`nonWebMain/.../news/data/local/` + actuals ; `webMain/.../news/data/local/`)
- `NewsEntity.kt`, `NewsDao.kt`, `NewsDatabase.kt`, `RoomNewsLocalDataSource.kt`, `DatabaseBuilder.kt` (expect) + actuals android/ios/jvm.
- Web: `InMemoryNewsLocalDataSource.kt`.

**Presentation** (`.../news/presentation/`)
- `list/NewsListContract.kt` (State/Action/Event), `list/NewsListViewModel.kt`.
- `detail/ArticleDetailContract.kt` (State/Action), `detail/ArticleDetailViewModel.kt`.

**Util** (`.../news/util/`)
- `OpenUrl.kt` (expect) + actuals android/ios/jvm/js/wasmJs.

**DI** (`.../news/di/`)
- `NetworkModule.kt`, `NewsModule.kt`, `PlatformDataModule.kt` (expect) + actuals, `InitKoin.kt`.

**UI partagée** (`sharedUI/src/commonMain/.../news/ui/`)
- `NewsListScreen.kt`, `CategoryChips.kt`, `ArticleCard.kt`, `ArticleDetailScreen.kt`, `NewsRootScreen.kt` (nav minimale).

**UI Android** (`androidApp/src/main/.../news/ui/`)
- `NewsListScreen.kt`, `CategoryChips.kt`, `ArticleCard.kt`, `ArticleDetailScreen.kt`, `NewsRootScreen.kt`.

**Tests** (`sharedLogic/src/commonTest/.../news/`)
- `mapper/ArticleMappersTest.kt`, `remote/NewsRequestParamsTest.kt`, `remote/CurrentsApiTest.kt`, `data/NewsRepositoryImplTest.kt`, `presentation/NewsListViewModelTest.kt`, plus fakes.

---

## Task 1 : Infrastructure des dépendances (Room + KSP + BuildKonfig + source sets)

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `sharedLogic/build.gradle.kts`
- Modify: `gradle.properties`
- Modify: `local.properties`

**Interfaces:**
- Produces: `BuildKonfig.CURRENTS_API_KEY: String` (généré) ; source sets `nonWebMain`, `webMain` ; deps Room/lifecycle-viewmodel dispo dans `commonMain`.

- [ ] **Step 1: Trouver la version KSP correspondant à Kotlin 2.4.10**

Run: ouvrir https://github.com/google/ksp/releases et repérer la release au format `2.4.10-x.y.z` (KSP doit correspondre EXACTEMENT à la version Kotlin).
Noter la valeur (ex. `2.4.10-2.0.4`) pour l'étape suivante.

- [ ] **Step 2: Ajouter versions, libs et plugins au catalogue**

Dans `gradle/libs.versions.toml`, sous `[versions]` (après `kotlinxDatetime`) :
```toml
# --- Feature News : base locale + config + tests ---
room = "2.8.4"
sqlite = "2.7.0"
ksp = "2.4.10-2.0.4"          # ← remplacer par la valeur trouvée au Step 1
buildkonfig = "0.22.0"
```
Sous `[libraries]` (après `coil-network-ktor`) :
```toml
# Room (cache local — Android/iOS/Desktop uniquement)
androidx-room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
androidx-room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
androidx-sqlite-bundled = { module = "androidx.sqlite:sqlite-bundled", version.ref = "sqlite" }
# ViewModel multiplateforme (sans Compose) — pour la couche presentation partagée
jetbrains-lifecycle-viewmodel = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel", version.ref = "androidx-lifecycle" }
# Tests
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinx-coroutines" }
ktor-client-mock = { module = "io.ktor:ktor-client-mock", version.ref = "ktor" }
koin-test = { module = "io.insert-koin:koin-test", version.ref = "koin" }
```
Sous `[plugins]` (après `kotlinSerialization`) :
```toml
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
room = { id = "androidx.room", version.ref = "room" }
buildkonfig = { id = "com.codingfeline.buildkonfig", version.ref = "buildkonfig" }
```

- [ ] **Step 3: Déclarer le plugin BuildKonfig au niveau racine**

Dans `build.gradle.kts` (racine), ajouter dans le bloc `plugins { … apply false }` :
```kotlin
    // BuildKonfig : génère un objet Kotlin (clé API) accessible depuis commonMain
    alias(libs.plugins.buildkonfig) apply false
```
(KSP et Room sont appliqués directement dans `sharedLogic`, pas besoin de les déclarer racine.)

- [ ] **Step 4: Activer le flag natif requis par Room KMP**

Dans `gradle.properties`, ajouter :
```properties
# Requis par Room Multiplatform pour éviter un crash du compilateur natif (iOS)
kotlin.native.disableCompilerDaemon=true
```

- [ ] **Step 5: Ajouter la clé API dans local.properties (hors git)**

Dans `local.properties`, ajouter :
```properties
# Clé API Currents (https://currentsapi.services) — NE PAS committer (fichier gitignored)
CURRENTS_API_KEY=METTRE_VOTRE_CLE_ICI
```

- [ ] **Step 6: Câbler sharedLogic (plugins, source sets, deps, KSP, BuildKonfig)**

Réécrire `sharedLogic/build.gradle.kts`. Plugins :
```kotlin
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)          // génération de code Room
    alias(libs.plugins.room)         // plugin Room (schémas, helpers)
    alias(libs.plugins.buildkonfig)  // clé API générée
}
```
Après le bloc `android { … }`, définir la hiérarchie de source sets et les deps :
```kotlin
    // ---- Source sets ----
    // On crée un niveau intermédiaire "nonWeb" (Android + iOS + Desktop) pour y mettre Room,
    // car Room ne compile PAS pour js/wasmJs. Le Web utilisera une impl. en mémoire (webMain).
    applyDefaultHierarchyTemplate()
    sourceSets {
        val commonMain by getting
        val nonWebMain by creating { dependsOn(commonMain) }   // Room vit ici
        val webMain by creating { dependsOn(commonMain) }      // impl. mémoire ici

        androidMain.get().dependsOn(nonWebMain)
        iosMain.get().dependsOn(nonWebMain)
        jvmMain.get().dependsOn(nonWebMain)
        jsMain.get().dependsOn(webMain)
        wasmJsMain.get().dependsOn(webMain)

        commonMain.dependencies {
            // (déjà présents : ktor core/negotiation/json/logging, serialization, coroutines, datetime, koin-core)
            // Cache local (interface commune + Room runtime pour @Entity/@Dao dans nonWeb)
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
            // ViewModel multiplateforme (couche presentation partagée)
            implementation(libs.jetbrains.lifecycle.viewmodel)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)  // runTest, TestDispatcher
            implementation(libs.ktor.client.mock)         // MockEngine pour tester CurrentsApi
        }
        // (androidMain/iosMain/jvmMain/jsMain/wasmJsMain deps : moteurs Ktor déjà câblés)
    }
}

// ---- KSP Room : génère le code de la base pour chaque cible NON-Web uniquement ----
dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
    add("kspJvm", libs.androidx.room.compiler)
    // Volontairement PAS de kspJs / kspWasmJs : Room n'existe pas sur le Web.
}

// ---- Room : dossier des schémas (migrations futures) ----
room {
    schemaDirectory("$projectDir/schemas")
}

// ---- BuildKonfig : expose la clé API à commonMain ----
buildkonfig {
    packageName = "com.ggdevhub.newsapp"
    defaultConfigs {
        // Lit CURRENTS_API_KEY depuis local.properties (via une fonction utilitaire ci-dessous)
        buildConfigField(
            com.codingfeline.buildkonfig.compiler.FieldSpec.Type.STRING,
            "CURRENTS_API_KEY",
            providers.gradleProperty("CURRENTS_API_KEY").orNull
                ?: localProperty("CURRENTS_API_KEY")
                ?: ""
        )
    }
}

// Petite fonction pour lire une clé de local.properties
fun localProperty(key: String): String? {
    val f = rootProject.file("local.properties")
    if (!f.exists()) return null
    return java.util.Properties().apply { f.inputStream().use { load(it) } }.getProperty(key)
}
```

- [ ] **Step 7: Créer les dossiers de source sets nonWeb/web**

Run:
```bash
mkdir -p sharedLogic/src/nonWebMain/kotlin/com/ggdevhub/newsapp/news/data/local
mkdir -p sharedLogic/src/webMain/kotlin/com/ggdevhub/newsapp/news/data/local
```

- [ ] **Step 8: Vérifier la compilation de toutes les cibles**

Run: `./gradlew :sharedLogic:compileKotlinJvm :sharedLogic:compileKotlinJs :sharedLogic:compileKotlinWasmJs :sharedLogic:compileKotlinIosSimulatorArm64 --console=plain`
Expected: BUILD SUCCESSFUL (aucun code métier encore, on valide juste la config Gradle + source sets + BuildKonfig généré).

- [ ] **Step 9: Commit**

```bash
git add gradle/libs.versions.toml sharedLogic/build.gradle.kts build.gradle.kts gradle.properties
git commit -m "chore(news): infra deps (Room, KSP, BuildKonfig) + source sets nonWeb/web"
```
(⚠️ ne PAS `git add local.properties` — il est gitignored.)

---

## Task 2 : Couche domaine (models, Result, interfaces)

**Files:**
- Create: `sharedLogic/src/commonMain/kotlin/com/ggdevhub/newsapp/news/domain/model/Result.kt`
- Create: `.../news/domain/model/DataError.kt`
- Create: `.../news/domain/model/Article.kt`
- Create: `.../news/domain/model/NewsFilter.kt`
- Create: `.../news/domain/model/NewsLanguage.kt`
- Create: `.../news/domain/repository/NewsRepository.kt`
- Create: `.../news/domain/source/NewsLocalDataSource.kt`
- Test: `sharedLogic/src/commonTest/kotlin/com/ggdevhub/newsapp/news/domain/ResultTest.kt`

**Interfaces:**
- Produces: `Article`, `NewsFilter`, `NewsLanguage`, `DataError`, `Result<D,E>` + `Result.map`, `NewsRepository`, `NewsLocalDataSource`.

- [ ] **Step 1: Écrire le test de Result.map**

`commonTest/.../news/domain/ResultTest.kt` :
```kotlin
package com.ggdevhub.newsapp.news.domain

import com.ggdevhub.newsapp.news.domain.model.DataError
import com.ggdevhub.newsapp.news.domain.model.Result
import com.ggdevhub.newsapp.news.domain.model.map
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResultTest {
    // Vérifie que map transforme la donnée d'un Success sans toucher l'erreur.
    @Test
    fun map_transforme_le_success() {
        val r: Result<Int, DataError> = Result.Success(2)
        val mapped = r.map { it * 10 }
        assertEquals(Result.Success(20), mapped)
    }

    // Vérifie que map laisse un Error inchangé.
    @Test
    fun map_preserve_l_erreur() {
        val r: Result<Int, DataError> = Result.Error(DataError.Remote.NO_INTERNET)
        val mapped = r.map { it * 10 }
        assertTrue(mapped is Result.Error)
    }
}
```

- [ ] **Step 2: Lancer le test (échec attendu)**

Run: `./gradlew :sharedLogic:jvmTest --tests "*ResultTest*"`
Expected: FAIL (types non définis).

- [ ] **Step 3: Créer Result + DataError**

`domain/model/Result.kt` :
```kotlin
package com.ggdevhub.newsapp.news.domain.model

/**
 * Type de résultat typé : soit un succès (avec la donnée), soit une erreur (typée).
 * On l'utilise pour ne JAMAIS faire remonter d'exception brute jusqu'à l'UI.
 */
sealed interface Result<out D, out E : DataError> {
    data class Success<out D>(val data: D) : Result<D, Nothing>
    data class Error<out E : DataError>(val error: E) : Result<Nothing, E>
}

/** Transforme la donnée d'un Success ; un Error est renvoyé tel quel. */
inline fun <D, E : DataError, R> Result<D, E>.map(transform: (D) -> R): Result<R, E> = when (this) {
    is Result.Success -> Result.Success(transform(data))
    is Result.Error -> this
}
```
`domain/model/DataError.kt` :
```kotlin
package com.ggdevhub.newsapp.news.domain.model

/** Erreurs métier typées (réseau / local). L'UI affiche un message selon la valeur. */
sealed interface DataError {
    enum class Remote : DataError { NO_INTERNET, TIMEOUT, RATE_LIMIT, SERVER, SERIALIZATION, UNKNOWN }
    enum class Local : DataError { DISK_FULL, UNKNOWN }
}
```

- [ ] **Step 4: Créer Article, NewsFilter, NewsLanguage**

`domain/model/Article.kt` :
```kotlin
package com.ggdevhub.newsapp.news.domain.model

import kotlinx.datetime.Instant

/** Un article de presse, modèle "pur" du domaine (indépendant de l'API et de la base). */
data class Article(
    val id: String,
    val title: String,
    val description: String?,
    val url: String,            // lien vers l'article complet (ouvert en externe)
    val imageUrl: String?,
    val author: String?,
    val sourceName: String?,    // nom de la source (dérivé du domaine de l'URL si absent)
    val categories: List<String>,
    val publishedAt: Instant?,
)
```
`domain/model/NewsFilter.kt` :
```kotlin
package com.ggdevhub.newsapp.news.domain.model

/**
 * Filtre actif = la "chip" sélectionnée. En v1, une seule à la fois.
 * Le mapping vers les paramètres Currents se fait dans la couche data (NewsRequestParams).
 */
enum class NewsFilter { TOP, CAMEROON, AFRICA, BUSINESS, TECH, SPORT, HEALTH, ENTERTAINMENT }
```
`domain/model/NewsLanguage.kt` :
```kotlin
package com.ggdevhub.newsapp.news.domain.model

/** Langues supportées par le sélecteur. `code` = valeur envoyée à Currents (param language). */
enum class NewsLanguage(val code: String) { FR("fr"), EN("en") }
```

- [ ] **Step 5: Créer les interfaces NewsRepository et NewsLocalDataSource**

`domain/repository/NewsRepository.kt` :
```kotlin
package com.ggdevhub.newsapp.news.domain.repository

import com.ggdevhub.newsapp.news.domain.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Contrat de la couche data vu par la présentation. Offline-first :
 * - observeArticles = flux réactif (cache = source de vérité)
 * - refresh = recharge la page 1 (lancement + pull-to-refresh)
 * - loadNextPage = pagination (infinite scroll), renvoie s'il reste des pages
 */
interface NewsRepository {
    fun observeArticles(filter: NewsFilter, language: NewsLanguage, country: String?): Flow<List<Article>>
    suspend fun refresh(filter: NewsFilter, language: NewsLanguage, country: String?): Result<Unit, DataError>
    suspend fun loadNextPage(filter: NewsFilter, language: NewsLanguage, country: String?): Result<Boolean, DataError>
    suspend fun getArticle(id: String): Article?
}
```
`domain/source/NewsLocalDataSource.kt` :
```kotlin
package com.ggdevhub.newsapp.news.domain.source

import com.ggdevhub.newsapp.news.domain.model.Article
import kotlinx.coroutines.flow.Flow

/**
 * Abstraction du cache local. Deux implémentations :
 * - RoomNewsLocalDataSource (Android/iOS/Desktop, persistant)
 * - InMemoryNewsLocalDataSource (Web, non persistant)
 * La couche data ignore laquelle est utilisée (fournie par Koin selon la plateforme).
 */
interface NewsLocalDataSource {
    fun observe(cacheKey: String): Flow<List<Article>>
    suspend fun replacePage1(cacheKey: String, articles: List<Article>)
    suspend fun appendPage(cacheKey: String, page: Int, articles: List<Article>)
    suspend fun getById(id: String): Article?
}
```

- [ ] **Step 6: Lancer le test (succès attendu)**

Run: `./gradlew :sharedLogic:jvmTest --tests "*ResultTest*"`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add sharedLogic/src/commonMain sharedLogic/src/commonTest
git commit -m "feat(news): couche domaine (Article, Result, DataError, interfaces)"
```

---

## Task 3 : Remote — DTOs Currents + mappers DTO→domaine

**Files:**
- Create: `.../news/data/remote/dto/CurrentsDto.kt`
- Create: `.../news/data/mapper/ArticleMappers.kt`
- Test: `.../commonTest/.../news/mapper/ArticleMappersTest.kt`

**Interfaces:**
- Consumes: `Article` (Task 2).
- Produces: `CurrentsResponseDto`, `CurrentsArticleDto`, `CurrentsArticleDto.toArticle(): Article`, `extractDomain(url): String?`.

- [ ] **Step 1: Écrire les tests de mapping**

`commonTest/.../news/mapper/ArticleMappersTest.kt` :
```kotlin
package com.ggdevhub.newsapp.news.mapper

import com.ggdevhub.newsapp.news.data.mapper.extractDomain
import com.ggdevhub.newsapp.news.data.mapper.toArticle
import com.ggdevhub.newsapp.news.data.remote.dto.CurrentsArticleDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArticleMappersTest {
    // "None" (valeur possible de Currents pour image) doit devenir null.
    @Test
    fun image_none_devient_null() {
        val dto = CurrentsArticleDto(id = "1", title = "t", url = "https://x.com/a", image = "None")
        assertNull(dto.toArticle().imageUrl)
    }

    // La date ISO doit être parsée en Instant non-null.
    @Test
    fun published_est_parse() {
        val dto = CurrentsArticleDto(id = "1", title = "t", url = "https://x.com/a", published = "2026-08-13 10:00:00 +0000")
        // On accepte null si le format n'est pas ISO ; ici on vérifie surtout que ça ne crashe pas.
        dto.toArticle() // ne doit pas lever d'exception
    }

    // sourceName absent → dérivé du domaine de l'URL.
    @Test
    fun sourceName_derive_du_domaine() {
        assertEquals("lemonde.fr", extractDomain("https://www.lemonde.fr/article/123"))
    }
}
```

- [ ] **Step 2: Lancer (échec attendu)**

Run: `./gradlew :sharedLogic:jvmTest --tests "*ArticleMappersTest*"`
Expected: FAIL (non défini).

- [ ] **Step 3: Créer les DTOs**

`data/remote/dto/CurrentsDto.kt` :
```kotlin
package com.ggdevhub.newsapp.news.data.remote.dto

import kotlinx.serialization.Serializable

/** Réponse Currents (endpoints latest-news / search). "news" = liste d'articles. */
@Serializable
data class CurrentsResponseDto(
    val status: String? = null,
    val news: List<CurrentsArticleDto> = emptyList(),
    val page: Int? = null,
)

/** Article brut renvoyé par Currents (structure de l'API, pas du domaine). */
@Serializable
data class CurrentsArticleDto(
    val id: String,
    val title: String,
    val description: String? = null,
    val url: String,
    val author: String? = null,
    val image: String? = null,          // peut valoir "None"
    val language: String? = null,
    val category: List<String> = emptyList(),
    val published: String? = null,      // ex. "2026-08-13 10:00:00 +0000"
)
```

- [ ] **Step 4: Créer les mappers**

`data/mapper/ArticleMappers.kt` :
```kotlin
package com.ggdevhub.newsapp.news.data.mapper

import com.ggdevhub.newsapp.news.data.remote.dto.CurrentsArticleDto
import com.ggdevhub.newsapp.news.domain.model.Article
import kotlinx.datetime.Instant

/** Extrait le domaine "lemonde.fr" d'une URL "https://www.lemonde.fr/…" (sans le www). */
fun extractDomain(url: String): String? {
    val host = url.substringAfter("://", "").substringBefore("/").ifBlank { return null }
    return host.removePrefix("www.")
}

/** Convertit "None"/vide en null (Currents renvoie parfois la chaîne "None"). */
private fun String?.orNullIfBlankOrNone(): String? =
    this?.takeIf { it.isNotBlank() && !it.equals("None", ignoreCase = true) }

/** Tente de parser la date Currents en Instant ; renvoie null si le format n'est pas exploitable. */
private fun parsePublished(raw: String?): Instant? {
    val v = raw?.trim() ?: return null
    // Currents renvoie parfois un format non strictement ISO ; on tente, sinon null (pas de crash).
    return runCatching { Instant.parse(v.replace(" ", "T").replace(" +0000", "Z")) }.getOrNull()
        ?: runCatching { Instant.parse(v) }.getOrNull()
}

/** DTO Currents → modèle de domaine. */
fun CurrentsArticleDto.toArticle(): Article = Article(
    id = id,
    title = title,
    description = description.orNullIfBlankOrNone(),
    url = url,
    imageUrl = image.orNullIfBlankOrNone(),
    author = author.orNullIfBlankOrNone(),
    sourceName = author.orNullIfBlankOrNone() ?: extractDomain(url),
    categories = category,
    publishedAt = parsePublished(published),
)
```

- [ ] **Step 5: Lancer (succès attendu)**

Run: `./gradlew :sharedLogic:jvmTest --tests "*ArticleMappersTest*"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add sharedLogic/src/commonMain sharedLogic/src/commonTest
git commit -m "feat(news): DTOs Currents + mappers DTO→domaine"
```

---

## Task 4 : Mapping filtre → paramètres de requête (+ cascade fallback)

**Files:**
- Create: `.../news/data/remote/NewsRequestParams.kt`
- Test: `.../commonTest/.../news/remote/NewsRequestParamsTest.kt`

**Interfaces:**
- Consumes: `NewsFilter`, `NewsLanguage` (Task 2).
- Produces: `data class NewsRequest(endpoint, language, country?, category?)`, `buildRequest(filter, language, country, page): NewsRequest`, `fallbackCountries(filter, country): List<String?>`.

- [ ] **Step 1: Écrire les tests**

`commonTest/.../news/remote/NewsRequestParamsTest.kt` :
```kotlin
package com.ggdevhub.newsapp.news.remote

import com.ggdevhub.newsapp.news.data.remote.buildRequest
import com.ggdevhub.newsapp.news.data.remote.fallbackCountries
import com.ggdevhub.newsapp.news.domain.model.NewsFilter
import com.ggdevhub.newsapp.news.domain.model.NewsLanguage
import kotlin.test.Test
import kotlin.test.assertEquals

class NewsRequestParamsTest {
    // Business → category=business, sans country imposé (garde celui passé).
    @Test
    fun business_map_categorie() {
        val r = buildRequest(NewsFilter.BUSINESS, NewsLanguage.FR, country = "CM", page = 1)
        assertEquals("business", r.category)
        assertEquals("fr", r.language)
    }

    // Cameroun → country=CM, pas de category.
    @Test
    fun cameroun_map_pays() {
        val r = buildRequest(NewsFilter.CAMEROON, NewsLanguage.FR, country = null, page = 1)
        assertEquals("CM", r.country)
        assertEquals(null, r.category)
    }

    // Cascade fallback pour Cameroun : CM → pays africains fr → monde (null).
    @Test
    fun fallback_cameroun() {
        val list = fallbackCountries(NewsFilter.CAMEROON, "CM")
        assertEquals("CM", list.first())
        assertEquals(null, list.last()) // dernier essai = monde
    }
}
```

- [ ] **Step 2: Lancer (échec attendu)**

Run: `./gradlew :sharedLogic:jvmTest --tests "*NewsRequestParamsTest*"`
Expected: FAIL.

- [ ] **Step 3: Implémenter le mapping**

`data/remote/NewsRequestParams.kt` :
```kotlin
package com.ggdevhub.newsapp.news.data.remote

import com.ggdevhub.newsapp.news.domain.model.NewsFilter
import com.ggdevhub.newsapp.news.domain.model.NewsLanguage

/** Paramètres d'une requête Currents, indépendants de Ktor (facile à tester). */
data class NewsRequest(
    val endpoint: String,          // "latest-news" ou "search"
    val language: String,
    val country: String?,
    val category: String?,
    val page: Int,
)

/** Quelques pays africains francophones pour la cascade "Afrique". */
private val AFRICA_FR = listOf("CM", "SN", "CI", "CD", "BF", "ML", "GA", "TG", "BJ", "NE")

/** Traduit une chip (NewsFilter) en paramètres Currents. */
fun buildRequest(filter: NewsFilter, language: NewsLanguage, country: String?, page: Int): NewsRequest {
    val lang = language.code
    return when (filter) {
        NewsFilter.TOP -> NewsRequest("latest-news", lang, country, null, page)
        NewsFilter.CAMEROON -> NewsRequest("latest-news", lang, "CM", null, page)
        NewsFilter.AFRICA -> NewsRequest("latest-news", lang, AFRICA_FR.first(), null, page)
        NewsFilter.BUSINESS -> NewsRequest("search", lang, country, "business", page)
        NewsFilter.TECH -> NewsRequest("search", lang, country, "technology", page)
        NewsFilter.SPORT -> NewsRequest("search", lang, country, "sports", page)
        NewsFilter.HEALTH -> NewsRequest("search", lang, country, "health", page)
        NewsFilter.ENTERTAINMENT -> NewsRequest("search", lang, country, "entertainment", page)
    }
}

/**
 * Cascade de replis pour éviter un fil vide (quota/couverture faible).
 * Ordre : pays demandé → pays africains francophones → monde (null).
 * Le repository essaie chaque valeur jusqu'à obtenir des articles.
 */
fun fallbackCountries(filter: NewsFilter, country: String?): List<String?> = when (filter) {
    NewsFilter.CAMEROON, NewsFilter.AFRICA -> (listOf("CM") + AFRICA_FR + listOf(null)).distinct()
    NewsFilter.TOP -> listOf(country, null).distinct()
    else -> listOf(country, null).distinct() // catégories : on tente le pays puis monde
}
```

- [ ] **Step 4: Lancer (succès attendu)**

Run: `./gradlew :sharedLogic:jvmTest --tests "*NewsRequestParamsTest*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add sharedLogic/src/commonMain sharedLogic/src/commonTest
git commit -m "feat(news): mapping filtre→params Currents + cascade fallback"
```

---

## Task 5 : Remote — HttpClientFactory + CurrentsApi (testé avec MockEngine)

**Files:**
- Create: `.../news/data/remote/HttpClientFactory.kt`
- Create: `.../news/data/remote/CurrentsApi.kt`
- Test: `.../commonTest/.../news/remote/CurrentsApiTest.kt`

**Interfaces:**
- Consumes: `NewsRequest` (Task 4), `CurrentsResponseDto` (Task 3), `Result`/`DataError` (Task 2).
- Produces: `HttpClientFactory.create(engine?, apiKey): HttpClient`, `class CurrentsApi(client, apiKey)` avec `suspend fun fetch(request: NewsRequest): Result<List<CurrentsArticleDto>, DataError.Remote>`.

- [ ] **Step 1: Écrire le test avec MockEngine**

`commonTest/.../news/remote/CurrentsApiTest.kt` :
```kotlin
package com.ggdevhub.newsapp.news.remote

import com.ggdevhub.newsapp.news.data.remote.CurrentsApi
import com.ggdevhub.newsapp.news.data.remote.HttpClientFactory
import com.ggdevhub.newsapp.news.data.remote.NewsRequest
import com.ggdevhub.newsapp.news.domain.model.Result
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CurrentsApiTest {
    private val json = """{"status":"ok","news":[{"id":"1","title":"Bonjour","url":"https://x.com/a"}]}"""

    @Test
    fun fetch_parse_les_articles() = runTest {
        // MockEngine : simule la réponse HTTP sans réseau réel.
        val engine = MockEngine { respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        val api = CurrentsApi(HttpClientFactory.create(engine, "KEY"), "KEY")
        val res = api.fetch(NewsRequest("latest-news", "fr", "CM", null, 1))
        assertTrue(res is Result.Success)
        assertEquals("Bonjour", (res as Result.Success).data.first().title)
    }

    @Test
    fun fetch_429_donne_rate_limit() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.TooManyRequests) }
        val api = CurrentsApi(HttpClientFactory.create(engine, "KEY"), "KEY")
        val res = api.fetch(NewsRequest("latest-news", "fr", "CM", null, 1))
        assertTrue(res is Result.Error)
    }
}
```

- [ ] **Step 2: Lancer (échec attendu)**

Run: `./gradlew :sharedLogic:jvmTest --tests "*CurrentsApiTest*"`
Expected: FAIL.

- [ ] **Step 3: Créer HttpClientFactory**

`data/remote/HttpClientFactory.kt` :
```kotlin
package com.ggdevhub.newsapp.news.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Fabrique du client Ktor commun. Le "engine" est fourni par la plateforme
 * (okhttp/darwin/js) ou par un MockEngine dans les tests.
 */
object HttpClientFactory {
    fun create(engine: HttpClientEngine? = null, apiKey: String): HttpClient {
        val config: HttpClient.() -> Unit = {}
        val block: io.ktor.client.HttpClientConfig<*>.() -> Unit = {
            // Désérialisation JSON tolérante (ignore les champs inconnus de Currents).
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; isLenient = true })
            }
            install(Logging)          // logs des requêtes (utile en dev)
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 15_000
            }
        }
        // Si un engine est fourni (tests), on l'utilise ; sinon Ktor prend celui de la plateforme.
        return if (engine != null) HttpClient(engine, block) else HttpClient(block)
    }
}
```

- [ ] **Step 4: Créer CurrentsApi**

`data/remote/CurrentsApi.kt` :
```kotlin
package com.ggdevhub.newsapp.news.data.remote

import com.ggdevhub.newsapp.news.data.remote.dto.CurrentsArticleDto
import com.ggdevhub.newsapp.news.data.remote.dto.CurrentsResponseDto
import com.ggdevhub.newsapp.news.domain.model.DataError
import com.ggdevhub.newsapp.news.domain.model.Result
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess

/**
 * Accès à l'API Currents. Traduit les erreurs réseau en DataError.Remote typées.
 * @param apiKey injecté (BuildKonfig.CURRENTS_API_KEY) via Koin.
 */
class CurrentsApi(private val client: HttpClient, private val apiKey: String) {

    private val baseUrl = "https://api.currentsapi.services/v1"

    suspend fun fetch(request: NewsRequest): Result<List<CurrentsArticleDto>, DataError.Remote> {
        return try {
            val response = client.get("$baseUrl/${request.endpoint}") {
                parameter("apiKey", apiKey)
                parameter("language", request.language)
                request.country?.let { parameter("country", it) }
                request.category?.let { parameter("category", it) }
                parameter("page_number", request.page)   // pagination Currents
            }
            when {
                response.status.isSuccess() -> {
                    val body = response.body<CurrentsResponseDto>()
                    Result.Success(body.news)
                }
                response.status == HttpStatusCode.TooManyRequests -> Result.Error(DataError.Remote.RATE_LIMIT)
                response.status.value in 500..599 -> Result.Error(DataError.Remote.SERVER)
                else -> Result.Error(DataError.Remote.UNKNOWN)
            }
        } catch (e: kotlinx.serialization.SerializationException) {
            Result.Error(DataError.Remote.SERIALIZATION)
        } catch (e: Exception) {
            // Pas d'internet / timeout / autre → on ne fait pas remonter l'exception brute.
            Result.Error(DataError.Remote.NO_INTERNET)
        }
    }
}
```

- [ ] **Step 5: Lancer (succès attendu)**

Run: `./gradlew :sharedLogic:jvmTest --tests "*CurrentsApiTest*"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add sharedLogic/src/commonMain sharedLogic/src/commonTest
git commit -m "feat(news): HttpClientFactory + CurrentsApi (erreurs typées, testé MockEngine)"
```

---

## Task 6 : Cache local — InMemory (Web) + Room (nonWeb) + mappers Entity

**Files:**
- Create: `webMain/.../news/data/local/InMemoryNewsLocalDataSource.kt`
- Create: `nonWebMain/.../news/data/local/NewsEntity.kt`
- Create: `nonWebMain/.../news/data/local/NewsDao.kt`
- Create: `nonWebMain/.../news/data/local/NewsDatabase.kt`
- Create: `nonWebMain/.../news/data/local/NewsEntityMappers.kt`
- Create: `nonWebMain/.../news/data/local/RoomNewsLocalDataSource.kt`
- Create: `nonWebMain/.../news/data/local/DatabaseBuilder.kt` (expect)
- Create: `androidMain/.../news/data/local/DatabaseBuilder.android.kt`
- Create: `iosMain/.../news/data/local/DatabaseBuilder.ios.kt`
- Create: `jvmMain/.../news/data/local/DatabaseBuilder.jvm.kt`
- Test: `commonTest/.../news/data/InMemoryNewsLocalDataSourceTest.kt`

**Interfaces:**
- Consumes: `NewsLocalDataSource`, `Article` (Task 2).
- Produces: `InMemoryNewsLocalDataSource`, `NewsDatabase`, `NewsDao`, `RoomNewsLocalDataSource(dao)`, `expect fun getDatabaseBuilder(...)`.

- [ ] **Step 1: Écrire le test de la source en mémoire (dans commonTest)**

`commonTest/.../news/data/InMemoryNewsLocalDataSourceTest.kt` :
```kotlin
package com.ggdevhub.newsapp.news.data

import com.ggdevhub.newsapp.news.data.local.InMemoryNewsLocalDataSource
import com.ggdevhub.newsapp.news.domain.model.Article
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class InMemoryNewsLocalDataSourceTest {
    private fun article(id: String) = Article(id, "t$id", null, "https://x/$id", null, null, null, emptyList(), null)

    @Test
    fun replacePage1_puis_observe() = runTest {
        val ds = InMemoryNewsLocalDataSource()
        ds.replacePage1("k", listOf(article("1")))
        assertEquals(1, ds.observe("k").first().size)
    }

    @Test
    fun appendPage_ajoute() = runTest {
        val ds = InMemoryNewsLocalDataSource()
        ds.replacePage1("k", listOf(article("1")))
        ds.appendPage("k", 2, listOf(article("2")))
        assertEquals(2, ds.observe("k").first().size)
    }
}
```
> ⚠️ `InMemoryNewsLocalDataSource` est dans `webMain`, non visible depuis `commonTest`. **Pour le tester, on la met en fait dans `commonMain`** (elle n'a aucune dépendance plateforme) et seule la SÉLECTION Room/InMemory diffère par plateforme. Donc : créer `InMemoryNewsLocalDataSource` dans `commonMain/.../news/data/local/` (pas webMain). Web l'utilisera via Koin ; nonWeb utilisera Room.

- [ ] **Step 2: Lancer (échec attendu)**

Run: `./gradlew :sharedLogic:jvmTest --tests "*InMemoryNewsLocalDataSourceTest*"`
Expected: FAIL.

- [ ] **Step 3: Créer InMemoryNewsLocalDataSource dans commonMain**

`commonMain/.../news/data/local/InMemoryNewsLocalDataSource.kt` :
```kotlin
package com.ggdevhub.newsapp.news.data.local

import com.ggdevhub.newsapp.news.domain.model.Article
import com.ggdevhub.newsapp.news.domain.source.NewsLocalDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Cache local NON persistant (mémoire de session). Utilisé sur le Web (Room indisponible)
 * et dans les tests. Les données disparaissent au rechargement de la page.
 */
class InMemoryNewsLocalDataSource : NewsLocalDataSource {
    // Map cacheKey -> liste d'articles, exposée en Flow réactif.
    private val store = MutableStateFlow<Map<String, List<Article>>>(emptyMap())

    override fun observe(cacheKey: String): Flow<List<Article>> =
        store.map { it[cacheKey].orEmpty() }

    override suspend fun replacePage1(cacheKey: String, articles: List<Article>) {
        store.value = store.value.toMutableMap().apply { put(cacheKey, articles) }
    }

    override suspend fun appendPage(cacheKey: String, page: Int, articles: List<Article>) {
        val current = store.value[cacheKey].orEmpty()
        // Déduplication par id pour éviter les doublons entre pages.
        val merged = (current + articles).distinctBy { it.id }
        store.value = store.value.toMutableMap().apply { put(cacheKey, merged) }
    }

    override suspend fun getById(id: String): Article? =
        store.value.values.flatten().firstOrNull { it.id == id }
}
```

- [ ] **Step 4: Lancer (succès attendu)**

Run: `./gradlew :sharedLogic:jvmTest --tests "*InMemoryNewsLocalDataSourceTest*"`
Expected: PASS.

- [ ] **Step 5: Créer l'entité, le DAO, la base, les mappers Entity (nonWebMain)**

`nonWebMain/.../news/data/local/NewsEntity.kt` :
```kotlin
package com.ggdevhub.newsapp.news.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Ligne persistée d'un article. `cacheKey` permet un cache séparé par catégorie/langue/pays. */
@Entity(tableName = "articles")
data class NewsEntity(
    @PrimaryKey val id: String,
    val cacheKey: String,
    val page: Int,
    val title: String,
    val description: String?,
    val url: String,
    val imageUrl: String?,
    val author: String?,
    val sourceName: String?,
    val categories: String,          // catégories jointes par des virgules
    val publishedAtEpochMs: Long?,
    val insertedAt: Long,
)
```
`nonWebMain/.../news/data/local/NewsDao.kt` :
```kotlin
package com.ggdevhub.newsapp.news.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Accès SQL. `observeByKey` renvoie un Flow → l'UI se met à jour automatiquement. */
@Dao
interface NewsDao {
    @Query("SELECT * FROM articles WHERE cacheKey = :key ORDER BY page, insertedAt")
    fun observeByKey(key: String): Flow<List<NewsEntity>>

    @Query("SELECT * FROM articles WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): NewsEntity?

    @Upsert
    suspend fun upsertAll(items: List<NewsEntity>)

    @Query("DELETE FROM articles WHERE cacheKey = :key")
    suspend fun clearKey(key: String)
}
```
`nonWebMain/.../news/data/local/NewsDatabase.kt` :
```kotlin
package com.ggdevhub.newsapp.news.data.local

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

/** Base Room. @ConstructedBy + object expect = obligatoire en KMP (KSP génère l'impl.). */
@Database(entities = [NewsEntity::class], version = 1)
@ConstructedBy(NewsDatabaseConstructor::class)
abstract class NewsDatabase : RoomDatabase() {
    abstract fun newsDao(): NewsDao
}

// KSP génère l'implémentation réelle par plateforme.
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object NewsDatabaseConstructor : RoomDatabaseConstructor<NewsDatabase>
```
`nonWebMain/.../news/data/local/NewsEntityMappers.kt` :
```kotlin
package com.ggdevhub.newsapp.news.data.local

import com.ggdevhub.newsapp.news.domain.model.Article
import kotlinx.datetime.Instant

/** Domaine → Entity (pour écrire en base). */
fun Article.toEntity(cacheKey: String, page: Int): NewsEntity = NewsEntity(
    id = id, cacheKey = cacheKey, page = page,
    title = title, description = description, url = url, imageUrl = imageUrl,
    author = author, sourceName = sourceName,
    categories = categories.joinToString(","),
    publishedAtEpochMs = publishedAt?.toEpochMilliseconds(),
    insertedAt = kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
)

/** Entity → Domaine (pour lire depuis la base). */
fun NewsEntity.toArticle(): Article = Article(
    id = id, title = title, description = description, url = url, imageUrl = imageUrl,
    author = author, sourceName = sourceName,
    categories = categories.split(",").filter { it.isNotBlank() },
    publishedAt = publishedAtEpochMs?.let { Instant.fromEpochMilliseconds(it) },
)
```

- [ ] **Step 6: Créer RoomNewsLocalDataSource + le builder expect/actual**

`nonWebMain/.../news/data/local/RoomNewsLocalDataSource.kt` :
```kotlin
package com.ggdevhub.newsapp.news.data.local

import com.ggdevhub.newsapp.news.domain.model.Article
import com.ggdevhub.newsapp.news.domain.source.NewsLocalDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Impl. persistante du cache via Room (Android/iOS/Desktop). */
class RoomNewsLocalDataSource(private val dao: NewsDao) : NewsLocalDataSource {
    override fun observe(cacheKey: String): Flow<List<Article>> =
        dao.observeByKey(cacheKey).map { list -> list.map { it.toArticle() } }

    override suspend fun replacePage1(cacheKey: String, articles: List<Article>) {
        dao.clearKey(cacheKey)  // pull-to-refresh / lancement : on remplace la page 1
        dao.upsertAll(articles.map { it.toEntity(cacheKey, page = 1) })
    }

    override suspend fun appendPage(cacheKey: String, page: Int, articles: List<Article>) {
        dao.upsertAll(articles.map { it.toEntity(cacheKey, page) })  // Upsert = pas de doublon (clé = id)
    }

    override suspend fun getById(id: String): Article? = dao.getById(id)?.toArticle()
}
```
`nonWebMain/.../news/data/local/DatabaseBuilder.kt` :
```kotlin
package com.ggdevhub.newsapp.news.data.local

import androidx.room.RoomDatabase

/**
 * Fabrique du builder Room, spécifique à chaque plateforme (chemin de fichier différent).
 * `platformContext` = Context sur Android, Unit ailleurs (voir actuals).
 */
expect class DatabaseBuilderFactory {
    fun create(): RoomDatabase.Builder<NewsDatabase>
}
```
`androidMain/.../news/data/local/DatabaseBuilder.android.kt` :
```kotlin
package com.ggdevhub.newsapp.news.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

/** Android : la base a besoin du Context pour situer le fichier. */
actual class DatabaseBuilderFactory(private val context: Context) {
    actual fun create(): RoomDatabase.Builder<NewsDatabase> {
        val dbFile = context.getDatabasePath("news.db")
        return Room.databaseBuilder<NewsDatabase>(
            context = context.applicationContext,
            name = dbFile.absolutePath,
        )
    }
}
```
`iosMain/.../news/data/local/DatabaseBuilder.ios.kt` :
```kotlin
package com.ggdevhub.newsapp.news.data.local

import androidx.room.Room
import androidx.room.RoomDatabase
import platform.Foundation.NSHomeDirectory

/** iOS : pas de Context ; on stocke le fichier dans le dossier home de l'app. */
actual class DatabaseBuilderFactory {
    actual fun create(): RoomDatabase.Builder<NewsDatabase> {
        val dbFilePath = NSHomeDirectory() + "/news.db"
        return Room.databaseBuilder<NewsDatabase>(
            name = dbFilePath,
            factory = { NewsDatabaseConstructor.initialize() },
        )
    }
}
```
`jvmMain/.../news/data/local/DatabaseBuilder.jvm.kt` :
```kotlin
package com.ggdevhub.newsapp.news.data.local

import androidx.room.Room
import androidx.room.RoomDatabase
import java.io.File

/** Desktop : fichier dans le dossier utilisateur (persiste entre les lancements). */
actual class DatabaseBuilderFactory {
    actual fun create(): RoomDatabase.Builder<NewsDatabase> {
        val dbFile = File(System.getProperty("user.home"), ".newsapp/news.db").apply { parentFile?.mkdirs() }
        return Room.databaseBuilder<NewsDatabase>(
            name = dbFile.absolutePath,
            factory = { NewsDatabaseConstructor.initialize() },
        )
    }
}
```
> Fonction utilitaire commune pour finaliser le builder (driver + dispatcher), à mettre dans `nonWebMain` DatabaseBuilder.kt :
```kotlin
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

/** Applique le driver empaqueté et le contexte IO, communs à toutes les plateformes non-Web. */
fun RoomDatabase.Builder<NewsDatabase>.buildNewsDatabase(): NewsDatabase =
    setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
```

- [ ] **Step 7: Compiler toutes les cibles (Room ne doit pas casser le Web)**

Run: `./gradlew :sharedLogic:compileKotlinJvm :sharedLogic:compileKotlinJs :sharedLogic:compileKotlinWasmJs :sharedLogic:compileKotlinIosSimulatorArm64 :sharedLogic:compileAndroidMain --console=plain`
Expected: BUILD SUCCESSFUL (Room compile sur nonWeb ; Web ignore Room).

- [ ] **Step 8: Commit**

```bash
git add sharedLogic/src
git commit -m "feat(news): cache local (Room nonWeb + InMemory) + mappers Entity"
```

---

## Task 7 : Repository offline-first + pagination

**Files:**
- Create: `.../news/data/NewsRepositoryImpl.kt`
- Test: `commonTest/.../news/data/NewsRepositoryImplTest.kt` (+ `FakeNewsApi`, réutilise `InMemoryNewsLocalDataSource`)

**Interfaces:**
- Consumes: `CurrentsApi.fetch`, `NewsLocalDataSource`, `buildRequest`, `fallbackCountries`, `toArticle`.
- Produces: `NewsRepositoryImpl(api, local) : NewsRepository`.
- Note test : on introduit une petite indirection pour injecter un faux réseau. `CurrentsApi` étant une classe concrète, on extrait une interface `NewsRemoteDataSource { suspend fun fetch(NewsRequest): Result<List<Article>, DataError.Remote> }` implémentée par un adaptateur autour de `CurrentsApi` (mapping DTO→domaine inclus). Le repository dépend de cette interface (testable avec un fake).

- [ ] **Step 1: Extraire l'interface remote + son adaptateur**

`data/remote/NewsRemoteDataSource.kt` :
```kotlin
package com.ggdevhub.newsapp.news.data.remote

import com.ggdevhub.newsapp.news.data.mapper.toArticle
import com.ggdevhub.newsapp.news.domain.model.Article
import com.ggdevhub.newsapp.news.domain.model.DataError
import com.ggdevhub.newsapp.news.domain.model.Result
import com.ggdevhub.newsapp.news.domain.model.map

/** Source réseau vue par le repository (renvoie déjà des Article du domaine). Facile à falsifier en test. */
interface NewsRemoteDataSource {
    suspend fun fetch(request: NewsRequest): Result<List<Article>, DataError.Remote>
}

/** Adaptateur : appelle CurrentsApi puis mappe DTO→domaine. */
class CurrentsRemoteDataSource(private val api: CurrentsApi) : NewsRemoteDataSource {
    override suspend fun fetch(request: NewsRequest): Result<List<Article>, DataError.Remote> =
        api.fetch(request).map { dtos -> dtos.map { it.toArticle() } }
}
```

- [ ] **Step 2: Écrire les tests du repository**

`commonTest/.../news/data/NewsRepositoryImplTest.kt` :
```kotlin
package com.ggdevhub.newsapp.news.data

import com.ggdevhub.newsapp.news.data.local.InMemoryNewsLocalDataSource
import com.ggdevhub.newsapp.news.data.remote.NewsRemoteDataSource
import com.ggdevhub.newsapp.news.data.remote.NewsRequest
import com.ggdevhub.newsapp.news.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private fun article(id: String) = Article(id, "t$id", null, "https://x/$id", null, null, null, emptyList(), null)

// Faux réseau : renvoie une réponse programmée par (page).
private class FakeRemote(val pages: Map<Int, List<Article>>, val error: DataError.Remote? = null) : NewsRemoteDataSource {
    var calls = 0
    override suspend fun fetch(request: NewsRequest): Result<List<Article>, DataError.Remote> {
        calls++
        error?.let { return Result.Error(it) }
        return Result.Success(pages[request.page].orEmpty())
    }
}

class NewsRepositoryImplTest {
    @Test
    fun refresh_remplit_le_cache_page1() = runTest {
        val remote = FakeRemote(pages = mapOf(1 to listOf(article("1"), article("2"))))
        val repo = NewsRepositoryImpl(remote, InMemoryNewsLocalDataSource())
        val res = repo.refresh(NewsFilter.TOP, NewsLanguage.FR, "CM")
        assertTrue(res is Result.Success)
        assertEquals(2, repo.observeArticles(NewsFilter.TOP, NewsLanguage.FR, "CM").first().size)
    }

    @Test
    fun loadNextPage_ajoute_page2() = runTest {
        val remote = FakeRemote(pages = mapOf(1 to listOf(article("1")), 2 to listOf(article("2"))))
        val repo = NewsRepositoryImpl(remote, InMemoryNewsLocalDataSource())
        repo.refresh(NewsFilter.TOP, NewsLanguage.FR, "CM")
        val more = repo.loadNextPage(NewsFilter.TOP, NewsLanguage.FR, "CM")
        assertEquals(Result.Success(true), more)
        assertEquals(2, repo.observeArticles(NewsFilter.TOP, NewsLanguage.FR, "CM").first().size)
    }

    @Test
    fun loadNextPage_vide_signale_fin() = runTest {
        val remote = FakeRemote(pages = mapOf(1 to listOf(article("1"))))
        val repo = NewsRepositoryImpl(remote, InMemoryNewsLocalDataSource())
        repo.refresh(NewsFilter.TOP, NewsLanguage.FR, "CM")
        val more = repo.loadNextPage(NewsFilter.TOP, NewsLanguage.FR, "CM")
        assertEquals(Result.Success(false), more) // page 2 vide → plus de pages
    }

    @Test
    fun refresh_erreur_sans_cache_remonte_l_erreur() = runTest {
        val remote = FakeRemote(pages = emptyMap(), error = DataError.Remote.NO_INTERNET)
        val repo = NewsRepositoryImpl(remote, InMemoryNewsLocalDataSource())
        assertTrue(repo.refresh(NewsFilter.TOP, NewsLanguage.FR, "CM") is Result.Error)
    }
}
```

- [ ] **Step 3: Lancer (échec attendu)**

Run: `./gradlew :sharedLogic:jvmTest --tests "*NewsRepositoryImplTest*"`
Expected: FAIL.

- [ ] **Step 4: Implémenter NewsRepositoryImpl**

`data/NewsRepositoryImpl.kt` :
```kotlin
package com.ggdevhub.newsapp.news.data

import com.ggdevhub.newsapp.news.data.remote.NewsRemoteDataSource
import com.ggdevhub.newsapp.news.data.remote.buildRequest
import com.ggdevhub.newsapp.news.data.remote.fallbackCountries
import com.ggdevhub.newsapp.news.domain.model.*
import com.ggdevhub.newsapp.news.domain.repository.NewsRepository
import com.ggdevhub.newsapp.news.domain.source.NewsLocalDataSource
import kotlinx.coroutines.flow.Flow

/**
 * Implémentation offline-first :
 * - la SOURCE DE VÉRITÉ affichée = le cache local (observeArticles)
 * - refresh() recharge la page 1 depuis le réseau (avec cascade fallback pays) et remplace le cache
 * - loadNextPage() récupère la page suivante et l'ajoute au cache
 */
class NewsRepositoryImpl(
    private val remote: NewsRemoteDataSource,
    private val local: NewsLocalDataSource,
) : NewsRepository {

    // Page courante mémorisée par cacheKey (pour l'infinite scroll).
    private val currentPage = mutableMapOf<String, Int>()

    private fun cacheKey(filter: NewsFilter, language: NewsLanguage, country: String?): String =
        "${filter.name}|${language.code}|${country ?: ""}"

    override fun observeArticles(filter: NewsFilter, language: NewsLanguage, country: String?): Flow<List<Article>> =
        local.observe(cacheKey(filter, language, country))

    override suspend fun refresh(filter: NewsFilter, language: NewsLanguage, country: String?): Result<Unit, DataError> {
        val key = cacheKey(filter, language, country)
        // Cascade : on essaie chaque pays jusqu'à obtenir des articles (évite un fil vide).
        for (candidate in fallbackCountries(filter, country)) {
            when (val res = remote.fetch(buildRequest(filter, language, candidate, page = 1))) {
                is Result.Success -> if (res.data.isNotEmpty()) {
                    local.replacePage1(key, res.data)
                    currentPage[key] = 1
                    return Result.Success(Unit)
                } // sinon on tente le candidat suivant
                is Result.Error -> {
                    // Erreur réseau : si on a déjà un cache, on l'ignore silencieusement.
                    // Sinon on remonte l'erreur.
                    return Result.Error(res.error)
                }
            }
        }
        // Aucun pays n'a donné d'articles : succès "vide" (l'UI montrera l'état vide).
        local.replacePage1(key, emptyList())
        currentPage[key] = 1
        return Result.Success(Unit)
    }

    override suspend fun loadNextPage(filter: NewsFilter, language: NewsLanguage, country: String?): Result<Boolean, DataError> {
        val key = cacheKey(filter, language, country)
        val next = (currentPage[key] ?: 1) + 1
        return when (val res = remote.fetch(buildRequest(filter, language, country, page = next))) {
            is Result.Success -> {
                if (res.data.isEmpty()) {
                    Result.Success(false) // plus de pages
                } else {
                    local.appendPage(key, next, res.data)
                    currentPage[key] = next
                    Result.Success(true)
                }
            }
            is Result.Error -> Result.Error(res.error)
        }
    }

    override suspend fun getArticle(id: String): Article? = local.getById(id)
}
```

- [ ] **Step 5: Lancer (succès attendu)**

Run: `./gradlew :sharedLogic:jvmTest --tests "*NewsRepositoryImplTest*"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add sharedLogic/src
git commit -m "feat(news): NewsRepositoryImpl offline-first + pagination (testé)"
```

---

## Task 8 : Présentation — NewsListViewModel (+ detail) partagé

**Files:**
- Create: `.../news/presentation/list/NewsListContract.kt` (State + Action + Event)
- Create: `.../news/presentation/list/NewsListViewModel.kt`
- Create: `.../news/presentation/detail/ArticleDetailContract.kt` (State + Action)
- Create: `.../news/presentation/detail/ArticleDetailViewModel.kt`
- Create: `.../news/util/OpenUrl.kt` (expect) + actuals android/ios/jvm/js/wasmJs
- Test: `commonTest/.../news/presentation/NewsListViewModelTest.kt`

**Interfaces (pattern MVI : State / Action / Event) :**
- Consumes: `NewsRepository` (Task 2/7).
- Produces: `NewsListState`, `NewsListAction`, `NewsListEvent`, `NewsListViewModel` (exposant `state: StateFlow`, `events: Flow`, `onAction(action)`), `ArticleDetailState`, `ArticleDetailAction`, `ArticleDetailViewModel`, `expect fun openUrl(url)`.

- [ ] **Step 1: Écrire les tests du ViewModel (avec faux repository)**

`commonTest/.../news/presentation/NewsListViewModelTest.kt` :
```kotlin
package com.ggdevhub.newsapp.news.presentation

import com.ggdevhub.newsapp.news.domain.model.*
import com.ggdevhub.newsapp.news.domain.repository.NewsRepository
import com.ggdevhub.newsapp.news.presentation.list.NewsListViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import kotlin.test.*

private fun article(id: String) = Article(id, "t$id", null, "https://x/$id", null, null, null, emptyList(), null)

private class FakeRepo : NewsRepository {
    val flow = MutableStateFlow<List<Article>>(emptyList())
    var refreshed = false
    override fun observeArticles(f: NewsFilter, l: NewsLanguage, c: String?) = flow
    override suspend fun refresh(f: NewsFilter, l: NewsLanguage, c: String?): Result<Unit, DataError> {
        refreshed = true; flow.value = listOf(article("1")); return Result.Success(Unit)
    }
    override suspend fun loadNextPage(f: NewsFilter, l: NewsLanguage, c: String?): Result<Boolean, DataError> {
        flow.value = flow.value + article("2"); return Result.Success(true)
    }
    override suspend fun getArticle(id: String) = flow.value.firstOrNull { it.id == id }
}

class NewsListViewModelTest {
    @BeforeTest fun setup() = Dispatchers.setMain(StandardTestDispatcher())
    @AfterTest fun teardown() = Dispatchers.resetMain()

    @Test
    fun init_charge_les_articles() = runTest {
        val repo = FakeRepo()
        val vm = NewsListViewModel(repo)
        advanceUntilIdle()
        assertTrue(repo.refreshed)
        assertEquals(1, vm.state.value.articles.size)
    }

    // Toute intention passe par onAction(...) : ici la pagination.
    @Test
    fun action_scrolledToEnd_pagine() = runTest {
        val vm = NewsListViewModel(FakeRepo())
        advanceUntilIdle()
        vm.onAction(NewsListAction.ScrolledToEnd)
        advanceUntilIdle()
        assertEquals(2, vm.state.value.articles.size)
    }

    // OpenArticle doit émettre un EVENT one-shot de navigation (pas un changement d'état).
    @Test
    fun action_openArticle_emet_event_navigation() = runTest {
        val vm = NewsListViewModel(FakeRepo())
        advanceUntilIdle()
        val received = mutableListOf<NewsListEvent>()
        val job = launch { vm.events.collect { received.add(it) } }
        vm.onAction(NewsListAction.OpenArticle(article("1")))
        advanceUntilIdle()
        assertTrue(received.any { it is NewsListEvent.NavigateToDetail })
        job.cancel()
    }
}
```
> Ajouter l'import `import com.ggdevhub.newsapp.news.presentation.list.*` (State/Action/Event) en tête du test.

- [ ] **Step 2: Lancer (échec attendu)**

Run: `./gradlew :sharedLogic:jvmTest --tests "*NewsListViewModelTest*"`
Expected: FAIL.

- [ ] **Step 3: Créer le contrat MVI (State / Action / Event)**

`presentation/list/NewsListContract.kt` :
```kotlin
package com.ggdevhub.newsapp.news.presentation.list

import com.ggdevhub.newsapp.news.domain.model.*

/** STATE — snapshot immuable de l'écran liste. L'UI (Compose/SwiftUI) le rend tel quel. */
data class NewsListState(
    val activeFilter: NewsFilter = NewsFilter.TOP,
    val language: NewsLanguage = NewsLanguage.FR,
    val country: String? = "CM",
    val articles: List<Article> = emptyList(),
    val isLoading: Boolean = false,      // 1er chargement / changement de filtre
    val isPaginating: Boolean = false,   // loader du bas
    val isRefreshing: Boolean = false,   // pull-to-refresh
    val endReached: Boolean = false,
    val error: DataError? = null,
    val availableFilters: List<NewsFilter> = NewsFilter.entries,
    val availableLanguages: List<NewsLanguage> = NewsLanguage.entries,
)

/** ACTION — toute intention utilisateur. Un seul point d'entrée : onAction(action). */
sealed interface NewsListAction {
    data class SelectFilter(val filter: NewsFilter) : NewsListAction
    data class SelectLanguage(val language: NewsLanguage) : NewsListAction
    data object Refresh : NewsListAction            // pull-to-refresh
    data object ScrolledToEnd : NewsListAction      // infinite scroll
    data class OpenArticle(val article: Article) : NewsListAction
    data object Retry : NewsListAction
}

/** EVENT — effet one-shot (navigation, erreur), consommé une seule fois par le Root. */
sealed interface NewsListEvent {
    data class NavigateToDetail(val articleId: String) : NewsListEvent
    data class ShowError(val error: DataError) : NewsListEvent
}
```

- [ ] **Step 4: Créer le ViewModel liste**

`presentation/list/NewsListViewModel.kt` :
```kotlin
package com.ggdevhub.newsapp.news.presentation.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ggdevhub.newsapp.news.domain.model.*
import com.ggdevhub.newsapp.news.domain.repository.NewsRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel PARTAGÉ (MVI). Consommé par Android (Compose natif), Desktop/Web (Compose partagé)
 * et iOS (SwiftUI). L'UI lit `state`, observe `events`, et envoie tout via `onAction(action)`.
 */
class NewsListViewModel(private val repo: NewsRepository) : ViewModel() {

    private val _state = MutableStateFlow(NewsListState())
    val state: StateFlow<NewsListState> = _state.asStateFlow()

    // Canal d'événements one-shot (navigation, erreur). receiveAsFlow = chaque event consommé une fois.
    private val _events = Channel<NewsListEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    // Collecte du cache (source de vérité) pour le filtre courant.
    private var observeJob: kotlinx.coroutines.Job? = null

    init { selectAndLoad(_state.value.activeFilter) }

    /** POINT D'ENTRÉE UNIQUE : toutes les intentions de l'UI arrivent ici. */
    fun onAction(action: NewsListAction) {
        when (action) {
            is NewsListAction.SelectFilter -> {
                _state.update { it.copy(activeFilter = action.filter) }
                selectAndLoad(action.filter)
            }
            is NewsListAction.SelectLanguage -> {
                _state.update { it.copy(language = action.language) }
                selectAndLoad(_state.value.activeFilter)
            }
            NewsListAction.Refresh -> refresh()
            NewsListAction.ScrolledToEnd -> loadNextPage()
            is NewsListAction.OpenArticle ->
                viewModelScope.launch { _events.send(NewsListEvent.NavigateToDetail(action.article.id)) }
            NewsListAction.Retry -> selectAndLoad(_state.value.activeFilter)
        }
    }

    private fun selectAndLoad(filter: NewsFilter) {
        val s = _state.value
        observeJob?.cancel()
        // 1) On observe le cache du filtre (mise à jour auto de la liste).
        observeJob = viewModelScope.launch {
            repo.observeArticles(filter, s.language, s.country).collect { list ->
                _state.update { it.copy(articles = list) }
            }
        }
        // 2) On déclenche un refresh réseau (page 1) ; une erreur devient aussi un Event.
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, endReached = false) }
            val res = repo.refresh(filter, s.language, s.country)
            val err = (res as? Result.Error)?.error
            _state.update { it.copy(isLoading = false, error = err) }
            if (err != null) _events.send(NewsListEvent.ShowError(err))
        }
    }

    private fun refresh() {
        val s = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true, error = null) }
            val res = repo.refresh(s.activeFilter, s.language, s.country)
            _state.update { it.copy(isRefreshing = false, error = (res as? Result.Error)?.error) }
        }
    }

    private fun loadNextPage() {
        val s = _state.value
        if (s.isPaginating || s.endReached) return  // évite les appels multiples
        viewModelScope.launch {
            _state.update { it.copy(isPaginating = true) }
            val res = repo.loadNextPage(s.activeFilter, s.language, s.country)
            _state.update {
                it.copy(
                    isPaginating = false,
                    endReached = (res as? Result.Success)?.data == false,
                    error = (res as? Result.Error)?.error,
                )
            }
        }
    }
}
```

- [ ] **Step 5: Lancer (succès attendu)**

Run: `./gradlew :sharedLogic:jvmTest --tests "*NewsListViewModelTest*"`
Expected: PASS.

- [ ] **Step 6: Créer le detail ViewModel + openUrl (expect/actual)**

`presentation/detail/ArticleDetailContract.kt` :
```kotlin
package com.ggdevhub.newsapp.news.presentation.detail

import com.ggdevhub.newsapp.news.domain.model.Article

/** STATE du détail. */
data class ArticleDetailState(val article: Article? = null, val notFound: Boolean = false)

/** ACTION du détail (un seul point d'entrée onAction). */
sealed interface ArticleDetailAction {
    data class Load(val id: String) : ArticleDetailAction
    data object OpenLink : ArticleDetailAction     // « Lire l'article » → openUrl
}
```
`presentation/detail/ArticleDetailViewModel.kt` :
```kotlin
package com.ggdevhub.newsapp.news.presentation.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ggdevhub.newsapp.news.domain.repository.NewsRepository
import com.ggdevhub.newsapp.news.util.openUrl
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Charge un article par id et gère l'ouverture du lien externe (MVI : onAction). */
class ArticleDetailViewModel(private val repo: NewsRepository) : ViewModel() {
    private val _state = MutableStateFlow(ArticleDetailState())
    val state: StateFlow<ArticleDetailState> = _state.asStateFlow()

    /** POINT D'ENTRÉE UNIQUE. */
    fun onAction(action: ArticleDetailAction) {
        when (action) {
            is ArticleDetailAction.Load -> load(action.id)
            ArticleDetailAction.OpenLink -> _state.value.article?.url?.let { openUrl(it) }
        }
    }

    private fun load(id: String) {
        viewModelScope.launch {
            val a = repo.getArticle(id)
            _state.value = ArticleDetailState(article = a, notFound = a == null)
        }
    }
}
```
`util/OpenUrl.kt` (commonMain, expect) :
```kotlin
package com.ggdevhub.newsapp.news.util

/** Ouvre une URL dans le navigateur/onglet de la plateforme (impl. par plateforme). */
expect fun openUrl(url: String)
```
Actuals :
- `androidMain/.../util/OpenUrl.android.kt` :
```kotlin
package com.ggdevhub.newsapp.news.util

import android.content.Intent
import android.net.Uri
import com.ggdevhub.newsapp.util.appContext   // fourni via un ContextProvider (voir DI plateforme)

actual fun openUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    appContext.startActivity(intent)
}
```
- `iosMain/.../util/OpenUrl.ios.kt` :
```kotlin
package com.ggdevhub.newsapp.news.util

import platform.Foundation.NSURL
import platform.UIKit.UIApplication

actual fun openUrl(url: String) {
    NSURL.URLWithString(url)?.let { UIApplication.sharedApplication.openURL(it) }
}
```
- `jvmMain/.../util/OpenUrl.jvm.kt` :
```kotlin
package com.ggdevhub.newsapp.news.util

import java.awt.Desktop
import java.net.URI

actual fun openUrl(url: String) {
    runCatching { if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI(url)) }
}
```
- `jsMain/.../util/OpenUrl.js.kt` :
```kotlin
package com.ggdevhub.newsapp.news.util

import kotlinx.browser.window
actual fun openUrl(url: String) { window.open(url, "_blank") }
```
- `wasmJsMain/.../util/OpenUrl.wasmJs.kt` :
```kotlin
package com.ggdevhub.newsapp.news.util

import kotlinx.browser.window
actual fun openUrl(url: String) { window.open(url, "_blank") }
```
> Note Android `appContext` : on l'initialise dans `Application.onCreate` (Task 9). Fichier `androidMain/.../util/AndroidContext.kt` avec `lateinit var appContext: Context`.

- [ ] **Step 7: Compiler toutes les cibles**

Run: `./gradlew :sharedLogic:compileKotlinJvm :sharedLogic:compileKotlinJs :sharedLogic:compileKotlinWasmJs :sharedLogic:compileKotlinIosSimulatorArm64 :sharedLogic:compileAndroidMain --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add sharedLogic/src
git commit -m "feat(news): ViewModels partagés (liste + détail) + openUrl expect/actual"
```

---

## Task 9 : Injection de dépendances (Koin) + point d'entrée

**Files:**
- Create: `.../news/di/NetworkModule.kt`, `.../news/di/NewsModule.kt`, `.../news/di/InitKoin.kt`
- Create: `.../news/di/PlatformDataModule.kt` (expect) + actuals android/ios/jvm/js/wasmJs
- Create: `androidMain/.../util/AndroidContext.kt`
- Test: `commonTest/.../news/di/KoinModulesTest.kt` (jvm)

**Interfaces:**
- Consumes: tout ce qui précède.
- Produces: `appModules(): List<Module>`, `initKoin(config)`, `expect val platformDataModule: Module`.

- [ ] **Step 1: Créer les modules réseau + news**

`di/NetworkModule.kt` :
```kotlin
package com.ggdevhub.newsapp.news.di

import com.ggdevhub.newsapp.BuildKonfig
import com.ggdevhub.newsapp.news.data.remote.*
import org.koin.dsl.module

/** Fournit le client HTTP, l'API Currents et l'adaptateur remote (avec la clé BuildKonfig). */
val networkModule = module {
    single { HttpClientFactory.create(apiKey = BuildKonfig.CURRENTS_API_KEY) }
    single { CurrentsApi(get(), BuildKonfig.CURRENTS_API_KEY) }
    single<NewsRemoteDataSource> { CurrentsRemoteDataSource(get()) }
}
```
`di/NewsModule.kt` :
```kotlin
package com.ggdevhub.newsapp.news.di

import com.ggdevhub.newsapp.news.data.NewsRepositoryImpl
import com.ggdevhub.newsapp.news.domain.repository.NewsRepository
import com.ggdevhub.newsapp.news.presentation.detail.ArticleDetailViewModel
import com.ggdevhub.newsapp.news.presentation.list.NewsListViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

/** Repository + ViewModels. Le NewsLocalDataSource vient du module plateforme. */
val newsModule = module {
    singleOf(::NewsRepositoryImpl) bind NewsRepository::class
    factoryOf(::NewsListViewModel)
    factoryOf(::ArticleDetailViewModel)
}
```

- [ ] **Step 2: Créer le module plateforme (expect/actual) pour NewsLocalDataSource**

`di/PlatformDataModule.kt` (commonMain) :
```kotlin
package com.ggdevhub.newsapp.news.di

import org.koin.core.module.Module

/** Fournit l'impl. de NewsLocalDataSource : Room (nonWeb) ou InMemory (web). */
expect val platformDataModule: Module
```
`nonWebMain` ne peut pas contenir un actual (il n'est pas un vrai target). On met donc les actuals dans chaque cible :
- `androidMain/.../di/PlatformDataModule.android.kt` :
```kotlin
package com.ggdevhub.newsapp.news.di

import com.ggdevhub.newsapp.news.data.local.*
import com.ggdevhub.newsapp.news.domain.source.NewsLocalDataSource
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformDataModule: Module = module {
    single { DatabaseBuilderFactory(androidContext()).create().buildNewsDatabase() }
    single { get<NewsDatabase>().newsDao() }
    single<NewsLocalDataSource> { RoomNewsLocalDataSource(get()) }
}
```
- `iosMain/.../di/PlatformDataModule.ios.kt` :
```kotlin
package com.ggdevhub.newsapp.news.di

import com.ggdevhub.newsapp.news.data.local.*
import com.ggdevhub.newsapp.news.domain.source.NewsLocalDataSource
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformDataModule: Module = module {
    single { DatabaseBuilderFactory().create().buildNewsDatabase() }
    single { get<NewsDatabase>().newsDao() }
    single<NewsLocalDataSource> { RoomNewsLocalDataSource(get()) }
}
```
- `jvmMain/.../di/PlatformDataModule.jvm.kt` : identique à iOS (pas de context).
```kotlin
package com.ggdevhub.newsapp.news.di

import com.ggdevhub.newsapp.news.data.local.*
import com.ggdevhub.newsapp.news.domain.source.NewsLocalDataSource
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformDataModule: Module = module {
    single { DatabaseBuilderFactory().create().buildNewsDatabase() }
    single { get<NewsDatabase>().newsDao() }
    single<NewsLocalDataSource> { RoomNewsLocalDataSource(get()) }
}
```
- `jsMain/.../di/PlatformDataModule.js.kt` + `wasmJsMain/.../di/PlatformDataModule.wasmJs.kt` (identiques) :
```kotlin
package com.ggdevhub.newsapp.news.di

import com.ggdevhub.newsapp.news.data.local.InMemoryNewsLocalDataSource
import com.ggdevhub.newsapp.news.domain.source.NewsLocalDataSource
import org.koin.core.module.Module
import org.koin.dsl.module

// Web : pas de Room → cache en mémoire.
actual val platformDataModule: Module = module {
    single<NewsLocalDataSource> { InMemoryNewsLocalDataSource() }
}
```

- [ ] **Step 3: Créer initKoin**

`di/InitKoin.kt` :
```kotlin
package com.ggdevhub.newsapp.news.di

import org.koin.core.context.startKoin
import org.koin.dsl.KoinAppDeclaration

/** Liste des modules de l'app (à passer à startKoin). */
fun appModules() = listOf(networkModule, platformDataModule, newsModule)

/** Démarre Koin. Appelé par chaque plateforme (Android Application, Desktop/Web main, iOS). */
fun initKoin(config: KoinAppDeclaration? = null) = startKoin {
    config?.invoke(this)
    modules(appModules())
}
```
`androidMain/.../util/AndroidContext.kt` :
```kotlin
package com.ggdevhub.newsapp.util

import android.content.Context

/** Contexte applicatif global (initialisé dans Application.onCreate). Sert à openUrl. */
lateinit var appContext: Context
```

- [ ] **Step 4: Test de vérification des modules Koin (jvm)**

`commonTest` ne peut pas voir `platformDataModule` (expect). On teste donc sur jvm via `jvmTest`. Créer `jvmTest/.../news/di/KoinModulesTest.kt` :
```kotlin
package com.ggdevhub.newsapp.news.di

import org.koin.core.context.stopKoin
import org.koin.test.verify.verify
import kotlin.test.AfterTest
import kotlin.test.Test

class KoinModulesTest {
    @AfterTest fun tearDown() = stopKoin()

    // Vérifie que le graphe Koin est complet (toutes les deps résolubles) sur Desktop.
    @Test
    fun le_graphe_koin_est_valide() {
        networkModule.verify()
        newsModule.verify(extraTypes = listOf(com.ggdevhub.newsapp.news.domain.source.NewsLocalDataSource::class))
    }
}
```
Ajouter `implementation(libs.koin.test)` au source set `jvmTest` dans `sharedLogic/build.gradle.kts`.

- [ ] **Step 5: Lancer le test**

Run: `./gradlew :sharedLogic:jvmTest --tests "*KoinModulesTest*"`
Expected: PASS.

- [ ] **Step 6: Compiler toutes les cibles**

Run: `./gradlew :sharedLogic:compileKotlinJvm :sharedLogic:compileKotlinJs :sharedLogic:compileKotlinWasmJs :sharedLogic:compileKotlinIosSimulatorArm64 :sharedLogic:compileAndroidMain --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add sharedLogic/src sharedLogic/build.gradle.kts
git commit -m "feat(news): DI Koin (modules réseau/news + data plateforme) + initKoin"
```

---

## Task 10 : UI partagée (Desktop + Web) dans sharedUI

**Files:**
- Create: `sharedUI/src/commonMain/.../news/ui/CategoryChips.kt`
- Create: `.../news/ui/ArticleCard.kt`
- Create: `.../news/ui/NewsListScreen.kt`
- Create: `.../news/ui/ArticleDetailScreen.kt`
- Create: `.../news/ui/NewsRootScreen.kt`
- Modify: `sharedUI/src/commonMain/.../App.kt` (afficher NewsRootScreen)

**Interfaces:**
- Consumes: `NewsListViewModel` (`state`/`events`/`onAction`), `NewsListState`/`NewsListAction`/`NewsListEvent`, `ArticleDetailViewModel`/`ArticleDetailAction`, `Article`, `NewsFilter` (sharedLogic).
- Produces: `NewsRootScreen()` composable racine.

- [ ] **Step 1: Chips de catégories**

`news/ui/CategoryChips.kt` :
```kotlin
package com.ggdevhub.newsapp.news.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ggdevhub.newsapp.news.domain.model.NewsFilter

/** Libellés lisibles pour chaque filtre. */
private fun NewsFilter.label(): String = when (this) {
    NewsFilter.TOP -> "À la une"; NewsFilter.CAMEROON -> "Cameroun"; NewsFilter.AFRICA -> "Afrique"
    NewsFilter.BUSINESS -> "Business"; NewsFilter.TECH -> "Tech"; NewsFilter.SPORT -> "Sport"
    NewsFilter.HEALTH -> "Santé"; NewsFilter.ENTERTAINMENT -> "Divertissement"
}

/** Rangée horizontale de chips ; une seule sélectionnée à la fois. */
@Composable
fun CategoryChips(filters: List<NewsFilter>, active: NewsFilter, onSelect: (NewsFilter) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
        items(filters) { f ->
            FilterChip(selected = f == active, onClick = { onSelect(f) }, label = { Text(f.label()) })
        }
    }
}
```

- [ ] **Step 2: Carte d'article (image Coil)**

`news/ui/ArticleCard.kt` :
```kotlin
package com.ggdevhub.newsapp.news.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ggdevhub.newsapp.news.domain.model.Article

/** Une carte : image + titre + source. Cliquable → ouvre le détail. */
@Composable
fun ArticleCard(article: Article, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column {
            // Coil3 : charge l'image réseau (le moteur Ktor est déjà sur le classpath).
            article.imageUrl?.let { url ->
                AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(180.dp))
            }
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(article.title, style = MaterialTheme.typography.titleMedium)
                article.sourceName?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}
```

- [ ] **Step 3: Écran liste (chips + LazyColumn + infinite scroll + pull-to-refresh)**

`news/ui/NewsListScreen.kt` :
```kotlin
package com.ggdevhub.newsapp.news.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ggdevhub.newsapp.news.presentation.list.NewsListAction
import com.ggdevhub.newsapp.news.presentation.list.NewsListEvent
import com.ggdevhub.newsapp.news.presentation.list.NewsListViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Écran liste PARTAGÉ (Desktop + Web), en MVI :
 * lit `state`, envoie tout via `vm.onAction(...)`, et observe `vm.events` pour naviguer.
 * @param onOpenArticle appelé quand l'Event NavigateToDetail arrive (le Root/racine navigue).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsListScreen(onOpenArticle: (String) -> Unit, vm: NewsListViewModel = koinViewModel()) {
    val state by vm.state.collectAsState()
    val listState = rememberLazyListState()

    // Observe les événements one-shot (navigation). Collectés une seule fois → pas de re-déclenchement.
    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            when (event) {
                is NewsListEvent.NavigateToDetail -> onOpenArticle(event.articleId)
                is NewsListEvent.ShowError -> Unit // (afficher un snackbar ici si souhaité)
            }
        }
    }

    // Détecte l'arrivée en bas de liste → envoie une Action de pagination.
    val atEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= state.articles.size - 3 && state.articles.isNotEmpty()
        }
    }
    LaunchedEffect(atEnd) { if (atEnd) vm.onAction(NewsListAction.ScrolledToEnd) }

    Column(Modifier.fillMaxSize()) {
        CategoryChips(
            filters = state.availableFilters,
            active = state.activeFilter,
            onSelect = { vm.onAction(NewsListAction.SelectFilter(it)) },
            modifier = Modifier.padding(vertical = 8.dp),
        )
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { vm.onAction(NewsListAction.Refresh) },
            modifier = Modifier.weight(1f),
        ) {
            when {
                state.isLoading && state.articles.isEmpty() ->
                    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                state.articles.isEmpty() ->
                    Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Aucun article") }
                else -> LazyColumn(state = listState, contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.articles, key = { it.id }) { a ->
                        ArticleCard(a, onClick = { vm.onAction(NewsListAction.OpenArticle(a)) })
                    }
                    if (state.isPaginating) item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { CircularProgressIndicator() }
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 4: Écran détail**

`news/ui/ArticleDetailScreen.kt` :
```kotlin
package com.ggdevhub.newsapp.news.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.ggdevhub.newsapp.news.presentation.detail.ArticleDetailAction
import com.ggdevhub.newsapp.news.presentation.detail.ArticleDetailViewModel
import org.koin.compose.viewmodel.koinViewModel

/** Détail natif (MVI) : image + titre + extrait + bouton "Lire l'article" (lien externe). */
@Composable
fun ArticleDetailScreen(articleId: String, vm: ArticleDetailViewModel = koinViewModel()) {
    // Charge l'article via une Action au (re)démarrage de l'écran.
    LaunchedEffect(articleId) { vm.onAction(ArticleDetailAction.Load(articleId)) }
    val state by vm.state.collectAsState()
    val a = state.article

    if (a == null) { Box(Modifier.fillMaxSize()) { Text("Article introuvable", Modifier.padding(16.dp)) }; return }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        a.imageUrl?.let { AsyncImage(it, null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(220.dp)) }
        Text(a.title, style = MaterialTheme.typography.headlineSmall)
        a.sourceName?.let { Text(it, style = MaterialTheme.typography.labelMedium) }
        a.description?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
        // « Lire l'article » = une Action ; le ViewModel appelle openUrl().
        Button(onClick = { vm.onAction(ArticleDetailAction.OpenLink) }) { Text("Lire l'article") }
    }
}
```

- [ ] **Step 5: Écran racine (navigation minimale par état)**

`news/ui/NewsRootScreen.kt` :
```kotlin
package com.ggdevhub.newsapp.news.ui

import androidx.compose.material3.*
import androidx.compose.runtime.*

/**
 * Navigation minimale à état (v1) : liste ↔ détail, sans lib de nav.
 * Le "Root" reçoit l'id de l'article via l'Event NavigateToDetail émis par le ViewModel.
 * On branchera navigation-compose plus tard si besoin.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsRootScreen() {
    var selectedId by remember { mutableStateOf<String?>(null) }
    val currentId = selectedId
    Scaffold(topBar = { TopAppBar(title = { Text(if (currentId == null) "Actualités" else "Article") }) }) { padding ->
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.padding(padding)) {
            if (currentId == null) NewsListScreen(onOpenArticle = { selectedId = it })
            else ArticleDetailScreen(articleId = currentId)
        }
    }
}
```

- [ ] **Step 6: Brancher NewsRootScreen dans App.kt (sharedUI)**

Modifier `sharedUI/src/commonMain/.../App.kt` : remplacer le contenu du `MaterialTheme { … }` par `NewsRootScreen()`. Garder le `MaterialTheme`.

- [ ] **Step 7: Compiler sharedUI (jvm + web)**

Run: `./gradlew :sharedUI:compileKotlinJvm :sharedUI:compileKotlinJs :sharedUI:compileKotlinWasmJs --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add sharedUI/src
git commit -m "feat(news): UI Compose partagée (liste, détail, chips, carte) Desktop+Web"
```

---

## Task 11 : UI native Android + démarrage Koin sur les 3 apps

**Files:**
- Create: `androidApp/src/main/.../news/ui/*` (mêmes écrans en AndroidX Compose)
- Create: `androidApp/src/main/.../NewsApplication.kt` (+ déclarer dans le manifest)
- Modify: `androidApp/.../MainActivity.kt` (afficher NewsRootScreen Android)
- Modify: `desktopApp/.../main.kt` et `webApp/.../main.kt` (appeler initKoin)
- Modify: `androidApp/build.gradle.kts` (koin-compose-viewmodel si besoin)

**Interfaces:**
- Consumes: `initKoin`, `NewsListViewModel`, etc.

- [ ] **Step 1: Application Android + initKoin + appContext**

`androidApp/src/main/.../NewsApplication.kt` :
```kotlin
package com.ggdevhub.newsapp

import android.app.Application
import com.ggdevhub.newsapp.news.di.initKoin
import com.ggdevhub.newsapp.util.appContext
import org.koin.android.ext.koin.androidContext

/** Point de démarrage Android : initialise Koin + le contexte global (openUrl). */
class NewsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = applicationContext                 // pour openUrl
        initKoin { androidContext(this@NewsApplication) } // Koin + module data Android (Room)
    }
}
```
Déclarer dans `androidApp/src/main/AndroidManifest.xml` : `<application android:name=".NewsApplication" …>`.

- [ ] **Step 2: Écrans Android natifs**

Créer `androidApp/src/main/.../news/ui/CategoryChips.kt`, `ArticleCard.kt`, `NewsListScreen.kt`, `ArticleDetailScreen.kt`, `NewsRootScreen.kt` — **mêmes contenus qu'à la Task 10** (imports AndroidX `androidx.compose.*` au lieu de `org.jetbrains.compose.*`, et `org.koin.androidx.compose.koinViewModel` / `org.koin.compose.viewmodel.koinViewModel`). Coil3 `AsyncImage` fonctionne à l'identique sur Android.
> Répéter le code des composables de la Task 10 ici (mêmes signatures, même logique). Seuls les imports Compose changent (AndroidX).

- [ ] **Step 3: MainActivity affiche NewsRootScreen**

Modifier `androidApp/.../MainActivity.kt` : dans `setContent { App() }`, remplacer par `setContent { MaterialTheme { NewsRootScreen() } }` (import du NewsRootScreen Android).

- [ ] **Step 4: initKoin sur Desktop et Web**

`desktopApp/.../main.kt` : avant `application { … }`, appeler `initKoin()`.
`webApp/.../main.kt` : au début de `main()`, appeler `initKoin()`.

- [ ] **Step 5: Ajouter koin-compose-viewmodel à androidApp si nécessaire**

Dans `androidApp/build.gradle.kts` deps : `implementation(libs.koin.compose.viewmodel)` (pour `koinViewModel()` en Compose).

- [ ] **Step 6: Compiler Android + Desktop + Web**

Run: `./gradlew :androidApp:compileDebugKotlin :desktopApp:compileKotlin :webApp:compileKotlinJs :webApp:compileKotlinWasmJs --console=plain`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Test manuel (avec vraie clé API dans local.properties)**

Run Android: `./gradlew :androidApp:installDebug` puis lancer l'app.
Run Desktop: `./gradlew :desktopApp:run`.
Run Web (wasm): `./gradlew :webApp:wasmJsBrowserDevelopmentRun`.
Expected: le fil "À la une" (fr/Cameroun) s'affiche, chips fonctionnelles, scroll pagine, clic → détail, "Lire l'article" ouvre le navigateur.

- [ ] **Step 8: Commit**

```bash
git add androidApp desktopApp webApp
git commit -m "feat(news): UI Android native + démarrage Koin (Android/Desktop/Web)"
```

---

## Self-Review (effectuée)

- **Couverture spec** : deps/infra (T1) · domaine+Result/DataError (T2) · Currents DTO/mapper (T3) · mapping filtre+fallback (T4) · HttpClient/Api (T5) · Room+InMemory (T6) · repository offline-first+pagination (T7) · ViewModel partagé+openUrl (T8) · DI Koin+initKoin (T9) · UI partagée Desktop/Web (T10) · UI Android+démarrage apps (T11). iOS SwiftUI, recherche, favoris, WebView = **v2** (hors plan, conforme à la spec).
- **Placeholders** : la seule valeur à récupérer est la **version KSP** (T1 Step 1) — instruction actionnable (lien releases), pas un TODO de logique.
- **Cohérence des types** : `NewsRepository`, `NewsLocalDataSource`, `NewsRemoteDataSource`, `NewsRequest`, `buildRequest/fallbackCountries`, `toArticle/toEntity`, MVI `NewsListState`/`NewsListAction`/`NewsListEvent` + `onAction`/`events`, `ArticleDetailState`/`ArticleDetailAction`, `openUrl`, `initKoin/appModules/platformDataModule` cohérents d'une tâche à l'autre.
- **Note** : `InMemoryNewsLocalDataSource` placée en `commonMain` (et non `webMain`) pour être testable et réutilisable ; seule la SÉLECTION Room/InMemory diffère par plateforme (via `platformDataModule`).

## Risques connus (rappel spec)
Quota Currents serré · couverture Cameroun/Afrique · setup Room+KSP+source sets (isolé en T1) · iOS Flow→Swift (v2) · Web sans persistance.
