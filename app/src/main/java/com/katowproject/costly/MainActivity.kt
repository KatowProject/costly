package com.katowproject.costly

import android.os.Bundle
import android.os.CancellationSignal
import android.hardware.biometrics.BiometricPrompt
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.lifecycle.ViewModelProvider
import com.katowproject.costly.ui.theme.CostlyTheme

class MainActivity : ComponentActivity() {
    private val model by lazy { ViewModelProvider(this)[FinanceViewModel::class.java] }
    private var locked by mutableStateOf(true)
    private var lastBackgroundTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val viewModel = model
        setContent {
            val themeMode = viewModel.data.settings["theme_mode"] ?: "system"
            val isDark = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            CostlyTheme(darkTheme = isDark) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val currentStrings = remember(viewModel.data.settings["language"]) {
                    LocalizationManager.getStrings(context, viewModel.data.settings["language"])
                }
                val currentCurrency = remember(viewModel.data.settings["currency"], currentStrings.language) {
                    LocalizationManager.getCurrency(viewModel.data.settings["currency"], currentStrings.language.defaultCurrency)
                }
                CompositionLocalProvider(
                    LocalStrings provides currentStrings,
                    LocalCurrency provides currentCurrency
                ) {
                    val savedState = rememberSaveableStateHolder()
                    if (viewModel.ready && viewModel.data.settings["app_lock"] == "true" && locked) {
                        LockScreen(viewModel, onUnlock = { locked = false }, onBiometric = { authenticate(currentStrings) })
                    } else savedState.SaveableStateProvider("app") { CostlyApp(viewModel) }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (model.data.settings["app_lock"] == "true") {
            val timeoutSeconds = model.data.settings["lock_timeout"]?.toLongOrNull() ?: 60L
            val elapsed = System.currentTimeMillis() - lastBackgroundTime
            if (lastBackgroundTime > 0L && elapsed > timeoutSeconds * 1000L) {
                locked = true
            }
        }
    }

    override fun onStop() {
        super.onStop()
        lastBackgroundTime = System.currentTimeMillis()
    }
    private fun authenticate(strings: AppStrings) {
        BiometricPrompt.Builder(this)
            .setTitle(strings.biometricTitle)
            .setSubtitle(strings.biometricSubtitle)
            .setNegativeButton(strings.usePinButton, mainExecutor) { _, _ -> }
            .build().authenticate(CancellationSignal(), mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) { locked = false }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) { model.message = strings.biometricUnavailable }
            })
    }
}
