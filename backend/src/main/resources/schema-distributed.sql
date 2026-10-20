-- Remote article IDs belong to the knowledge database; no local foreign key is valid.
SELECT pg_advisory_lock(741852962);
CREATE TABLE IF NOT EXISTS knowledge_external_vector_index (
 article_id BIGINT PRIMARY KEY, fingerprint VARCHAR(64) NOT NULL,
 indexed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP);
CREATE TABLE IF NOT EXISTS smartticket_article_vectors (
 id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
 content TEXT, metadata JSON, embedding vector(768));
CREATE INDEX IF NOT EXISTS idx_smartticket_vectors_embedding
 ON smartticket_article_vectors USING hnsw (embedding vector_cosine_ops);
SELECT pg_advisory_unlock(741852962);
