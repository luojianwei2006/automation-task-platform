package com.task.platform.upload.controller;

import com.task.platform.upload.storage.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UploadController 单元测试
 *
 * <p>使用 MockMvc 模拟 HTTP 请求，MockBean 隔离 FileStorageService 依赖。
 * 无需导入 UploadProperties / StaticResourceConfig，Controller 仅依赖 FileStorageService。</p>
 */
@WebMvcTest(UploadController.class)
@DisplayName("UploadController 单元测试")
class UploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FileStorageService storageService;

    /** 有效的图片文件（1KB PNG） */
    private MockMultipartFile validImageFile;

    /** 超过 5MB 的大文件 */
    private MockMultipartFile oversizedFile;

    /** 非图片类型的文件 */
    private MockMultipartFile nonImageFile;

    /** 空文件 */
    private MockMultipartFile emptyFile;

    @BeforeEach
    void setUp() {
        validImageFile = new MockMultipartFile(
                "file",
                "test.png",
                "image/png",
                new byte[1024]
        );

        oversizedFile = new MockMultipartFile(
                "file",
                "big.jpg",
                "image/jpeg",
                new byte[6 * 1024 * 1024] // 6MB > 5MB
        );

        nonImageFile = new MockMultipartFile(
                "file",
                "doc.pdf",
                "application/pdf",
                new byte[1024]
        );

        emptyFile = new MockMultipartFile(
                "file",
                "empty.jpg",
                "image/jpeg",
                new byte[0]
        );

        // 默认 Mock: 成功上传返回
        when(storageService.upload(any(), anyString()))
                .thenReturn("/upload/uploads/image/test-uuid.png");
        when(storageService.getAccessUrl(anyString()))
                .thenReturn("/api/upload/uploads/image/test-uuid.png");
    }

    // ======================== POST /upload/image ========================

    @Nested
    @DisplayName("POST /upload/image - 单文件上传")
    class UploadImage {

        @Test
        @DisplayName("正常上传图片文件 → 返回 UploadResult")
        void shouldUploadImageSuccessfully() throws Exception {
            mockMvc.perform(multipart("/upload/image")
                            .file(validImageFile)
                            .param("type", "image"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.msg").value("上传成功"))
                    .andExpect(jsonPath("$.data.relativePath").value("/upload/uploads/image/test-uuid.png"))
                    .andExpect(jsonPath("$.data.accessUrl").value("/api/upload/uploads/image/test-uuid.png"))
                    .andExpect(jsonPath("$.data.filename").value("test.png"))
                    .andExpect(jsonPath("$.data.size").value(1024));

            verify(storageService).upload(any(), eq("image"));
        }

        @Test
        @DisplayName("使用默认 type → 自动填充 image")
        void shouldUseDefaultTypeWhenNotSpecified() throws Exception {
            when(storageService.upload(any(), eq("image")))
                    .thenReturn("/upload/uploads/image/default-uuid.jpg");
            when(storageService.getAccessUrl("/upload/uploads/image/default-uuid.jpg"))
                    .thenReturn("/api/upload/uploads/image/default-uuid.jpg");

            mockMvc.perform(multipart("/upload/image")
                            .file(validImageFile))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(storageService).upload(any(), eq("image"));
        }

        @Test
        @DisplayName("空文件 → 返回 400 错误")
        void shouldRejectEmptyFile() throws Exception {
            mockMvc.perform(multipart("/upload/image")
                            .file(emptyFile))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.msg").value("上传文件不能为空"));

            verify(storageService, never()).upload(any(), anyString());
        }

        @Test
        @DisplayName("不传 file 参数 → 返回 400 错误")
        void shouldRejectMissingFile() throws Exception {
            mockMvc.perform(multipart("/upload/image"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.msg").value("上传文件不能为空"));

            verify(storageService, never()).upload(any(), anyString());
        }
    }

    // ======================== POST /upload/images ========================

    @Nested
    @DisplayName("POST /upload/images - 多文件上传")
    class UploadImages {

        @Test
        @DisplayName("正常上传 2 个图片 → 返回列表")
        void shouldUploadMultipleImagesSuccessfully() throws Exception {
            MockMultipartFile file1 = new MockMultipartFile(
                    "files", "img1.png", "image/png", new byte[512]);
            MockMultipartFile file2 = new MockMultipartFile(
                    "files", "img2.jpg", "image/jpeg", new byte[1024]);

            when(storageService.upload(any(), eq("image")))
                    .thenReturn("/upload/uploads/image/uuid1.png")
                    .thenReturn("/upload/uploads/image/uuid2.jpg");
            when(storageService.getAccessUrl(anyString()))
                    .thenReturn("/api/upload/uploads/image/uuid1.png")
                    .thenReturn("/api/upload/uploads/image/uuid2.jpg");

            mockMvc.perform(multipart("/upload/images")
                            .file(file1)
                            .file(file2))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.msg").value("上传成功"))
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].filename").value("img1.png"))
                    .andExpect(jsonPath("$.data[1].filename").value("img2.jpg"));
        }

        @Test
        @DisplayName("超过 4 个文件 → 返回 400")
        void shouldRejectMoreThan4Files() throws Exception {
            mockMvc.perform(multipart("/upload/images")
                            .file(new MockMultipartFile("files", "1.png", "image/png", new byte[1]))
                            .file(new MockMultipartFile("files", "2.png", "image/png", new byte[1]))
                            .file(new MockMultipartFile("files", "3.png", "image/png", new byte[1]))
                            .file(new MockMultipartFile("files", "4.png", "image/png", new byte[1]))
                            .file(new MockMultipartFile("files", "5.png", "image/png", new byte[1])))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.msg").value("单次最多上传 4 个文件"));

            verify(storageService, never()).upload(any(), anyString());
        }

        @Test
        @DisplayName("空文件列表 → 返回 400")
        void shouldRejectEmptyFileList() throws Exception {
            mockMvc.perform(multipart("/upload/images"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.msg").value("上传文件列表不能为空"));
        }
    }

    // ======================== POST /upload/wallet-qrcode ========================

    @Nested
    @DisplayName("POST /upload/wallet-qrcode - 钱包二维码上传")
    class UploadWalletQrcode {

        @Test
        @DisplayName("正常上传二维码图片 → type 固定为 qrcode")
        void shouldUploadQrcodeSuccessfully() throws Exception {
            when(storageService.upload(any(), eq("qrcode")))
                    .thenReturn("/upload/uploads/qrcode/qr-uuid.png");
            when(storageService.getAccessUrl("/upload/uploads/qrcode/qr-uuid.png"))
                    .thenReturn("/api/upload/uploads/qrcode/qr-uuid.png");

            MockMultipartFile qrcodeFile = new MockMultipartFile(
                    "file", "wallet-qr.png", "image/png", new byte[2048]);

            mockMvc.perform(multipart("/upload/wallet-qrcode")
                            .file(qrcodeFile))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.msg").value("上传成功"))
                    .andExpect(jsonPath("$.data.relativePath").value("/upload/uploads/qrcode/qr-uuid.png"))
                    .andExpect(jsonPath("$.data.accessUrl").value("/api/upload/uploads/qrcode/qr-uuid.png"))
                    .andExpect(jsonPath("$.data.filename").value("wallet-qr.png"))
                    .andExpect(jsonPath("$.data.size").value(2048));

            verify(storageService).upload(any(), eq("qrcode"));
        }

        @Test
        @DisplayName("空文件 → 返回 400")
        void shouldRejectEmptyFile() throws Exception {
            mockMvc.perform(multipart("/upload/wallet-qrcode")
                            .file(emptyFile))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.msg").value("上传文件不能为空"));

            verify(storageService, never()).upload(any(), anyString());
        }
    }

    // ======================== 文件校验测试（通过 Mock 模拟 storageService 抛异常） ========================

    @Nested
    @DisplayName("文件格式与大小校验")
    class FileValidation {

        @Test
        @DisplayName("非 image 类型 → storageService 抛出 BusinessException")
        void shouldRejectNonImageType() throws Exception {
            when(storageService.upload(any(), anyString()))
                    .thenThrow(new com.task.platform.common.exception.BusinessException(
                            400, "仅支持上传图片格式文件（image/*），当前类型: application/pdf"));

            mockMvc.perform(multipart("/upload/image")
                            .file(nonImageFile))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("超过 5MB → storageService 抛出 BusinessException")
        void shouldRejectOversizedFile() throws Exception {
            when(storageService.upload(any(), anyString()))
                    .thenThrow(new com.task.platform.common.exception.BusinessException(
                            400, "文件大小不能超过 5MB"));

            mockMvc.perform(multipart("/upload/image")
                            .file(oversizedFile))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("空文件（0字节）→ storageService 抛出 BusinessException")
        void shouldRejectZeroByteFile() throws Exception {
            when(storageService.upload(any(), anyString()))
                    .thenThrow(new com.task.platform.common.exception.BusinessException(
                            400, "上传文件不能为空"));

            mockMvc.perform(multipart("/upload/image")
                            .file(emptyFile))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(400));
        }
    }
}
