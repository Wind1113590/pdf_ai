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
      - ./init.sql:/docker-entrypoint-initdb.d/init.sql   # 挂载初始化SQL
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



2. 准备数据库初始化脚本 init.sql
将下面提供的所有建表语句复制到一个 init.sql 文件中，放在与 docker-compose.yml 相同的目录下。MySQL 容器首次启动时会自动执行该脚本。
