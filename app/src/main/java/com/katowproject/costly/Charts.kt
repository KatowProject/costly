package com.katowproject.costly

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.katowproject.costly.ui.theme.*
import java.time.YearMonth

fun categoryTotals(data: FinanceData, month: YearMonth): List<Pair<Category, Long>> = data.categories.filter { it.type == "EXPENSE" }.map { it to data.spent(it.id, month) }.filter { it.second > 0 }.sortedByDescending { it.second }

@Composable
fun CategoryDonut(data: FinanceData, month: YearMonth) {
    val strings = LocalStrings.current
    val total = data.total("EXPENSE", month)
    val parts = categoryTotals(data, month)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Box(Modifier.size(140.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(136.dp)) {
                val thickness = 20.dp.toPx()
                val inset = thickness / 2
                if (total == 0L) drawArc(Line, 0f, 360f, false, Offset(inset, inset), Size(size.width - thickness, size.height - thickness), style = Stroke(thickness))
                else {
                    var start = -90f
                    parts.forEach { (category, amount) ->
                        val sweep = amount.toFloat() / total * 360f
                        drawArc(Color(category.color), start + .7f, (sweep - 1.4f).coerceAtLeast(.1f), false, Offset(inset, inset), Size(size.width - thickness, size.height - thickness), style = Stroke(thickness))
                        start += sweep
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(strings.expense, fontSize = 10.sp, color = Muted)
                Text(formatRupiah(total, strings.lang), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            (if (parts.isEmpty()) data.categories.filter { it.type == "EXPENSE" }.take(4).map { it to 0L } else parts.take(5)).forEach { (category, amount) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(Color(category.color), CircleShape)); Spacer(Modifier.width(6.dp))
                    Text(localizedCategoryName(category.name, strings.lang), Modifier.weight(1f), fontSize = 10.sp, maxLines = 1)
                    Text(if (total == 0L) "0%" else "${amount * 100 / total}%", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun CashFlowChart(data: FinanceData, month: YearMonth) {
    val strings = LocalStrings.current
    val entries = data.monthly(month)
    val income = (1..month.lengthOfMonth()).map { day -> entries.filter { it.type == "INCOME" && it.date.dayOfMonth <= day }.sumOf { it.amount }.toFloat() }
    val expense = (1..month.lengthOfMonth()).map { day -> entries.filter { it.type == "EXPENSE" && it.date.dayOfMonth <= day }.sumOf { it.amount }.toFloat() }
    val maximum = maxOf(income.maxOrNull() ?: 0f, expense.maxOrNull() ?: 0f, 1f) * 1.15f
    val millionUnit = if (strings.lang == AppLanguage.EN) "M" else "jt"
    val thousandUnit = if (strings.lang == AppLanguage.EN) "k" else "rb"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        listOf(strings.income to Green, strings.expense to Red).forEach { (name, color) ->
            Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(color, CircleShape)); Spacer(Modifier.width(5.dp)); Text(name, fontSize = 9.sp, color = Muted)
            }
        }
    }
    Spacer(Modifier.height(18.dp))
    Row(Modifier.fillMaxWidth().height(160.dp)) {
        Column(Modifier.width(39.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            (4 downTo 0).forEach { i ->
                val v = (maximum * i / 4).toLong()
                Text(when { v >= 1_000_000 -> "${v / 1_000_000} $millionUnit"; v >= 1000 -> "${v / 1000} $thousandUnit"; else -> v.toString() }, fontSize = 9.sp, color = Muted)
            }
        }
        Canvas(Modifier.weight(1f).fillMaxHeight().padding(vertical = 5.dp)) {
            (0..4).forEach { row -> val y = size.height * row / 4; drawLine(Line, Offset(0f, y), Offset(size.width, y), 1.dp.toPx()) }
            listOf(income to Green, expense to Red).forEach { (values, color) ->
                val path = Path()
                values.forEachIndexed { index, value -> val x = size.width * index / (values.size - 1); val y = size.height * (1 - value / maximum); if (index == 0) path.moveTo(x, y) else path.lineTo(x, y) }
                val fill = Path().apply { addPath(path); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
                drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = .13f), color.copy(alpha = 0f))))
                drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            }
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 39.dp, top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf(1, 7, 14, 21, month.lengthOfMonth()).forEach {
            Text(it.toString() + " " + month.format(java.time.format.DateTimeFormatter.ofPattern("MMM", strings.lang.locale)), fontSize = 8.sp, color = Muted)
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(strings.cashFlowAccumulation, fontSize = 10.sp, color = Muted)
}
