<script setup>
import { ref, onMounted } from "vue";
import { useRoute } from "vue-router";
import { useAuth } from "../stores/auth";
import api from "../api";
import { date } from "../utils";
const route = useRoute(),
  auth = useAuth(),
  article = ref(null),
  error = ref(""),
  loading = ref(true);
onMounted(async () => {
  try {
    article.value = await api.get(`/knowledge/${route.params.id}`);
  } catch (e) {
    error.value = e.userMessage || "文章加载失败";
  } finally {
    loading.value = false;
  }
});
</script>
<template>
  <section class="panel article-panel" v-loading="loading">
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
    /><template v-if="article"
      ><div class="panel-title">
        <el-tag>{{ article.category }}</el-tag
        ><el-button
          v-if="auth.isAdmin"
          @click="$router.push(`/admin/knowledge/${article.id}/edit`)"
          >编辑文章</el-button
        >
      </div>
      <h2 class="article-title">{{ article.title }}</h2>
      <p class="muted">
        {{ date(article.updatedAt) }} ·
        {{ article.status === "DRAFT" ? "未发布草稿" : "已发布" }}
      </p>
      <p class="article-summary">{{ article.summary }}</p>
      <article class="article-content pre-wrap">{{ article.content }}</article>
      <div class="keywords">
        <el-tag
          v-for="k in article.keywords.split(/[,，;；\s]+/)"
          :key="k"
          type="info"
          >{{ k }}</el-tag
        >
      </div></template
    >
  </section>
</template>
