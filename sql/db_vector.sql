-- ==============================================================================
-- OmniMerchant - PGVector 向量库
-- 数据库版本: PostgreSQL 16 + pgvector 0.7+
-- 索引类型: HNSW (高召回率 + 低延迟,适合在线查询)
-- ==============================================================================

-- 创建数据库
-- CREATE DATABASE omnimerchant_vector;
-- \c omnimerchant_vector;

-- 启用扩展
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS pg_trgm;       -- 三元组索引(模糊匹配)
CREATE EXTENSION IF NOT EXISTS btree_gin;     -- 加速 metadata 过滤

-- ==============================================================================
-- 表 V1: policy_vectors (政策/知识库向量表)
-- ==============================================================================
-- 业务背景:
--   存储 knowledge_doc 切分后的 chunk,每个 chunk 一行。
--   metadata 用 JSONB 存灵活属性(章节/页码/段落),便于 SearchRequest 过滤。
--   chunk_text 同时存原文和处理后的全文索引列,支持向量+BM25 混合检索。
-- ==============================================================================
DROP TABLE IF EXISTS policy_vectors CASCADE;
CREATE TABLE policy_vectors (
    id              BIGSERIAL                          PRIMARY KEY,
    chunk_uuid      VARCHAR(64)               NOT NULL UNIQUE,

    -- 多租户隔离
    tenant_id       BIGINT                    NOT NULL,

    -- 关联文档
    doc_id          BIGINT                    NOT NULL,
    doc_uuid        VARCHAR(64)               NOT NULL,
    doc_type        VARCHAR(32)               NOT NULL,
    doc_version     INT                       NOT NULL DEFAULT 1,

    -- chunk 基础信息
    chunk_index     INT                       NOT NULL,
    chunk_text      TEXT                      NOT NULL,
    chunk_text_en   TEXT,                              -- 英语翻译版本(用于跨语言检索)
    chunk_length    INT                       NOT NULL,
    section         VARCHAR(255),                      -- 章节(如 "Returns","Shipping")
    section_path    VARCHAR(512),                      -- 完整路径(如 "Returns > International")
    page_number     INT,                               -- 页码(PDF 来源时)

    -- 多语言
    language        VARCHAR(8)                NOT NULL DEFAULT 'en',

    -- 向量(OpenAI text-embedding-3-small = 1536 维)
    embedding       vector(1536)              NOT NULL,
    embedding_model VARCHAR(64)               NOT NULL DEFAULT 'text-embedding-3-small',

    -- 元数据(灵活扩展)
    metadata        JSONB                     NOT NULL DEFAULT '{}',

    -- 全文检索辅助列(英语 BM25)
    chunk_tsv       tsvector GENERATED ALWAYS AS (
                        to_tsvector('english', COALESCE(chunk_text_en, chunk_text))
                    ) STORED,

    -- 检索统计(用于优化)
    retrieval_count INT                       NOT NULL DEFAULT 0,
    last_retrieved_at TIMESTAMP,

    created_at      TIMESTAMP                 NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP                 NOT NULL DEFAULT NOW()
);

-- 注释
COMMENT ON TABLE  policy_vectors IS '政策/知识库向量表';
COMMENT ON COLUMN policy_vectors.tenant_id IS '租户ID(必须用于过滤)';
COMMENT ON COLUMN policy_vectors.embedding IS 'OpenAI text-embedding-3-small 1536 维';
COMMENT ON COLUMN policy_vectors.metadata IS '元数据 JSONB,可存任意属性';

-- 多租户隔离索引(WHERE tenant_id = ? 必走此索引)
CREATE INDEX idx_policy_vec_tenant      ON policy_vectors (tenant_id);
CREATE INDEX idx_policy_vec_tenant_doc  ON policy_vectors (tenant_id, doc_id);
CREATE INDEX idx_policy_vec_tenant_type ON policy_vectors (tenant_id, doc_type, language);

-- HNSW 向量索引(余弦相似度)
-- m=16: 每个节点最多 16 个邻居(默认值,平衡召回/构建速度)
-- ef_construction=64: 构建时搜索深度
-- 查询时通过 SET hnsw.ef_search = 40; 调整召回质量
CREATE INDEX idx_policy_vec_embedding ON policy_vectors
USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);

-- 全文检索索引(BM25 用)
CREATE INDEX idx_policy_vec_tsv ON policy_vectors USING gin(chunk_tsv);

-- 元数据过滤索引(JSONB)
CREATE INDEX idx_policy_vec_metadata ON policy_vectors USING gin(metadata);

-- 文档版本快速过滤(只查最新版本)
CREATE INDEX idx_policy_vec_doc_version ON policy_vectors (doc_id, doc_version DESC);

-- 自动更新 updated_at 触发器
CREATE OR REPLACE FUNCTION trg_update_timestamp()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_policy_vec_update
BEFORE UPDATE ON policy_vectors
FOR EACH ROW EXECUTE FUNCTION trg_update_timestamp();


-- ==============================================================================
-- 表 V2: product_vectors (商品向量表)
-- ==============================================================================
-- 业务背景:
--   商品按多种粒度切分:title/description/attributes/reviews 各成一行。
--   chunk_type 标识来源,检索时可加权(title 比 review 权重高)。
-- ==============================================================================
DROP TABLE IF EXISTS product_vectors CASCADE;
CREATE TABLE product_vectors (
    id              BIGSERIAL                          PRIMARY KEY,
    chunk_uuid      VARCHAR(64)               NOT NULL UNIQUE,

    -- 多租户
    tenant_id       BIGINT                    NOT NULL,

    -- 关联商品
    product_id      BIGINT                    NOT NULL,
    external_product_id VARCHAR(128)          NOT NULL,
    sku             VARCHAR(128),

    -- chunk 信息
    chunk_index     INT                       NOT NULL,
    chunk_text      TEXT                      NOT NULL,
    chunk_text_en   TEXT,
    chunk_type      VARCHAR(32)               NOT NULL,  -- title/description/attribute/feature/review/qa
    chunk_weight    REAL                      NOT NULL DEFAULT 1.0,  -- 检索时的权重

    -- 商品分类(冗余便于过滤)
    category_l1     VARCHAR(64),
    category_l2     VARCHAR(64),
    brand           VARCHAR(128),
    price           DECIMAL(12,4),

    language        VARCHAR(8)                NOT NULL DEFAULT 'en',

    -- 向量
    embedding       vector(1536)              NOT NULL,
    embedding_model VARCHAR(64)               NOT NULL DEFAULT 'text-embedding-3-small',

    metadata        JSONB                     NOT NULL DEFAULT '{}',

    chunk_tsv       tsvector GENERATED ALWAYS AS (
                        to_tsvector('english', COALESCE(chunk_text_en, chunk_text))
                    ) STORED,

    retrieval_count INT                       NOT NULL DEFAULT 0,
    last_retrieved_at TIMESTAMP,

    created_at      TIMESTAMP                 NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP                 NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE product_vectors IS '商品向量表';

CREATE INDEX idx_product_vec_tenant         ON product_vectors (tenant_id);
CREATE INDEX idx_product_vec_tenant_product ON product_vectors (tenant_id, product_id);
CREATE INDEX idx_product_vec_tenant_cat     ON product_vectors (tenant_id, category_l1, category_l2);
CREATE INDEX idx_product_vec_tenant_type    ON product_vectors (tenant_id, chunk_type);

CREATE INDEX idx_product_vec_embedding ON product_vectors
USING hnsw (embedding vector_cosine_ops)
WITH (m = 16, ef_construction = 64);

CREATE INDEX idx_product_vec_tsv      ON product_vectors USING gin(chunk_tsv);
CREATE INDEX idx_product_vec_metadata ON product_vectors USING gin(metadata);
CREATE INDEX idx_product_vec_sku      ON product_vectors (tenant_id, sku);

-- 价格范围过滤(对比型查询)
CREATE INDEX idx_product_vec_price ON product_vectors (tenant_id, price);

CREATE TRIGGER trg_product_vec_update
BEFORE UPDATE ON product_vectors
FOR EACH ROW EXECUTE FUNCTION trg_update_timestamp();


-- ==============================================================================
-- 表 V3: query_cache_vectors (Query 缓存向量表)
-- ==============================================================================
-- 业务背景:
--   缓存常见用户问题的 embedding,新查询先做相似度搜索,
--   如果有 score > 0.95 的旧问题,直接复用旧问题的检索结果。
--   降低 embedding 调用 + RAG 检索的成本。
-- ==============================================================================
DROP TABLE IF EXISTS query_cache_vectors CASCADE;
CREATE TABLE query_cache_vectors (
    id              BIGSERIAL                          PRIMARY KEY,
    tenant_id       BIGINT                    NOT NULL,

    query_text      TEXT                      NOT NULL,
    query_text_en   TEXT,
    query_hash      CHAR(32)                  NOT NULL,
    language        VARCHAR(8)                NOT NULL DEFAULT 'en',

    embedding       vector(1536)              NOT NULL,

    -- 缓存的检索结果(JSONB)
    retrieval_result JSONB                    NOT NULL,
    result_score    REAL                      NOT NULL DEFAULT 0,

    hit_count       INT                       NOT NULL DEFAULT 0,
    last_hit_at     TIMESTAMP,
    expires_at      TIMESTAMP                 NOT NULL,

    created_at      TIMESTAMP                 NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uk_query_cache_hash ON query_cache_vectors (tenant_id, query_hash);
CREATE INDEX idx_query_cache_tenant     ON query_cache_vectors (tenant_id);
CREATE INDEX idx_query_cache_embedding  ON query_cache_vectors
USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_query_cache_expires    ON query_cache_vectors (expires_at);


-- ==============================================================================
-- 视图: 跨租户的统计(供后台管理用)
-- ==============================================================================
CREATE OR REPLACE VIEW v_vector_storage_stats AS
SELECT
    'policy_vectors' AS table_name,
    tenant_id,
    COUNT(*) AS chunk_count,
    SUM(chunk_length) AS total_chars,
    MAX(updated_at) AS last_updated
FROM policy_vectors
GROUP BY tenant_id
UNION ALL
SELECT
    'product_vectors' AS table_name,
    tenant_id,
    COUNT(*) AS chunk_count,
    SUM(LENGTH(chunk_text)) AS total_chars,
    MAX(updated_at) AS last_updated
FROM product_vectors
GROUP BY tenant_id;


-- ==============================================================================
-- 常用查询示例(注释,供参考)
-- ==============================================================================

-- 1. 纯向量检索(取 Top 10)
-- SELECT id, chunk_text, 1 - (embedding <=> '[...]'::vector) AS similarity
-- FROM policy_vectors
-- WHERE tenant_id = 1 AND doc_type = 'REFUND_POLICY'
-- ORDER BY embedding <=> '[...]'::vector
-- LIMIT 10;

-- 2. 混合检索(向量 0.7 + BM25 0.3)
-- WITH vec AS (
--     SELECT id, 1 - (embedding <=> $1::vector) AS score
--     FROM policy_vectors
--     WHERE tenant_id = $2
--     ORDER BY embedding <=> $1::vector
--     LIMIT 20
-- ),
-- bm AS (
--     SELECT id, ts_rank(chunk_tsv, plainto_tsquery('english', $3)) AS score
--     FROM policy_vectors
--     WHERE tenant_id = $2
--       AND chunk_tsv @@ plainto_tsquery('english', $3)
--     LIMIT 20
-- )
-- SELECT p.id, p.chunk_text,
--        COALESCE(vec.score, 0) * 0.7 + COALESCE(bm.score, 0) * 0.3 AS final_score
-- FROM policy_vectors p
-- LEFT JOIN vec ON p.id = vec.id
-- LEFT JOIN bm  ON p.id = bm.id
-- WHERE vec.id IS NOT NULL OR bm.id IS NOT NULL
-- ORDER BY final_score DESC
-- LIMIT 10;
