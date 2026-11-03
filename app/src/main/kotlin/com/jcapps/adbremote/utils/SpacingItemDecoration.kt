package com.jcapps.adbremote.utils

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Equal [spacing] (px) around every item of a linear or grid list; respects orientation and
 * reverse layout. Claimed by group main-b (used by many screens). RECON-UNSURE: class name.
 */
class SpacingItemDecoration(
    // obf: f5993
    private val spacing: Int,
) : RecyclerView.ItemDecoration() {
    // obf: C1225

    override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
        super.getItemOffsets(outRect, view, parent, state)
        when (val layoutManager = parent.layoutManager) {
            is GridLayoutManager -> {
                val position = parent.getChildAdapterPosition(view)
                val itemCount = state.itemCount
                val orientation = layoutManager.orientation
                val spanCount = layoutManager.spanCount
                val isReversed = layoutManager.reverseLayout
                @Suppress("UNUSED_VARIABLE")
                val rows = if (itemCount % spanCount == 0) itemCount / spanCount else itemCount / spanCount + 1
                val column = if (orientation == RecyclerView.HORIZONTAL) position / spanCount else position % spanCount
                val row = if (orientation == RecyclerView.HORIZONTAL) position % spanCount else position / spanCount
                val isFirstColumn = column == 0
                val isFirstRow = row == 0
                val firstColumnSpacing = if (isFirstColumn) spacing else 0
                val firstRowSpacing = if (isFirstRow) spacing else 0
                if (orientation == RecyclerView.HORIZONTAL) {
                    outRect.left = if (isReversed) spacing else firstColumnSpacing
                    outRect.top = (spanCount - row) * spacing / spanCount
                    outRect.right = if (isReversed) firstColumnSpacing else spacing
                    outRect.bottom = (row + 1) * spacing / spanCount
                } else if (orientation == RecyclerView.VERTICAL) {
                    outRect.left = (spanCount - column) * spacing / spanCount
                    outRect.top = if (isReversed) spacing else firstRowSpacing
                    outRect.right = (column + 1) * spacing / spanCount
                    outRect.bottom = if (isReversed) firstRowSpacing else spacing
                }
            }

            is LinearLayoutManager -> {
                val orientation = layoutManager.orientation
                val isReversed = layoutManager.stackFromEnd xor layoutManager.reverseLayout
                val lastSpacing = if (parent.getChildAdapterPosition(view) == state.itemCount - 1) spacing else 0
                if (orientation == RecyclerView.HORIZONTAL) {
                    outRect.left = if (isReversed) lastSpacing else spacing
                    outRect.top = spacing
                    outRect.right = if (isReversed) spacing else lastSpacing
                    outRect.bottom = spacing
                } else if (orientation == RecyclerView.VERTICAL) {
                    outRect.left = spacing
                    outRect.top = if (isReversed) lastSpacing else spacing
                    outRect.right = spacing
                    outRect.bottom = if (isReversed) spacing else lastSpacing
                }
            }
        }
    }
}
