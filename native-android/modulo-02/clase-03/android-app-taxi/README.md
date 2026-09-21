# Android App — MVVM + Clean Architecture (Compose, Hilt)

Proyecto Android (Kotlin + Jetpack Compose) con **MVVM** y **Clean Architecture**. Integra un backend **NestJS** que expone `POST /passenger/login` (inicio de sesión por número de teléfono) y responde con `accessToken`, `refreshToken` y el perfil del pasajero.

> Paquetes base: `commons`, `core`, `features`. Capas por paquete: `data`, `domain`, `presentation`.

---

## Arquitectura

```
presentation  →  domain  ←  data
     UI           Reglas       Infraestructura (Retrofit / almacenamiento seguro de sesión)
```

- **Presentation**: Composables, ViewModels, estado UI, navegación.
- **Domain**: **Modelos de dominio**, **Use Cases** y **contratos** (interfaces: `AuthRepository`, `SessionStore`). Sin dependencias Android.
- **Data**: **Implementaciones** de los contratos, **data sources** (Retrofit, SharedPreferences cifrado) y **mappers** DTO ⇄ Domain.

**Paquetes**:
- `commons`: reutilizables **no de negocio** (UI, utilidades).
- `core`: piezas **transversales** de negocio/infra (sesión, network, tema, `MainActivity`).
- `features`: cada funcionalidad vertical (`splash`, `signin`).

Estructura (resumen):
```
com/example/android/
├─ AppMain.kt                                    // @HiltAndroidApp
├─ commons/presentation/                         // PhoneInputField, PrimaryButton, AppToast, SystemBars
├─ core/
│  ├─ data/
│  │  ├─ AuthInterceptor.kt                      // agrega Authorization: Bearer <token>
│  │  ├─ NetworkModule.kt                        // OkHttp/Retrofit (Hilt)
│  │  ├─ SecurityModule.kt                       // SessionStore + AuthInterceptor (Hilt)
│  │  └─ SessionStoreEncryptedPrefs.kt           // implementación de SessionStore
│  ├─ domain/SessionStore.kt                     // contrato de sesión
│  └─ presentation/
│     ├─ activity/MainActivity.kt                // NavHost (Splash → SignIn)
│     └─ theme/{Color,Theme,Type}.kt
└─ features/
   ├─ splash/presentation/SplashScreen.kt
   └─ signin/
      ├─ SignInModule.kt                         // Hilt: AuthApi, AuthRepository, use case
      ├─ data/{mapper,remote,repository}/...     // AuthApi, DTOs, AuthRepositoryImpl
      ├─ domain/{model,repository,usecase}/...   // Passenger, SessionTokens, SignInWithPhoneUseCase
      └─ presentation/{SignInScreen,SignInViewModel}.kt
```

Los recursos de cada feature viven junto a su pantalla (`features/<feature>/presentation/res`) y se registran como carpetas de recursos en `app/build.gradle.kts` (ver [Configuración de build](#configuración-de-build-agp-9)).

![Diagrama](../../clase-02/img/mvvm-mobile.webp)

---

## Features actuales

- **Splash**: pantalla de carga 1.5 s → navega a **SignIn**.
- **SignIn**: el usuario ingresa su teléfono (9 dígitos, prefijo `+51` visual) → `POST passenger/login` → los tokens se guardan en `SessionStore`.

**Estados de SignIn** (`SignInState`), diseñados en [`design-m02-c03.pen`](../design-m02-c03.pen):

| Estado | Qué ve el usuario |
|---|---|
| `Idle` | Formulario; el botón «Ingresar» se habilita al escribir los 9 dígitos |
| `Loading` | Botón deshabilitado con spinner |
| `Success(welcomeName)` | **Toast verde** «¡Bienvenido, {nombre}!» y `onGoHome(welcomeName)` |
| `Error(message)` | **Toast rojo** con el mensaje del servidor |

El toast (`AppToast` / `ToastHost` en `commons/presentation`) aparece bajo la barra de estado, es accesible (*live region*) y se cierra a los 3 s llamando a `SignInViewModel.reset()`, que devuelve el estado a `Idle`. En `Error`, el mensaje sale del campo `message` del JSON de error del backend (`AuthRepositoryImpl`); si no hay conexión muestra un texto propio en español.

Estado del alcance: `Home` y `SignUp` son placeholders en `MainActivity`, y `onGoHome` / `onGoSignUp` de `SignInPhoneScreen` todavía no navegan.

---

## 🛠️ Stack

| Componente | Versión |
|---|---|
| **Android Gradle Plugin** | 9.3.3 (Kotlin integrado; serie 9.3, la que acepta Android Studio 2026.1) |
| **Gradle Wrapper** | 9.7.1 |
| **Kotlin** | 2.4.20 (compilador real, ver nota) |
| **KSP** | 2.3.12 |
| **Compose BOM** | 2026.09.00 (Material 3 1.4.0, UI 1.12.1, Material Icons Core 1.7.8) |
| **Activity Compose** | 1.13.0 |
| **Core KTX** | 1.19.0 |
| **Lifecycle** | 2.11.0 (`runtime-ktx`, `viewmodel-ktx`, `runtime-compose`) |
| **Navigation Compose** | 2.10.1 |
| **Hilt** | 2.60.1 (con **KSP**) |
| **androidx.hilt** `hilt-lifecycle-viewmodel-compose` | 1.4.0 |
| **Retrofit** | 3.0.0 + `converter-gson` |
| **OkHttp** | 5.5.0 (+ `logging-interceptor`) |
| **Coroutines** | 1.11.0 |
| **compileSdk / targetSdk / minSdk** | 37 / 37 / 29 |
| **Java/JDK** | 17 |
| **Tests** | JUnit 4.13.2, AndroidX Test JUnit 1.3.0, Espresso 3.7.0 |

> **Nota (Kotlin)**: AGP 9.3.3 trae por dentro el Kotlin Gradle Plugin 2.2.10, pero el plugin `org.jetbrains.kotlin.plugin.compose` declarado con `kotlin = "2.4.20"` lo sube a 2.4.20 (`kotlin-gradle-plugin:2.2.10 -> 2.4.20` en `./gradlew buildEnvironment`, y `kotlin-compiler-embeddable:2.4.20` en la configuración `kotlinCompilerClasspath`). La versión `kotlin` del catálogo es, por tanto, la que compila el proyecto.

**Requisitos**

| Herramienta | Versión |
|---|---|
| Android Studio | Serie 2026.1 («Quail») o superior, con soporte para AGP 9.3. Si el IDE rechaza la versión de AGP, ver el punto 11 de [Troubleshooting](#troubleshooting-errores-comunes) |
| JDK | **17** |
| Gradle | 9.7.1 (lo descarga el wrapper) |
| Android SDK | Platform **37** y Build Tools ≥ 36.0.0 |
| Dispositivo/emulador | API 29 o superior |

---

## Arranque rápido

1) **Clonar** y abrir el proyecto en Android Studio.  
2) Verifica **toolchain** y **Gradle**:
   - `gradle/wrapper/gradle-wrapper.properties` → `gradle-9.7.1-bin.zip`
   - JDK 17: *Settings → Build Tools → Gradle → Gradle JDK = 17*.
3) **Base URL** del backend (`BuildConfig`). El valor versionado apunta a un túnel de desarrollo personal; para el emulador cámbialo a `http://10.0.2.2:3001/`:
   ```kotlin
   // app/build.gradle.kts
   android {
       defaultConfig {
           buildConfigField("String", "API_BASE_URL", "\"http://10.0.2.2:3001/\"")
       }
       buildFeatures {
           compose = true
           buildConfig = true
       }
   }
   ```
4) **Permisos y cleartext** (HTTP), tal como están en el proyecto:
   ```xml
   <!-- AndroidManifest.xml -->
   <uses-permission android:name="android.permission.INTERNET" />
   <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

   <application
       android:usesCleartextTraffic="true"
       android:networkSecurityConfig="@xml/network_security_config" ... />
   ```
   ```xml
   <!-- res/xml/network_security_config.xml -->
   <network-security-config>
       <base-config cleartextTrafficPermitted="true" />
   </network-security-config>
   ```
5) **Backend NestJS** ejecutándose en el host (puerto **3001**, `APPLICATION_PORT`). Endpoint usado por la app: `POST /passenger/login` con body `{"phone":"51987654321"}` (E.164 sin `+`).  
   - En emulador Android, usa `http://10.0.2.2:3001/` (alias de `localhost` del host).  
   - Si usas dispositivo físico: IP LAN del host (p. ej., `http://192.168.x.x:3001/`) y `listen('0.0.0.0')` en NestJS.
6) **Run**: `app` en emulador o dispositivo.

**Comandos de validación (terminal)**

| Comando | Qué valida |
|---|---|
| `./gradlew clean assembleDebug --warning-mode all` | Compila y muestra todas las advertencias de deprecación |
| `./gradlew testDebugUnitTest` | Tests unitarios JVM (`ExampleUnitTest`) |
| `./gradlew lintDebug` | Lint (reporte en `app/build/reports/lint-results-debug.html`) |
| `./gradlew connectedDebugAndroidTest` | Tests instrumentados en emulador/dispositivo (`ExampleInstrumentedTest`, `SessionStoreEncryptedPrefsTest`) |

> `connectedDebugAndroidTest` desinstala la app y el APK de test al terminar.

---

## Configuración de build (AGP 9)

Con AGP 9 el soporte de Kotlin viene **integrado**: el módulo `app` no aplica `org.jetbrains.kotlin.android`. Solo se declara el plugin de Compose (que fija la versión de Kotlin), Hilt y KSP:

```kotlin
// app/build.gradle.kts
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.example.android"
    compileSdk {
        version = release(37)
    }
    // ...
    sourceSets["main"].res.directories += listOf(
        "src/main/res",
        "src/main/java/com/example/android/features/splash/presentation/res",
        "src/main/java/com/example/android/features/signin/presentation/res"
    )
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}
```

Qué cambió respecto a AGP 8.x en este proyecto:

| Antes (AGP 8.x) | Ahora (AGP 9.x) |
|---|---|
| `alias(libs.plugins.kotlin.android)` en `plugins {}` | Se elimina (Kotlin integrado) |
| `kotlinOptions { jvmTarget = "17" }` | `kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }` |
| `compileSdk = 36` / `targetSdk = 36` | `compileSdk { version = release(37) }` / `targetSdk = 37` |
| `sourceSets["main"].res.srcDirs(...)` | `sourceSets["main"].res.directories += listOf(...)` |
| `constraints { implementation(libs.javapoet) }` | Se elimina: Dagger 2.60.1 ya resuelve `javapoet:1.13.0` |
| `android.useAndroidX` y `android.nonTransitiveRClass` en `gradle.properties` | Se eliminan (son el valor por defecto) |
| Sin *configuration cache* | `org.gradle.configuration-cache=true` en `gradle.properties` |

KSP 2.3.12 + Hilt 2.60.1 funcionan con el Kotlin integrado de AGP 9 sin banderas extra en `gradle.properties`.

---

## 🔌 NetworkModule (referencia)

```kotlin
// core/data/NetworkModule.kt
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton
    fun provideLogging(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }

    @Provides @Singleton
    fun provideOkHttp(
        logging: HttpLoggingInterceptor,
        auth: AuthInterceptor
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(logging)
        .addInterceptor(auth)
        .build()

    @Provides @Singleton
    fun provideRetrofit(client: OkHttpClient): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
}
```

`BuildConfig.API_BASE_URL` debe terminar con `/`. Retrofit 3 y OkHttp 5 mantienen esta API (`Retrofit.Builder`, `OkHttpClient.Builder`, `HttpLoggingInterceptor`, `retrofit2.HttpException`) sin cambios de código respecto a Retrofit 2 / OkHttp 4.

`AuthInterceptor` añade el header `Authorization` con el token guardado en `SessionStore`:

```kotlin
// core/data/AuthInterceptor.kt
class AuthInterceptor(
    private val session: SessionStore
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val token = runBlocking { session.accessToken().first() }
        val req = if (!token.isNullOrBlank()) {
            original.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else original
        return chain.proceed(req)
    }
}
```

> `HttpLoggingInterceptor.Level.BODY` imprime cuerpos de request/response en Logcat (incluidos los tokens del login). Úsalo solo en desarrollo.

---

## MVVM + Clean: responsabilidades

- **Use Cases (domain)**: **lógica de negocio** y políticas (p. ej., normalizar el teléfono y guardar la sesión al autenticarse).  
- **Repository impl (data)**: **infraestructura** (Retrofit + mappers DTO → dominio).  
- **ViewModel (presentation)**: orquesta casos de uso y expone estado UI.

Caso de uso del login (signin):
```kotlin
// features/signin/domain/usecase/SignInWithPhoneUseCase.kt
class SignInWithPhoneUseCase(
    private val repo: AuthRepository,
    private val session: SessionStore
) {
    operator fun invoke(phoneReq: String): Flow<SignInState> = flow {
        val phone = "51$phoneReq"
        emit(SignInState.Loading)

        try {
            val result = repo.signInWithPhone(phone)
            session.saveTokens(
                result.tokens.accessToken,
                result.tokens.refreshToken
            )
            emit(SignInState.Success(result.user.givenName ?: result.user.phoneNumber))
        } catch (t: Throwable) {
            emit(SignInState.Error(t.message ?: "No se pudo iniciar sesión"))
        }
    }
}
```

Repositorio y API (data):
```kotlin
// features/signin/data/remote/AuthApi.kt
interface AuthApi {
    @POST("passenger/login")
    suspend fun login(@Body body: PassengerLoginRequest): PassengerLoginResponse
}

// features/signin/data/repository/AuthRepositoryImpl.kt
class AuthRepositoryImpl(
    private val api: AuthApi
) : AuthRepository {

    override suspend fun signInWithPhone(phone: String): SignInResult {
        try {
            val res = api.login(PassengerLoginRequest(phone))
            return res.toDomain()
        } catch (e: HttpException) {
            val msg = e.response()?.errorBody()?.string()?.takeIf { it.isNotBlank() }
            throw RuntimeException(msg ?: e.message())
        } catch (t: Throwable) {
            throw RuntimeException(t.message ?: "Sign in failed")
        }
    }
}
```

ViewModel y consumo del estado en Compose:
```kotlin
// features/signin/presentation/SignInViewModel.kt
@HiltViewModel
class SignInViewModel @Inject constructor(
    private val signInWithPhone: SignInWithPhoneUseCase
) : ViewModel() {

    private val _ui = MutableStateFlow<SignInState>(SignInState.Idle)
    val ui: StateFlow<SignInState> = _ui

    fun submit(phone: String) {
        viewModelScope.launch {
            signInWithPhone(phone).collect { _ui.value = it }
        }
    }

    fun reset() { _ui.value = SignInState.Idle }
}

// features/signin/presentation/SignInScreen.kt
val state by vm.ui.collectAsStateWithLifecycle()   // androidx.lifecycle.compose
```

`collectAsStateWithLifecycle()` reemplaza a `collectAsState()`: deja de recolectar el `StateFlow` cuando la pantalla no está al menos en `STARTED`.

---

## 🧭 Navegación

`MainActivity` usa `NavHost` con las rutas `splash`, `sign_in`, `home` y `sign_up` (`sealed class Route`). El flujo real es **Splash** → **SignIn**:

```kotlin
// core/presentation/activity/MainActivity.kt
composable(Route.Splash.path) {
    SplashScreen(
        onFinished = {
            nav.navigate(Route.SignIn.path) {
                popUpTo(Route.Splash.path) { inclusive = true }
                launchSingleTop = true
            }
        }
    )
}
```

La app es *edge-to-edge* (`enableEdgeToEdge` con barras transparentes). Con `targetSdk 35+` el color de las barras del sistema lo decide el contenido que dibujas detrás; por eso `NavigationBarStyle(darkIcons)` (en `commons/presentation/SystemBars.kt`) solo ajusta el color de los **iconos** de la barra de navegación.

---

## Checklist de salud

- [ ] `BuildConfig.API_BASE_URL` apunta a `http://10.0.2.2:3001/` (emulador) y **termina con `/`**.  
- [ ] Backend NestJS levantado en el puerto **3001** (`POST /passenger/login`).  
- [ ] `android:usesCleartextTraffic="true"` y `network_security_config` permiten HTTP.  
- [ ] Permiso `INTERNET` declarado en Manifest.  
- [ ] JDK 17 + Gradle 9.7.1 + AGP 9.3.3 + Kotlin 2.4.20 + KSP 2.3.12.  
- [ ] Android Studio con soporte para AGP 9.3 (serie 2026.1 o superior).  
- [ ] Sin `org.jetbrains.kotlin.android` en los `plugins {}` (Kotlin integrado de AGP 9).  
- [ ] No mezclar `kapt` y `ksp` para el mismo processor (Hilt usa **KSP**).

---

## Troubleshooting (errores comunes)

**1) EPERM (Operation not permitted) al llamar API**  
Causa: falta permiso `INTERNET` o usar IP LAN en emulador.  
✔ Agrega `<uses-permission android:name="android.permission.INTERNET"/>` y usa `http://10.0.2.2:3001/`.

**2) CLEARTEXT not permitted**  
✔ `android:usesCleartextTraffic="true"` + `network_security_config` con `<base-config cleartextTrafficPermitted="true" />`.

**3) BuildConfig deshabilitado**  
```
defaultConfig contains custom BuildConfig fields, but the feature is disabled.
```
✔ `android.buildFeatures.buildConfig = true`.

**4) AGP 9: plugin `org.jetbrains.kotlin.android` ya no se aplica**  
```
Failed to apply plugin 'org.jetbrains.kotlin.android'
The 'org.jetbrains.kotlin.android' plugin is no longer required for Kotlin support since AGP 9.0.
```
✔ Elimina el plugin de `app/build.gradle.kts`, de `build.gradle.kts` (raíz) y del catálogo. Mantén `org.jetbrains.kotlin.plugin.compose`.

**5) AGP 9: `Unresolved reference 'kotlinOptions'`**  
✔ Usa `kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }` con `import org.jetbrains.kotlin.gradle.dsl.JvmTarget`.

**6) AGP 9: advertencia `'fun srcDirs(vararg srcDirs: Any): Any' is deprecated`**  
✔ `sourceSets["main"].res.directories += listOf(...)`.

**7) `MasterKey` / `EncryptedSharedPreferences` deprecados**  
```
'class EncryptedSharedPreferences : Any, SharedPreferences' is deprecated. Deprecated in Java.
'class MasterKey : Any' is deprecated. Deprecated in Java.
```
Con `androidx.security:security-crypto:1.1.0` (estable) toda la librería está `@Deprecated`. ✔ Este proyecto usa `SessionStoreEncryptedPrefs` con AES-256-GCM sobre AndroidKeyStore (ver [Sesión](#sesión-y-acceso-a-datos-sessionstore)) y ya no depende de `security-crypto`.

**8) `navigationBarColor` / `isStatusBarContrastEnforced` deprecados (API 35)**  
```
'var navigationBarColor: Int' is deprecated. Deprecated in Java.
'var isStatusBarContrastEnforced: Boolean' is deprecated. Deprecated in Java.
```
✔ Con *edge-to-edge* esas propiedades no tienen efecto en Android 15+; `enableEdgeToEdge(...)` ya configura las barras. Se eliminó su uso.

**9) ¿Qué Kotlin se está usando de verdad?**  
AGP 9 trae KGP 2.2.10 por defecto, pero el plugin de Compose lo sube a la versión del catálogo.  
✔ `./gradlew buildEnvironment | grep kotlin-gradle-plugin` (debe mostrar `2.2.10 -> 2.4.20`).

**10) Tras `connectedDebugAndroidTest` la app desapareció del emulador**  
La tarea desinstala la app y el APK de test al terminar. ✔ Vuelve a instalar con `./gradlew installDebug`.

**11) Android Studio: «The project is using an incompatible version (AGP x) of the Android Gradle plugin»**  
```
The project is using an incompatible version (AGP 9.4.1) of the Android Gradle plugin.
Latest supported version is AGP 9.3.0
```
Causa: cada versión de Android Studio solo acepta AGP hasta cierta serie (`major.minor`); una AGP más nueva exige un Studio más nuevo. El error lo da el **IDE**: desde terminal, `./gradlew assembleDebug` compila con ambas.  
✔ Opción A: usa en `gradle/libs.versions.toml` una AGP de la serie que soporta tu Studio (este proyecto: `agp = "9.3.3"`).  
✔ Opción B: actualiza Android Studio (*Help → Check for Updates*) y sube `agp`. Al quedarte en una AGP anterior, lint muestra el aviso informativo `AndroidGradlePluginVersion` ("hay una versión más nueva"): no es un error.

**12) Preview: `Expected an activity context for creating a HiltViewModelFactory`**  
```
java.lang.IllegalStateException: Expected an activity context for creating a HiltViewModelFactory
but instead found: com.android.layoutlib.bridge.android.BridgeContext@...
    at androidx.hilt.lifecycle.viewmodel.compose.HiltViewModelKt.rememberHiltViewModelFactory(...)
```
Causa: el `@Preview` renderiza en `layoutlib`, donde el contexto no es una `Activity`, y `hiltViewModel()` (parámetro por defecto de la pantalla) no puede crear el ViewModel.  
✔ Separa la pantalla en dos: `SignInPhoneScreen` (con `hiltViewModel()`, la que usa `MainActivity`) y `SignInPhoneContent` (solo recibe `state`, `phone` y callbacks, sin Hilt). Los `@Preview` llaman a `SignInPhoneContent` con un estado de ejemplo (`SignInPhoneScreenPreviewEmpty`, `...Filled`, `...Loading`, `...Success` y `...Error`).

**13) `Unresolved reference 'icons'` al usar `Icons.Filled.CheckCircle`**  
`material3` no expone los iconos en el classpath de compilación. ✔ Agrega `androidx.compose.material:material-icons-core` (sin versión: la gestiona el BOM). El set *core* incluye `CheckCircle` y `Warning`, que usa `AppToast`; el resto está en `material-icons-extended`, mucho más pesado.

---

## Sesión y acceso a datos (`SessionStore`)

- **`SessionStore` (core/domain)** define el contrato, sin tipos de Android:
  ```kotlin
  interface SessionStore {
      suspend fun saveTokens(access: String, refresh: String)
      fun accessToken(): Flow<String?>
      fun refreshToken(): Flow<String?>
      suspend fun clear()
  }
  ```
- **`SessionStoreEncryptedPrefs` (core/data)** implementa el contrato con `SharedPreferences` y cifra cada token antes de guardarlo.
- `SecurityModule` lo provee como `@Singleton` y `AuthInterceptor` lo consume para añadir `Authorization`.
- Los use cases deciden políticas de negocio (guardar tokens al autenticarse, limpiar al cerrar sesión, etc.).

Cifrado (sin dependencias adicionales, solo `javax.crypto` y AndroidKeyStore):

| Elemento | Valor |
|---|---|
| Algoritmo | `AES/GCM/NoPadding`, clave de 256 bits |
| Clave | Generada en `AndroidKeyStore` (alias `session_store_aes_key`); nunca sale del Keystore |
| IV | 12 bytes aleatorios generados por el Keystore en cada cifrado |
| Autenticación | Tag GCM de 128 bits + AAD = nombre de la clave (`access_token` / `refresh_token`) |
| Formato guardado | `Base64(IV ‖ texto cifrado)` en el archivo `session_store` |
| Lectura inválida | Valor corrupto, manipulado, intercambiado o cifrado con otra clave → `null` (sin sesión) |

```kotlin
// core/data/SessionStoreEncryptedPrefs.kt (extracto)
private fun encrypt(key: String, plainText: String): String {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, secretKey)
    cipher.updateAAD(key.toByteArray(Charsets.UTF_8))
    val cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
    return Base64.getEncoder().encodeToString(cipher.iv + cipherText)
}
```

`SessionStoreEncryptedPrefsTest` (`androidTest`) verifica sobre el store real: guardar → leer → `clear()`, sobrescritura, que en disco no haya texto plano, lectura desde otra instancia y valores corruptos/intercambiados.

---

## Inyección de dependencias en este proyecto (Hilt)

Esta app usa **Hilt (sobre Dagger)** para desacoplar UI, lógica de negocio y acceso a datos. Beneficios: **testabilidad**, **sustitución** de implementaciones (mock/fake/real), **configuración centralizada** y **scopes** alineados con el ciclo de vida Android.

### 1) Piezas clave y dónde viven

- **`@HiltAndroidApp`** → `AppMain.kt`  
  Inicializa el **contenedor raíz** de Hilt al iniciar la app.
- **`@AndroidEntryPoint`** → `MainActivity` (host de UI)  
  Habilita la inyección en la Activity que aloja los Composables.
- **Módulos de DI compartidos** → `core/data/*`  
  - `NetworkModule`: `HttpLoggingInterceptor`, `OkHttpClient`, `Retrofit` (scope `@Singleton`), base URL desde `BuildConfig.API_BASE_URL`.
  - `SecurityModule`: `SessionStore` (→ `SessionStoreEncryptedPrefs`) y `AuthInterceptor`.
- **Módulo del feature** → `features/signin/SignInModule.kt`  
  - `AuthApi` (Retrofit), `AuthRepository` (→ `AuthRepositoryImpl`) y `SignInWithPhoneUseCase`.
- **Dominio del feature** → `features/signin/domain/*`  
  - Contrato (`AuthRepository`), modelos (`Passenger`, `SessionTokens`) y **Use Case** (`SignInWithPhoneUseCase`).
- **Presentación** → `features/signin/presentation/*`  
  - `@HiltViewModel` `SignInViewModel` recibe el **use case** por constructor; Compose lo obtiene con `hiltViewModel()` (`androidx.hilt.lifecycle.viewmodel.compose`).

### 2) Flujo de inyección (de extremo a extremo)

```kotlin
// core/data/SecurityModule.kt
@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    @Provides @Singleton
    fun provideSessionStore(@ApplicationContext ctx: Context): SessionStore =
        SessionStoreEncryptedPrefs(ctx)

    @Provides @Singleton
    fun provideAuthInterceptor(session: SessionStore): AuthInterceptor =
        AuthInterceptor(session)
}

// features/signin/SignInModule.kt
@Module
@InstallIn(SingletonComponent::class)
object SignInModule {

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi =
        retrofit.create(AuthApi::class.java)

    @Provides
    @Singleton
    fun provideAuthRepository(api: AuthApi): AuthRepository =
        AuthRepositoryImpl(api)

    @Provides
    @Singleton
    fun provideSignInUseCase(
        repo: AuthRepository,
        session: SessionStore
    ): SignInWithPhoneUseCase = SignInWithPhoneUseCase(repo, session)
}
```

> **Idea central**: la UI conoce **use cases**; los use cases dependen de **contratos** (`AuthRepository`, `SessionStore`); la **implementación** real se resuelve en **data** vía Hilt. El grafo es `SignInViewModel → SignInWithPhoneUseCase → (AuthRepository → AuthApi → Retrofit → OkHttpClient → AuthInterceptor → SessionStore)`.

### 3) Scopes y ciclo de vida

- `@Singleton` → objetos de **aplicación** (`OkHttpClient`, `Retrofit`, `SessionStore`, repositorios).  
- `@HiltViewModel` → cada ViewModel tiene su propio **scope** y se crea con sus dependencias.

### 4) Qualifiers

Si algún día necesitas **dos** instancias del mismo tipo (p. ej., dos `Retrofit` con distinta base URL), se distinguen con `@Qualifier` propios. Este proyecto tiene un único `Retrofit`, por lo que no los usa.

### 5) Sesión y headers

El header `Authorization` lo añade `AuthInterceptor` (ver [NetworkModule](#-networkmodule-referencia)); los repositorios no se preocupan por headers: la **infraestructura** lo resuelve.

### 6) Testing (pistas)

- **Domain**: tests puros de use cases con **fakes** de `AuthRepository` y `SessionStore` (sin Android).  
- **UI**: `HiltAndroidRule` y `@BindValue` para inyectar fakes en tests de `ViewModel`/Compose.  
- **Data**: tests instrumentados como `SessionStoreEncryptedPrefsTest` (Keystore real) o fakes de red.
