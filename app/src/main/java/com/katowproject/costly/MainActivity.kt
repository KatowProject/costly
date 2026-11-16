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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val viewModel = model
        setContent {
            CostlyTheme {
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
    override fun onStop() { super.onStop(); locked = true }
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
