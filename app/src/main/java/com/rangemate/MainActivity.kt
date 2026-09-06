package com.rangemate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.rangemate.di.PreferencesEntryPoint
import com.rangemate.ui.theme.RangeMateTheme
import com.rangemate.ui.navigation.RangeMateNavGraph
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Entry-point lookup from Application (SingletonComponent) to avoid
        // Dagger 2.52 Kotlin metadata issues during members-injection validation.
        val preferences = EntryPointAccessors
            .fromApplication(this, PreferencesEntryPoint::class.java)
            .preferencesManager()
        setContent {
            val globalSettings by preferences.globalPreferences.collectAsState(initial = null)
            RangeMateTheme(themeMode = globalSettings?.themeMode ?: "DARK") {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RangeMateNavGraph()
                }
            }
        }
    }
}
