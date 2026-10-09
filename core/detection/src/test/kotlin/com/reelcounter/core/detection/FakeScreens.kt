package com.reelcounter.core.detection

const val IG = "com.instagram.android"
const val IG_PAGER = "$IG:id/clips_viewer_view_pager"
const val IG_AUTHOR = "$IG:id/clips_author_username"
const val IG_CAPTION = "$IG:id/clips_caption_component"
const val IG_FEED = "$IG:id/feed_recycler"
const val LAUNCHER = "com.google.android.apps.nexuslauncher"
const val YT = "com.google.android.youtube"
const val YT_PAGER = "$YT:id/reel_recycler"

/** YouTube Shorts as measured on YouTube 20.10: the pager is shorter than the screen (bottom nav). */
val YT_PAGE = ScreenRect(0, 0, 1080, 2211)

val SCREEN = ScreenSize(1080, 2400)
val FULL = ScreenRect(0, 0, 1080, 2400)

val testPack = RulesPack(
    version = 1,
    apps = listOf(
        AppRules(
            packageName = IG,
            surfaceViewIds = listOf(IG_PAGER),
            pagerViewIds = listOf(IG_PAGER),
            fingerprintViewIds = listOf(IG_AUTHOR, IG_CAPTION),
        ),
        // Like real YouTube: no fingerprint IDs (its overlay has no view IDs) and no pager indexes.
        AppRules(
            packageName = YT,
            surfaceViewIds = listOf(YT_PAGER),
            pagerViewIds = listOf(YT_PAGER),
        ),
    ),
)

class FakeNode(
    override val viewId: String? = null,
    override val className: String? = null,
    override val bounds: ScreenRect = FULL,
    override val isVisibleToUser: Boolean = true,
    override val isScrollable: Boolean = false,
    override val text: String? = null,
    override val contentDescription: String? = null,
    override val collectionRowIndex: Int = -1,
    val children: List<FakeNode> = emptyList(),
) : UiNode {
    override val childCount: Int get() = children.size

    override fun child(index: Int): UiNode? = children.getOrNull(index)

    override fun findByViewId(fullViewId: String): List<UiNode> = buildList {
        fun visit(node: FakeNode) {
            if (node.viewId == fullViewId) add(node)
            node.children.forEach(::visit)
        }
        children.forEach(::visit)
    }
}

/** The Reels player showing reel [index]. [rowIndex] = false hides CollectionItemInfo. */
fun reelsScreen(
    index: Int,
    author: String = "author$index",
    caption: String = "caption $index",
    rowIndex: Boolean = true,
): FakeNode = FakeNode(
    children = listOf(
        FakeNode(
            viewId = IG_PAGER,
            className = "androidx.recyclerview.widget.RecyclerView",
            isScrollable = true,
            children = listOf(
                FakeNode(
                    collectionRowIndex = if (rowIndex) index else -1,
                    children = listOf(
                        FakeNode(viewId = IG_AUTHOR, bounds = ScreenRect(40, 2000, 600, 2060), text = author),
                        FakeNode(viewId = IG_CAPTION, bounds = ScreenRect(40, 2080, 1000, 2140), text = caption),
                    ),
                ),
            ),
        ),
    ),
)

fun feedScreen(): FakeNode = FakeNode(
    children = listOf(
        FakeNode(
            viewId = IG_FEED,
            className = "androidx.recyclerview.widget.RecyclerView",
            isScrollable = true,
        ),
    ),
)

fun windowChanged(t: Long, pkg: String = IG) =
    DetectorEvent(kind = EventKind.WINDOW_STATE_CHANGED, packageName = pkg, timeMillis = t)

fun contentChanged(t: Long, pkg: String = IG) =
    DetectorEvent(kind = EventKind.WINDOW_CONTENT_CHANGED, packageName = pkg, timeMillis = t)

fun pagerScrolled(t: Long, from: Int, to: Int = from, count: Int = 50, deltaY: Int = 0) = DetectorEvent(
    kind = EventKind.VIEW_SCROLLED,
    packageName = IG,
    timeMillis = t,
    sourceViewId = IG_PAGER,
    sourceClassName = "androidx.recyclerview.widget.RecyclerView",
    fromIndex = from,
    toIndex = to,
    itemCount = count,
    scrollDeltaY = deltaY,
)

fun shortsScreen(): FakeNode = FakeNode(
    children = listOf(
        FakeNode(
            viewId = YT_PAGER,
            className = "android.support.v7.widget.RecyclerView",
            bounds = YT_PAGE,
            isScrollable = true,
            children = listOf(FakeNode(bounds = YT_PAGE)),
        ),
    ),
)

fun shortsScrolled(t: Long, deltaY: Int) = DetectorEvent(
    kind = EventKind.VIEW_SCROLLED,
    packageName = YT,
    timeMillis = t,
    sourceViewId = YT_PAGER,
    sourceClassName = "android.support.v7.widget.RecyclerView",
    scrollDeltaY = deltaY,
)
