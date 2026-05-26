# PDF AI 智能问答系统

基于 LangChain4j + Qdrant + Redis + MySQL 的 PDF 问答系统，支持语义检索与 RAG 混合排名。

## 技术栈

- **后端框架**：Spring Boot
- **向量数据库**：Qdrant (v1.9.0)
- **缓存/向量存储辅助**：Redis Stack Server
- **关系数据库**：MySQL 8.0+
- **文件存储**：阿里云 OSS
- **AI 模型**：OpenAI 兼容接口（可配置）

---

## 一键 Docker 环境

使用 Docker Compose 快速启动所有中间件。

### 1. 创建 `docker-compose.yml`

```yaml
version: '3.8'

services:
  mysql:
    image: mysql:8.0
    container_name: pdf-ai-mysql
    restart: always
    environment:
      MYSQL_ROOT_PASSWORD: 123456
      MYSQL_DATABASE: pdf_ai
      MYSQL_CHARACTER_SET_SERVER: utf8mb4
      MYSQL_COLLATION_SERVER: utf8mb4_unicode_ci
    ports:
      - "3306:3306"
    volumes:
      - mysql_data:/var/lib/mysql
      - ./init.sql:/docker-entrypoint-initdb.d/init.sql
    networks:
      - pdf-ai-network

  redis:
    image: redis/redis-stack-server:latest
    container_name: pdf-ai-redis
    restart: always
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    networks:
      - pdf-ai-network

  qdrant:
    image: qdrant/qdrant:v1.9.0
    container_name: pdf-ai-qdrant
    restart: always
    ports:
      - "6333:6333"
    volumes:
      - qdrant_data:/qdrant/storage
    networks:
      - pdf-ai-network

volumes:
  mysql_data:
  redis_data:
  qdrant_data:

networks:
  pdf-ai-network:
    driver: bridge
```

### 2. 准备数据库初始化脚本 `init.sql`
将下面提供的所有建表语句复制到一个 init.sql 文件中，放在与 docker-compose.yml 相同的目录下。MySQL 容器首次启动时会自动执行该脚本。

```sql
CREATE DATABASE IF NOT EXISTS pdf_ai;
USE pdf_ai;

-- 系统用户表
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) NOT NULL COMMENT '账号',
  `password` varchar(100) NOT NULL COMMENT '加密密码',
  `nickname` varchar(50) DEFAULT NULL COMMENT '昵称',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '0-禁用 1-正常',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统用户';

-- PDF文档表
CREATE TABLE `pdf_document` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'PDF主键ID',
  `user_id` bigint NOT NULL COMMENT '上传用户ID',
  `name` varchar(255) NOT NULL COMMENT 'PDF文件名',
  `file_path` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '' COMMENT 'PDF绝对路径',
  `file_size` bigint NOT NULL COMMENT '文件大小(字节)',
  `total_pages` int DEFAULT NULL COMMENT '总页数',
  `parse_status` tinyint NOT NULL DEFAULT '0' COMMENT '0-待解析 1-解析中 2-成功 3-失败',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_user_name` (`user_id`,`name`),
  KEY `user_createtime` (`user_id`,`create_time` DESC)
) ENGINE=InnoDB AUTO_INCREMENT=100033 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='PDF文档';

-- PDF分块表
CREATE TABLE `pdf_chunk` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分块ID',
  `pdf_id` bigint NOT NULL COMMENT '关联PDF ID',
  `chunk_index` int NOT NULL COMMENT '分块序号',
  `content` mediumtext NOT NULL COMMENT '分块原文',
  `page_num` int DEFAULT NULL COMMENT 'PDF页码',
  `vector_id` varchar(100) DEFAULT NULL COMMENT '向量存储ID(用于删除)',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_pdf_id` (`pdf_id`)
) ENGINE=InnoDB AUTO_INCREMENT=1051 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='PDF分块文本';

-- AI会话表
CREATE TABLE `ai_chat_session` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '会话ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `session_name` varchar(255) NOT NULL DEFAULT '新对话' COMMENT '会话名',
  `bind_pdf_id` bigint DEFAULT NULL COMMENT '绑定的PDF ID',
  `deleted` tinyint NOT NULL DEFAULT '0',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI对话会话';

-- AI消息表
CREATE TABLE `ai_chat_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '消息ID',
  `session_id` bigint NOT NULL COMMENT '会话ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `query_content` text NOT NULL COMMENT '用户问题',
  `ai_response` mediumtext NOT NULL COMMENT 'AI回答',
  `retrieval_content` mediumtext COMMENT 'RAG召回原文',
  `retrieval_page_nums` varchar(500) DEFAULT NULL COMMENT '来源页码，多个用逗号分隔：1,5,9',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_session_id` (`session_id`)
) ENGINE=InnoDB AUTO_INCREMENT=30 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI问答消息';
```

### 3.环境变量配置 `application.yml`
## 设置方式（任选其一）
- 在 IDE 中：设置 Environment variables 启动参数。

- 命令行启动：

```bash
export langchain4j_open_ai_streaming_chat_model_api_key=sk-xxx
java -jar pdf-ai.jar
```
- 使用 .env 文件（配合 Docker Compose）：在 docker-compose.yml 同级目录创建 .env 文件，然后在 application.yml 或启动命令中引用。

敏感信息或差异化配置通过环境变量注入。请根据你的实际值设置以下环境变量：
环境变量名	说明	示例值

| 环境变量名 | 说明 | 示例值 |
|-----------|------|--------|
| `langchain4j.open-ai.streaming-chat-model.model-name` | 聊天模型名称 | `gpt-3.5-turbo` |
| `langchain4j.open-ai.streaming-chat-model.api-key` | OpenAI API Key | `sk-xxxx` |
| `langchain4j.open-ai.streaming-chat-model.base-url` | API 代理地址（可选） | `https://api.openai.com/v1` |
| `langchain4j.open-ai.embedding-model.model-name` | 嵌入模型名称 | `text-embedding-ada-002` |
| `langchain4j.open-ai.embedding-model.api-key` | 嵌入模型 API Key | `sk-xxxx` |
| `langchain4j.open-ai.embedding-model.base-url` | 嵌入模型代理地址 | `https://api.openai.com/v1` |
| `ai.alioss.endpoint` | 阿里云 OSS 端点 | `oss-cn-hangzhou.aliyuncs.com` |
| `ai.alioss.access-key-id` | 阿里云 AccessKey ID | `LTAI5t...` |
| `ai.alioss.access-key-secret` | 阿里云 AccessKey Secret | `...` |
| `ai.alioss.bucket-name` | OSS 存储桶名称 | `pdf-ai-bucket` |

应用配置说明 (`application.yml` 结构)
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/pdf_ai?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root
    password: 123456          # 应与 docker-compose 中的 MYSQL_ROOT_PASSWORD 一致
  data:
    redis:
      host: localhost
      port: 6379
  profiles:
    active: dev               # 激活 dev 配置，可自定义

langchain4j:
  open-ai:
    streaming-chat-model:
      model-name: ${langchain4j.open-ai.streaming-chat-model.model-name}
      api-key: ${langchain4j.open-ai.streaming-chat-model.api-key}
      base-url: ${langchain4j.open-ai.streaming-chat-model.base-url}
    embedding-model:
      model-name: ${langchain4j.open-ai.embedding-model.model-name}
      api-key: ${langchain4j.open-ai.embedding-model.api-key}
      base-url: ${langchain4j.open-ai.embedding-model.base-url}

ai:
  alioss:
    endpoint: ${ai.alioss.endpoint}
    access-key-id: ${ai.alioss.access-key-id}
    access-key-secret: ${ai.alioss.access-key-secret}
    bucket-name: ${ai.alioss.bucket-name}
```
---

## 贡献与许可
本项目遵循 MIT 协议，欢迎提交 Issue 或 PR。
