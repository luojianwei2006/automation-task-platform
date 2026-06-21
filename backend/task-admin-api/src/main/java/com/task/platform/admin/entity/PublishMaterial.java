package com.task.platform.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 素材表实体（视频发布功能）
 * 对应数据库表：t_material
 */
@Data
@TableName("t_material")
public class PublishMaterial {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联项目ID */
    @TableField("project_id")
    private Long projectId;

    /** 素材类型：text/image/music/video */
    private String type;

    /** 素材标题 */
    private String title;

    /** 文件URL */
    @TableField("file_url")
    private String fileUrl;

    /** 文件大小(字节) */
    @TableField("file_size")
    private Long fileSize;

    /** 文案内容(type=text时) */
    private String content;

    /** 时长(秒) */
    private Integer duration;

    /** 分辨率 */
    private String resolution;

    /** 段落序号 */
    @TableField("sort_order")
    private Integer sortOrder = 0;

    /** 逻辑删除：0=正常 1=已删除 */
    private Integer deleted = 0;

    /** 创建时间 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
