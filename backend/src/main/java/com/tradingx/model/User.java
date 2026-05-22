package com.tradingx.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 用户实体类
 * 存储系统用户的基本信息，包括用户名、密码、角色等
 */
@Entity
@Table(name = "app_user", uniqueConstraints = @UniqueConstraint(columnNames = "username"), comment = "用户表 - 存储系统用户的基本信息")
public class User {

    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 用户名（唯一）
     */
    @Column(nullable = false, unique = true, comment = "用户名")
    private String username;

    /**
     * 密码（加密存储）
     */
    @Column(nullable = false, comment = "密码（加密存储）")
    private String password;

    /**
     * 用户角色（如 user、admin）
     */
    @Column(nullable = false, comment = "用户角色")
    private String role = "user";

    /**
     * 用户状态（如 pending、active）
     */
    @Column(nullable = false, comment = "用户状态")
    private String status = "pending";

    /**
     * 创建时间
     */
    @JsonProperty("created_at")
    @Column(name = "created_at", updatable = false, comment = "创建时间")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    @JsonProperty("created_at")
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}