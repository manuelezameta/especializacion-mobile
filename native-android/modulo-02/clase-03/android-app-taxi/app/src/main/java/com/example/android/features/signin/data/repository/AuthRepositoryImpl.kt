package com.example.android.features.signin.data.repository

import com.example.android.features.signin.data.mapper.toDomain
import com.example.android.features.signin.data.remote.AuthApi
import com.example.android.features.signin.data.remote.dto.PassengerLoginRequest
import com.example.android.features.signin.domain.repository.AuthRepository
import com.example.android.features.signin.domain.repository.SignInResult
import com.google.gson.JsonParser
import retrofit2.HttpException
import java.io.IOException

class AuthRepositoryImpl(
    private val api: AuthApi
) : AuthRepository {

    override suspend fun signInWithPhone(phone: String): SignInResult {
        try {
            val res = api.login(PassengerLoginRequest(phone))
            return res.toDomain()
        } catch (e: HttpException) {
            throw RuntimeException(e.serverMessage() ?: "No se pudo iniciar sesión")
        } catch (e: IOException) {
            throw RuntimeException("No se pudo conectar con el servidor. Revisa tu conexión")
        } catch (t: Throwable) {
            throw RuntimeException(t.message ?: "Sign in failed")
        }
    }
}

private fun HttpException.serverMessage(): String? = runCatching {
    val body = response()?.errorBody()?.string().orEmpty()
    JsonParser.parseString(body).asJsonObject.get("message")
        ?.takeIf { it.isJsonPrimitive }
        ?.asString
}.getOrNull()?.takeIf { it.isNotBlank() }