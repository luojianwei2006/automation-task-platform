package com.task.platform.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.task.platform.model.ApiResponse
import com.task.platform.model.PageResponse
import com.task.platform.model.TaskDTO
import com.task.platform.model.TaskRecordDTO
import com.task.platform.network.ApiClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 任务数据仓库
 * 封装所有任务相关网络请求
 */
@Singleton
class TaskRepository @Inject constructor(
    private val apiClient: ApiClient,
    @ApplicationContext private val appContext: Context
) {

    /** 获取任务列表（分页） */
    suspend fun getTasks(
        platform: Int?,
        type: Int?,
        page: Int = 1,
        size: Int = 20
    ): Result<PageResponse<TaskDTO>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val response = apiClient.apiService.getTasks(platform, type, page, size)
            if (response.code == 200) {
                Result.success(response.data ?: PageResponse(emptyList(), 0, page.toLong(), size.toLong()))
            } else {
                Result.failure(Exception(response.msg ?: "未知错误"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 获取任务详情 */
    suspend fun getTaskDetail(id: Long): Result<TaskDTO?> = withContext(Dispatchers.IO) {
        return@withContext try {
            val response = apiClient.apiService.getTaskDetail(id)
            if (response.code == 200) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.msg ?: "未知错误"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 接受任务 */
    suspend fun acceptTask(id: Long): Result<Void?> = withContext(Dispatchers.IO) {
        return@withContext try {
            val response = apiClient.apiService.acceptTask(id)
            if (response.code == 200) {
                Result.success(null)
            } else {
                Result.failure(Exception(response.msg ?: "未知错误"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 获取我的任务记录（分页） */
    suspend fun getMyTasks(
        page: Int = 1,
        size: Int = 20
    ): Result<PageResponse<TaskDTO>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val response = apiClient.apiService.getMyTasks(page, size)
            if (response.code == 200) {
                Result.success(response.data ?: PageResponse(emptyList(), 0, page.toLong(), size.toLong()))
            } else {
                Result.failure(Exception(response.msg ?: "未知错误"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 提交任务截图+定位 */
    suspend fun submitTask(id: Long, screenshotUrl: String, latitude: Double?, longitude: Double?): Result<Void?> = withContext(Dispatchers.IO) {
        return@withContext try {
            val body = mutableMapOf<String, Any>("screenshotUrl" to screenshotUrl)
            latitude?.let { body["latitude"] = it }
            longitude?.let { body["longitude"] = it }
            val response = apiClient.apiService.submitTask(id, body)
            if (response.code == 200) {
                Result.success(null)
            } else {
                Result.failure(Exception(response.msg ?: "未知错误"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 获取当前用户对指定任务的记录 */
    suspend fun getTaskRecord(taskId: Long): Result<TaskRecordDTO?> = withContext(Dispatchers.IO) {
        return@withContext try {
            val response = apiClient.apiService.getTaskRecord(taskId)
            if (response.code == 200) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.msg ?: "未知错误"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 放弃任务 */
    suspend fun abandonTask(id: Long): Result<Void?> = withContext(Dispatchers.IO) {
        return@withContext try {
            val response = apiClient.apiService.abandonTask(id)
            if (response.code == 200) {
                Result.success(null)
            } else {
                Result.failure(Exception(response.msg ?: "未知错误"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 获取任务记录详情 */
    suspend fun getTaskRecordDetail(recordId: Long): Result<TaskRecordDTO?> = withContext(Dispatchers.IO) {
        return@withContext try {
            val response = apiClient.apiService.getTaskRecordDetail(recordId)
            if (response.code == 200) {
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.msg ?: "未知错误"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== 文件上传 + 任务提交（一步完成）====================

    /**
     * Uri → MultipartBody.Part（用于文件上传）
     * @param uri 图片 Uri
     * @param fieldName 表单字段名（默认 "files"）
     */
    private fun uriToMultipartBodyPart(uri: Uri, fieldName: String = "files"): MultipartBody.Part {
        val inputStream: InputStream = appContext.contentResolver.openInputStream(uri)!!
        val bytes = inputStream.readBytes()
        inputStream.close()
        val requestBody = bytes.toRequestBody("image/*".toMediaTypeOrNull(), 0, bytes.size)
        val filename = "screenshot_" + System.currentTimeMillis() + ".jpg"
        return MultipartBody.Part.createFormData(fieldName, filename, requestBody)
    }

    /**
     * 一步完成：上传截图 + 提交任务
     * @param taskId 任务ID
     * @param uris 截图 Uri 列表
     * @param latitude 纬度（可空）
     * @param longitude 经度（可空）
     */
    suspend fun submitTaskWithBatchUpload(
        taskId: Long,
        uris: List<Uri>,
        latitude: Double?,
        longitude: Double?
    ): Result<Void?> = withContext(Dispatchers.IO) {
        return@withContext try {
            val parts = mutableListOf<MultipartBody.Part>()

            // 添加截图文件（字段名 "files"，与后端 @RequestParam("files") 对应）
            uris.forEach { uri ->
                parts.add(uriToMultipartBodyPart(uri, "files"))
            }

            // 添加定位信息（可选）
            latitude?.let {
                val body = it.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                parts.add(MultipartBody.Part.createFormData("latitude", null, body))
            }
            longitude?.let {
                val body = it.toString().toRequestBody("text/plain".toMediaTypeOrNull())
                parts.add(MultipartBody.Part.createFormData("longitude", null, body))
            }

            val response = apiClient.apiService.submitWithUpload(taskId, parts)
            if (response.code == 200) {
                Result.success(null)
            } else {
                Result.failure(Exception(response.msg ?: "未知错误"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
