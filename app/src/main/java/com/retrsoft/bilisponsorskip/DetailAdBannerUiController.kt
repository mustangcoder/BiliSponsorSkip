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

internal class DetailAdBannerUiController(
    private val controller: SkipController,
) {
    private data class HiddenState(val originalVisibility: Int)

    private data class ScanResult(
        val hasAdLabel: Boolean,
        val hasDetailAction: Boolean,
        val candidate: ViewGroup?,
    )

    private val mainHandler = Handler(Looper.getMainLooper())
    private val hiddenViews = WeakHashMap<ViewGroup, HiddenState>()
    private var resumedActivity: WeakReference<Activity>? = null
    private var observedDecor: WeakReference<View>? = null
    private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var renderScheduled = false

    fun start() {
        controller.addUiStateListener(::requestRender)
        Log.d("detail ad banner controller started")
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
            runCatching(::render).onFailure { Log.e("failed to update detail ad banner", it) }
        }, RENDER_DELAY_MS)
    }

    private fun render() {
        val activity = resumedActivity?.get() ?: return
        val enabled = controller.uiSnapshot().hideDetailAdBanner
        if (!enabled) {
            restoreAll()
            return
        }
        if (activity.javaClass.name !in TARGET_ACTIVITY_CLASSES) return
        val decor = activity.window?.decorView as? ViewGroup ?: return
        val banner = findBannerContainer(decor, activity) ?: return
        when (detailAdBannerAction(
            enabled = true,
            matches = true,
            currentlyHidden = banner.visibility == View.GONE,
        )) {
            DetailAdBannerAction.HIDE -> {
                hiddenViews.putIfAbsent(banner, HiddenState(banner.visibility))
                banner.visibility = View.GONE
                Log.d("detail ad banner hidden: ${banner.javaClass.name}")
            }
            DetailAdBannerAction.NONE,
            DetailAdBannerAction.RESTORE,
            -> Unit
        }
    }

    private fun findBannerContainer(decor: ViewGroup, activity: Activity): ViewGroup? {
        val knownFrame = findViewGroupByResource(decor, AD_FRAME_RESOURCE)
        if (knownFrame != null) {
            val result = scan(knownFrame)
            if (isKnownDetailAdFrame(resourceName(knownFrame), result.hasDetailAction)) {
                val path = generateSequence(knownFrame as View?) { it.parent as? View }.toList().asReversed()
                val targetResource = detailAdBannerContainerResource(path.map(::resourceName))
                if (targetResource != null) {
                    return path.lastOrNull { resourceName(it) == targetResource } as? ViewGroup
                }
            }
        }
        val commonAncestor = scan(decor).candidate ?: return null
        return collapsibleContainer(commonAncestor, decor, activity.dp(MAX_BANNER_HEIGHT_DP))
    }

    private fun scan(view: View): ScanResult {
        val text = (view as? TextView)?.text?.toString()?.trim().orEmpty()
        val description = view.contentDescription?.toString()?.trim().orEmpty()
        var hasAdLabel = text == "广告" || description == "广告"
        var hasDetailAction = text.contains("查看详情") || description.contains("查看详情")
        var candidate: ViewGroup? = null
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                val child = scan(view.getChildAt(index))
                hasAdLabel = hasAdLabel || child.hasAdLabel
                hasDetailAction = hasDetailAction || child.hasDetailAction
                if (candidate == null) candidate = child.candidate
            }
            if (candidate == null && hasAdLabel && hasDetailAction) {
                candidate = view
            }
        }
        return ScanResult(hasAdLabel, hasDetailAction, candidate)
    }

    private fun findViewGroupByResource(root: View, resource: String): ViewGroup? {
        if (root is ViewGroup && resourceName(root) == resource) return root
        if (root !is ViewGroup) return null
        for (index in 0 until root.childCount) {
            findViewGroupByResource(root.getChildAt(index), resource)?.let { return it }
        }
        return null
    }

    private fun resourceName(view: View): String {
        if (view.id == View.NO_ID) return ""
        return runCatching { view.resources.getResourceEntryName(view.id) }.getOrDefault("")
    }

    private fun collapsibleContainer(
        commonAncestor: ViewGroup,
        decor: ViewGroup,
        maxHeight: Int,
    ): ViewGroup {
        var result = commonAncestor
        var parent = result.parent as? ViewGroup
        while (parent != null && parent !== decor) {
            val fullWidth = parent.width >= decor.width * MIN_WIDTH_RATIO
            val compactHeight = parent.height in 1..maxHeight
            if (!fullWidth || !compactHeight) break
            result = parent
            parent = parent.parent as? ViewGroup
        }
        return result
    }

    private fun restoreAll() {
        hiddenViews.entries.toList().forEach { (view, state) ->
            view.visibility = state.originalVisibility
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

    private fun Activity.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        const val RENDER_DELAY_MS = 250L
        const val MAX_BANNER_HEIGHT_DP = 180
        const val MIN_WIDTH_RATIO = 0.8f
        val TARGET_ACTIVITY_CLASSES = setOf(
            "com.bilibili.ship.theseus.detail.UnitedBizDetailsActivity",
            "com.bilibili.video.videodetail.VideoDetailsActivity",
        )
    }
}
