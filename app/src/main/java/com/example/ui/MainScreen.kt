package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.chat.ChatScreen
import com.example.ui.chat.ChatViewModel
import com.example.ui.imagegen.ImageGenScreen
import com.example.ui.imagegen.ImageGenViewModel
import com.example.ui.profile.ProfileScreen
import com.example.ui.qa.ExploreQaScreen
import com.example.ui.qa.QaViewModel
import com.example.ui.theme.AiCyan
import com.example.ui.theme.AiPurple

data class NavigationTab(
    val titleEn: String,
    val titleUr: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun MainScreen(
    onSignOutComplete: () -> Unit,
    chatViewModel: ChatViewModel = viewModel(),
    qaViewModel: QaViewModel = viewModel(),
    imageGenViewModel: ImageGenViewModel = viewModel()
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    // Intercept back button to return to home tab if on secondary tab
    BackHandler(enabled = selectedTabIndex != 0) {
        selectedTabIndex = 0
    }

    val tabs = listOf(
        NavigationTab(
            titleEn = "Chat",
            titleUr = "گفتگو",
            selectedIcon = Icons.Filled.ChatBubble,
            unselectedIcon = Icons.Outlined.ChatBubbleOutline,
            testTag = "nav_tab_chat"
        ),
        NavigationTab(
            titleEn = "Knowledge",
            titleUr = "علم و آگہی",
            selectedIcon = Icons.Filled.School,
            unselectedIcon = Icons.Outlined.School,
            testTag = "nav_tab_qa"
        ),
        NavigationTab(
            titleEn = "Image Gen",
            titleUr = "تصویر ساز",
            selectedIcon = Icons.Filled.Palette,
            unselectedIcon = Icons.Outlined.Palette,
            testTag = "nav_tab_image"
        ),
        NavigationTab(
            titleEn = "Sync & Profile",
            titleUr = "پروفائل",
            selectedIcon = Icons.Filled.CloudDone,
            unselectedIcon = Icons.Outlined.CloudQueue,
            testTag = "nav_tab_profile"
        )
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedScreen = maxWidth >= 600.dp

        if (isExpandedScreen) {
            // Adaptive Tablet / Foldable layout with NavigationRail
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val isSelected = selectedTabIndex == index
                        NavigationRailItem(
                            selected = isSelected,
                            onClick = { selectedTabIndex = index },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.titleEn
                                )
                            },
                            label = { Text(tab.titleEn) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = AiPurple,
                                indicatorColor = AiPurple.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag(tab.testTag)
                        )
                    }
                }

                ScreenContent(
                    selectedTabIndex = selectedTabIndex,
                    chatViewModel = chatViewModel,
                    qaViewModel = qaViewModel,
                    imageGenViewModel = imageGenViewModel,
                    onSignOutComplete = onSignOutComplete,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            // Mobile layout with standard NavigationBar
            Scaffold(
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 8.dp
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            val isSelected = selectedTabIndex == index
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = { selectedTabIndex = index },
                                icon = {
                                    Icon(
                                        imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.titleEn
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.titleEn,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = AiPurple,
                                    indicatorColor = AiPurple.copy(alpha = 0.15f)
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }
            ) { innerPadding ->
                ScreenContent(
                    selectedTabIndex = selectedTabIndex,
                    chatViewModel = chatViewModel,
                    qaViewModel = qaViewModel,
                    imageGenViewModel = imageGenViewModel,
                    onSignOutComplete = onSignOutComplete,
                    modifier = Modifier.padding(innerPadding)
                )
            }
        }
    }
}

@Composable
private fun ScreenContent(
    selectedTabIndex: Int,
    chatViewModel: ChatViewModel,
    qaViewModel: QaViewModel,
    imageGenViewModel: ImageGenViewModel,
    onSignOutComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.Box(modifier = modifier.fillMaxSize()) {
        when (selectedTabIndex) {
            0 -> ChatScreen(viewModel = chatViewModel)
            1 -> ExploreQaScreen(viewModel = qaViewModel)
            2 -> ImageGenScreen(viewModel = imageGenViewModel)
            3 -> ProfileScreen(
                chatViewModel = chatViewModel,
                qaViewModel = qaViewModel,
                onSignOutComplete = onSignOutComplete
            )
        }
    }
}
