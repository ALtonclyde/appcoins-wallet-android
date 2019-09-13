package com.asfoundation.wallet.ui.appcoins

import android.graphics.Rect
import android.util.TypedValue
import android.view.View
import androidx.recyclerview.widget.RecyclerView


class ReferralNotificationsItemDecorator : RecyclerView.ItemDecoration() {

  override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView,
                              state: RecyclerView.State) {

    if (parent.adapter != null && parent.adapter!!.itemCount > 1) {
      if (parent.getChildAdapterPosition(view) == 0) {
        if (isRtl(view)) {
          outRect.right = 8
          outRect.left = 0
        } else {
          outRect.right = 0
          outRect.left = 8
        }
      }

      if (parent.getChildAdapterPosition(view) == state.itemCount - 1) {
        if (isRtl(view)) {
          outRect.right = 0
          outRect.left = 8
        } else {
          outRect.right = 8
          outRect.left = 0
        }
      }
    } else {
      outRect.right = 16
      outRect.left = 16

      val maxWith = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 400.toFloat(),
          parent.context.resources
              .displayMetrics)
          .toInt()

      val margins = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32.toFloat(),
          parent.context.resources
              .displayMetrics)
          .toInt()

      val screenWith =
          TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_PX, parent.measuredWidth.toFloat(),
              parent.context.resources
                  .displayMetrics)
              .toInt()

      val cardWidth = if (screenWith > maxWith) {
        maxWith
      } else {
        screenWith - margins
      }

      var sidePadding = (screenWith - cardWidth) / 2
      sidePadding = Math.max(0, sidePadding)
      outRect.set(sidePadding, 0, sidePadding, 0)
    }

  }

  private fun isRtl(view: View): Boolean {
    return view.context
        .resources
        .configuration
        .layoutDirection == View.LAYOUT_DIRECTION_RTL
  }
}
