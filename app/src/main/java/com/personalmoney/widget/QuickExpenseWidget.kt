package com.personalmoney.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.Action
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.personalmoney.MainActivity
import com.personalmoney.R

class QuickExpenseWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetContent(context = context)
            }
        }
    }
}

@Composable
private fun WidgetContent(context: Context) {
    val primary = GlanceTheme.colors.primary
    val onPrimary = GlanceTheme.colors.onPrimary
    val surface = GlanceTheme.colors.surface
    val onSurface = GlanceTheme.colors.onSurface
    val secondary = GlanceTheme.colors.secondary
    val onSecondary = GlanceTheme.colors.onSecondary

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(surface)
            .cornerRadius(20.dp)
            .padding(12.dp)
            .clickable(openQuickAddAction(context, "EXPENSE")),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = context.getString(R.string.widget_quick_expense),
            style = TextStyle(
                color = onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        Box(
            modifier = GlanceModifier
                .size(48.dp)
                .background(primary)
                .cornerRadius(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "+",
                style = TextStyle(
                    color = onPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
        Spacer(modifier = GlanceModifier.height(8.dp))
        Row(modifier = GlanceModifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            ActionPill(label = "−", color = primary, textColor = onPrimary, action = openQuickAddAction(context, "EXPENSE"))
            Spacer(modifier = GlanceModifier.width(8.dp))
            ActionPill(label = "+", color = secondary, textColor = onSecondary, action = openQuickAddAction(context, "INCOME"))
        }
    }
}

@Composable
private fun ActionPill(
    label: String,
    color: ColorProvider,
    textColor: ColorProvider,
    action: Action
) {
    Box(
        modifier = GlanceModifier
            .size(width = 56.dp, height = 32.dp)
            .background(color)
            .cornerRadius(16.dp)
            .clickable(action),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = textColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

private fun openQuickAddAction(context: Context, type: String): Action {
    val intent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = Uri.parse("personalmoney://quickadd?type=$type&categoryId=")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }
    return actionStartActivity(intent)
}
