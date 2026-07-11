package com.task.platform.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.task.platform.ui.login.LoginScreen
import com.task.platform.update.UpdateDialog
import com.task.platform.update.UpdateViewModel
import com.task.platform.ui.login.RegisterScreen
import com.task.platform.ui.login.SplashScreen
import com.task.platform.ui.main.MainScreen
import com.task.platform.ui.profile.RealAuthScreen
import com.task.platform.ui.profile.WalletBindingScreen
import com.task.platform.ui.profile.SettingsScreen
import com.task.platform.ui.profile.AboutScreen
import com.task.platform.ui.profile.EditProfileScreen
import com.task.platform.ui.task.MyTasksScreen
import com.task.platform.ui.task.ScreenshotUploadScreen
import com.task.platform.ui.task.TaskDetailScreen
import com.task.platform.ui.earnings.EarningsScreen

/**
 * 导航路由定义
 */
object TaskRoutes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val MAIN = "main"
    const val REAL_AUTH = "real_auth"
    const val TASK_DETAIL = "task_detail/{taskId}"
    const val MY_TASKS = "my_tasks"
    const val SCREENSHOT_UPLOAD = "screenshot_upload/{taskId}"
    const val EARNINGS = "earnings"
    const val WITHDRAW = "withdraw"
    const val PROFILE = "profile"
    const val WALLET_BINDING = "wallet_binding"
    const val SETTINGS = "settings"
    const val EDIT_PROFILE = "edit_profile"
    const val ABOUT = "about"
}

/**
 * 主导航图
 */
@Composable
fun TaskNavGraph(
    navController: NavHostController,
    updateViewModel: UpdateViewModel,
    startDestination: String = TaskRoutes.SPLASH
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // 启动页
        composable(TaskRoutes.SPLASH) {
            SplashScreen(
                onNavigateToLogin = {
                    navController.navigate(TaskRoutes.LOGIN) {
                        popUpTo(TaskRoutes.SPLASH) { inclusive = true }
                    }
                },
                onNavigateToMain = {
                    navController.navigate(TaskRoutes.MAIN) {
                        popUpTo(TaskRoutes.SPLASH) { inclusive = true }
                    }
                }
            )
        }

        // 登录页
        composable(TaskRoutes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(TaskRoutes.MAIN) {
                        popUpTo(TaskRoutes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToRegister = {
                    navController.navigate(TaskRoutes.REGISTER)
                }
            )
        }

        // 注册页
        composable(TaskRoutes.REGISTER) {
            RegisterScreen(
                onRegisterSuccess = {
                    navController.navigate(TaskRoutes.REAL_AUTH)
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // 主页面（底部导航）
        composable(TaskRoutes.MAIN) {
            MainScreen(navController = navController)
        }

        // 实名认证页
        composable(TaskRoutes.REAL_AUTH) {
            RealAuthScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }

        // 任务详情页
        composable(TaskRoutes.TASK_DETAIL) { backStackEntry ->
            val taskId = backStackEntry.arguments?.getString("taskId")?.toLongOrNull() ?: 0L
            TaskDetailScreen(
                navController = navController,
                taskId = taskId
            )
        }

        // 我的任务记录页
        composable(TaskRoutes.MY_TASKS) {
            MyTasksScreen(navController = navController)
        }

        // 截图上传页
        composable(TaskRoutes.SCREENSHOT_UPLOAD) { backStackEntry ->
            val taskId = backStackEntry.arguments?.getString("taskId")?.toLongOrNull() ?: 0L
            ScreenshotUploadScreen(
                navController = navController,
                taskId = taskId
            )
        }

        // ===== 钱包绑定页 =====
        composable(TaskRoutes.WALLET_BINDING) {
            WalletBindingScreen(navController = navController)
        }

        // ===== 设置页 =====
        composable(TaskRoutes.SETTINGS) {
            SettingsScreen(navController = navController)
        }

        // ===== 关于页 =====
        composable(TaskRoutes.ABOUT) {
            AboutScreen(navController = navController)
        }

        // ===== 编辑资料页 =====
        composable(TaskRoutes.EDIT_PROFILE) {
            EditProfileScreen(navController = navController)
        }

        // ===== 收益页 =====
        composable(TaskRoutes.EARNINGS) {
            EarningsScreen(navController = navController)
        }

        // ===== 提现页 =====
        composable(TaskRoutes.WITHDRAW) {
            com.task.platform.ui.earnings.WithdrawScreen(navController = navController)
        }
    }

    // 全局版本更新弹窗（覆盖在最上层，可选更新不破坏既有导航）
    val context = LocalContext.current
    val updateState by updateViewModel.updateState.collectAsState()
    UpdateDialog(
        state = updateState,
        onUpdate = { updateViewModel.startDownload(context) },
        onDismiss = { updateViewModel.dismiss() },
        onCancel = { updateViewModel.cancelDownload() }
    )
}
