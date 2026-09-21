package com.example.android.features.signin.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.android.R
import com.example.android.commons.presentation.NavigationBarStyle
import com.example.android.commons.presentation.PhoneInputField
import com.example.android.commons.presentation.PrimaryButton
import com.example.android.commons.presentation.ToastHost
import com.example.android.commons.presentation.ToastMessage
import com.example.android.commons.presentation.ToastType
import com.example.android.features.signin.domain.usecase.SignInState
import kotlinx.coroutines.delay

private const val TOAST_DURATION_MS = 3_000L

@Composable
fun SignInPhoneScreen(
    onGoHome: (String) -> Unit,
    onGoSignUp: () -> Unit,
    modifier: Modifier = Modifier,
    vm: SignInViewModel = hiltViewModel()
) {
    var phone by remember { mutableStateOf("") }
    val state by vm.ui.collectAsStateWithLifecycle()

    LaunchedEffect(state) {
        when (val s = state) {
            is SignInState.Success -> onGoHome(s.welcomeName)
            else -> Unit
        }
    }

    LaunchedEffect(state) {
        if (state is SignInState.Success || state is SignInState.Error) {
            delay(TOAST_DURATION_MS)
            vm.reset()
        }
    }

    SignInPhoneContent(
        state = state,
        phone = phone,
        onPhoneChange = { phone = it },
        onSubmit = vm::submit,
        modifier = modifier
    )
}

@Composable
fun SignInPhoneContent(
    state: SignInState,
    phone: String,
    onPhoneChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = Color.White
    val normalized = remember(phone) { phone.filter(Char::isDigit) }
    val isValid = normalized.length == 9
    val loading = state is SignInState.Loading
    val toast = when (state) {
        is SignInState.Success -> ToastMessage(ToastType.Success, "¡Bienvenido, ${state.welcomeName}!")
        is SignInState.Error -> ToastMessage(ToastType.Error, state.message)
        else -> null
    }

    NavigationBarStyle(darkIcons = true)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
    ) {
        Image(
            painter = androidx.compose.ui.res.painterResource(id = R.drawable.feature_signin_picture),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                .padding(top = 24.dp)
                .align(Alignment.TopCenter)
        )

        // Tarjeta inferior
        Surface(
            color = Color(0xFFF0F0F0),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            shadowElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(
                    WindowInsets.navigationBars
                        .union(WindowInsets.ime)
                        .only(WindowInsetsSides.Bottom)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = buildAnnotatedString {
                        append("Validaremos tu ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("identidad") }
                        append(", con tu número de teléfono")
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF0F0F0F)
                )

                PhoneInputField(
                    value = phone,
                    onValueChange = onPhoneChange,
                    placeholder = "Ingresa tu número de teléfono",
                    modifier = Modifier.padding(top = 4.dp)
                )

                PrimaryButton(
                    text = "Ingresar",
                    onClick = { onSubmit(normalized) },
                    enabled = isValid && !loading,
                    loading = loading,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        ToastHost(
            toast = toast,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun SignInPhoneScreenPreviewEmpty() {
    SignInPhoneContent(
        state = SignInState.Idle,
        phone = "",
        onPhoneChange = {},
        onSubmit = {}
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun SignInPhoneScreenPreviewFilled() {
    SignInPhoneContent(
        state = SignInState.Idle,
        phone = "987654321",
        onPhoneChange = {},
        onSubmit = {}
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun SignInPhoneScreenPreviewLoading() {
    SignInPhoneContent(
        state = SignInState.Loading,
        phone = "987654321",
        onPhoneChange = {},
        onSubmit = {}
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun SignInPhoneScreenPreviewSuccess() {
    SignInPhoneContent(
        state = SignInState.Success("Jorge"),
        phone = "987654321",
        onPhoneChange = {},
        onSubmit = {}
    )
}

@Preview(showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun SignInPhoneScreenPreviewError() {
    SignInPhoneContent(
        state = SignInState.Error("Tu número de teléfono no existe."),
        phone = "987654321",
        onPhoneChange = {},
        onSubmit = {}
    )
}
