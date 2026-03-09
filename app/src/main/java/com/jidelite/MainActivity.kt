package com.jidelite

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory
import com.jidelite.ui.main.MainRoute
import com.jidelite.ui.main.MainViewModel
import com.jidelite.ui.theme.JIdeLiteTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val viewModelFactory = AndroidViewModelFactory.getInstance(application)
        val viewModel = ViewModelProvider(this, viewModelFactory)[MainViewModel::class.java]
        setContent {
            JIdeLiteTheme {
                MainRoute(viewModel = viewModel)
            }
        }
    }
}
