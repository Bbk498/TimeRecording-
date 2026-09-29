package com.example.timerecording

/**
 * 甘特图布局常量
 *
 * 集中定义甘特图各区域的尺寸常量（单位dp），供 GanttChartView 和 GanttTimeAxisView 共享使用，
 * 保证两个视图的布局坐标一致。
 */
object GanttLayoutConstants {
    const val topLabelDp = 35f       // 顶部项目名区域高度
    const val topPaddingDp = 16f     // 图表顶部留白
    const val bottomPaddingDp = 24f  // 图表底部留白
    const val minColumnDp = 65f      // 每列最小宽度
    const val barWidthRatio = 0.65f   // 竖条占列宽比例
}
