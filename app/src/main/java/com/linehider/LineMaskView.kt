package com.linehider

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/**
 * 全局覆盖层上的遮罩绘制 View。
 *
 * 背景完全透明，仅在配置的 [MaskLine] 位置绘制实心竖条。
 * 通过 [updateLines] 更新线条后调用 invalidate 重绘。
 */
class LineMaskView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var lines: List<MaskLine> = emptyList()

    /**
     * 更新遮罩线列表并触发重绘。
     */
    fun updateLines(newLines: List<MaskLine>) {
        lines = newLines
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val h = height
        for (line in lines) {
            if (!line.enabled) continue
            paint.color = line.color
            // 绘制从顶部到底部的实心竖条
            canvas.drawRect(
                line.x.toFloat(),
                0f,
                (line.x + line.width).toFloat(),
                h.toFloat(),
                paint
            )
        }
    }
}
