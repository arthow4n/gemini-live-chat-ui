package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ChatMessage
import com.example.data.ChatThread
import com.example.data.SettingsStore
import com.example.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import java.io.File

// Dark Cyber/Obsidian Palette
val ObsidianBackground = Color(0xFF09090B)
val ObsidianSurface = Color(0xFF121217)
val ObsidianCard = Color(0xFF1B1B22)
val CyberCyan = Color(0xFF00F0FF)
val CyberPurple = Color(0xFF9D00FF)
val CyberPink = Color(0xFFFF007F)
val SoftGray = Color(0xFF8E8E9F)
val GhostWhite = Color(0xFFF3F3F7)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatApp(
    viewModel: ChatViewModel,
    requestMicrophonePermission: (onGranted: () -> Unit) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val allThreads by viewModel.allThreads.collectAsStateWithLifecycle()
    val activeThreadId by viewModel.activeThreadId.collectAsStateWithLifecycle()
    val activeThread by viewModel.activeThread.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()

    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val playingMessageId by viewModel.playingMessageId.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()

    // Settings
    val customApiKey by viewModel.customApiKey.collectAsStateWithLifecycle()
    val defaultSystemPrompt by viewModel.defaultSystemPrompt.collectAsStateWithLifecycle()
    val defaultModel by viewModel.defaultModel.collectAsStateWithLifecycle()
    val defaultVoice by viewModel.defaultVoice.collectAsStateWithLifecycle()
    val handsFreeMode by viewModel.handsFreeMode.collectAsStateWithLifecycle()

    // Dialog states
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showNewThreadDialog by remember { mutableStateOf(false) }
    var showTunerPanel by remember { mutableStateOf(false) }
    var showDebugLogs by remember { mutableStateOf(false) }

    // LazyColumn Scroll State
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, isRecording, isLoading) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = ObsidianSurface,
                drawerContentColor = GhostWhite,
                modifier = Modifier.width(320.dp)
            ) {
                // Drawer Title
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Logo",
                                tint = CyberCyan,
                                modifier = Modifier
                                    .size(32.dp)
                                    .padding(end = 8.dp)
                            )
                            Text(
                                text = "GEMINI LIVE",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = GhostWhite
                            )
                        }
                        Text(
                            text = "Vocal Assistant Platform",
                            fontSize = 11.sp,
                            color = SoftGray,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Divider(color = ObsidianCard, thickness = 1.dp)

                // Threads Action
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "CHATS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = SoftGray,
                        fontFamily = FontFamily.Monospace
                    )
                    IconButton(
                        onClick = { showNewThreadDialog = true },
                        modifier = Modifier.testTag("new_thread_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Thread",
                            tint = CyberCyan
                        )
                    }
                }

                // Threads List
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (allThreads.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No vocal threads yet.\nTap '+' to launch.",
                                    textAlign = TextAlign.Center,
                                    color = SoftGray,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        items(allThreads) { thread ->
                            val isSelected = thread.id == activeThreadId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) ObsidianCard else Color.Transparent)
                                    .clickable {
                                        viewModel.selectThread(thread.id)
                                        coroutineScope.launch { drawerState.close() }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChatBubbleOutline,
                                    contentDescription = "Thread",
                                    tint = if (isSelected) CyberCyan else SoftGray,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = thread.title,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) GhostWhite else SoftGray,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = thread.modelName.substringAfterLast("/"),
                                        fontSize = 10.sp,
                                        color = SoftGray.copy(alpha = 0.6f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = { viewModel.deleteThread(thread) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Thread",
                                        tint = Color.Red.copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Divider(color = ObsidianCard, thickness = 1.dp)

                // Settings Footer Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showApiKeyDialog = true }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = SoftGray
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "SYSTEM TUNING",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = GhostWhite,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (customApiKey.isBlank()) "Demo API Key (Configured)" else "Custom User API Key Active",
                            fontSize = 10.sp,
                            color = if (customApiKey.isBlank()) CyberCyan else CyberPink,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = ObsidianSurface,
                        titleContentColor = GhostWhite
                    ),
                    title = {
                        Column {
                            Text(
                                text = activeThread?.title ?: "Select Thread",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            activeThread?.let {
                                Text(
                                    text = "Model: ${it.modelName} | Voice: ${it.voiceName}",
                                    fontSize = 10.sp,
                                    color = SoftGray,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menu",
                                tint = CyberCyan
                            )
                        }
                    },
                    actions = {
                        if (activeThread != null) {
                            IconButton(
                                onClick = { viewModel.saveHandsFreeMode(!handsFreeMode) },
                                modifier = Modifier.testTag("hands_free_toggle_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Hearing,
                                    contentDescription = "Toggle Hands-Free Mode",
                                    tint = if (handsFreeMode) CyberCyan else SoftGray
                                )
                            }
                            IconButton(
                                onClick = { showDebugLogs = !showDebugLogs },
                                modifier = Modifier.testTag("debug_logs_toggle_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BugReport,
                                    contentDescription = "Toggle Debug Logs",
                                    tint = if (showDebugLogs) CyberPink else SoftGray
                                )
                            }
                            IconButton(
                                onClick = { showTunerPanel = !showTunerPanel },
                                modifier = Modifier.testTag("tuner_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (showTunerPanel) Icons.Default.CheckCircle else Icons.Default.Tune,
                                    contentDescription = "Tune Active Thread",
                                    tint = if (showTunerPanel) CyberCyan else SoftGray
                                )
                            }
                        }
                    }
                )
            },
            containerColor = ObsidianBackground
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Collapsible Tuner Panel
                AnimatedVisibility(
                    visible = showTunerPanel && activeThread != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    activeThread?.let { thread ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                            shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, ObsidianCard, RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
                                .padding(16.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "THREAD CONFIGURATION (POWER USER)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberCyan,
                                    fontFamily = FontFamily.Monospace
                                )

                                // Active Thread Prompt Editor
                                var currentPromptText by remember(thread.id) { mutableStateOf(thread.systemPrompt) }
                                Column {
                                    Text(
                                        text = "Custom System Instruction",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GhostWhite
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = currentPromptText,
                                        onValueChange = {
                                            currentPromptText = it
                                            viewModel.updateThreadPrompt(thread.id, it)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(100.dp)
                                            .testTag("system_prompt_input"),
                                        textStyle = TextStyle(fontSize = 13.sp, color = GhostWhite),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = CyberCyan,
                                            unfocusedBorderColor = ObsidianCard,
                                            focusedContainerColor = ObsidianBackground,
                                            unfocusedContainerColor = ObsidianBackground
                                        )
                                    )
                                }

                                // Quick Thread Parameters (Muted / Readonly for Power Assistant Layout)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Voice Model", fontSize = 10.sp, color = SoftGray)
                                        Text(thread.modelName.substringAfterLast("/"), fontSize = 12.sp, color = GhostWhite, fontWeight = FontWeight.Bold)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Voice Persona", fontSize = 10.sp, color = SoftGray)
                                        Text(thread.voiceName, fontSize = 12.sp, color = CyberPurple, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Error Box
                errorMessage?.let { error ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Red.copy(alpha = 0.2f))
                            .border(1.dp, Color.Red, RoundedCornerShape(0.dp))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Error, contentDescription = "Error", tint = Color.Red)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = error, color = Color.Red, fontSize = 12.sp)
                        }
                    }
                }

                // Conversational Area
                if (activeThread == null) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            listOf(CyberCyan.copy(alpha = 0.3f), Color.Transparent)
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Intro Microphone",
                                    tint = CyberCyan,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                            Text(
                                text = "Initialize Your Voice Chat Session",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = GhostWhite,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Experience real-time direct verbal exchange powered by Gemini's native audio output model.",
                                fontSize = 13.sp,
                                color = SoftGray,
                                textAlign = TextAlign.Center
                            )
                            Button(
                                onClick = { showNewThreadDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = ObsidianBackground),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier.testTag("init_session_button")
                            ) {
                                Text("New Voice Chat", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
                    ) {
                        if (messages.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 40.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Ready to speak.",
                                            color = CyberCyan,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "Hold or press the Microphone to converse verbally.",
                                            color = SoftGray,
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        } else {
                            items(messages) { message ->
                                val isUser = message.role == "user"
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.Bottom,
                                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                                        modifier = Modifier.fillMaxWidth(0.85f)
                                    ) {
                                        if (!isUser) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(CyberPurple.copy(alpha = 0.2f))
                                                    .border(1.dp, CyberPurple, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Assistant,
                                                    contentDescription = "Gemini",
                                                    tint = CyberCyan,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                        }

                                        // Message Bubble
                                        Card(
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isUser) ObsidianCard else ObsidianSurface
                                            ),
                                            shape = RoundedCornerShape(
                                                topStart = 16.dp,
                                                topEnd = 16.dp,
                                                bottomStart = if (isUser) 16.dp else 4.dp,
                                                bottomEnd = if (isUser) 4.dp else 16.dp
                                            ),
                                            modifier = Modifier
                                                .border(
                                                    1.dp,
                                                    if (isUser) CyberCyan.copy(alpha = 0.3f) else ObsidianCard,
                                                    RoundedCornerShape(
                                                        topStart = 16.dp,
                                                        topEnd = 16.dp,
                                                        bottomStart = if (isUser) 16.dp else 4.dp,
                                                        bottomEnd = if (isUser) 4.dp else 16.dp
                                                    )
                                                )
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(
                                                    text = message.text ?: "",
                                                    color = GhostWhite,
                                                    fontSize = 14.sp
                                                )

                                                if (message.audioPath != null) {
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(ObsidianBackground)
                                                            .clickable {
                                                                viewModel.playVoiceMessage(
                                                                    message.id,
                                                                    File(message.audioPath)
                                                                )
                                                            }
                                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                                    ) {
                                                        val isPlayingCurrent = playingMessageId == message.id
                                                        Icon(
                                                            imageVector = if (isPlayingCurrent) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                                            contentDescription = "Play/Pause",
                                                            tint = if (isPlayingCurrent) CyberPink else CyberCyan,
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        if (isPlayingCurrent) {
                                                            EqualizerIndicator()
                                                        } else {
                                                            Text(
                                                                text = "Listen response",
                                                                fontSize = 12.sp,
                                                                color = SoftGray,
                                                                fontFamily = FontFamily.Monospace
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        if (isUser) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .clip(CircleShape)
                                                    .background(CyberCyan.copy(alpha = 0.2f))
                                                    .border(1.dp, CyberCyan, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Person,
                                                    contentDescription = "User",
                                                    tint = CyberCyan,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Debug Log Viewer Collapsible Panel
                AnimatedVisibility(
                    visible = showDebugLogs && activeThread != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    DebugLogPanel(viewModel = viewModel)
                }

                // Interaction Area
                if (activeThread != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ObsidianSurface)
                            .border(1.dp, ObsidianCard)
                            .padding(16.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Token usage metrics display
                            activeThread?.let { thread ->
                                if (thread.lastTotalTokens > 0 || thread.cumulativeInputTokens > 0) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(ObsidianBackground)
                                            .border(1.dp, ObsidianCard, RoundedCornerShape(8.dp))
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Current Context Window Size
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                            modifier = Modifier.weight(1.1f)
                                        ) {
                                            Text(
                                                text = "CONTEXT WINDOW USAGE",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = SoftGray,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "In: ${thread.lastPromptTokens}",
                                                    fontSize = 11.sp,
                                                    color = CyberCyan,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                                Text(
                                                    text = "Out: ${thread.lastCandidatesTokens}",
                                                    fontSize = 11.sp,
                                                    color = CyberPink,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                                Text(
                                                    text = "Total: ${thread.lastTotalTokens}",
                                                    fontSize = 11.sp,
                                                    color = GhostWhite,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                if (thread.lastCachedTokens > 0) {
                                                    Text(
                                                        text = "(${thread.lastCachedTokens} cached)",
                                                        fontSize = 10.sp,
                                                        color = CyberPurple,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                            }
                                        }

                                        Box(
                                            modifier = Modifier
                                                .height(24.dp)
                                                .width(1.dp)
                                                .background(ObsidianCard)
                                                .padding(horizontal = 4.dp)
                                        )

                                        // Total Session Token Accumulators
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                            horizontalAlignment = Alignment.End,
                                            modifier = Modifier.weight(0.9f)
                                        ) {
                                            Text(
                                                text = "CUMULATIVE SESSION",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = SoftGray,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Σ In: ${thread.cumulativeInputTokens}",
                                                    fontSize = 11.sp,
                                                    color = CyberCyan.copy(alpha = 0.8f),
                                                    fontFamily = FontFamily.Monospace
                                                )
                                                Text(
                                                    text = "Σ Out: ${thread.cumulativeOutputTokens}",
                                                    fontSize = 11.sp,
                                                    color = CyberPink.copy(alpha = 0.8f),
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Text typing backup option
                            var textInput by remember { mutableStateOf("") }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = textInput,
                                    onValueChange = { textInput = it },
                                    placeholder = { Text("Or write something...", color = SoftGray) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("text_input"),
                                    maxLines = 3,
                                    textStyle = TextStyle(color = GhostWhite, fontSize = 14.sp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = CyberCyan.copy(alpha = 0.5f),
                                        unfocusedBorderColor = ObsidianCard,
                                        focusedContainerColor = ObsidianBackground,
                                        unfocusedContainerColor = ObsidianBackground
                                    ),
                                    shape = RoundedCornerShape(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(
                                    onClick = {
                                        if (textInput.isNotBlank()) {
                                            viewModel.sendTextMessage(textInput)
                                            textInput = ""
                                        }
                                    },
                                    enabled = textInput.isNotBlank() && !isLoading,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(if (textInput.isNotBlank()) CyberCyan else ObsidianCard)
                                        .testTag("send_text_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Send,
                                        contentDescription = "Send Text",
                                        tint = if (textInput.isNotBlank()) ObsidianBackground else SoftGray,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            // Hands-free VAD status indicator
                            val handsFreeActive by viewModel.handsFreeMode.collectAsStateWithLifecycle()
                            if (handsFreeActive) {
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .padding(bottom = 12.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(CyberCyan.copy(alpha = 0.1f))
                                        .border(1.dp, CyberCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (isRecording) CyberCyan else if (playingMessageId != null) CyberPink else CyberPurple)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isRecording) "HANDS-FREE: LISTENING (VAD ACTIVE)" else if (playingMessageId != null) "HANDS-FREE: MODEL SPEAKING" else "HANDS-FREE: STANDBY",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (isRecording) CyberCyan else if (playingMessageId != null) CyberPink else SoftGray
                                    )
                                }
                            }

                            // Glowing Centralized Recording Core
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (isRecording) {
                                    VoiceWaveVisualizer()
                                } else if (isLoading) {
                                    CircularProgressIndicator(
                                        color = CyberCyan,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .padding(8.dp)
                                    )
                                } else if (playingMessageId != null) {
                                    // Assistant is speaking. Show "Tap to Interrupt" Button!
                                    IconButton(
                                        onClick = {
                                            viewModel.stopPlayback()
                                            requestMicrophonePermission {
                                                viewModel.startRecordingVoice()
                                            }
                                        },
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.radialGradient(
                                                    listOf(CyberPink.copy(alpha = 0.4f), Color.Transparent)
                                                )
                                            )
                                            .border(2.dp, CyberPink, CircleShape)
                                            .testTag("interrupt_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VolumeUp,
                                            contentDescription = "Interrupt Model Speech",
                                            tint = CyberPink,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                } else {
                                    IconButton(
                                        onClick = {
                                            requestMicrophonePermission {
                                                viewModel.startRecordingVoice()
                                            }
                                        },
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                            .background(
                                                Brush.radialGradient(
                                                    listOf(CyberCyan.copy(alpha = 0.4f), Color.Transparent)
                                                )
                                            )
                                            .border(2.dp, CyberCyan, CircleShape)
                                            .testTag("record_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Mic,
                                            contentDescription = "Speak",
                                            tint = CyberCyan,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                }

                                if (isRecording) {
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Button(
                                        onClick = { viewModel.stopRecordingVoice() },
                                        colors = ButtonDefaults.buttonColors(containerColor = CyberPink),
                                        shape = RoundedCornerShape(24.dp),
                                        modifier = Modifier.testTag("stop_record_button")
                                    ) {
                                        Text("FINISH SPEAKING", fontWeight = FontWeight.Bold, color = GhostWhite)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- DIALOGS ---

    // 1. Api Key Config Dialog
    if (showApiKeyDialog) {
        var apiKeyText by remember { mutableStateOf(customApiKey) }
        var systemPromptText by remember { mutableStateOf(defaultSystemPrompt) }
        var selectedModel by remember { mutableStateOf(defaultModel) }
        var selectedVoice by remember { mutableStateOf(defaultVoice) }
        val handsFreeInitial by viewModel.handsFreeMode.collectAsStateWithLifecycle()
        var saveHandsFreeEnabled by remember { mutableStateOf(handsFreeInitial) }
        val effortInitial by viewModel.reasoningEffort.collectAsStateWithLifecycle()
        var selectedEffort by remember { mutableStateOf(effortInitial) }
        val languageInitial by viewModel.expectedLanguage.collectAsStateWithLifecycle()
        var expectedLanguageText by remember { mutableStateOf(languageInitial) }

        AlertDialog(
            containerColor = ObsidianSurface,
            titleContentColor = GhostWhite,
            textContentColor = SoftGray,
            onDismissRequest = { showApiKeyDialog = false },
            title = {
                Text(
                    "SYSTEM PREFERENCES",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = CyberCyan
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // API Key
                    Column {
                        Text("Custom Gemini API Key", fontSize = 12.sp, color = GhostWhite, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = apiKeyText,
                            onValueChange = { apiKeyText = it },
                            placeholder = { Text("Defaults to environment config", fontSize = 12.sp, color = SoftGray) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("api_key_input"),
                            textStyle = TextStyle(fontSize = 13.sp, color = GhostWhite),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = ObsidianCard,
                                focusedContainerColor = ObsidianBackground,
                                unfocusedContainerColor = ObsidianBackground
                            )
                        )
                    }

                    // Default System Prompt
                    Column {
                        Text("Default System Instruction", fontSize = 12.sp, color = GhostWhite, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = systemPromptText,
                            onValueChange = { systemPromptText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                            textStyle = TextStyle(fontSize = 13.sp, color = GhostWhite),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = ObsidianCard,
                                focusedContainerColor = ObsidianBackground,
                                unfocusedContainerColor = ObsidianBackground
                            )
                        )
                    }

                    // Expected Language Selection
                    Column {
                        Text("Expected Assistant Output Language", fontSize = 12.sp, color = GhostWhite, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = expectedLanguageText,
                            onValueChange = { expectedLanguageText = it },
                            placeholder = { Text("e.g. Spanish, Japanese, French (leave empty for default)", fontSize = 12.sp, color = SoftGray) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("expected_language_input"),
                            textStyle = TextStyle(fontSize = 13.sp, color = GhostWhite),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = ObsidianCard,
                                focusedContainerColor = ObsidianBackground,
                                unfocusedContainerColor = ObsidianBackground
                            ),
                            singleLine = true
                        )
                    }

                    // Default Model Selection
                    Column {
                        Text("Default Audio Model", fontSize = 12.sp, color = GhostWhite, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = selectedModel,
                            onValueChange = { selectedModel = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(fontSize = 13.sp, color = GhostWhite, fontFamily = FontFamily.Monospace),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = ObsidianCard,
                                focusedContainerColor = ObsidianBackground,
                                unfocusedContainerColor = ObsidianBackground
                            )
                        )
                    }

                    // Default Voice Selection
                    Column {
                        Text("Default Assistant Voice", fontSize = 12.sp, color = GhostWhite, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        val voices = listOf("Puck", "Charon", "Kore", "Fenrir", "Aoede")
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            voices.forEach { voice ->
                                val isVoiceSelected = selectedVoice == voice
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isVoiceSelected) CyberPurple else ObsidianBackground)
                                        .border(1.dp, if (isVoiceSelected) CyberCyan else ObsidianCard, RoundedCornerShape(8.dp))
                                        .clickable { selectedVoice = voice }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = voice,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isVoiceSelected) GhostWhite else SoftGray
                                    )
                                }
                            }
                        }
                    }

                    // Hands-Free / Voice Interruption Mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(ObsidianBackground)
                            .border(1.dp, ObsidianCard, RoundedCornerShape(8.dp))
                            .clickable { saveHandsFreeEnabled = !saveHandsFreeEnabled }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Hands-Free Voice Mode",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = GhostWhite
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Auto-detects silence to submit speech and continues conversational loop.",
                                fontSize = 10.sp,
                                color = SoftGray
                            )
                        }
                        Switch(
                            checked = saveHandsFreeEnabled,
                            onCheckedChange = { saveHandsFreeEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = CyberCyan,
                                checkedTrackColor = CyberCyan.copy(alpha = 0.3f),
                                uncheckedThumbColor = SoftGray,
                                uncheckedTrackColor = ObsidianCard
                            )
                        )
                    }

                    // LLM Thinking Level Selection
                    Column {
                        Text("LLM Thinking Level", fontSize = 12.sp, color = GhostWhite, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        val efforts = listOf("none", "minimal", "full")
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            efforts.forEach { effort ->
                                val isEffortSelected = selectedEffort == effort
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isEffortSelected) CyberPurple else ObsidianBackground)
                                        .border(1.dp, if (isEffortSelected) CyberCyan else ObsidianCard, RoundedCornerShape(8.dp))
                                        .clickable { selectedEffort = effort }
                                        .padding(vertical = 8.dp)
                                        .testTag("thinking_level_${effort}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = effort.uppercase(),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isEffortSelected) GhostWhite else SoftGray
                                    )
                                }
                            }
                        }
                    }

                    // Reset Button
                    Button(
                        onClick = {
                            viewModel.resetSettingsToDefault()
                            apiKeyText = ""
                            systemPromptText = SettingsStore.DEFAULT_SYSTEM_PROMPT
                            selectedModel = SettingsStore.DEFAULT_MODEL
                            selectedVoice = SettingsStore.DEFAULT_VOICE
                            saveHandsFreeEnabled = true
                            selectedEffort = "minimal"
                            expectedLanguageText = ""
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = CyberPink
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .border(1.dp, CyberPink.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .testTag("reset_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Settings",
                            modifier = Modifier.size(16.dp),
                            tint = CyberPink
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "RESET ALL SETTINGS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = CyberPink
                        )
                    }

                    // Delete All Chats Button
                    Button(
                        onClick = {
                            viewModel.deleteAllThreads()
                            showApiKeyDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Transparent,
                            contentColor = CyberPink
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .border(1.dp, CyberPink.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                            .testTag("delete_all_chats_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Delete All Chats",
                            modifier = Modifier.size(16.dp),
                            tint = CyberPink
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "DELETE ALL CHATS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = CyberPink
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = ObsidianBackground),
                    onClick = {
                        viewModel.saveApiKey(apiKeyText)
                        viewModel.saveSystemPrompt(systemPromptText)
                        viewModel.saveModelName(selectedModel)
                        viewModel.saveVoiceName(selectedVoice)
                        viewModel.saveHandsFreeMode(saveHandsFreeEnabled)
                        viewModel.saveReasoningEffort(selectedEffort)
                        viewModel.saveExpectedLanguage(expectedLanguageText)
                        showApiKeyDialog = false
                    }
                ) {
                    Text("SAVE CHANGES", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) {
                    Text("CANCEL", color = SoftGray)
                }
            }
        )
    }

    // 2. New Thread Creation Dialog
    if (showNewThreadDialog) {
        var threadTitle by remember { mutableStateOf("") }
        var threadPrompt by remember { mutableStateOf(defaultSystemPrompt) }
        var threadVoice by remember { mutableStateOf(defaultVoice) }

        AlertDialog(
            containerColor = ObsidianSurface,
            titleContentColor = GhostWhite,
            textContentColor = SoftGray,
            onDismissRequest = { showNewThreadDialog = false },
            title = {
                Text(
                    "LAUNCH VOCAL SESSION",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = CyberCyan
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text("Session Title", fontSize = 12.sp, color = GhostWhite, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = threadTitle,
                            onValueChange = { threadTitle = it },
                            placeholder = { Text("e.g. Brainstorming session", fontSize = 12.sp, color = SoftGray) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("thread_title_input"),
                            textStyle = TextStyle(fontSize = 13.sp, color = GhostWhite),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = ObsidianCard,
                                focusedContainerColor = ObsidianBackground,
                                unfocusedContainerColor = ObsidianBackground
                            )
                        )
                    }

                    Column {
                        Text("Custom System Instruction (Override)", fontSize = 12.sp, color = GhostWhite, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = threadPrompt,
                            onValueChange = { threadPrompt = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp),
                            textStyle = TextStyle(fontSize = 13.sp, color = GhostWhite),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberCyan,
                                unfocusedBorderColor = ObsidianCard,
                                focusedContainerColor = ObsidianBackground,
                                unfocusedContainerColor = ObsidianBackground
                            )
                        )
                    }

                    Column {
                        Text("Assistant Voice", fontSize = 12.sp, color = GhostWhite, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        val voices = listOf("Puck", "Charon", "Kore", "Fenrir", "Aoede")
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            voices.forEach { voice ->
                                val isVoiceSelected = threadVoice == voice
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isVoiceSelected) CyberPurple else ObsidianBackground)
                                        .border(1.dp, if (isVoiceSelected) CyberCyan else ObsidianCard, RoundedCornerShape(8.dp))
                                        .clickable { threadVoice = voice }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = voice,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isVoiceSelected) GhostWhite else SoftGray
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = CyberCyan, contentColor = ObsidianBackground),
                    onClick = {
                        viewModel.createThread(
                            title = threadTitle,
                            customPrompt = threadPrompt,
                            customModel = defaultModel,
                            customVoice = threadVoice
                        )
                        showNewThreadDialog = false
                    }
                ) {
                    Text("START SESSION", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewThreadDialog = false }) {
                    Text("CANCEL", color = SoftGray)
                }
            }
        )
    }
}

// Visual pulsing voice waves when recording
@Composable
fun VoiceWaveVisualizer() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val heights = listOf(
        infiniteTransition.animateValue(12.dp, 36.dp, Dp.VectorConverter, infiniteRepeatable(animation = tween(400, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = "h1"),
        infiniteTransition.animateValue(18.dp, 48.dp, Dp.VectorConverter, infiniteRepeatable(animation = tween(350, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = "h2"),
        infiniteTransition.animateValue(8.dp, 32.dp, Dp.VectorConverter, infiniteRepeatable(animation = tween(500, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = "h3"),
        infiniteTransition.animateValue(22.dp, 56.dp, Dp.VectorConverter, infiniteRepeatable(animation = tween(450, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = "h4"),
        infiniteTransition.animateValue(14.dp, 40.dp, Dp.VectorConverter, infiniteRepeatable(animation = tween(300, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = "h5")
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(16.dp)
    ) {
        heights.forEach { height ->
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(height.value)
                    .background(CyberCyan, shape = CircleShape)
            )
        }
    }
}

// Minimalist active playing audio indicator (Equalizer animation)
@Composable
fun EqualizerIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "eq")
    val heights = listOf(
        infiniteTransition.animateValue(4.dp, 16.dp, Dp.VectorConverter, infiniteRepeatable(animation = tween(300, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = "eq1"),
        infiniteTransition.animateValue(8.dp, 20.dp, Dp.VectorConverter, infiniteRepeatable(animation = tween(250, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = "eq2"),
        infiniteTransition.animateValue(6.dp, 14.dp, Dp.VectorConverter, infiniteRepeatable(animation = tween(350, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = "eq3")
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Playing ", fontSize = 12.sp, color = CyberPink, fontFamily = FontFamily.Monospace)
        heights.forEach { height ->
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(height.value)
                    .background(CyberPink, shape = CircleShape)
            )
        }
    }
}

@Composable
fun DebugLogPanel(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val logs by viewModel.liveLogs.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // Auto-scroll to the bottom when a new log arrives
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(listOf(CyberPink.copy(alpha = 0.5f), ObsidianCard)),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ObsidianCard)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(CyberCyan)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "LIVE API DEBUG LOGGER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = GhostWhite,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "(${logs.size} packets)",
                        fontSize = 10.sp,
                        color = SoftGray,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Clear Button
                    IconButton(
                        onClick = { viewModel.clearLiveLogs() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear logs",
                            tint = SoftGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Divider(color = ObsidianBackground, thickness = 1.dp)

            // Scrollable Logs
            if (logs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ObsidianBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = "Code",
                            tint = SoftGray.copy(alpha = 0.3f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Awaiting live API traffic...",
                            fontSize = 12.sp,
                            color = SoftGray,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ObsidianBackground)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(logs, key = { it.id }) { log ->
                        DebugLogItem(log = log)
                    }
                }
            }
        }
    }
}

@Composable
fun DebugLogItem(log: com.example.api.LiveApiLog) {
    var isExpanded by remember { mutableStateOf(false) }

    val (tag, tagColor, textColor) = when (log.direction) {
        com.example.api.LogDirection.SEND -> Triple("SEND >>", CyberCyan, CyberCyan.copy(alpha = 0.15f))
        com.example.api.LogDirection.RECEIVE -> Triple("<< RECV", CyberPink, CyberPink.copy(alpha = 0.15f))
        com.example.api.LogDirection.INFO -> Triple("SYSTEM", CyberPurple, CyberPurple.copy(alpha = 0.15f))
        com.example.api.LogDirection.ERROR -> Triple("ERROR", Color.Red, Color.Red.copy(alpha = 0.15f))
    }

    // Format simple time
    val timeStr = remember(log.timestamp) {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
        sdf.format(java.util.Date(log.timestamp))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded }
            .border(1.dp, ObsidianCard, RoundedCornerShape(6.dp))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(textColor)
                            .border(1.dp, tagColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = tag,
                            color = tagColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = timeStr,
                        color = SoftGray,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Expand Log",
                    tint = SoftGray,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Payload Preview / Complete
            Text(
                text = if (isExpanded) log.payload else {
                    if (log.payload.length > 150) {
                        log.payload.take(150) + " ... (tap to expand)"
                    } else {
                        log.payload
                    }
                },
                color = if (log.direction == com.example.api.LogDirection.ERROR) Color.Red else GhostWhite.copy(alpha = 0.9f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 15.sp,
                maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
