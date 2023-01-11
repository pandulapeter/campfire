package com.pandulapeter.campfire.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.pandulapeter.campfire.domain.api.useCases.GetScreenDataUseCase
import com.pandulapeter.campfire.domain.api.useCases.LoadScreenDataUseCase
import com.pandulapeter.campfire.shared.describe
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.inject

@Composable
fun CampfireApp() {
    val getScreenData by inject<GetScreenDataUseCase>(GetScreenDataUseCase::class.java)
    val loadScreenData by inject<LoadScreenDataUseCase>(LoadScreenDataUseCase::class.java)

    val screenData by getScreenData()
        .map { it.describe() }
        .collectAsState(initial = null)

    rememberCoroutineScope().launch { loadScreenData(false) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "$screenData",
            style = TextStyle(fontSize = 24.sp)
        )
    }
}