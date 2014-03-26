package it.timgreen.android.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.View.MeasureSpec

object Snapshot {
  def getSnapshot(v: View): Bitmap = {
    if (v.getWidth + v.getHeight == 0) {
      v.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
      v.layout(0, 0, v.getMeasuredWidth, v.getMeasuredHeight)
    }

    Option(getSnapshotViaDrawingCache(v))
      .getOrElse(getSnapshotViaDrawingCache(v))
  }

  private def getSnapshotViaDrawingCache(v: View): Bitmap = {
    v.setDrawingCacheEnabled(true)
    val bitmap = Bitmap.createBitmap(v.getDrawingCache)
    v.setDrawingCacheEnabled(false)

    bitmap
  }

  private def getSnapshotViaDraw(v: View): Bitmap = {
    val bitmap = Bitmap.createBitmap(v.getWidth, v.getHeight, Bitmap.Config.RGB_565)
    v.draw(new Canvas(bitmap))
    bitmap
  }
}
