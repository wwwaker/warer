package com.wwwaker.warer.core.formula.layout

import com.wwwaker.warer.core.formula.model.BigOp
import com.wwwaker.warer.core.formula.model.Delim
import com.wwwaker.warer.core.formula.model.DelimKind
import com.wwwaker.warer.core.formula.model.Frac
import com.wwwaker.warer.core.formula.model.Func
import com.wwwaker.warer.core.formula.model.LimitsPlacement
import com.wwwaker.warer.core.formula.model.Node
import com.wwwaker.warer.core.formula.model.Num
import com.wwwaker.warer.core.formula.model.Row
import com.wwwaker.warer.core.formula.model.Script
import com.wwwaker.warer.core.formula.model.Sqrt
import com.wwwaker.warer.core.formula.model.Sym

/**
 * 公式布局引擎：把 AST 计算为"带坐标的绘制指令"。
 *
 * 算法特点：
 * - 每个节点只需实现 [measure] 与 [place] 两件事；
 * - 统一使用"基线上方 = ascent / 基线下 = descent"的盒模型，父节点据此对齐子节点；
 * - 公式树通常 **< 100 个节点**，因此**不做任何增量优化**：每次编辑全量重排即可（远小于 1ms）。
 *
 * 坐标系、单位与 y 轴方向见 [DrawCmd] 的说明。
 */
object FormulaLayout {

    /** 便捷入口：布局整棵公式树。 */
    fun layoutFormula(node: Node, ctx: LayoutContext): LayoutResult {
        val measured = measure(node, ctx)
        val commands = mutableListOf<DrawCmd>()
        place(node, 0.0, 0.0, ctx, commands)
        return LayoutResult(measured, commands)
    }

    /** 计算节点尺寸，不产生绘制指令。 */
    fun measure(node: Node, ctx: LayoutContext): Measured = when (node) {
        is Row -> measureRow(node, ctx)
        is Sym -> measureText(node.ch.toString(), ctx)
        is Num -> measureText(node.text, ctx)
        is Func -> measureText(node.name, ctx)
        is Frac -> measureFrac(node, ctx)
        is Script -> measureScript(node, ctx)
        is Sqrt -> measureSqrt(node, ctx)
        is Delim -> measureDelim(node, ctx)
        is BigOp -> measureBigOp(node, ctx)
    }

    /** 在 ([x], [baselineY]) 处放置节点，并把绘制指令追加到 [out]。 */
    fun place(
        node: Node,
        x: Double,
        baselineY: Double,
        ctx: LayoutContext,
        out: MutableList<DrawCmd>
    ) {
        when (node) {
            is Row -> placeRow(node, x, baselineY, ctx, out)
            is Sym -> out.add(DrawCmd.Text(node.ch.toString(), x, baselineY, ctx.scale))
            is Num -> out.add(DrawCmd.Text(node.text, x, baselineY, ctx.scale))
            is Func -> out.add(DrawCmd.Text(node.name, x, baselineY, ctx.scale))
            is Frac -> placeFrac(node, x, baselineY, ctx, out)
            is Script -> placeScript(node, x, baselineY, ctx, out)
            is Sqrt -> placeSqrt(node, x, baselineY, ctx, out)
            is Delim -> placeDelim(node, x, baselineY, ctx, out)
            is BigOp -> placeBigOp(node, x, baselineY, ctx, out)
        }
    }

    // ============================ 基础 ============================

    private fun measureText(text: String, ctx: LayoutContext): Measured = Measured(
        width = ctx.metrics.width(text) * ctx.scale,
        ascent = ctx.metrics.ascent * ctx.scale,
        descent = ctx.metrics.descent * ctx.scale
    )

    // ============================ Row ============================

    private fun measureRow(node: Row, ctx: LayoutContext): Measured {
        // 空行 = 一个"**待输入的槽位**"，不是"没有内容"：它一旦被填入就会有字符大小，
        // 因此这里按一个字符来度量。
        //
        // 若按 0 度量，父节点会以为这里什么都没有，空分母的基线就会落在分数线上
        // （因为分母的基线 = 分母顶边 + 分母 ascent，ascent 为 0 就顶到分数线），
        // 光标随之穿进分数线里；分数线的宽度也会退化成一个点。真机上就是这么暴露的。
        if (node.children.isEmpty()) {
            return Measured(
                width = LayoutConstants.SLOT_MIN_WIDTH,
                ascent = ctx.metrics.ascent * ctx.scale,
                descent = ctx.metrics.descent * ctx.scale
            )
        }

        var width = 0.0
        var ascent = 0.0
        var descent = 0.0
        for (child in node.children) {
            val m = measure(child, ctx)
            width += m.width
            if (m.ascent > ascent) ascent = m.ascent
            if (m.descent > descent) descent = m.descent
        }
        return Measured(width, ascent, descent)
    }

    /**
     * 水平依次放置，且**所有子节点共享同一条基线** —— 这是"看起来像公式"的关键。
     * 这里会二次调用 [measure]（公式规模极小，不做缓存是刻意的简化）。
     */
    private fun placeRow(
        node: Row,
        x: Double,
        baselineY: Double,
        ctx: LayoutContext,
        out: MutableList<DrawCmd>
    ) {
        // 每个 Row 都会走到这里，且顺序与 Row.collectRows() 的深度优先顺序一致，
        // 因此这里就是记录插入点几何的唯一入口。
        val measured = measureRow(node, ctx)
        for (visitor in ctx.visitors) visitor.enterRow(x, baselineY, measured)

        // 空行给出一个占位框，提示用户"这里可以输入"（参考 GeoGebra / Desmos）
        if (node.children.isEmpty()) {
            out.add(
                DrawCmd.Slot(
                    x = x,
                    y = baselineY - LayoutConstants.SLOT_ASCENT,
                    width = measured.width,
                    height = LayoutConstants.SLOT_ASCENT + LayoutConstants.SLOT_DESCENT
                )
            )
        }

        var cursor = x
        for (child in node.children) {
            for (visitor in ctx.visitors) visitor.beforeChild(cursor)
            place(child, cursor, baselineY, ctx, out)
            cursor += measure(child, ctx).width
        }
        for (visitor in ctx.visitors) visitor.endRow(cursor)
    }

    // ============================ 分式 ============================

    // ---------- 分式垂直几何 ----------
    //
    // 统一用"相对基线的 y 偏移"表达（与 DrawCmd 坐标系一致：y 向下为正），
    // 且 measure 与 place **共用同一组偏移量** —— 二者各自推导最容易产生漂移。
    //
    // 历史教训：分母顶边的偏移曾少了一个负号（写成基线**下方** AXIS 处），
    // 结果分母被顶到分数线上；而当时单测是"照着实现复算同一个表达式"，
    // 无论如何都通过，直到真机上看截图才发现。因此下面的偏移量只有一个出处。

    /** 分数线上边到基线的偏移。 */
    private val ruleTopOffset: Double
        get() = -(LayoutConstants.AXIS_HEIGHT + LayoutConstants.RULE_THICKNESS / 2)

    /** 分数线下边到基线的偏移。 */
    private val ruleBottomOffset: Double
        get() = -(LayoutConstants.AXIS_HEIGHT - LayoutConstants.RULE_THICKNESS / 2)

    /** 分子底边到基线的偏移。 */
    private val numeratorBottomOffset: Double
        get() = ruleTopOffset - LayoutConstants.FRACTION_GAP

    /** 分母顶边到基线的偏移。 */
    private val denominatorTopOffset: Double
        get() = ruleBottomOffset + LayoutConstants.FRACTION_GAP

    private fun fracWidth(num: Measured, den: Measured): Double =
        maxOf(num.width, den.width) + 2 * LayoutConstants.FRACTION_SIDE_PADDING

    private fun measureFrac(node: Frac, ctx: LayoutContext): Measured {
        val num = measure(node.numerator, ctx)
        val den = measure(node.denominator, ctx)

        // 盒顶 = 分子底边继续往上 num.height；盒底 = 分母顶边继续往下 den.height。
        // 注意必须用 height 而不是 ascent —— 分子底边贴在分数线上方，
        // 漏掉 descent 会让分子戳出盒子。
        return Measured(
            width = fracWidth(num, den),
            ascent = -numeratorBottomOffset + num.height,
            descent = denominatorTopOffset + den.height
        )
    }

    private fun placeFrac(
        node: Frac,
        x: Double,
        baselineY: Double,
        ctx: LayoutContext,
        out: MutableList<DrawCmd>
    ) {
        val num = measure(node.numerator, ctx)
        val den = measure(node.denominator, ctx)
        val width = fracWidth(num, den)

        // 分数线：中心恰好落在数学轴上
        out.add(
            DrawCmd.Rect(
                x = x,
                y = baselineY + ruleTopOffset,
                width = width,
                height = LayoutConstants.RULE_THICKNESS
            )
        )

        // 分子：底边贴在分数线上方，水平居中
        place(
            node.numerator,
            x + (width - num.width) / 2,
            baselineY + numeratorBottomOffset - num.descent,
            ctx,
            out
        )

        // 分母：顶边贴在分数线下方，水平居中
        place(
            node.denominator,
            x + (width - den.width) / 2,
            baselineY + denominatorTopOffset + den.ascent,
            ctx,
            out
        )
    }

    // ============================ 上下标 ============================

    private fun measureScript(node: Script, ctx: LayoutContext): Measured {
        val base = measure(node.base, ctx)
        val scriptCtx = ctx.scaled(LayoutConstants.SCRIPT_SCALE)
        val sup = node.superscript?.let { measure(it, scriptCtx) }
        val sub = node.subscript?.let { measure(it, scriptCtx) }

        val scriptsWidth = maxOf(sup?.width ?: 0.0, sub?.width ?: 0.0)
        val width = base.width +
            (if (scriptsWidth > 0.0) LayoutConstants.SCRIPT_GAP + scriptsWidth else 0.0)

        var ascent = base.ascent
        var descent = base.descent
        sup?.let { ascent = maxOf(ascent, supShift(base.ascent, it) + it.ascent) }
        sub?.let { descent = maxOf(descent, subShift(base.descent, it) + it.descent) }

        return Measured(width, ascent, descent)
    }

    private fun placeScript(
        node: Script,
        x: Double,
        baselineY: Double,
        ctx: LayoutContext,
        out: MutableList<DrawCmd>
    ) {
        val base = measure(node.base, ctx)
        place(node.base, x, baselineY, ctx, out)

        val scriptCtx = ctx.scaled(LayoutConstants.SCRIPT_SCALE)
        val scriptX = x + base.width + LayoutConstants.SCRIPT_GAP

        node.superscript?.let {
            val m = measure(it, scriptCtx)
            place(it, scriptX, baselineY - supShift(base.ascent, m), scriptCtx, out)
        }
        node.subscript?.let {
            val m = measure(it, scriptCtx)
            place(it, scriptX, baselineY + subShift(base.descent, m), scriptCtx, out)
        }
    }

    /** 上标基线相对主体基线的抬升量（正数表示向上）。 */
    private fun supShift(baseAscent: Double, sup: Measured): Double = maxOf(
        LayoutConstants.SUP_MIN_SHIFT,
        baseAscent + LayoutConstants.SCRIPT_CLEARANCE - sup.ascent
    )

    /** 下标基线相对主体基线的下沉量（正数表示向下）。 */
    private fun subShift(baseDescent: Double, sub: Measured): Double = maxOf(
        LayoutConstants.SUB_MIN_SHIFT,
        baseDescent + LayoutConstants.SCRIPT_CLEARANCE - sub.descent
    )

    // ============================ 根号 ============================

    private fun measureSqrt(node: Sqrt, ctx: LayoutContext): Measured {
        val radicand = measure(node.radicand, ctx)
        val indexCtx = ctx.scaled(LayoutConstants.RADICAL_INDEX_SCALE)

        val width = indexReservedWidth(node.index?.let { measure(it, indexCtx) }) +
            ctx.metrics.width(LayoutConstants.RADICAL_GLYPH) * ctx.scale +
            LayoutConstants.RADICAL_GAP +
            radicand.width +
            LayoutConstants.RADICAL_RIGHT_PADDING
        val ascent = radicand.ascent + LayoutConstants.RADICAL_PADDING + LayoutConstants.RULE_THICKNESS
        val descent = radicand.descent + LayoutConstants.RADICAL_PADDING

        return Measured(width, ascent, descent)
    }

    private fun placeSqrt(
        node: Sqrt,
        x: Double,
        baselineY: Double,
        ctx: LayoutContext,
        out: MutableList<DrawCmd>
    ) {
        val radicand = measure(node.radicand, ctx)
        val indexCtx = ctx.scaled(LayoutConstants.RADICAL_INDEX_SCALE)

        val indexWidth = indexReservedWidth(node.index?.let { measure(it, indexCtx) })
        val glyphWidth = ctx.metrics.width(LayoutConstants.RADICAL_GLYPH) * ctx.scale
        val totalWidth = indexWidth + glyphWidth + LayoutConstants.RADICAL_GAP +
            radicand.width + LayoutConstants.RADICAL_RIGHT_PADDING

        val top = baselineY -
            (radicand.ascent + LayoutConstants.RADICAL_PADDING + LayoutConstants.RULE_THICKNESS)
        val bottom = baselineY + radicand.descent + LayoutConstants.RADICAL_PADDING
        val radicalX = x + indexWidth

        // 根号：一条折线同时画出"斜杠 + 上横线"，因此会随内容高度自动伸缩
        val h = bottom - top
        out.add(
            DrawCmd.Polyline(
                points = listOf(
                    Point2(radicalX, top + 0.40 * h),
                    Point2(radicalX + 0.30 * glyphWidth, bottom),
                    Point2(radicalX + 0.80 * glyphWidth, top),
                    Point2(x + totalWidth, top)
                ),
                thickness = LayoutConstants.RULE_THICKNESS
            )
        )

        // 递归顺序必须与 Slots.slots() 一致：[被开方数, 根指数]，否则光标探针会错位。
        // 被开方数：上下留白与根号内部一致，其基线恰好落在节点基线上
        place(node.radicand, radicalX + glyphWidth + LayoutConstants.RADICAL_GAP, baselineY, ctx, out)

        // n 次方根指数：顶部与根号顶部对齐
        node.index?.let { indexNode ->
            val m = measure(indexNode, indexCtx)
            place(indexNode, x, top + m.ascent, indexCtx, out)
        }
    }

    /** 根式指数实际占用的水平宽度（允许与根号部分重叠）。 */
    private fun indexReservedWidth(index: Measured?): Double =
        index?.let { it.width * (1 - LayoutConstants.RADICAL_INDEX_OVERLAP) } ?: 0.0

    // ============================ 括号 ============================

    private fun measureDelim(node: Delim, ctx: LayoutContext): Measured {
        val content = measure(node.content, ctx)
        val stretch = content.height > LayoutConstants.DELIM_STRETCH_THRESHOLD
        val leftWidth = delimiterWidth(node.left, stretch, ctx)
        val rightWidth = delimiterWidth(node.right, stretch, ctx)

        // 用字形绘制时需要保证字形自身高度也被盒模型容纳
        val glyphAscent = if (stretch) 0.0 else ctx.metrics.ascent * ctx.scale
        val glyphDescent = if (stretch) 0.0 else ctx.metrics.descent * ctx.scale
        val pad = if (stretch) LayoutConstants.DELIM_PADDING else 0.0

        return Measured(
            width = leftWidth + content.width + rightWidth,
            ascent = maxOf(content.ascent + pad, glyphAscent),
            descent = maxOf(content.descent + pad, glyphDescent)
        )
    }

    private fun placeDelim(
        node: Delim,
        x: Double,
        baselineY: Double,
        ctx: LayoutContext,
        out: MutableList<DrawCmd>
    ) {
        val content = measure(node.content, ctx)
        val stretch = content.height > LayoutConstants.DELIM_STRETCH_THRESHOLD
        val leftWidth = delimiterWidth(node.left, stretch, ctx)
        val rightWidth = delimiterWidth(node.right, stretch, ctx)
        val contentX = x + leftWidth
        val rightX = contentX + content.width

        val pad = if (stretch) LayoutConstants.DELIM_PADDING else 0.0
        val top = baselineY - (content.ascent + pad)
        val bottom = baselineY + content.descent + pad

        // 严格按视觉顺序产出指令：左括号 → 内容 → 右括号。
        // 这样指令序列与屏幕顺序一致，后续做点击命中 / 光标定位时可以直接复用。
        placeDelimiterSide(node.left.leftChar, x, leftWidth, top, bottom, baselineY, stretch, ctx, out)
        place(node.content, contentX, baselineY, ctx, out)
        placeDelimiterSide(node.right.rightChar, rightX, rightWidth, top, bottom, baselineY, stretch, ctx, out)
    }

    /** 绘制单侧括号：[ch] 为空格表示该侧不绘制（对应 [DelimKind.NONE]）。 */
    private fun placeDelimiterSide(
        ch: Char,
        x: Double,
        width: Double,
        top: Double,
        bottom: Double,
        baselineY: Double,
        stretch: Boolean,
        ctx: LayoutContext,
        out: MutableList<DrawCmd>
    ) {
        if (ch == ' ') return
        if (stretch) {
            drawStretchyDelimiter(ch, x, width, top, bottom, out)
        } else {
            out.add(DrawCmd.Text(ch.toString(), x, baselineY, ctx.scale))
        }
    }

    private fun delimiterWidth(kind: DelimKind, stretch: Boolean, ctx: LayoutContext): Double = when {
        kind == DelimKind.NONE -> 0.0
        stretch -> kind.pathWidth * ctx.scale
        else -> ctx.metrics.width(kind.leftChar.toString()) * ctx.scale
    }

    /**
     * 按内容高度生成可伸缩括号的轮廓（用采样折线近似曲线）。
     *
     * 这里按**字符**而不是 [DelimKind] 分派 —— 因为同一个 kind 的左右两侧形状是镜像的。
     */
    private fun drawStretchyDelimiter(
        ch: Char,
        x: Double,
        width: Double,
        top: Double,
        bottom: Double,
        out: MutableList<DrawCmd>
    ) {
        if (ch == ' ') return
        val thickness = LayoutConstants.RULE_THICKNESS
        val steps = LayoutConstants.DELIM_CURVE_STEPS
        val mid = (top + bottom) / 2

        when (ch) {
            // 圆括号：二次贝塞尔，控制点取到 2x - (x+w)，使极值恰好落在 x（或 x+w）
            '(' -> out.add(
                DrawCmd.Polyline(
                    sampleQuadratic(x + width, top, x - width, mid, x + width, bottom, steps),
                    thickness
                )
            )
            ')' -> out.add(
                DrawCmd.Polyline(
                    sampleQuadratic(x, top, x + 2 * width, mid, x, bottom, steps),
                    thickness
                )
            )

            '{' -> out.add(
                DrawCmd.Polyline(braceOutline(x, width, top, bottom, openingLeft = true), thickness)
            )
            '}' -> out.add(
                DrawCmd.Polyline(braceOutline(x, width, top, bottom, openingLeft = false), thickness)
            )

            '[' -> out.add(
                DrawCmd.Polyline(
                    listOf(Point2(x + width, top), Point2(x, top), Point2(x, bottom), Point2(x + width, bottom)),
                    thickness
                )
            )
            ']' -> out.add(
                DrawCmd.Polyline(
                    listOf(Point2(x, top), Point2(x + width, top), Point2(x + width, bottom), Point2(x, bottom)),
                    thickness
                )
            )

            '⌊' -> out.add(
                DrawCmd.Polyline(listOf(Point2(x, top), Point2(x, bottom), Point2(x + width, bottom)), thickness)
            )
            '⌋' -> out.add(
                DrawCmd.Polyline(listOf(Point2(x + width, top), Point2(x + width, bottom), Point2(x, bottom)), thickness)
            )
            '⌈' -> out.add(
                DrawCmd.Polyline(listOf(Point2(x, bottom), Point2(x, top), Point2(x + width, top)), thickness)
            )
            '⌉' -> out.add(
                DrawCmd.Polyline(listOf(Point2(x + width, bottom), Point2(x + width, top), Point2(x, top)), thickness)
            )

            '⟨' -> out.add(
                DrawCmd.Polyline(listOf(Point2(x + width, top), Point2(x, mid), Point2(x + width, bottom)), thickness)
            )
            '⟩' -> out.add(
                DrawCmd.Polyline(listOf(Point2(x, top), Point2(x + width, mid), Point2(x, bottom)), thickness)
            )

            '|' -> out.add(
                DrawCmd.Polyline(listOf(Point2(x + width / 2, top), Point2(x + width / 2, bottom)), thickness)
            )
            '‖' -> {
                out.add(
                    DrawCmd.Polyline(listOf(Point2(x + width * 0.25, top), Point2(x + width * 0.25, bottom)), thickness)
                )
                out.add(
                    DrawCmd.Polyline(listOf(Point2(x + width * 0.75, top), Point2(x + width * 0.75, bottom)), thickness)
                )
            }
        }
    }

    private fun braceOutline(
        x: Double,
        width: Double,
        top: Double,
        bottom: Double,
        openingLeft: Boolean
    ): List<Point2> {
        val h = bottom - top
        val mid = (top + bottom) / 2
        val body = if (openingLeft) x + width * 0.3 else x + width * 0.7
        val cusp = if (openingLeft) x else x + width
        val outer = if (openingLeft) x + width else x
        return listOf(
            Point2(outer, top),
            Point2(body, top + 0.15 * h),
            Point2(body, mid - 0.08 * h),
            Point2(cusp, mid),
            Point2(body, mid + 0.08 * h),
            Point2(body, bottom - 0.15 * h),
            Point2(outer, bottom)
        )
    }

    /** 二次贝塞尔采样为折线。 */
    private fun sampleQuadratic(
        x0: Double,
        y0: Double,
        cx: Double,
        cy: Double,
        x1: Double,
        y1: Double,
        steps: Int
    ): List<Point2> = (0..steps).map { i ->
        val t = i.toDouble() / steps
        val u = 1 - t
        Point2(
            u * u * x0 + 2 * u * t * cx + t * t * x1,
            u * u * y0 + 2 * u * t * cy + t * t * y1
        )
    }

    // ============================ 大算符 ============================

    private fun measureBigOp(node: BigOp, ctx: LayoutContext): Measured {
        val op = measureText(node.kind.glyph, ctx.scaled(node.kind.scale))
        val limitCtx = ctx.scaled(LayoutConstants.BIGOP_LIMIT_SCALE)
        val upper = node.upper?.let { measure(it, limitCtx) }
        val lower = node.lower?.let { measure(it, limitCtx) }

        return when (node.kind.limits) {
            LimitsPlacement.ABOVE_BELOW -> {
                val width = maxOf(op.width, upper?.width ?: 0.0, lower?.width ?: 0.0)
                val ascent = (upper?.let { it.height + LayoutConstants.BIGOP_GAP } ?: 0.0) + op.ascent
                val descent = (lower?.let { it.height + LayoutConstants.BIGOP_GAP } ?: 0.0) + op.descent
                Measured(width, ascent, descent)
            }

            LimitsPlacement.SIDE -> {
                val limitsWidth = maxOf(upper?.width ?: 0.0, lower?.width ?: 0.0)
                val width = op.width +
                    (if (limitsWidth > 0.0) LayoutConstants.BIGOP_SIDE_GAP + limitsWidth else 0.0)
                var ascent = op.ascent
                var descent = op.descent
                upper?.let { ascent = maxOf(ascent, supShift(op.ascent, it) + it.ascent) }
                lower?.let { descent = maxOf(descent, subShift(op.descent, it) + it.descent) }
                Measured(width, ascent, descent)
            }

            LimitsPlacement.BELOW -> {
                val width = maxOf(op.width, lower?.width ?: 0.0)
                val descent = (lower?.let { it.height + LayoutConstants.BIGOP_GAP } ?: 0.0) + op.descent
                Measured(width, op.ascent, descent)
            }
        }
    }

    private fun placeBigOp(
        node: BigOp,
        x: Double,
        baselineY: Double,
        ctx: LayoutContext,
        out: MutableList<DrawCmd>
    ) {
        val opCtx = ctx.scaled(node.kind.scale)
        val op = measureText(node.kind.glyph, opCtx)
        val limitCtx = ctx.scaled(LayoutConstants.BIGOP_LIMIT_SCALE)
        val lower = node.lower?.let { measure(it, limitCtx) }
        val upper = node.upper?.let { measure(it, limitCtx) }

        // 递归顺序必须与 Slots.slots() 一致：[下限, 上限]，否则光标探针会错位。
        when (node.kind.limits) {
            LimitsPlacement.ABOVE_BELOW -> {
                val width = maxOf(op.width, lower?.width ?: 0.0, upper?.width ?: 0.0)
                out.add(
                    DrawCmd.Text(node.kind.glyph, x + (width - op.width) / 2, baselineY, opCtx.scale)
                )
                if (node.lower != null && lower != null) {
                    place(
                        node.lower,
                        x + (width - lower.width) / 2,
                        baselineY + op.descent + LayoutConstants.BIGOP_GAP + lower.ascent,
                        limitCtx,
                        out
                    )
                }
                if (node.upper != null && upper != null) {
                    place(
                        node.upper,
                        x + (width - upper.width) / 2,
                        baselineY - op.ascent - LayoutConstants.BIGOP_GAP - upper.descent,
                        limitCtx,
                        out
                    )
                }
            }

            LimitsPlacement.SIDE -> {
                out.add(DrawCmd.Text(node.kind.glyph, x, baselineY, opCtx.scale))
                val limitX = x + op.width + LayoutConstants.BIGOP_SIDE_GAP
                if (node.lower != null && lower != null) {
                    place(node.lower, limitX, baselineY + subShift(op.descent, lower), limitCtx, out)
                }
                if (node.upper != null && upper != null) {
                    place(node.upper, limitX, baselineY - supShift(op.ascent, upper), limitCtx, out)
                }
            }

            LimitsPlacement.BELOW -> {
                val width = maxOf(op.width, lower?.width ?: 0.0)
                out.add(
                    DrawCmd.Text(node.kind.glyph, x + (width - op.width) / 2, baselineY, opCtx.scale)
                )
                if (node.lower != null && lower != null) {
                    place(
                        node.lower,
                        x + (width - lower.width) / 2,
                        baselineY + op.descent + LayoutConstants.BIGOP_GAP + lower.ascent,
                        limitCtx,
                        out
                    )
                }
            }
        }
    }
}
