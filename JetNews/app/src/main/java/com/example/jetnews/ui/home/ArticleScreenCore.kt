package com.example.jetnews.ui.home

import arrow.core.none
import arrow.core.some
import arrow.optics.Optional
import arrow.optics.optics
import com.example.jetnews.framework.Reducer
import com.example.jetnews.model.Post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

sealed class LoadedStatus<out T> {
    data class Loaded<T>(val value: T) : LoadedStatus<T>()
    object Loading : LoadedStatus<Nothing>()
    object NotLoaded : LoadedStatus<Nothing>()
}

//Bottom Bar
@optics
data class BottomBarState(
    val liked: Boolean,
    val bookmarked: Boolean,
    val post: Post
) {
    companion object
}

@optics
sealed class BottomBarAction {
    object ActionLike : BottomBarAction()
    object ActionBookmark : BottomBarAction()
    data class ActionShare(val post: Post) : BottomBarAction()
    object UnImplementedAction : BottomBarAction()
    object None : BottomBarAction()

    companion object
}

class BottomBarEnvironment(
    val toggleLike: () -> Flow<Unit>,
    val toggleBookmark: () -> Flow<Unit>,
    val share: (post: Post) -> Flow<Unit>
)


val BottomBarReducer: Reducer<BottomBarState, BottomBarAction, BottomBarEnvironment> =
    { state, action, env, _ ->
        when (action) {
            BottomBarAction.ActionBookmark -> {
                state to env.toggleBookmark()
                    .map { BottomBarAction.None }
                    .flowOn(Dispatchers.IO)
            }
            BottomBarAction.ActionLike -> {
                state to env.toggleLike()
                    .map { BottomBarAction.None }
                    .flowOn(Dispatchers.IO)
            }
            is BottomBarAction.ActionShare -> {
                state to env.share(action.post)
                    .map { BottomBarAction.None }
                    .flowOn(Dispatchers.IO)
            }
            BottomBarAction.None -> state to emptyFlow()
            BottomBarAction.UnImplementedAction -> state to emptyFlow()
        }
    }

//Top Article Screen Section
@optics
data class ArticleScreenTopSectionState(val post: Post, val openDialog: Boolean) {
    companion object
}

sealed class ArticleScreenTopSectionAction() {
    object BackAction : ArticleScreenTopSectionAction()
    object FontSizeAction : ArticleScreenTopSectionAction()
    object OpenDialog : ArticleScreenTopSectionAction()
    object CloseDialog : ArticleScreenTopSectionAction()
    object None : ArticleScreenTopSectionAction()

    companion object
}

class ArticleScreenTopSectionEnvironment(
    val backAction: () -> Flow<Unit>
)

val articleScreenTopSectionReducer: Reducer<ArticleScreenTopSectionState, ArticleScreenTopSectionAction, ArticleScreenTopSectionEnvironment> =
    { state, action, env, _ ->
        when (action) {
            ArticleScreenTopSectionAction.BackAction -> {
                state to env.backAction().map { ArticleScreenTopSectionAction.None }
                    .flowOn(Dispatchers.Main)
            }
            ArticleScreenTopSectionAction.FontSizeAction -> state to emptyFlow()
            ArticleScreenTopSectionAction.OpenDialog -> state.copy(openDialog = true) to emptyFlow()
            ArticleScreenTopSectionAction.CloseDialog -> state.copy(openDialog = false) to emptyFlow()
            ArticleScreenTopSectionAction.None -> state to emptyFlow()
        }
    }

// Article Screen
data class ArticleScreenState(
    val articleScreenTopSectionState: LoadedStatus<ArticleScreenTopSectionState>,
    val bottomBarState: LoadedStatus<BottomBarState>
) {
    companion object {
        val articleScreenTopSectionAction: Optional<ArticleScreenState, ArticleScreenTopSectionState> =
            Optional(
                getOption = { if (it.articleScreenTopSectionState is LoadedStatus.Loaded) it.articleScreenTopSectionState.value.some() else none() },
                set = { parentState, childState ->
                    parentState.copy(
                        articleScreenTopSectionState = LoadedStatus.Loaded(
                            childState
                        )
                    )
                }
            )
        val bottomBarState: Optional<ArticleScreenState, BottomBarState> = Optional(
            getOption = { if (it.bottomBarState is LoadedStatus.Loaded) it.bottomBarState.value.some() else none() },
            set = { parentState, childState ->
                parentState.copy(
                    bottomBarState = LoadedStatus.Loaded(
                        childState
                    )
                )
            }
        )
    }

}

@optics
sealed class ArticleScreenActions {
    companion object

    @optics
    data class ArticleScreenTopSectionAction(val action: com.example.jetnews.ui.home.ArticleScreenTopSectionAction) :
        ArticleScreenActions() {
        companion object
    }

    @optics
    data class ArticleScreenBottomBarAction(val action: com.example.jetnews.ui.home.BottomBarAction) :
        ArticleScreenActions() {
        companion object
    }

    data class LoadPostData(val postId: String) : ArticleScreenActions()
    data class PostDataLoaded(val post: Post) : ArticleScreenActions()


}




