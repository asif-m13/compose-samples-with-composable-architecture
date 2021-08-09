/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.jetnews.ui.home

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.ScaffoldState
import androidx.compose.material.SnackbarResult
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.rememberScaffoldState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import arrow.core.andThen
import arrow.optics.Optional
import arrow.optics.optics
import com.example.jetnews.R
import com.example.jetnews.data.Result
import com.example.jetnews.data.posts.PostsRepository
import com.example.jetnews.data.posts.impl.BlockingFakePostsRepository
import com.example.jetnews.framework.Reducer
import com.example.jetnews.framework.Store
import com.example.jetnews.framework.StoreView
import com.example.jetnews.framework.forEach
import com.example.jetnews.model.Post
import com.example.jetnews.ui.components.InsetAwareTopAppBar
import com.example.jetnews.ui.state.UiState
import com.example.jetnews.ui.theme.JetnewsTheme
import com.example.jetnews.utils.produceUiState
import com.example.jetnews.utils.supportWideScreen
import com.google.accompanist.insets.LocalWindowInsets
import com.google.accompanist.insets.rememberInsetsPaddingValues
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Stateful HomeScreen which manages state using [produceUiState]
 *
 * @param postsRepository data source for this screen
 * @param navigateToArticle (event) request navigation to Article screen
 * @param openDrawer (event) request opening the app drawer
 * @param scaffoldState (state) state for the [Scaffold] component on this screen
 */
@Composable
fun HomeScreen(
    postsRepository: PostsRepository,
    navigateToArticle: (String) -> Unit,
    openDrawer: () -> Unit,
    scaffoldState: ScaffoldState = rememberScaffoldState()
) {
    val (postUiState, refreshPost, clearError) = produceUiState(postsRepository) {
        getPosts()
    }

    // [collectAsState] will automatically collect a Flow<T> and return a State<T> object that
    // updates whenever the Flow emits a value. Collection is cancelled when [collectAsState] is
    // removed from the composition tree.
    val favorites by postsRepository.observeFavorites().collectAsState(setOf())

    // Returns a [CoroutineScope] that is scoped to the lifecycle of [HomeScreen]. When this
    // screen is removed from composition, the scope will be cancelled.
    val coroutineScope = rememberCoroutineScope()

    HomeScreen(
        posts = postUiState.value,
        favorites = favorites,
        onToggleFavorite = {
            coroutineScope.launch { postsRepository.toggleFavorite(it) }
        },
        onRefreshPosts = refreshPost,
        onErrorDismiss = clearError,
        navigateToArticle = navigateToArticle,
        openDrawer = openDrawer,
        scaffoldState = scaffoldState
    )
}

/**
 * Responsible for displaying the Home Screen of this application.
 *
 * Stateless composable is not coupled to any specific state management.
 *
 * @param posts (state) the data to show on the screen
 * @param favorites (state) favorite posts
 * @param onToggleFavorite (event) toggles favorite for a post
 * @param onRefreshPosts (event) request a refresh of posts
 * @param onErrorDismiss (event) request the current error be dismissed
 * @param navigateToArticle (event) request navigation to Article screen
 * @param openDrawer (event) request opening the app drawer
 * @param scaffoldState (state) state for the [Scaffold] component on this screen
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
    posts: UiState<List<Post>>,
    favorites: Set<String>,
    onToggleFavorite: (String) -> Unit,
    onRefreshPosts: () -> Unit,
    onErrorDismiss: () -> Unit,
    navigateToArticle: (String) -> Unit,
    openDrawer: () -> Unit,
    scaffoldState: ScaffoldState
) {
    val coroutineScope = rememberCoroutineScope()

    val store = remember {
        Store.of(
            state = HomeScreenState(openDrawer = false),
            reducer = HomeScreenReducer,
            environment = HomeScreenEnvironment(
                openDrawer = {
                    flow {
                        openDrawer()
                        emit(Unit)
                    }
                }
            )
        )
    }

    StoreView(store = store) { state ->
        if (posts.hasError) {
            val errorMessage = stringResource(id = R.string.load_error)
            val retryMessage = stringResource(id = R.string.retry)

            // If onRefreshPosts or onErrorDismiss change while the LaunchedEffect is running,
            // don't restart the effect and use the latest lambda values.
            val onRefreshPostsState by rememberUpdatedState(onRefreshPosts)
            val onErrorDismissState by rememberUpdatedState(onErrorDismiss)

            // Show snackbar using a coroutine, when the coroutine is cancelled the snackbar will
            // automatically dismiss. This coroutine will cancel whenever posts.hasError is false
            // (thanks to the surrounding if statement) or if scaffoldState.snackbarHostState changes.
            LaunchedEffect(scaffoldState.snackbarHostState) {
                val snackbarResult = scaffoldState.snackbarHostState.showSnackbar(
                    message = errorMessage,
                    actionLabel = retryMessage
                )
                when (snackbarResult) {
                    SnackbarResult.ActionPerformed -> onRefreshPostsState()
                    SnackbarResult.Dismissed -> onErrorDismissState()
                }
            }
        }

        Scaffold(
            scaffoldState = scaffoldState,
            topBar = {
                val title = stringResource(id = R.string.app_name)
                InsetAwareTopAppBar(
                    title = { Text(text = title) },
                    navigationIcon = {
                        IconButton(onClick = sendToStore(HomeScreenAction.OpenDrawer)) {
                            Icon(
                                painter = painterResource(R.drawable.ic_jetnews_logo),
                                contentDescription = stringResource(R.string.cd_open_navigation_drawer)
                            )
                        }
                    }
                )
            }
        ) { innerPadding ->
            val modifier = Modifier.padding(innerPadding)
            LoadingContent(
                empty = posts.initialLoad,
                emptyContent = { FullScreenLoading() },
                loading = posts.loading,
                onRefresh = onRefreshPosts,
                content = {
                    HomeScreenErrorAndContent(
                        posts = posts,
                        onRefresh = {
                            onRefreshPosts()
                        },
                        navigateToArticle = navigateToArticle,
                        favorites = favorites,
                        onToggleFavorite = onToggleFavorite,
                        modifier = modifier.supportWideScreen()
                    )
                }
            )
        }
    }
}

/**
 * Display an initial empty state or swipe to refresh content.
 *
 * @param empty (state) when true, display [emptyContent]
 * @param emptyContent (slot) the content to display for the empty state
 * @param loading (state) when true, display a loading spinner over [content]
 * @param onRefresh (event) event to request refresh
 * @param content (slot) the main content to show
 */
@Composable
private fun LoadingContent(
    empty: Boolean,
    emptyContent: @Composable () -> Unit,
    loading: Boolean,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit
) {
    if (empty) {
        emptyContent()
    } else {
        SwipeRefresh(
            state = rememberSwipeRefreshState(loading),
            onRefresh = onRefresh,
            content = content,
        )
    }
}

/**
 * Responsible for displaying any error conditions around [PostList].
 *
 * @param posts (state) list of posts and error state to display
 * @param onRefresh (event) request to refresh data
 * @param navigateToArticle (event) request navigation to Article screen
 * @param favorites (state) all favorites
 * @param onToggleFavorite (event) request a single favorite be toggled
 * @param modifier modifier for root element
 */
@Composable
private fun HomeScreenErrorAndContent(
    posts: UiState<List<Post>>,
    onRefresh: () -> Unit,
    navigateToArticle: (String) -> Unit,
    favorites: Set<String>,
    onToggleFavorite: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (posts.data != null) {
        PostList(posts.data, navigateToArticle, favorites, onToggleFavorite, modifier)
    } else if (!posts.hasError) {
        // if there are no posts, and no error, let the user refresh manually
        TextButton(onClick = onRefresh, modifier.fillMaxSize()) {
            Text(stringResource(id = R.string.home_tap_to_load_content), textAlign = TextAlign.Center)
        }
    } else {
        // there's currently an error showing, don't show any content
        Box(modifier.fillMaxSize()) { /* empty screen */ }
    }
}

/**
 * Display a list of posts.
 *
 * When a post is clicked on, [navigateToArticle] will be called to navigate to the detail screen
 * for that post.
 *
 * @param posts (state) the list to display
 * @param navigateToArticle (event) request navigation to Article screen
 * @param modifier modifier for the root element
 */
@Composable
private fun PostList(
    posts: List<Post>,
    navigateToArticle: (postId: String) -> Unit,
    favorites: Set<String>,
    onToggleFavorite: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val postTop = posts[3]
    val postsSimple = posts.subList(0, 2)
    val postsPopular = posts.subList(2, 7)
    val postsHistory = posts.subList(7, 10)

    LazyColumn(
        modifier = modifier,
        contentPadding = rememberInsetsPaddingValues(
            insets = LocalWindowInsets.current.systemBars,
            applyTop = false
        )
    ) {
        val topSectionStore = Store.of(
            state = PostListTopSectionState(
                post = postTop
            ),
            reducer = PostListTopSectionReducer,
            environment = PostListTopSectionEnvironment(
                navigateToArticle = {id -> flow{
                    navigateToArticle(id)
                    emit(Unit)
                }}
            )
        )
        item { PostListTopSection(topSectionStore) }
        item { PostListSimpleSection(postsSimple, navigateToArticle, favorites, onToggleFavorite) }
        item { PostListPopularSection(postsPopular, navigateToArticle) }
        item { PostListHistorySection(postsHistory, navigateToArticle) }
    }
}

/**
 * Full screen circular progress indicator
 */
@Composable
private fun FullScreenLoading() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .wrapContentSize(Alignment.Center)
    ) {
        CircularProgressIndicator()
    }
}

/**
 * Top section of [PostList]
 *
 * @param post (state) highlighted post to display
 * @param navigateToArticle (event) request navigation to Article screen
 */
@Composable
private fun PostListTopSection(store:Store<PostListTopSectionState, PostListTopSectionAction>) {
    StoreView(store) { state ->
        Text(
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            text = stringResource(id = R.string.home_top_section_title),
            style = MaterialTheme.typography.subtitle1
        )
        PostCardTop(
            post = state.post,
            modifier = Modifier.clickable(onClick = { sendToStore(PostListTopSectionAction.Clicked(state.post.id))() })
        )
        PostListDivider()
    }
}

data class PostListTopSectionState(val post:Post)

sealed class PostListTopSectionAction{
    data class Clicked(val id: String):PostListTopSectionAction()
    object None:PostListTopSectionAction()
}

class PostListTopSectionEnvironment(
    val navigateToArticle: (String) -> Flow<Unit>
)


val PostListTopSectionReducer:Reducer<PostListTopSectionState, PostListTopSectionAction, PostListTopSectionEnvironment> = {
    state, action, env, _ ->
    when(action){
        is PostListTopSectionAction.Clicked -> Pair(
            state,
            env
                .navigateToArticle(action.id)
                .flowOn(Dispatchers.Main)
                .map { PostListTopSectionAction.None }
        )

        PostListTopSectionAction.None -> Pair(
            state,
            emptyFlow()
        )
    }
}

/**
 * Full-width list items for [PostList]
 *
 * @param posts (state) to display
 * @param navigateToArticle (event) request navigation to Article screen
 */
@Composable
private fun PostListSimpleSection(
    posts: List<Post>,
    navigateToArticle: (String) -> Unit,
    favorites: Set<String>,
    onToggleFavorite: (String) -> Unit
) {

    val postListSimpleSectionState = PostListSimpleSectionState(
        posts = posts.map { post ->
            post.id to PostCardSimpleState(
                post = post,
                isFavorite = favorites.contains(post.id)
            )
        }.toMap()
    )


    val store = Store.of(
        state = postListSimpleSectionState,
        reducer = PostCardSimpleReducer.forEach(
            states = PostListSimpleSectionState.posts,
            actionMapper = PostListSimpleSectionAction.postCardSimpleActions.value,
            environmentMapper = { PostCardSimpleEnvironment(
                navigateToArticle = it.navigateToArticle,
                onToggleFavorite = it.toggleFavorite
            ) }
        ),
        environment = PostListSimpleSectionEnvironment(
            navigateToArticle = { id ->
                flowOf(id).map { navigateToArticle(it) }
            },
            toggleFavorite = { id ->
                flowOf(id).map { onToggleFavorite(it) }
            }
        )
    )

    StoreView(store) { state ->
        Column {
            store.forStates<PostCardSimpleState, PostCardSimpleAction, String>(
                appState = state,
                states = {it.posts},
                actionMapper = {id, action-> PostListSimpleSectionAction.PostCardSimpleActions(id to action) }
            ){ viewStore ->
                PostCardSimple(viewStore)
            }
        }
    }

}

@optics
data class PostListSimpleSectionState(
    val posts:Map<String, PostCardSimpleState>
){
    companion object
}

class PostListSimpleSectionEnvironment(
    val navigateToArticle: (String) -> Flow<Unit>,
    val toggleFavorite:(String) -> Flow<Unit>
)

@optics
sealed class PostListSimpleSectionAction{
    companion object {
    }
    @optics
    data class PostCardSimpleActions(val value:Pair<String, PostCardSimpleAction>):PostListSimpleSectionAction(){
        companion object
    }
}

@optics
data class PostListPopularSectionState(
    val posts:Map<String, PostCardPopularState>
){
    companion object
}

@optics
sealed class PostListPopularSectionAction{
    companion object
    @optics data class PostCardPopularActions(val value:Pair<String, PostCardPopularAction>):PostListPopularSectionAction(){
        companion object
    }
}

class PostListPopularSectionEnvironment(
    val navigateToArticle: (String) -> Flow<Unit>
)

val PostListPopularSectionReducer: Reducer<PostListPopularSectionState, PostListPopularSectionAction, PostListPopularSectionEnvironment> =
    PostCardPopularReducer.forEach(
        states = PostListPopularSectionState.posts,
        actionMapper = PostListPopularSectionAction.postCardPopularActions.value,
        environmentMapper = {
            PostCardPopularEnvironment(
                navigateToArticle = it.navigateToArticle
            )
        }
    )

/**
 * Horizontal scrolling cards for [PostList]
 *
 * @param posts (state) to display
 * @param navigateToArticle (event) request navigation to Article screen
 */
@Composable
private fun PostListPopularSection(
    posts: List<Post>,
    navigateToArticle: (String) -> Unit
) {

    val store = Store.of(
        state = PostListPopularSectionState(
            posts = posts.map { post -> post.id to PostCardPopularState(post) }.toMap(),
        ),
        reducer = PostListPopularSectionReducer,
        environment = PostListPopularSectionEnvironment(
            navigateToArticle = { id ->
                flowOf(id).mapNotNull { navigateToArticle(it) }
            }
        )
    )

    StoreView(store) { state ->
        Column {
            Text(
                modifier = Modifier.padding(16.dp),
                text = stringResource(id = R.string.home_popular_section_title),
                style = MaterialTheme.typography.subtitle1
            )

            LazyRow(modifier = Modifier.padding(end = 16.dp)) {
                items(posts) { post ->

                    PostCardPopular(
                        store.forView(
                            appState = state,
                            stateBuilder = { state.posts[post.id]!! },
                            actionMapper = { PostListPopularSectionAction.PostCardPopularActions(post.id to it) }
                        ),
                        Modifier.padding(start = 16.dp, bottom = 16.dp)
                    )
                }
            }
            PostListDivider()
        }
    }


}

@optics
data class PostListHistorySectionState(
    val posts:Map<String, PostCardHistoryState>
){
    companion object
}

@optics
sealed class PostListHistorySectionAction{
    companion object
    @optics
    data class PostCardHistoryActions(val value:Pair<String, PostCardHistoryAction>):PostListHistorySectionAction(){
        companion object
    }
}

class PostListHistorySectionEnvironment(
    val navigateToArticle:(String) -> Flow<Unit>
)

val PostListHistorySectionReducer:Reducer<PostListHistorySectionState, PostListHistorySectionAction, PostListHistorySectionEnvironment> =
    PostCardHistoryReducer.forEach(
        states = PostListHistorySectionState.posts,
        actionMapper = PostListHistorySectionAction.postCardHistoryActions.value,
        environmentMapper = {
            PostCardHistoryEnvironment(
                navigateToArticle = it.navigateToArticle
            )
        }
    )

/**
 * Full-width list items that display "based on your history" for [PostList]
 *
 * @param posts (state) to display
 * @param navigateToArticle (event) request navigation to Article screen
 */
@Composable
private fun PostListHistorySection(
    posts: List<Post>,
    navigateToArticle: (String) -> Unit
) {
    val store = Store.of<PostListHistorySectionState, PostListHistorySectionAction, PostListHistorySectionEnvironment>(
        state = PostListHistorySectionState(
            posts = posts.map { post -> post.id to PostCardHistoryState(post = post, openDialog = false) }.toMap(),
        ),
        reducer = PostListHistorySectionReducer,
        environment = PostListHistorySectionEnvironment(
            navigateToArticle = { id ->
                flowOf(id).map { navigateToArticle(id) }
            }
        )
    )

    StoreView(store) { state ->
        Column {

            store.forStates<PostCardHistoryState, PostCardHistoryAction, String>(
                appState = state,
                states = { it.posts },
                actionMapper = {id, action -> PostListHistorySectionAction.PostCardHistoryActions(id to action)}
            ) { viewStore ->
                PostCardHistory(viewStore)
                PostListDivider()
            }
        }
    }
}

/**
 * Full-width divider with padding for [PostList]
 */
@Composable
private fun PostListDivider() {
    Divider(
        modifier = Modifier.padding(horizontal = 14.dp),
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.08f)
    )
}

@Preview("Home screen")
@Preview("Home screen (dark)", uiMode = UI_MODE_NIGHT_YES)
@Preview("Home screen (big font)", fontScale = 1.5f)
@Preview("Home screen (large screen)", device = Devices.PIXEL_C)
@Composable
fun PreviewHomeScreen() {
    val posts = runBlocking {
        (BlockingFakePostsRepository().getPosts() as Result.Success).data
    }
    JetnewsTheme {
        HomeScreen(
            posts = UiState(data = posts),
            favorites = setOf(),
            onToggleFavorite = { /*TODO*/ },
            onRefreshPosts = { /*TODO*/ },
            onErrorDismiss = { /*TODO*/ },
            navigateToArticle = { /*TODO*/ },
            openDrawer = { /*TODO*/ },
            scaffoldState = rememberScaffoldState()
        )
    }
}


data class HomeScreenState(val openDrawer: Boolean)

sealed class HomeScreenAction{
    companion object
    object OpenDrawer:HomeScreenAction()
    object DrawerOpened:HomeScreenAction()
}

val HomeScreenReducer:Reducer<HomeScreenState, HomeScreenAction, HomeScreenEnvironment> = {state, action, env, scope ->

    when(action){
        HomeScreenAction.OpenDrawer -> Pair(
            state.copy(openDrawer = true),
            env
                .openDrawer()
                .flowOn(Dispatchers.Main)
                .map { HomeScreenAction.DrawerOpened }
        )

        HomeScreenAction.DrawerOpened -> Pair(
            state.copy(openDrawer = false),
            emptyFlow()
        )
    }
}

class HomeScreenEnvironment(
    val openDrawer:() -> Flow<Unit>
)