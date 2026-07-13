package com.task.platform.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 协议文档实体
 * 对应 t_agreement 表，存储「关于我们 / 隐私协议 / 注册协议」等富文本 HTML。
 *
 * <p>读写双方（admin-api 写、user-service 读）共用此实体与 {@code AgreementMapper}，
 * 因为两模块的 datasource 指向同一 {@code task_platform} 库，且字段结构完全一致。</p>
 *
 * @author TaskPlatform
 */
@Data
@TableName("t_agreement")
public class Agreement implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 协议类型：about / privacy / register（唯一索引） */
    private String type;

    /** 协议标题 */
    private String title;

    /** 协议内容 HTML 片段（标准 HTML，不含 html/head/body 外壳） */
    private String contentHtml;

    /** 版本号，每次保存自增 */
    private Integer version;

    /** 更新时间（入库自动刷新；admin-api 的 MetaObjectHandler 会填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /** 最后操作人（admin 用户ID），缺失时由服务层落默认值 */
    private String updatedBy;
}
