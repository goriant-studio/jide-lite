package com.jidelite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import com.jidelite.ui.main.MainRoute
import com.jidelite.ui.main.MainViewModel
import com.jidelite.ui.theme.JIdeLiteTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        setContent {
            JIdeLiteTheme {
                MainRoute(viewModel = viewModel)
            }
        }
    }
}
