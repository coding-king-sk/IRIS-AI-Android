package com.irisx.ai.core.agent

import android.content.Context
import com.irisx.ai.core.tools.AlarmTool
import com.irisx.ai.core.tools.BatteryTool
import com.irisx.ai.core.tools.BluetoothTool
import com.irisx.ai.core.tools.BrightnessTool
import com.irisx.ai.core.tools.CalculatorTool
import com.irisx.ai.core.tools.CalendarAddTool
import com.irisx.ai.core.tools.CalendarTodayTool
import com.irisx.ai.core.tools.CallTool
import com.irisx.ai.core.tools.CameraTool
import com.irisx.ai.core.tools.CameraVisionTool
import com.irisx.ai.core.tools.ClockTool
import com.irisx.ai.core.tools.ConnectivityTool
import com.irisx.ai.core.tools.ContactInfoTool
import com.irisx.ai.core.tools.DndTool
import com.irisx.ai.core.tools.ExplainScreenTool
import com.irisx.ai.core.tools.FillFormTool
import com.irisx.ai.core.tools.FlashlightTool
import com.irisx.ai.core.tools.HotspotTool
import com.irisx.ai.core.tools.InstagramSendTool
import com.irisx.ai.core.tools.LocationShareTool
import com.irisx.ai.core.tools.MacroListTool
import com.irisx.ai.core.tools.MacroRunTool
import com.irisx.ai.core.tools.MacroSaveTool
import com.irisx.ai.core.tools.MediaTool
import com.irisx.ai.core.tools.MemorySearchTool
import com.irisx.ai.core.tools.MusicControlTool
import com.irisx.ai.core.tools.NeuralWakeSetupTool
import com.irisx.ai.core.tools.NeuralWakeStatusTool
import com.irisx.ai.core.tools.NoteTool
import com.irisx.ai.core.tools.NotificationDigestTool
import com.irisx.ai.core.tools.NowPlayingTool
import com.irisx.ai.core.tools.OpenAppTool
import com.irisx.ai.core.tools.PlayMusicTool
import com.irisx.ai.core.tools.ReadNotesTool
import com.irisx.ai.core.tools.ReadNotificationsTool
import com.irisx.ai.core.tools.ReadScreenTool
import com.irisx.ai.core.tools.ReminderAddTool
import com.irisx.ai.core.tools.ReminderListTool
import com.irisx.ai.core.tools.ReplyScreenTool
import com.irisx.ai.core.tools.RingerTool
import com.irisx.ai.core.tools.ScreenActionTool
import com.irisx.ai.core.tools.ScreenshotTool
import com.irisx.ai.core.tools.SendPhotoTool
import com.irisx.ai.core.tools.SettingsPanelTool
import com.irisx.ai.core.tools.SmsTool
import com.irisx.ai.core.tools.TapTool
import com.irisx.ai.core.tools.TelegramSendTool
import com.irisx.ai.core.tools.TimerTool
import com.irisx.ai.core.tools.TypeTextTool
import com.irisx.ai.core.tools.UnitConvertTool
import com.irisx.ai.core.tools.VoiceStatusTool
import com.irisx.ai.core.tools.VolumeTool
import com.irisx.ai.core.tools.VoskSetupTool
import com.irisx.ai.core.tools.VoskStatusTool
import com.irisx.ai.core.tools.WebSearchTool
import com.irisx.ai.core.tools.WhatsAppSendTool
import com.irisx.ai.core.tools.WhatsAppTool
import com.irisx.ai.core.tools.YoutubeSearchTool

class ToolRegistry(private val context: Context) {

    val tools: List<IrisTool> = listOf(
        OpenAppTool(),
        CallTool(),
        SmsTool(),
        WhatsAppTool(),
        WhatsAppSendTool(),
        InstagramSendTool(),
        TelegramSendTool(),
        SendPhotoTool(),
        LocationShareTool(),
        PlayMusicTool(),
        YoutubeSearchTool(),
        MusicControlTool(),
        NowPlayingTool(),
        CameraTool(),
        CameraVisionTool(),
        AlarmTool(),
        TimerTool(),
        ReminderAddTool(),
        ReminderListTool(),
        FlashlightTool(),
        VolumeTool(),
        BrightnessTool(),
        MediaTool(),
        DndTool(),
        RingerTool(),
        BluetoothTool(),
        HotspotTool(),
        NoteTool(),
        ReadNotesTool(),
        MemorySearchTool(),
        ScreenActionTool(),
        ReadScreenTool(),
        ExplainScreenTool(),
        ReplyScreenTool(),
        ScreenshotTool(),
        TapTool(),
        TypeTextTool(),
        FillFormTool(),
        ReadNotificationsTool(),
        NotificationDigestTool(),
        BatteryTool(),
        ClockTool(),
        VoiceStatusTool(),
        VoskSetupTool(),
        VoskStatusTool(),
        NeuralWakeSetupTool(),
        NeuralWakeStatusTool(),
        SettingsPanelTool(),
        ConnectivityTool(),
        CalculatorTool(),
        UnitConvertTool(),
        ContactInfoTool(),
        CalendarAddTool(),
        CalendarTodayTool(),
        MacroSaveTool(),
        MacroRunTool(),
        MacroListTool(),
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
