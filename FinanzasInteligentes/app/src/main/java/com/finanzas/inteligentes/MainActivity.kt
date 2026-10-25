package com.finanzas.inteligentes

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.finanzas.inteligentes.data.AppState
import com.finanzas.inteligentes.data.Security
import com.finanzas.inteligentes.ui.AppRoot
import com.finanzas.inteligentes.ui.LockScreen
import com.finanzas.inteligentes.ui.MainViewModel

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) Security.load(this)
        setContent {
            FinanzasTheme {
                val vm: MainViewModel = viewModel()
                if (AppState.locked) LockScreen(this@MainActivity) else AppRoot(vm)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (AppState.pinSet) AppState.locked = true
    }
}

@Composable
fun FinanzasTheme(content: @Composable () -> Unit) {
    val dark = when (AppState.themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(ctx)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
