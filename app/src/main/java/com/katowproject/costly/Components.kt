package com.katowproject.costly

import android.app.DatePickerDialog
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.katowproject.costly.ui.theme.*
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun AppCard(modifier: Modifier = Modifier, color: Color = Color.White, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(color).border(1.dp, Line, RoundedCornerShape(20.dp)).padding(18.dp), content = content)
}
@Composable
fun Heading(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
        if (action != null) TextButton(onClick = onAction, contentPadding = PaddingValues(0.dp)) { Text(action, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
    }
}
@Composable
fun PrimaryButton(text: String, icon: String? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Button(onClick, Modifier.fillMaxWidth().height(54.dp), enabled = enabled, shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(12.dp)) {
        if (icon != null) { Glyph(icon, Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}
@Composable
fun SoftButton(text: String, icon: String = "plus", onClick: () -> Unit) {
    FilledTonalButton(onClick, Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.filledTonalButtonColors(containerColor = Lavender, contentColor = Purple)) {
        Glyph(icon, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
@Composable
fun IconTile(icon: String, color: Color = Purple, size: Int = 42) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(13.dp)).background(color.copy(alpha = .09f)), contentAlignment = Alignment.Center) { Glyph(icon, Modifier.size((size * .5f).dp), tint = color) }
}
@Composable
fun Field(label: String, value: String, onValue: (String) -> Unit, placeholder: String = "", numeric: Boolean = false, password: Boolean = false, lines: Int = 1) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        OutlinedTextField(value, onValueChange = { if (!numeric || it.all(Char::isDigit)) onValue(it) }, modifier = Modifier.fillMaxWidth(), placeholder = { Text(placeholder, fontSize = 13.sp, color = Muted) },
            singleLine = lines == 1, minLines = lines, shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Number else if (password) KeyboardType.Password else KeyboardType.Text),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Line, focusedBorderColor = Purple), textStyle = LocalTextStyle.current.copy(fontSize = 14.sp))
    }
}
@Composable
fun <T> SelectField(label: String, options: List<T>, selected: T?, name: (T) -> String, onSelect: (T) -> Unit, icon: String = "grid") {
    val strings = LocalStrings.current
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Ink)
        Box {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, Line, RoundedCornerShape(12.dp)).clickable { expanded = true }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Glyph(icon, Modifier.size(18.dp), tint = Purple); Spacer(Modifier.width(10.dp))
                Text(selected?.let(name) ?: strings.selectFieldPlaceholder(label), Modifier.weight(1f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Glyph("down", Modifier.size(16.dp), tint = Muted)
            }
            DropdownMenu(expanded, { expanded = false }) { options.forEach { option -> DropdownMenuItem(text = { Text(name(option), fontSize = 14.sp) }, onClick = { onSelect(option); expanded = false }) } }
        }
    }
}
@Composable
fun DateField(label: String, date: LocalDate, onDate: (LocalDate) -> Unit) {
    val strings = LocalStrings.current
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, Line, RoundedCornerShape(12.dp)).clickable {
            DatePickerDialog(context, { _, year, month, day -> onDate(LocalDate.of(year, month + 1, day)) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
        }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Glyph("calendar", Modifier.size(19.dp), tint = Green); Spacer(Modifier.width(10.dp)); Text(localizedDateLabel(date, strings.lang), Modifier.weight(1f), fontSize = 14.sp); Glyph("down", Modifier.size(16.dp), tint = Muted)
        }
    }
}
@Composable
fun Segments(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Color(0xFFF1EEF7)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        options.forEachIndexed { i, text ->
            Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (i == selected) Color.White else Color.Transparent).clickable { onSelect(i) }.padding(vertical = 11.dp), contentAlignment = Alignment.Center) {
                Text(text, color = if (i == selected) Purple else Muted, fontSize = 12.sp, fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Medium)
            }
        }
    }
}
@Composable
fun MonthChip(month: YearMonth, onMonth: (YearMonth) -> Unit) {
    val strings = LocalStrings.current
    var open by remember { mutableStateOf(false) }
    Surface(onClick = { open = true }, shape = RoundedCornerShape(20.dp), color = Color.White, border = BorderStroke(1.dp, Line)) {
        Row(Modifier.padding(horizontal = 13.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) { Glyph("calendar", Modifier.size(14.dp), tint = Purple); Spacer(Modifier.width(7.dp)); Text(localizedMonthLabel(month, strings.lang), fontSize = 12.sp, fontWeight = FontWeight.Medium); Spacer(Modifier.width(7.dp)); Glyph("down", Modifier.size(12.dp), tint = Muted) }
    }
    if (open) {
        var year by remember { mutableIntStateOf(month.year) }
        AlertDialog(onDismissRequest = { open = false }, title = { Text(strings.selectPeriod) }, text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) { IconButton({ year-- }) { Glyph("arrow") }; Text(year.toString(), Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center); IconButton({ year++ }) { Glyph("chevron") } }
                (0..3).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        (1..3).forEach { col ->
                            val m = YearMonth.of(year, row * 3 + col)
                            TextButton({ onMonth(m); open = false }, Modifier.weight(1f)) {
                                Text(m.format(java.time.format.DateTimeFormatter.ofPattern("MMM", strings.lang.locale)))
                            }
                        }
                    }
                }
            }
        }, confirmButton = { TextButton({ onMonth(YearMonth.now()); open = false }) { Text(strings.thisMonth) } })
    }
}
@Composable
fun EmptyState(title: String, subtitle: String, icon: String = "list", action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        IconTile(icon, Purple, 56)
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = Muted, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (action != null) TextButton(onAction) { Glyph("plus", Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text(action) }
    }
}
@Composable
fun ProgressBar(fraction: Float, color: Color = Purple) {
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(8.dp)).background(Line)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(Brush.horizontalGradient(listOf(color, color.copy(alpha = .65f)))))
    }
}
@Composable
fun ScreenTitle(title: String, subtitle: String? = null, back: (() -> Unit)? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (back != null) { IconButton(back, Modifier.size(36.dp)) { Glyph("arrow", tint = Ink) }; Spacer(Modifier.width(8.dp)) }
        Column(Modifier.weight(1f)) { Text(title, fontSize = 23.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.7).sp); if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 5.dp)) }
        trailing()
    }
}
@Composable
fun SettingsRow(icon: String, title: String, detail: String? = null, onClick: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, Purple, 38); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) { Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold); if (detail != null) Text(detail, fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 4.dp)) }
        if (trailing != null) trailing() else Glyph("chevron", Modifier.size(16.dp), tint = Muted)
    }
}
@Composable
fun FormDialog(title: String, onDismiss: () -> Unit, valid: Boolean, busy: Boolean = false, onSave: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val strings = LocalStrings.current
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, title = { Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp), content = content) },
        confirmButton = { TextButton(onSave, enabled = valid && !busy) { Text(if (busy) strings.savingProgress else strings.save) } },
        dismissButton = { TextButton(onDismiss, enabled = !busy) { Text(strings.cancel, color = Muted) } }, containerColor = Page)
}
