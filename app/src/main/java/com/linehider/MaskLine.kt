package com.linehider

/**
 * 一条遮罩线的配置。
 *
 * @param id      唯一标识（0..MAX_LINES-1）
 * @param x       横向起始坐标（像素，相对屏幕左上角）
 * @param width   遮罩条宽度（像素）
 * @param color   遮罩条颜色（默认黑色）
 * @param enabled 是否启用
 */
data class MaskLine(
    val id: Int,
    val x: Int,
    val width: Int,
    val color: Int,
    val enabled: Boolean
) {
    companion object {
        /** 最多支持的遮罩线数量。 */
        const val MAX_LINES = 10

        /** 默认遮罩颜色：纯黑。 */
        const val DEFAULT_COLOR = 0xFF000000.toInt()

        /** 默认宽度（像素）。 */
        const val DEFAULT_WIDTH = 2
    }
}
