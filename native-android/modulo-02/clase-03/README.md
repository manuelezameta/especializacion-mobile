# Módulo 2 · Sesión 3

## Casos de Uso y Capa de Dominio

---

## Objetivos

1. Diseñar la capa de dominio **independiente de frameworks** bajo Clean Architecture.
2. Definir con precisión las **responsabilidades** de los UseCases.
3. Asegurar la **separación** entre lógica de negocio (dominio) y UI.
4. Implementar **Flows** en el dominio para modelar procesos asincrónicos (login).

---

## Contenido

1. Diseño de la capa de dominio
2. Definición y responsabilidades de los UseCases
3. Separación de lógica de negocio y UI
4. Uso de Flows en la capa de dominio
5. Práctica: el ejemplo `android-app-taxi`

---

## Desarrollo de la clase

### 1) Diseño de la capa de dominio

#### Teoría (Clean)

-   **Propósito**: concentrar reglas de negocio (invariantes, validaciones, políticas).
-   **Independencia**: cero dependencias de Android/UI/HTTP/DB; solo Kotlin estándar (y `kotlinx.coroutines` para `Flow`).
-   **Elementos**:
    -   **Entidades/Value Objects**: modelo de negocio puro.
    -   **Servicios/Casos de uso**: orquestan reglas para una acción del sistema.
    -   **Contratos (Interfaces)**: p. ej., `AuthRepository`, `SessionStore`.
    -   **Errores de dominio**: tipados (`sealed interface`), nunca textos ni códigos HTTP.
-   **Direcciones de dependencia**:
    -   Dominio define contratos.
    -   Data los implementa.
    -   UI consume casos de uso a través de ViewModel.
    -   Regla: **nada en dominio conoce a UI ni a Data**.

#### Ejemplo (Login)

**Entidades de dominio:**

```kotlin
data class Credentials(
    val email: String,
    val password: String
)

data class AuthUser(
    val id: String,
    val displayName: String,
    val email: String
)

data class SessionToken(
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresAtEpochSeconds: Long? = null
)
```

**Errores de dominio:**

```kotlin
sealed interface AuthDomainError {
    data object InvalidEmail : AuthDomainError
    data object WeakPassword : AuthDomainError
    data object Unauthorized : AuthDomainError
    data object Unexpected : AuthDomainError
}

class AuthException(val error: AuthDomainError) : Exception(error.toString())
```

`AuthException` es la forma en que un error de dominio **viaja** por el código (validación, repositorio) sin depender de textos ni de códigos HTTP.

**Contratos:**

```kotlin
interface AuthRepository {
    suspend fun login(creds: Credentials): Pair<AuthUser, SessionToken>
    suspend fun logout()
}

interface SessionStore {
    suspend fun save(session: SessionToken, user: AuthUser)
    suspend fun clear()
    suspend fun current(): Pair<AuthUser, SessionToken>?
}
```

**Implementación del contrato (capa Data):**

Quien conoce HTTP es Data, así que **ahí** se traducen los errores técnicos a errores de dominio. El dominio nunca inspecciona códigos ni mensajes de excepción.

```kotlin
class AuthRepositoryImpl(
    private val api: AuthApi
) : AuthRepository {

    override suspend fun login(creds: Credentials): Pair<AuthUser, SessionToken> =
        try {
            api.login(LoginRequest(creds.email, creds.password)).toDomain()
        } catch (e: HttpException) {
            throw AuthException(
                if (e.code() == 401) AuthDomainError.Unauthorized else AuthDomainError.Unexpected
            )
        }

    override suspend fun logout() = api.logout()
}
```

> `AuthApi`, `LoginRequest` y el mapper `toDomain()` (DTO → dominio) también viven en Data. Cualquier otra excepción (p. ej. `IOException` por falta de red) la absorbe el caso de uso como `Unexpected`.

---

### 2) Definición y responsabilidades de los UseCases

#### Teoría (Clean)

-   **Un caso de uso = una acción de negocio** (única responsabilidad).
-   **I/O explícito**: parámetros de entrada y resultado claro.
-   **Agregan reglas**: validan, transforman, orquestan repositorios y stores.
-   **Testeables**: puros o con dependencias simulables (mocks/fakes).
-   **Sin detalles técnicos**: delegan a contratos, no a librerías concretas.
-   **Errores tipados**: fallan con `AuthDomainError`, no con textos.

#### Ejemplo (Login)

**Validación de credenciales:**

```kotlin
class ValidateCredentialsUseCase {
    operator fun invoke(email: String, password: String): Result<Credentials> {
        val normalizedEmail = email.trim()
        if (normalizedEmail.isBlank() || !normalizedEmail.contains("@")) {
            return Result.failure(AuthException(AuthDomainError.InvalidEmail))
        }
        if (password.length < 6) {
            return Result.failure(AuthException(AuthDomainError.WeakPassword))
        }
        return Result.success(Credentials(email = normalizedEmail, password = password))
    }
}
```

**LoginUseCase con Flow:**

```kotlin
sealed interface DomainResult<out T> {
    data object Loading : DomainResult<Nothing>
    data class Success<T>(val data: T) : DomainResult<T>
    data class Error(val cause: AuthDomainError) : DomainResult<Nothing>
}

class LoginUseCase(
    private val authRepository: AuthRepository,
    private val sessionStore: SessionStore,
    private val validateCredentials: ValidateCredentialsUseCase
) {
    fun execute(email: String, password: String): Flow<DomainResult<AuthUser>> = flow {
        emit(DomainResult.Loading)

        val credentials = validateCredentials(email, password).getOrThrow()
        val (user, token) = authRepository.login(credentials)
        sessionStore.save(token, user)

        emit(DomainResult.Success(user))
    }.catch { emit(DomainResult.Error(it.toAuthDomainError())) }
}

private fun Throwable.toAuthDomainError(): AuthDomainError =
    (this as? AuthException)?.error ?: AuthDomainError.Unexpected
```

El camino feliz se lee de corrido; **todos** los fallos (validación, red, servidor) terminan en un único `catch` que los convierte en `DomainResult.Error`. Por qué `catch` del Flow y no un `try/catch` propio, en la sección 4.

**LogoutUseCase:**

```kotlin
class LogoutUseCase(
    private val authRepository: AuthRepository,
    private val sessionStore: SessionStore
) {
    suspend operator fun invoke() {
        runCatching { authRepository.logout() }
        sessionStore.clear()
    }
}
```

> Regla de negocio: el cierre de sesión **local** nunca depende de la red. Si el servidor falla (o la corrutina se cancela), la sesión igual se limpia.

#### Probar un caso de uso (fakes, sin Android)

Como el dominio no depende de Android, se prueba con JUnit/`kotlin.test` y `runTest` de `kotlinx-coroutines-test`, sin emulador:

```kotlin
class FakeAuthRepository(
    private val onLogin: suspend () -> Pair<AuthUser, SessionToken>
) : AuthRepository {
    override suspend fun login(creds: Credentials) = onLogin()
    override suspend fun logout() = Unit
}

class FakeSessionStore : SessionStore {
    var savedToken: SessionToken? = null
    override suspend fun save(session: SessionToken, user: AuthUser) { savedToken = session }
    override suspend fun clear() { savedToken = null }
    override suspend fun current(): Pair<AuthUser, SessionToken>? = null
}

class LoginUseCaseTest {

    private val user = AuthUser(id = "1", displayName = "Ana", email = "ana@mail.com")
    private val token = SessionToken(accessToken = "access")

    @Test
    fun `credenciales validas emiten Loading y Success y guardan la sesion`() = runTest {
        val store = FakeSessionStore()
        val useCase = LoginUseCase(FakeAuthRepository { user to token }, store, ValidateCredentialsUseCase())

        val states = useCase.execute("ana@mail.com", "123456").toList()

        assertEquals(listOf(DomainResult.Loading, DomainResult.Success(user)), states)
        assertEquals(token, store.savedToken)
    }

    @Test
    fun `un 401 del repositorio se refleja como Unauthorized`() = runTest {
        val useCase = LoginUseCase(
            FakeAuthRepository { throw AuthException(AuthDomainError.Unauthorized) },
            FakeSessionStore(),
            ValidateCredentialsUseCase()
        )

        val last = useCase.execute("ana@mail.com", "123456").toList().last()

        assertEquals(DomainResult.Error(AuthDomainError.Unauthorized), last)
    }
}
```

---

### 3) Separación de lógica de negocio y UI

#### Teoría (Clean)

-   **UI**: solo renderiza estados y emite eventos.
-   **ViewModel**: orquesta casos de uso, maneja estado de pantalla.
-   **Dominio**: resuelve reglas; nunca conoce Android/UI.

**Anti-patrones:**

-   Invocar repositorios directamente en Activities/Fragments.
-   Guardar tokens en la UI.
-   Mostrar mensajes crudos de excepciones técnicas.
-   Exponer el `MutableStateFlow` (se expone `StateFlow` con `asStateFlow()`).
-   Recolectar un Flow en Compose sin conocer el ciclo de vida (`collectAsState()`): usar `collectAsStateWithLifecycle()`.
-   Usar un resultado de dominio como estado inicial de la pantalla (p. ej. arrancar en `Loading`): el estado de UI tiene su propio `Idle`.

#### Ejemplo (Login)

**Estado de pantalla y ViewModel:**

El dominio responde con `DomainResult`; el ViewModel lo traduce a un **estado de UI** propio (que además tiene `Idle`: la pantalla existe antes de que el usuario pulse "Ingresar").

```kotlin
sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data class Success(val user: AuthUser) : LoginUiState
    data class Error(val error: AuthDomainError) : LoginUiState
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
    private val logoutUseCase: LogoutUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    fun login(email: String, password: String) {
        loginUseCase.execute(email, password)
            .onEach { result ->
                _uiState.value = result.fold(
                    onLoading = { LoginUiState.Loading },
                    onSuccess = { LoginUiState.Success(it) },
                    onError = { LoginUiState.Error(it) }
                )
            }
            .launchIn(viewModelScope)
    }

    fun logout() {
        viewModelScope.launch {
            logoutUseCase()
            _uiState.value = LoginUiState.Idle
        }
    }
}
```

> `fold` es la extensión de `DomainResult` que se define en la sección 4.

**Inyección sin ensuciar el dominio:**

Los casos de uso **no** llevan `@Inject`: el dominio sigue libre de frameworks y se construyen en un módulo de Hilt (capa Data/DI), igual que en `android-app-taxi`.

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AuthModule {

    @Provides
    @Singleton
    fun provideAuthRepository(api: AuthApi): AuthRepository = AuthRepositoryImpl(api)

    @Provides
    fun provideLoginUseCase(repo: AuthRepository, store: SessionStore): LoginUseCase =
        LoginUseCase(repo, store, ValidateCredentialsUseCase())

    @Provides
    fun provideLogoutUseCase(repo: AuthRepository, store: SessionStore): LogoutUseCase =
        LogoutUseCase(repo, store)
}
```

**UI (Compose) solo reacciona:**

-   `Idle` → mostrar el formulario.
-   `Loading` → mostrar spinner.
-   `Success(user)` → navegar a Home.
-   `Error(err)` → mostrar mensaje legible (el mapeo error → texto vive en la UI, con `strings.xml`, para poder traducirlo).

```kotlin
@Composable
fun LoginScreen(
    onLoggedIn: (AuthUser) -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        val current = state
        if (current is LoginUiState.Success) onLoggedIn(current.user)
    }

    when (val current = state) {
        LoginUiState.Idle -> LoginForm(errorRes = null, onSubmit = viewModel::login)
        LoginUiState.Loading -> CircularProgressIndicator()
        is LoginUiState.Success -> Unit
        is LoginUiState.Error -> LoginForm(errorRes = current.error.toMessageRes(), onSubmit = viewModel::login)
    }
}

@StringRes
private fun AuthDomainError.toMessageRes(): Int = when (this) {
    AuthDomainError.InvalidEmail -> R.string.error_invalid_email
    AuthDomainError.WeakPassword -> R.string.error_weak_password
    AuthDomainError.Unauthorized -> R.string.error_unauthorized
    AuthDomainError.Unexpected -> R.string.error_unexpected
}
```

> `LoginForm` es un composable sin estado (campos + botón) que no se muestra aquí. Al ser `AuthDomainError` una `sealed interface`, el `when` es **exhaustivo**: si mañana se agrega un error, el compilador obliga a mapearlo.

---

### 4) Uso de Flows en la capa de dominio

#### Teoría (Clean)

-   **Uso**: modelar procesos con múltiples estados (`Loading`, `Success`, `Error`).
-   **Beneficios**: asincronía, composición, testabilidad.
-   **Regla**: dominio no conoce `Dispatchers.Main`; tampoco fija dispatchers a mano (si un caso de uso necesita uno, se **inyecta**).
-   **Flow frío**: `flow { ... }` no ejecuta nada hasta que alguien hace `collect`; cada colector dispara su propia ejecución.
-   **Errores**: se manejan con el operador `catch`, que solo atrapa fallos **aguas arriba** (repositorio, store, validación).

**Por qué `catch` y no `try/catch` alrededor de `emit`:**

| Enfoque | Si el **colector** lanza una excepción al recibir `Success` |
| --- | --- |
| `try { ...; emit(Success) } catch (t: Throwable) { emit(Error) }` | Se captura y el `emit(Error)` lanza `IllegalStateException: Flow exception transparency is violated`, **enmascarando** la causa real |
| `flow { ... }.catch { emit(Error) }` | Se propaga intacta al colector |

-   Un `try/catch` que envuelve `emit(...)` atrapa también los errores de **quien colecta**, y el Flow lo prohíbe (transparencia de excepciones).
-   `catch (t: Throwable)` intercepta además la `CancellationException`. Con un `emit` posterior "funciona" porque este la vuelve a lanzar, pero no conviene depender de eso: el operador `catch` no toca la cancelación.

Ambos comportamientos (el del colector y la cancelación sin `Error`) están cubiertos con pruebas unitarias sobre el código de esta clase.

#### Ejemplo (Login)

Ya implementado en `LoginUseCase.execute(...)`.  
Extensión para consumo más limpio (devuelve un valor, así se usa como expresión, p. ej. para mapear a estado de UI):

```kotlin
inline fun <T, R> DomainResult<T>.fold(
    onLoading: () -> R,
    onSuccess: (T) -> R,
    onError: (AuthDomainError) -> R
): R = when (this) {
    is DomainResult.Loading -> onLoading()
    is DomainResult.Success -> onSuccess(data)
    is DomainResult.Error -> onError(cause)
}
```

---

### 5) Práctica: el ejemplo `android-app-taxi`

Esta carpeta trae el ejemplo completo: una app Android que inicia sesión contra un backend real.

| Proyecto | Qué es | Guía |
| --- | --- | --- |
| [`infra-app-taxi`](./infra-app-taxi) | MySQL, Redis y API con Docker Compose | [README](./infra-app-taxi/README.md) |
| [`backend-app-taxi`](./backend-app-taxi) | API NestJS: `POST /passenger/login` | [README](./backend-app-taxi/README.md) |
| [`android-app-taxi`](./android-app-taxi) | App Android (Compose + Hilt + Retrofit) | [README](./android-app-taxi/README.md) |

Orden para ejecutarlo: **infra → backend → app**.

**Dónde vive cada concepto de la clase en el ejemplo:**

| Concepto de la clase | En la teoría | En `android-app-taxi` (`app/src/main/java/com/example/android/…`) |
| --- | --- | --- |
| Contrato de repositorio | `AuthRepository` | `features/signin/domain/repository/AuthRepository.kt` |
| Contrato de sesión | `SessionStore` | `core/domain/SessionStore.kt` (implementación `SessionStoreEncryptedPrefs` en `core/data/`) |
| Modelos de dominio | `AuthUser`, `SessionToken` | `features/signin/domain/model/` |
| Caso de uso con Flow | `LoginUseCase` | `features/signin/domain/usecase/SignInWithPhoneUseCase.kt` |
| Estado emitido por el Flow | `DomainResult` | `SignInState` (en el mismo archivo del caso de uso) |
| Implementación del contrato | `AuthRepositoryImpl` | `features/signin/data/repository/AuthRepositoryImpl.kt` |
| Construcción de casos de uso (DI) | `AuthModule` | `features/signin/SignInModule.kt` |
| ViewModel | `LoginViewModel` | `features/signin/presentation/SignInViewModel.kt` |
| UI | `LoginScreen` | `features/signin/presentation/SignInScreen.kt` |

> El ejemplo usa **login por teléfono** (`SignInWithPhoneUseCase`) en vez de email/contraseña, pero la estructura es la misma. Una diferencia consciente: en el ejemplo `SignInState.Error` lleva el mensaje del servidor como texto para mantenerlo corto; la clase muestra la versión de producción, con **errores tipados** traducidos en Data y textos resueltos en la UI.

**Caso real de "cambiar la implementación sin tocar el dominio":** `androidx.security:security-crypto` (`EncryptedSharedPreferences`, `MasterKey`) quedó **deprecada** por Google. En el ejemplo se reemplazó por cifrado AES-256-GCM con una clave guardada en el **Android Keystore**, y lo único que cambió fue `SessionStoreEncryptedPrefs` en Data: el contrato `SessionStore`, el caso de uso y la UI quedaron intactos. Es exactamente lo que promete la regla "el dominio define contratos, Data los implementa".

**Stack del ejemplo (septiembre 2026)** — la fuente de verdad es el `gradle/libs.versions.toml` de cada proyecto:

| Componente | Versión |
| --- | --- |
| Android Gradle Plugin | 9.3.3 (Kotlin integrado) |
| Gradle | 9.7.1 |
| Kotlin / KSP | 2.4.20 / 2.3.12 |
| Compose BOM | 2026.09.00 |
| Lifecycle / Navigation Compose | 2.11.0 / 2.10.1 |
| Hilt / androidx.hilt | 2.60.1 / 1.4.0 |
| Retrofit / OkHttp | 3.0.0 / 5.5.0 |
| kotlinx-coroutines | 1.11.0 |
| compileSdk / targetSdk / minSdk | 37 / 37 / 29 |
| JDK | 17 |

**Backend e infra (septiembre 2026):**

| Componente | Versión |
| --- | --- |
| Node.js | 24 LTS (mínimo para compilar: 22.22.3) |
| pnpm | 12.5.1 (fijado en `packageManager`) |
| NestJS / TypeORM | 12 / 1.1 |
| TypeScript | 6.0 |
| MySQL / Redis (Docker) | 9.7 LTS / 8.10 |

> Node 24 incluye `corepack`, que instala solo el pnpm indicado en `package.json` (`corepack enable`). Si tienes un volumen de MySQL creado con la versión anterior (8.0.35), lee el aviso de migración en el README de `infra-app-taxi` antes de levantar la infra.

---

## Conclusiones

-   La **capa de dominio** es el núcleo: entidades, reglas y contratos.
-   Los **UseCases** representan acciones específicas como `Login` o `Logout`.
-   La **UI no contiene reglas**, solo refleja estados emitidos.
-   Los **Flows** permiten manejar procesos asincrónicos de manera clara y escalable.
-   Los **errores de dominio son tipados**: Data traduce lo técnico (HTTP, red) y la UI traduce lo tipado a texto; el dominio no conoce ninguno de los dos.
-   Un dominio sin Android se **prueba con fakes**, rápido y sin emulador.

---

## Diagramas

**Dependencias entre capas** (Presentation → Domain ← Data):

![Diagrama de capas](./_img/1.png)

> En el diagrama, `SessionStoreImpl` / *EncryptedPrefs* corresponde a `SessionStoreEncryptedPrefs` del ejemplo: SharedPreferences con valores cifrados por una clave del Android Keystore (ya no `EncryptedSharedPreferences`).

**Secuencia del login** (éxito y error 401):

![Diagrama de secuencia](./_img/2.png)

> En el diagrama, `intentLogin(...)` equivale a `LoginViewModel.login(...)` del código de esta clase.
