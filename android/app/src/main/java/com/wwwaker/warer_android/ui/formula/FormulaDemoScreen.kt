package com.wwwaker.warer_android.ui.formula

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wwwaker.warer.core.formula.model.BigOp
import com.wwwaker.warer.core.formula.model.BigOpKind
import com.wwwaker.warer.core.formula.model.Delim
import com.wwwaker.warer.core.formula.model.DelimKind
import com.wwwaker.warer.core.formula.model.Frac
import com.wwwaker.warer.core.formula.model.Func
import com.wwwaker.warer.core.formula.model.Node
import com.wwwaker.warer.core.formula.model.Num
import com.wwwaker.warer.core.formula.model.Row
import com.wwwaker.warer.core.formula.model.Script
import com.wwwaker.warer.core.formula.model.Sqrt
import com.wwwaker.warer.core.formula.model.Sym

/**
 * 原生公式排版演示页（开发用）。
 *
 * 存在的意义：让 `:core` 的布局引擎**肉眼可见**。
 * 这里没有任何 WebView / KaTeX，全部是 Canvas 直接绘制。
 */
@Composable
fun FormulaDemoScreen(modifier: Modifier = Modifier) {
    val samples = remember { demoFormulas() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("原生公式排版演示", style = MaterialTheme.typography.titleMedium)
        Text(
            "几何全部由 :core 的布局引擎算出，Canvas 直接绘制 —— 无 WebView、无 KaTeX。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        samples.forEach { (label, node) ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FormulaView(node = node, fontSize = 30.sp)
            }
        }
    }
}

// ============================ AST 构造小工具 ============================

private fun row(vararg nodes: Node) = Row(nodes.toList())
private fun n(text: String) = Num(text)
private fun v(ch: Char) = Sym(ch)
private fun gap() = Sym(' ')
private fun frac(a: Node, b: Node) = Frac(Row(listOf(a)), Row(listOf(b)))
private fun sup(base: Node, exponent: Node) = Script(Row(listOf(base)), superscript = Row(listOf(exponent)))
private fun sub(base: Node, index: Node) = Script(Row(listOf(base)), subscript = Row(listOf(index)))
private fun sqrt(x: Node) = Sqrt(Row(listOf(x)))

private fun demoFormulas(): List<Pair<String, Node>> = listOf(
    "分式：1/2" to frac(n("1"), n("2")),

    "上下标：x²、aₙ、x₁²" to row(
        sup(v('x'), n("2")), gap(),
        sub(v('a'), v('n')), gap(),
        Script(
            Row(listOf(v('x'))),
            superscript = Row(listOf(n("2"))),
            subscript = Row(listOf(n("1")))
        )
    ),

    "根号：√2、√(a²+b²)" to row(
        sqrt(n("2")), gap(),
        sqrt(row(sup(v('a'), n("2")), v('+'), sup(v('b'), n("2"))))
    ),

    "嵌套：1/√2" to frac(n("1"), sqrt(n("2"))),

    "n 次方根：∛8" to Sqrt(Row(listOf(n("8"))), index = Row(listOf(n("3")))),

    "复合分式：(1+x²)/√(1-x²)" to frac(
        row(n("1"), v('+'), sup(v('x'), n("2"))),
        sqrt(row(n("1"), v('-'), sup(v('x'), n("2"))))
    ),

    "二次求根公式" to row(
        v('x'), v('='),
        frac(
            row(
                v('-'), v('b'), v('±'),
                sqrt(row(sup(v('b'), n("2")), v('-'), n("4"), v('a'), v('c')))
            ),
            row(n("2"), v('a'))
        )
    ),

    "括号（矮内容 → 字体字形）：(1+x²)" to Delim(
        DelimKind.PAREN,
        Row(listOf(n("1"), v('+'), sup(v('x'), n("2")))),
        DelimKind.PAREN
    ),

    "括号（高内容 → 矢量伸缩）：(1/x)" to Delim(
        DelimKind.PAREN,
        Row(listOf(frac(n("1"), v('x')))),
        DelimKind.PAREN
    ),

    "绝对值 / 方括号 / 花括号" to row(
        Delim(DelimKind.ABS, row(v('x'), v('-'), n("1")), DelimKind.ABS),
        gap(),
        Delim(DelimKind.BRACKET, row(n("1"), v(','), n("2")), DelimKind.BRACKET),
        gap(),
        Delim(DelimKind.BRACE, row(sup(v('x'), n("2"))), DelimKind.BRACE)
    ),

    "求和：∑(0→n) x²" to row(
        BigOp(BigOpKind.SUM, lower = Row(listOf(n("0"))), upper = Row(listOf(v('n')))),
        gap(),
        sup(v('x'), n("2"))
    ),

    "积分：∫(0→1) sin(x) dx" to row(
        BigOp(BigOpKind.INTEGRAL, lower = Row(listOf(n("0"))), upper = Row(listOf(n("1")))),
        gap(),
        Func("sin"), v('('), v('x'), v(')'), v('d'), v('x')
    ),

    "连乘：∏(k→n) k" to row(
        BigOp(BigOpKind.PRODUCT, lower = Row(listOf(v('k'))), upper = Row(listOf(v('n')))),
        gap(),
        v('k')
    ),

    "极限：lim(x→0) sin(x)/x" to row(
        BigOp(BigOpKind.LIM, lower = Row(listOf(v('x'), v('→'), n("0")))),
        gap(),
        frac(row(Func("sin"), v('('), v('x'), v(')')), row(v('x')))
    )
)
