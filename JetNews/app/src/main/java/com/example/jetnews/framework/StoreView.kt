package com.example.jetnews.framework

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class StoreHolder<State, Action>(
    private val store: Store<State, Action>,
    private val coroutineScope: CoroutineScope
){
    fun sendToStore(action:Action):() -> Unit = {
        coroutineScope.launch {
            store.send(action = action, coroutineScope)
        }
    }

    fun <T> sendToStore(action:(T) -> Action):(T) -> Unit = { value ->
        coroutineScope.launch {
            store.send(action = action(value), coroutineScope)
        }
    }
}

@Composable
fun <State, Action> StoreView(
    store: Store<State, Action>,
    viewBody:@Composable StoreHolder<State, Action>.(State) -> Unit
){
    val state by store.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    StoreHolder(store, coroutineScope).viewBody(state)
}