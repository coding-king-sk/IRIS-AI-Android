package com.irisx.ai.core.agent

import android.content.Context
import com.irisx.ai.core.tools.AlarmTool
import com.irisx.ai.core.tools.BatteryTool
import com.irisx.ai.core.tools.BrightnessTool
import com.irisx.ai.core.tools.CalculatorTool
import com.irisx.ai.core.tools.CalendarAddTool
import com.irisx.ai.core.tools.CalendarTodayTool
import com.irisx.ai.core.tools.CallTool
import com.irisx.ai.core.tools.ClockTool
import com.irisx.ai.core.tools.ConnectivityTool
import com.irisx.ai.core.tools.ContactInfoTool
import com.irisx.ai.core.tools.FlashlightTool
import com.irisx.ai.core.tools.MediaTool
import com.irisx.ai.core.tools.NoteTool
import com.irisx.ai.core.tools.NotificationDigestTool
import com.irisx.ai.core.tools.OpenAppTool
import com.irisx.ai.core.tools.ReadNotesTool
import com.irisx.ai.core.tools.ReadNotificationsTool
import com.irisx.ai.core.tools.ReadScreenTool
import com.irisx.ai.core.tools.ReminderAddTool
import com.irisx.ai.core.tools.ReminderListTool
import com.irisx.ai.core.tools.ScreenActionTool
import com.irisx.ai.core.tools.ScreenshotTool
import com.irisx.ai.core.tools.SettingsPanelTool
import com.irisx.ai.core.tools.SmsTool
import com.irisx.ai.core.tools.TimerTool
import com.irisx.ai.core.tools.UnitConvertTool
import com.irisx.ai.core.tools.VolumeTool
import com.irisx.ai.core.tools.WebSearchTool
import com.irisx.ai.core.tools.WhatsAppTool

class ToolRegistry(private val context: Context) {

    val tools: List<IrisTool> = listOf(
        OpenAppTool(),
        CallTool(),
        SmsTool(),
        WhatsAppTool(),
        AlarmTool(),
        TimerTool(),
        ReminderAddTool(),
        ReminderListTool(),
        FlashlightTool(),
        VolumeTool(),
        BrightnessTool(),
        MediaTool(),
        NoteTool(),
        ReadNotesTool(),
        ScreenActionTool(),
        ReadScreenTool(),
        ScreenshotTool(),
        ReadNotificationsTool(),
        NotificationDigestTool(),
        BatteryTool(),
        ClockTool(),
        SettingsPanelTool(),
        ConnectivityTool(),
        CalculatorTool(),
        UnitConvertTool(),
        ContactInfoTool(),
        CalendarAddTool(),
        CalendarTodayTool(),
        WebSearchTool()
    )

    private val byName = tools.associateBy { it.name }

    fun execute(call: ToolCall): ToolResult {
        val tool = byName[call.name]
            ?: return ToolResult(false, "Ye kaam mere tools me nahi hai: " + call.name)
        return runCatching { tool.run(context, call.args) }
            .getOrElse { ToolResult(false, "Tool fail hua: " + (it.message ?: "unknown error")) }
    }
}
