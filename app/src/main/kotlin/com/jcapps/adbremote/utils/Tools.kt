package com.jcapps.adbremote.utils

import com.jcapps.adbremote.R
import com.jcapps.adbremote.model.Tool
import com.jcapps.adbremote.model.ToolType

// obf: AbstractC0650.f4445 — RECON-UNSURE: file/val name
val tools = listOf(
    Tool(ToolType.APPS, R.string.apps, R.drawable.ic_apps, R.color.tool_apps),
    Tool(ToolType.PROCESSES, R.string.processes, R.drawable.ic_processes, R.color.tool_processes),
    Tool(ToolType.FILES, R.string.files, R.drawable.ic_files, R.color.tool_files),
    Tool(ToolType.CLIPBOARD, R.string.clipboard, R.drawable.ic_clipboard, R.color.tool_clipboard),
    Tool(ToolType.PERFORMANCE, R.string.performance, R.drawable.ic_performance, R.color.tool_performance),
    Tool(ToolType.SCREENSHOT, R.string.screenshot, R.drawable.ic_screenshot_tool, R.color.tool_screenshot),
    Tool(ToolType.MIRROR, R.string.mirror, R.drawable.ic_mirror_tool, R.color.tool_mirror), // rebuild addition
    Tool(ToolType.DEVICE_INFO, R.string.device_info, R.drawable.ic_device_info, R.color.tool_device_info),
    Tool(ToolType.NTP, R.string.ntp, R.drawable.ic_ntp, R.color.tool_ntp),
)
