package com.task.platform.ui.main

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.task.platform.R
import com.task.platform.ui.earnings.EarningsScreen
import com.task.platform.ui.profile.ProfileScreen
import com.task.platform.ui.publish.PublishScreen
import com.task.platform.ui.task.MyTasksScreen
import com.task.platform.ui.task.TaskHallScreen
import com.task.platform.ui.theme.Shape
import androidx.compose.material3.MaterialTheme

// ─── 主题色 ───────────────────────────────────────

/**
 * 主屏幕 - 带底部导航
 */
@Composable
fun MainScreen(navController: NavHostController) {
    val mainNavController = rememberNavController()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            PremiumBottomNavBar(navController = mainNavController)
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            NavHost(
                navController = mainNavController,
                startDestination = BottomNavItem.Task.route
            ) {
                composable(BottomNavItem.Task.route) {
                    TaskHallScreen(
                        onTaskClick = { taskId ->
                            navController.navigate("task_detail/$taskId")
                        }
                    )
                }
                composable(BottomNavItem.Publish.route) {
                    PublishScreen(navController = navController)
                }
                composable(BottomNavItem.Earnings.route) {
                    EarningsScreen(navController = navController)
                }
                composable(BottomNavItem.Profile.route) {
                    ProfileScreen(navController = navController)
                }
                composable("my_tasks") {
                    MyTasksScreen(navController = navController)
                }
            }
        }
    }
}

/**
 * 底部导航栏 - 大图标 + 大字体，适合中老年用户
 * - 圆角顶部 + 阴影浮起
 * - 选中项：渐变橙色背景指示器 + 橙色大图标 + 加粗大文字 + 渐变下划线
 * - 未选中项：灰色图标 + 细文字
 */
@Composable
private fun PremiumBottomNavBar(navController: NavHostController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = Shape.radiusXl,
                ambientColor = Color.Black.copy(alpha = 0.06f),
                spotColor = Color.Black.copy(alpha = 0.06f)
            ),
        shape = Shape.radiusXl,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 20.dp)
                .height(68.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem.items.forEach { item ->
                val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                PremiumNavItem(
                    item = item,
                    selected = selected,
                    onClick = {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.findStartDestination()!!.id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    }
}

/**
 * 单个导航项 - 大图标大字体设计
 */
@Composable
private fun PremiumNavItem(
    item: BottomNavItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    val iconColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(300),
        label = "iconColor"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(300),
        label = "textColor"
    )
    val labelWeight = if (selected) FontWeight.Bold else FontWeight.Medium

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .width(80.dp)
            .height(68.dp)
    ) {
        // 图标区域 + 选中指示器
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(44.dp)
        ) {
            // 选中时的渐变圆角方形背景
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                )
                            )
                        )
                )
            }

            Icon(
                painter = painterResource(id = item.iconResId),
                contentDescription = stringResource(id = item.titleResId),
                tint = iconColor,
                modifier = Modifier
                    .size(if (selected) 28.dp else 26.dp)
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        // 文字 - 大字体适合中老年
        Text(
            text = stringResource(id = item.titleResId),
            color = textColor,
            fontSize = if (selected) 15.sp else 13.sp,
            fontWeight = labelWeight,
            lineHeight = 16.sp
        )

        // 选中指示条
        if (selected) {
            Spacer(modifier = Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .width(20.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
                        )
                    )
            )
        }
    }
}

/**
 * 底部导航项定义
 */
sealed class BottomNavItem(
    val route: String,
    @StringRes val titleResId: Int,
    val iconResId: Int
) {
    object Task : BottomNavItem(
        route = "task_hall",
        titleResId = R.string.nav_task,
        iconResId = R.drawable.ic_task
    )

    object Publish : BottomNavItem(
        route = "publish_hall",
        titleResId = R.string.nav_publish,
        iconResId = R.drawable.ic_publish
    )

    object Earnings : BottomNavItem(
        route = "earnings",
        titleResId = R.string.nav_earnings,
        iconResId = R.drawable.ic_earnings
    )

    object Profile : BottomNavItem(
        route = "profile",
        titleResId = R.string.nav_profile,
        iconResId = R.drawable.ic_profile
    )

    companion object {
        val items = listOf(Task, Publish, Earnings, Profile)
    }
}
