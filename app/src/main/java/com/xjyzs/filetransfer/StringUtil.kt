package com.xjyzs.filetransfer

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight

@Composable
fun StringDisplay(input: String) {
    val colors = mapOf(
        30 to Color.Black,
        31 to MaterialTheme.colorScheme.error,
        33 to MaterialTheme.colorScheme.primary,
        34 to Color.Blue,
        35 to Color.Magenta,
        36 to Color.Cyan,
        37 to Color.White
    )
    val builder = AnnotatedString.Builder()

    var currentColor: Color? = null
    var bold = false

    val regex = Regex("\u001B\\[([0-9;]+)m")
    var lastIndex = 0

    regex.findAll(input).forEach { match ->
        val start = match.range.first
        if (start > lastIndex) {
            builder.pushStyle(
                SpanStyle(
                    color = currentColor ?: Color.Unspecified,
                    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
                )
            )
            builder.append(input.substring(lastIndex, start))
            builder.pop()
        }
        val codes = match.groupValues[1].split(";").mapNotNull { it.toIntOrNull() }
        for (code in codes) {
            when {
                code == 0 -> {
                    currentColor = null
                    bold = false
                }

                code == 1 -> bold = true
                code in 30..37 -> currentColor = colors[code]
            }
        }

        lastIndex = match.range.last + 1
    }
    if (lastIndex < input.length) {
        builder.pushStyle(
            SpanStyle(
                color = currentColor ?: Color.Unspecified,
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
            )
        )
        builder.append(input.substring(lastIndex))
        builder.pop()
    }

    Text(builder.toAnnotatedString())
}
