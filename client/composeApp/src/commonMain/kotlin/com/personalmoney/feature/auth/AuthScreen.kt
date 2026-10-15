package com.personalmoney.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.personalmoney.core.ui.verticalScrollbar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalmoney.core.auth.ServerCheck
import com.personalmoney.core.ui.theme.Accent
import com.personalmoney.core.ui.webChrome
import com.personalmoney.core.ui.theme.LocalFinanceColors
import com.personalmoney.core.ui.theme.PmButtonShape
import com.personalmoney.core.ui.theme.StatusCyan
import com.personalmoney.core.ui.theme.StatusPurple
import com.personalmoney.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Sign-in and registration, laid out like the web client's `AuthShell` + `Login`.
 *
 * This is the first screen anyone sees, so it is the one that decides whether the phone
 * and the browser look like the same product. It follows the web app closely: the drifting
 * colour wash behind everything, the gradient brand mark, a heavy centred title, and a
 * translucent card holding labelled fields with leading icons.
 *
 * Still one screen with a toggle rather than the web app's two routes. Two forms differing
 * by a pair of name fields is one form; what mattered was the look, not the URL.
 */
@Composable
fun AuthScreen(
    onSignedIn: () -> Unit,
    viewModel: AuthViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.signedIn) {
        if (state.signedIn) onSignedIn()
    }

    // A Surface rather than a plain Box: Surface is what publishes `LocalContentColor`,
    // and without it every Text that does not name a colour falls back to black - which
    // looks correct on the light theme and is invisible on the dark one.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            AuthBackdrop()

            // Inset the content, not the backdrop: the gradient behind should still run
            // under the status bar and the gesture pill, which is the point of drawing
            // edge to edge. Without this the wordmark shared a line with the clock - the
            // screen has no Scaffold to apply insets for it, and MainActivity's
            // enableEdgeToEdge() means nobody else will either.
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                // The site header already carries the wordmark on the web, and drawing both
                // put it on the page twice. This row exists for the platforms where sign-in
                // has no shell around it at all.
                if (!webChrome) BrandRow()

                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScrollbar(scrollState)
                        .verticalScroll(scrollState)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Column(
                        // The web app widens the column for registration, where the name
                        // fields make the card taller and a narrow one starts to look like a
                        // ladder.
                        modifier = Modifier.widthIn(max = if (state.registerMode) 480.dp else 420.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AuthHeading(registerMode = state.registerMode)
                        Spacer(Modifier.height(28.dp))
                        AuthCard(state = state, viewModel = viewModel)
                        Spacer(Modifier.height(28.dp))
                        // A server that has closed registration has nothing to switch to, and
                        // until an address has been checked there is no way to know. In
                        // register mode the toggle stays regardless, or a closed server would
                        // leave no way back to signing in.
                        val instance = state.instance
                        if (state.registerMode || instance == null || instance.registrationOpen || instance.setupRequired) {
                            TextButton(onClick = viewModel::toggleMode, enabled = !state.busy) {
                                Text(
                                    stringResource(
                                        if (state.registerMode) Res.string.auth_have_account
                                        else Res.string.auth_need_account
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    // The scheme's accent, not the palette's light step: that
                                    // one is the indigo picked to carry on near-black, and on
                                    // the light theme's page it is 3.0:1 - a link that reads as
                                    // disabled. `primary` is the same colour in dark and the
                                    // darker step in light.
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The colour wash behind the form.
 *
 * Three soft radial washes, the same three the web app blurs into its background. Drawn as
 * gradients with an alpha falloff rather than as blurred circles: `Modifier.blur` is a
 * no-op below Android 12, and a decorative background that appears on some phones and not
 * others is worse than one that is slightly less diffuse everywhere.
 */
@Composable
private fun AuthBackdrop() {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Accent.copy(alpha = 0.20f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(0f, 0f),
                        radius = 900f,
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(StatusCyan.copy(alpha = 0.15f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                        radius = 950f,
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(StatusPurple.copy(alpha = 0.10f), Color.Transparent),
                        radius = 700f,
                    )
                )
        )
    }
}

/** The wordmark, top left, exactly as the web app's shell draws it. */
@Composable
private fun BrandRow() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrandMark(size = 32.dp, iconSize = 16.dp, corner = 10.dp)
        Spacer(Modifier.size(10.dp))
        val brand = MaterialTheme.colorScheme.primary
        Text(
            buildAnnotatedString {
                append("Personal")
                withStyle(SpanStyle(color = brand)) { append("Money") }
            },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Indigo to cyan, the web app's `from-brand to-info`. */
@Composable
private fun BrandMark(size: androidx.compose.ui.unit.Dp, iconSize: androidx.compose.ui.unit.Dp, corner: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(Brush.linearGradient(listOf(Accent, StatusCyan))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.TrendingUp,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun AuthHeading(registerMode: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BrandMark(size = 56.dp, iconSize = 28.dp, corner = 16.dp)
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(
                if (registerMode) Res.string.auth_register_title else Res.string.auth_login_title
            ),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(
                if (registerMode) Res.string.auth_register_subtitle
                else Res.string.auth_login_subtitle
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AuthCard(state: AuthUiState, viewModel: AuthViewModel) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large),
        shape = MaterialTheme.shapes.large,
        // Slightly translucent, so the wash behind carries through the card the way the
        // web app's `bg-card/85` does.
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
    ) {
        Column(
            Modifier.padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            LabelledField(
                label = stringResource(Res.string.auth_server_label),
                value = state.serverAddress,
                onValueChange = viewModel::onServerAddressChange,
                icon = Icons.Outlined.Dns,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                hint = stringResource(Res.string.auth_server_hint),
            )
            if (state.cleartext) {
                Text(
                    stringResource(Res.string.auth_server_cleartext_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            state.serverProblem?.let { problem ->
                ErrorAlert(
                    when (problem) {
                        ServerCheck.Invalid -> stringResource(Res.string.auth_server_invalid)
                        ServerCheck.Unreachable -> stringResource(Res.string.auth_server_unreachable)
                        ServerCheck.NotPersonalMoney -> stringResource(Res.string.auth_server_not_personalmoney)
                        is ServerCheck.Unsupported -> stringResource(Res.string.auth_server_unsupported, problem.apiVersion)
                        is ServerCheck.Ok -> ""
                    }
                )
            }

            if (state.registerMode) {
                LabelledField(
                    label = stringResource(Res.string.auth_first_name),
                    value = state.firstName,
                    onValueChange = viewModel::onFirstNameChange,
                    icon = Icons.Outlined.Badge,
                )
                LabelledField(
                    label = stringResource(Res.string.auth_last_name),
                    value = state.lastName,
                    onValueChange = viewModel::onLastNameChange,
                    icon = Icons.Outlined.Badge,
                )
            }

            LabelledField(
                label = stringResource(Res.string.auth_email),
                value = state.email,
                onValueChange = viewModel::onEmailChange,
                icon = Icons.Outlined.Mail,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
            )

            LabelledField(
                label = stringResource(Res.string.auth_password),
                value = state.password,
                onValueChange = viewModel::onPasswordChange,
                icon = Icons.Outlined.Lock,
                masked = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                hint = if (state.registerMode) {
                    stringResource(
                        Res.string.auth_password_hint,
                        AuthUiState.MIN_PASSWORD_LENGTH,
                    )
                } else null,
            )

            state.error?.let { ErrorAlert(it) }

            Button(
                onClick = viewModel::submit,
                enabled = state.canSubmit,
                shape = PmButtonShape,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (state.busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(stringResource(Res.string.auth_signing_in))
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Login,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        stringResource(
                            if (state.registerMode) Res.string.auth_create_account
                            else Res.string.auth_sign_in
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/**
 * A label above the field, not floating inside it.
 *
 * Material's floating label animates into the border and takes the placeholder's place;
 * the web app puts a small static label above and keeps a placeholder underneath. Both
 * are visible at once there, which is the difference worth reproducing.
 */
@Composable
private fun LabelledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    icon: ImageVector,
    masked: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    hint: String? = null,
) {
    val finance = LocalFinanceColors.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            shape = PmButtonShape,
            leadingIcon = {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = finance.muted)
            },
            visualTransformation = if (masked) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = keyboardOptions,
            colors = OutlinedTextFieldDefaults.colors(
                // The field sits a step *below* the card, as `.form-control` does against
                // `.card` - the input is the recess, not another raised surface.
                unfocusedContainerColor = MaterialTheme.colorScheme.background,
                focusedContainerColor = MaterialTheme.colorScheme.background,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        hint?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = finance.muted)
        }
    }
}

/** The web app's `.alert-error`: a tinted panel with a warning glyph, not bare red text. */
@Composable
private fun ErrorAlert(message: String) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(16.dp),
            )
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}
