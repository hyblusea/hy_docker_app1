package com.tradingx.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 策略实体类
 * 存储量化交易策略的基本信息，包括策略名称、代码、描述等
 */
@Entity
@Table(name = "strategy", comment = "策略表 - 存储量化交易策略的基本信息")
public class Strategy {

    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 策略名称
     */
    @Column(nullable = false, comment = "策略名称")
    private String name;

    /**
     * 策略语言（如 Python）
     */
    @Column(nullable = false, comment = "策略语言")
    private String language;

    /**
     * 策略代码
     */
    @Column(columnDefinition = "TEXT", comment = "策略代码")
    private String code;

    /**
     * 策略描述
     */
    @Column(columnDefinition = "TEXT", comment = "策略描述")
    private String description;

    /**
     * 是否有效
     */
    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE", comment = "是否有效")
    private Boolean valid = true;

    /**
     * 编译错误信息
     */
    @JsonProperty("compile_error")
    @Column(name = "compile_error", columnDefinition = "TEXT", comment = "编译错误信息")
    private String compileError;

    /**
     * 创建人用户名
     */
    @JsonProperty("created_by")
    @Column(name = "created_by", comment = "创建人用户名")
    private String createdBy;

    /**
     * 创建人角色
     */
    @JsonProperty("created_by_role")
    @Column(name = "created_by_role", comment = "创建人角色")
    private String createdByRole;

    /**
     * 创建时间
     */
    @JsonProperty("created_at")
    @Column(name = "created_at", updatable = false, comment = "创建时间")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @JsonProperty("updated_at")
    @Column(name = "updated_at", comment = "更新时间")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Boolean getValid() { return valid; }
    public void setValid(Boolean valid) { this.valid = valid; }
    public String getCompileError() { return compileError; }
    public void setCompileError(String compileError) { this.compileError = compileError; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public String getCreatedByRole() { return createdByRole; }
    public void setCreatedByRole(String createdByRole) { this.createdByRole = createdByRole; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}