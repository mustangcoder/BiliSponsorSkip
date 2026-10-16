package com.retrsoft.bilisponsorskip

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import java.lang.ref.WeakReference
import java.util.WeakHashMap

internal class MineGameCenterUiController(
    private val controller: SkipController,
) {
    private data class HiddenState(
        val originalVisibility: Int,
        val originalHeight: Int,
        val originalTopMargin: Int,
        val originalBottomMargin: Int,
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val hiddenViews = WeakHashMap<ViewGroup, HiddenState>()
    private var resumedActivity: WeakReference<Activity>? = null
    private var observedDecor: WeakReference<View>? = null
    private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var renderScheduled = false

    fun start() {
        controller.addUiStateListener(::requestRender)
        Log.d("mine game center controller started")
    }

    fun onActivityResumed(activity: Activity) {
        resumedActivity = WeakReference(activity)
        observeLayout(activity)
        requestRender()
    }

    fun onActivityPaused(activity: Activity) {
        if (resumedActivity?.get() !== activity) return
        resumedActivity = null
        stopObservingLayout()
    }

    fun onActivityDestroyed(activity: Activity) {
        if (resumedActivity?.get() !== activity) return
        resumedActivity = null
        stopObservingLayout()
        restoreAll()
    }

    private fun requestRender() {
        if (renderScheduled) return
        renderScheduled = true
        mainHandler.postDelayed({
            renderScheduled = false
            runCatching(::render).onFailure { Log.e("failed to update mine game center card", it) }
        }, RENDER_DELAY_MS)
    }

    private fun render() {
        val activity = resumedActivity?.get() ?: return
        val enabled = controller.uiSnapshot().hideMineGameCenterCard
        if (!enabled) {
            restoreAll()
            return
        }
        if (activity.javaClass.name !in TARGET_ACTIVITY_CLASSES) return
        val decor = activity.window?.decorView as? ViewGroup ?: return
        val card = findGameCenterCard(decor) ?: return
        when (mineGameCenterCardAction(enabled, matches = true, card.visibility == View.GONE)) {
            MineGameCenterCardAction.HIDE -> {
                val layout = card.layoutParams
                val margins = layout as? ViewGroup.MarginLayoutParams
                val state = HiddenState(
                    originalVisibility = card.visibility,
                    originalHeight = layout.height,
                    originalTopMargin = margins?.topMargin ?: 0,
                    originalBottomMargin = margins?.bottomMargin ?: 0,
                )
                hiddenViews.putIfAbsent(card, state)
                card.visibility = View.GONE
                layout.height = requireNotNull(
                    mineGameCenterCardTargetHeight(
                        enabled = true,
                        matches = true,
                        currentlyCollapsed = false,
                        originalHeight = state.originalHeight,
                    ),
                )
                margins?.topMargin = 0
                margins?.bottomMargin = 0
                card.layoutParams = layout
                card.requestLayout()
                Log.d("mine game center card hidden and collapsed: ${card.javaClass.name}")
            }
            MineGameCenterCardAction.NONE,
            MineGameCenterCardAction.RESTORE,
            -> Unit
        }
    }

    private fun findGameCenterCard(view: View): ViewGroup? {
        if (view !is ViewGroup) return null
        if (isMineGameCenterCard(signals(view))) return view
        for (index in 0 until view.childCount) {
            findGameCenterCard(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private fun signals(card: ViewGroup): MineCardSignals {
        val header = card.children().filterIsInstance<ViewGroup>()
            .firstOrNull { resourceName(it) == HEADER_RESOURCE }
        val items = card.children().filterIsInstance<ViewGroup>()
            .firstOrNull { resourceName(it) == ITEM_CONTAINER_RESOURCE }
        return MineCardSignals(
            title = header?.descendants()?.filterIsInstance<TextView>()
                ?.firstOrNull { resourceName(it) == TITLE_RESOURCE }
                ?.text?.toString().orEmpty(),
            headerResource = header?.let(::resourceName).orEmpty(),
            itemContainerResource = items?.let(::resourceName).orEmpty(),
            itemLabels = items?.descendants()
                ?.mapNotNull { child ->
                    child.contentDescription?.toString()?.trim()?.takeIf(String::isNotEmpty)
                }
                ?.toSet().orEmpty(),
        )
    }

    private fun ViewGroup.children(): Sequence<View> = sequence {
        for (index in 0 until childCount) yield(getChildAt(index))
    }

    private fun View.descendants(): Sequence<View> = sequence {
        yield(this@descendants)
        if (this@descendants is ViewGroup) {
            for (index in 0 until childCount) yieldAll(getChildAt(index).descendants())
        }
    }

    private fun resourceName(view: View): String {
        if (view.id == View.NO_ID) return ""
        return runCatching { view.resources.getResourceEntryName(view.id) }.getOrDefault("")
    }

    private fun restoreAll() {
        hiddenViews.entries.toList().forEach { (view, state) ->
            view.visibility = state.originalVisibility
            val layout = view.layoutParams
            layout.height = requireNotNull(
                mineGameCenterCardTargetHeight(
                    enabled = false,
                    matches = true,
                    currentlyCollapsed = true,
                    originalHeight = state.originalHeight,
                ),
            )
            (layout as? ViewGroup.MarginLayoutParams)?.let { margins ->
                margins.topMargin = state.originalTopMargin
                margins.bottomMargin = state.originalBottomMargin
            }
            view.layoutParams = layout
            view.requestLayout()
        }
        hiddenViews.clear()
    }

    private fun observeLayout(activity: Activity) {
        val decor = activity.window?.decorView ?: return
        if (observedDecor?.get() === decor) return
        stopObservingLayout()
        val listener = ViewTreeObserver.OnGlobalLayoutListener(::requestRender)
        decor.viewTreeObserver.addOnGlobalLayoutListener(listener)
        observedDecor = WeakReference(decor)
        globalLayoutListener = listener
    }

    private fun stopObservingLayout() {
        val decor = observedDecor?.get()
        val listener = globalLayoutListener
        if (decor != null && listener != null && decor.viewTreeObserver.isAlive) {
            decor.viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
        observedDecor = null
        globalLayoutListener = null
    }

    private companion object {
        const val RENDER_DELAY_MS = 250L
        const val HEADER_RESOURCE = "head_layout"
        const val ITEM_CONTAINER_RESOURCE = "item_recycle"
        const val TITLE_RESOURCE = "title"
        val TARGET_ACTIVITY_CLASSES = setOf("tv.danmaku.bili.MainActivityV2")
    }
}
