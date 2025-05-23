package com.realityexpander.tasky.auth_feature.presentation.register_screen

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import com.ramcosta.composedestinations.navigation.EmptyDestinationsNavigator
import com.realityexpander.tasky.core.util.internetConnectivityObserver.IInternetConnectivityObserver
import com.realityexpander.tasky.R
import com.realityexpander.tasky.auth_feature.presentation.components.EmailField
import com.realityexpander.tasky.auth_feature.presentation.components.NameField
import com.realityexpander.tasky.auth_feature.presentation.components.PasswordField
import com.realityexpander.tasky.core.presentation.common.modifiers.*
import com.realityexpander.tasky.core.presentation.theme.TaskyShapes
import com.realityexpander.tasky.core.presentation.theme.TaskyTheme
import com.realityexpander.tasky.core.presentation.util.keyboardVisibilityObserver
import com.realityexpander.tasky.core.util.internetConnectivityObserver.InternetAvailabilityIndicator
import com.realityexpander.tasky.destinations.LoginScreenDestination

@Composable
@Destination
fun RegisterScreen(
    @Suppress("UNUSED_PARAMETER")  // extracted from navArgs in the viewModel
    username: String? = null,
    @Suppress("UNUSED_PARAMETER")  // extracted from navArgs in the viewModel
    email: String? = null,
    @Suppress("UNUSED_PARAMETER")  // extracted from navArgs in the viewModel
    password: String? = null,
    @Suppress("UNUSED_PARAMETER")  // extracted from navArgs in the viewModel
    confirmPassword: String? = null,
    navigator: DestinationsNavigator,
    viewModel: RegisterViewModel = hiltViewModel(),
) {
    val registerState by viewModel.registerState.collectAsState()
    val connectivityState by viewModel.onlineState.collectAsState(
        initial = IInternetConnectivityObserver.OnlineStatus.OFFLINE // must start as Offline
    )

    RegisterScreenContent(
        state = registerState,
        onAction = viewModel::sendEvent,
        navigator = navigator,
    )

    InternetAvailabilityIndicator(connectivityState)
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun RegisterScreenContent(
    state: RegisterState,
    onAction: (RegisterEvent) -> Unit,
    navigator: DestinationsNavigator,
) {
    val focusManager = LocalFocusManager.current
    val isKeyboardOpen by keyboardVisibilityObserver()

    fun performRegister() {
        onAction(RegisterEvent.Register(
            username = state.username,
            email = state.email,
            password = state.password,
            confirmPassword = state.confirmPassword,
        ))

        focusManager.clearFocus()
    }

    fun navigateToLogin() {
        navigator.navigate(
            LoginScreenDestination(
                username = state.username,  // saved here in case the user comes back to registration
                email = state.email,
                password = state.password,
                confirmPassword = state.confirmPassword  // saved here in case the comes goes back to registration
            )
        ) {
            popUpTo(LoginScreenDestination.route) {
                inclusive = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BackHandler(true) {
        navigateToLogin()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(color = MaterialTheme.colors.onSurface)
    ) col1@ {
        Spacer(modifier = Modifier.largeHeight())
        Text(
            text = stringResource(R.string.register_title),
            style = MaterialTheme.typography.h2,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.surface,
            modifier = Modifier
                .align(alignment = Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.mediumHeight())

        Column(
            modifier = Modifier
                .taskyScreenTopCorners(color = MaterialTheme.colors.surface)
                .verticalScroll(rememberScrollState())
                .weight(1f)
        ) col2@ {

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = DP.small, end = DP.small)
            ) colInnerScroll@{

                Spacer(modifier = Modifier.mediumHeight())

                // • USERNAME
                NameField(
                    value = state.username,
                    label = null,
                    isError = state.isInvalidUsername,
                    onValueChange = {
                        onAction(RegisterEvent.UpdateUsername(it))
                    }
                )
                AnimatedVisibility(state.isInvalidUsername && state.isInvalidUsernameMessageVisible) {
                    Text(text = stringResource(R.string.error_invalid_username), color = Color.Red)
                }
                Spacer(modifier = Modifier.smallHeight())

                // • EMAIL
                EmailField(
                    value = state.email,
                    label = null,
                    isError = state.isInvalidEmail,
                    onValueChange = {
                        onAction(RegisterEvent.UpdateEmail(it))
                    }
                )
                AnimatedVisibility(state.isInvalidEmail && state.isInvalidEmailMessageVisible) {
                    Text(text = stringResource(R.string.error_invalid_email), color = Color.Red)
                }
                Spacer(modifier = Modifier.smallHeight())

                // • PASSWORD
                PasswordField(
                    value = state.password,
                    label = null,
                    isError = state.isInvalidPassword,
                    onValueChange = {
                        onAction(RegisterEvent.UpdatePassword(it))
                    },
                    isPasswordVisible = state.isPasswordVisible,
                    clickTogglePasswordVisibility = {
                        onAction(RegisterEvent.SetIsPasswordVisible(!state.isPasswordVisible))
                    },
                    imeAction = ImeAction.Next,
                )
                if (state.isInvalidPassword && state.isInvalidPasswordMessageVisible) {
                    Text(text = stringResource(R.string.error_invalid_password), color = Color.Red)
                }
                Spacer(modifier = Modifier.smallHeight())

                // • CONFIRM PASSWORD
                PasswordField(
                    label = null, //stringResource(R.string.register_label_confirm_password),
                    placeholder = stringResource(R.string.register_placeholder_confirm_password),
                    value = state.confirmPassword,
                    isError = state.isInvalidConfirmPassword,
                    onValueChange = {
                        onAction(RegisterEvent.UpdateConfirmPassword(it))
                    },
                    isPasswordVisible = state.isPasswordVisible,
                    clickTogglePasswordVisibility = {
                        onAction(RegisterEvent.SetIsPasswordVisible(!state.isPasswordVisible))
                    },
                    imeAction = ImeAction.Done,
                    doneAction = {
                        performRegister()
                    },
                )
                AnimatedVisibility(state.isInvalidConfirmPassword && state.isInvalidConfirmPasswordMessageVisible) {
                    Text(
                        text = stringResource(R.string.error_invalid_confirm_password),
                        color = Color.Red
                    )
                    Spacer(modifier = Modifier.extraSmallHeight())
                }
                // • SHOW IF MATCHING PASSWORDS
                AnimatedVisibility(!state.isPasswordsMatch) {
                    Text(
                        text = stringResource(R.string.register_error_passwords_do_not_match),
                        color = Color.Red
                    )
                    Spacer(modifier = Modifier.extraSmallHeight())
                }
                // • SHOW PASSWORD REQUIREMENTS
                AnimatedVisibility(state.isInvalidPasswordMessageVisible || state.isInvalidConfirmPasswordMessageVisible) {
                    Text(
                        text = stringResource(R.string.register_password_requirements),
                        color = Color.Red
                    )
                    Spacer(modifier = Modifier.extraSmallHeight())
                }
                Spacer(modifier = Modifier.mediumHeight())

                // • REGISTER BUTTON
                Button(
                    onClick = {
                        performRegister()
                    },
                    enabled = !state.isLoading,
                    modifier = Modifier
                        .taskyWideButton(color = MaterialTheme.colors.primary)
                        .align(alignment = Alignment.CenterHorizontally)
                ) {
                    Text(
                        text = stringResource(R.string.register_button),
                        fontSize = MaterialTheme.typography.button.fontSize,
                    )
                    if (state.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(start = DP.small)
                                .size(16.dp)
                                .align(alignment = Alignment.CenterVertically)
                        )
                    }
                }
                Spacer(modifier = Modifier.mediumHeight())

                // STATUS //////////////////////////////////////////

                AnimatedVisibility(state.errorMessage != null) {
                    state.errorMessage?.getOrNull?.let { errorMessage ->
                        Spacer(modifier = Modifier.extraSmallHeight())
                        Text(
                            text = errorMessage,
                            color = Color.Red,
                            modifier = Modifier
                                .animateContentSize()
                        )
                        Spacer(modifier = Modifier.extraSmallHeight())
                    }
                }
                AnimatedVisibility(state.statusMessage != null) {
                    state.statusMessage?.getOrNull?.let { message ->
                        Spacer(modifier = Modifier.extraSmallHeight())
                        Text(text = message)
                        Spacer(modifier = Modifier.extraSmallHeight())
                    }
                }
            }


            // • GO TO LOGIN BUTTON
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = DP.small, bottom = DP.small)
                    .weight(1f)
            ) {
                this@col1.AnimatedVisibility(
                    visible = !isKeyboardOpen,
                    enter = fadeIn() + slideInVertically(
                        initialOffsetY = { it }
                    ),
                    exit = fadeOut(),
                    modifier = Modifier
                        .background(color = MaterialTheme.colors.surface)
                        .align(alignment = Alignment.BottomStart)
                ) {
                    // • BACK TO LOGIN BUTTON
                    CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) { // allows smaller touch-targets
                        IconButton(
                            onClick = {
                                navigateToLogin()
                            },
                            modifier = Modifier
                                .size(DP.XXLarge)
                                .clip(shape = TaskyShapes.MediumButtonRoundedCorners)
                                .background(color = MaterialTheme.colors.onSurface)
                                .align(alignment = Alignment.BottomStart)
                        ) {
                                Icon(
                                    tint = MaterialTheme.colors.surface,
                                    imageVector = Icons.Filled.ChevronLeft,
                                    contentDescription = stringResource(R.string.register_description_back),
                                    modifier = Modifier
                                        .align(alignment = Alignment.Center)
                                        .size(DP.XXLarge)
                                        .padding(start = 9.dp, end = 9.dp) // fine tunes the icon size (weird)
                                )
                            }
                    }
                }
            }
        }
    }
}


//    // • BACK TO LOG IN BUTTON (alternate design) leave for reference
//    Text(
//        text = stringResource(R.string.register_already_a_member_sign_in),
//        style = MaterialTheme.typography.body2,
//        color = MaterialTheme.colors.primaryVariant,
//        modifier = Modifier
//            .align(alignment = Alignment.CenterHorizontally)
//            .clickable(onClick = {
//                navigateToLogin()
//            })
//    )


@Composable
@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    group= "Night Mode=true",
    heightDp = 600
)
fun RegisterScreenPreview() {
    TaskyTheme {
        Surface {
            RegisterScreenContent(
                navigator = EmptyDestinationsNavigator,
                state = RegisterState(
                    email = "chris@demo.com",
                    password = "1234567Az",
                    confirmPassword = "1234567Az",
                    isInvalidEmail = false,
                    isInvalidPassword = false,
                    isInvalidConfirmPassword = false,
                    isPasswordsMatch = true,
                    isInvalidEmailMessageVisible = false,
                    isInvalidPasswordMessageVisible = false,
                    isInvalidConfirmPasswordMessageVisible = false,
                    isPasswordVisible = false,
                    isLoading = false,
                    errorMessage = null,  // UiText.Res(R.string.error_invalid_email),
                    statusMessage = null, // UiText.Res(R.string.login_logged_in),
                    authInfo = null,
                ),
                onAction = {},
            )
        }
    }
}

@Composable
@Preview(
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_NO,
    group="Night Mode=false",
    heightDp = 600
)
fun RegisterScreenPreview_NightMode_NO() {
    RegisterScreenPreview()
}
