package com.wwwaker.warer.core.formula.layout

/**
 * 数学排版常量，单位一律为 **em**。
 *
 * 取值参考 TeXbook / KaTeX 的量级，为简化首版实现做了取舍。
 * 之所以集中放在这里，是因为单元测试会直接引用这些常量断言——**调优它们不会让测试失效**。
 */
object LayoutConstants {

    // ---------- 全局 ----------

    /** 数学轴高度：分数线、根号等的对齐基准，位于基线之上。 */
    const val AXIS_HEIGHT = 0.25

    /** 规则线（分数线、上横线）的默认线宽。 */
    const val RULE_THICKNESS = 0.04

    // ---------- 上下标 ----------

    /** 上下标相对主体的缩放比例。 */
    const val SCRIPT_SCALE = 0.7

    /** 上下标与主体之间的水平间隙。 */
    const val SCRIPT_GAP = 0.05

    /** 上标顶部相对主体顶部、下标底部相对主体底部至少保持的净空。 */
    const val SCRIPT_CLEARANCE = 0.10

    /** 上标的最小抬升量（主体较矮时生效）。 */
    const val SUP_MIN_SHIFT = 0.45

    /** 下标的最小下沉量（主体较矮时生效）。 */
    const val SUB_MIN_SHIFT = 0.22

    // ---------- 空槽位（尚未输入的待填位置） ----------

    /**
     * 空槽位的最小宽度。
     *
     * 空行并非"没有内容"，而是"**等着被输入**"：它一旦被填入就会有字符大小。
     * 如果按 0 度量，父节点会以为这里什么都没有 —— 空分母的基线会落在分数线上，
     * 光标就会穿进分数线里（真机上出现过），分数线的宽度也会退化成一个点。
     */
    const val SLOT_MIN_WIDTH = 0.45

    /**
     * 占位框相对基线的高度。
     *
     * 取"一个字符"的高度而不是整行高度（ascent + descent）：占位框是给用户的视觉提示，
     * 形状应当像"一个字"，而不是像一条竖着的行距。
     */
    const val SLOT_ASCENT = 0.55
    const val SLOT_DESCENT = 0.15

    // ---------- 分式 ----------

    /** 分子 / 分母与分数线之间的垂直间隙。 */
    const val FRACTION_GAP = 0.10

    /** 分式左右两侧的内边距。 */
    const val FRACTION_SIDE_PADDING = 0.10

    // ---------- 根式 ----------

    /** 根号字形的文本表示（其宽度由字体度量给出，从而让根号宽度随字体自适应）。 */
    const val RADICAL_GLYPH = "√"

    /** 根号内部上下留白。 */
    const val RADICAL_PADDING = 0.10

    /** 根号与内部被开方数之间的间隙。 */
    const val RADICAL_GAP = 0.05

    /** 根号右侧留白。 */
    const val RADICAL_RIGHT_PADDING = 0.05

    /** 根式指数（n 次方根）的缩放比例。 */
    const val RADICAL_INDEX_SCALE = 0.5

    /** 根式指数与根号的重叠比例（0 = 完全不重叠，1 = 完全重叠）。 */
    const val RADICAL_INDEX_OVERLAP = 0.3

    // ---------- 括号 ----------

    /**
     * 内容总高度超过该阈值（em）时，括号改用矢量路径绘制（可伸缩）。
     * 内容较矮时用字体字形，视觉上更贴近正文。
     */
    const val DELIM_STRETCH_THRESHOLD = 1.2

    /** 可伸缩括号相对内容上下额外的留白。 */
    const val DELIM_PADDING = 0.05

    /** 采样可伸缩括号曲线时的分段数。 */
    const val DELIM_CURVE_STEPS = 12

    // ---------- 大算符 ----------

    /** 大算符上下限相对主体的缩放比例。 */
    const val BIGOP_LIMIT_SCALE = 0.7

    /** 大算符与其上下限之间的垂直间隙。 */
    const val BIGOP_GAP = 0.08

    /** 大算符与其右侧上下限（∫ 的形式）之间的水平间隙。 */
    const val BIGOP_SIDE_GAP = 0.05
}
