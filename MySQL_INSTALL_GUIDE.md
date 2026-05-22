# MySQL 安装配置指南

## 一、MySQL 下载

### 1. 访问 MySQL 官网下载页
https://dev.mysql.com/downloads/mysql/

### 2. 选择版本
- 选择 MySQL Community Server
- 选择 Windows (x86, 64-bit), ZIP Archive
- 或者直接下载 MySQL Installer

### 推荐：使用 MySQL Installer（更简单）
https://dev.mysql.com/downloads/installer/

---

## 二、安装步骤

### 方法一：使用 MySQL Installer（推荐）

1. 下载 MySQL Installer (mysql-installer-community-8.x.x.x.msi)
2. 双击运行安装程序
3. 选择安装类型：Custom（自定义）
4. 选择要安装的产品：
   - MySQL Server 8.x
   - MySQL Workbench（可选，用于管理）
   - MySQL Shell（可选）
5. 设置安装路径：
   - 将安装目录设置为：`D:\mysql\`
   - 将数据目录设置为：`D:\mysql\data\`
6. 设置 root 密码（请记住此密码）
7. 配置端口（默认3306）
8. 完成安装

### 方法二：使用 ZIP 压缩包（手动安装）

1. 下载 MySQL ZIP 压缩包
2. 解压到：`D:\mysql\`
3. 在 `D:\mysql\` 目录下创建 `data` 文件夹
4. 创建配置文件 `my.ini`：
```ini
[mysqld]
basedir=D:\\mysql
datadir=D:\\mysql\\data
port=3306
character-set-server=utf8mb4
default-storage-engine=INNODB

[mysql]
default-character-set=utf8mb4

[client]
default-character-set=utf8mb4
```
5. 以管理员身份打开命令行，进入 `D:\mysql\bin`
6. 初始化数据库：
```cmd
mysqld --initialize --console
```
7. 记下生成的临时 root 密码
8. 安装为服务：
```cmd
mysqld --install MySQL8
```
9. 启动服务：
```cmd
net start MySQL8
```
10. 登录并修改密码：
```cmd
mysql -u root -p
```
然后执行：
```sql
ALTER USER 'root'@'localhost' IDENTIFIED BY '你的新密码';
FLUSH PRIVILEGES;
```

---

## 三、创建数据库和用户

### 1. 登录 MySQL
```cmd
mysql -u root -p
```

### 2. 创建数据库
```sql
CREATE DATABASE tradingx CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 3. 创建专用用户（可选，推荐）
```sql
CREATE USER 'tradingx'@'localhost' IDENTIFIED BY 'tradingx_password';
GRANT ALL PRIVILEGES ON tradingx.* TO 'tradingx'@'localhost';
FLUSH PRIVILEGES;
```

---

## 四、数据库初始化

执行项目中的 `schema.sql` 文件来创建表结构。

---

## 五、验证安装

1. 打开浏览器访问：`http://localhost:8080`
2. 查看后端日志，确认数据库连接成功

---

## 六、常见问题

### 问题1：端口被占用
修改 `my.ini` 中的 port 配置，或关闭占用3306端口的程序。

### 问题2：忘记 root 密码
1. 停止 MySQL 服务
2. 以跳过权限验证方式启动：
```cmd
mysqld --skip-grant-tables
```
3. 另开一个命令行，修改密码
4. 重启 MySQL 服务

### 问题3：数据目录权限问题
确保 `D:\mysql\data` 目录有足够的权限。
