<script setup>
import { ref, onMounted } from "vue";
import { ElMessage } from "element-plus";
import api from "../api";
const enabled = ref(false),
  ready = ref(false),
  question = ref(""),
  busy = ref(false),
  answer = ref(null);
onMounted(async () => {
  try {
    enabled.value = (await api.get("/system/capabilities")).ai;
    ready.value = true;
  } catch {}
});
async function ask() {
  if (!question.value.trim()) return ElMessage.warning("请输入问题");
  busy.value = true;
  answer.value = null;
  try {
    answer.value = await api.post(
      "/ai/assist",
      { message: question.value },
      { timeout: 180000 },
    );
  } catch {
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <section class="panel">
    <h2>知识问答助手</h2>
    <p class="muted">
      使用 Ollama 本地模型、Embedding 与 pgvector
      检索已发布知识。回答仅供排查参考，来源文章可直接核对。
    </p>
    <el-alert
      v-if="!ready"
      title="正在读取服务能力，失败时请刷新重试"
      type="info"
      :closable="false"
    /><el-alert
      v-else-if="!enabled"
      title="当前为轻量模式。启用 enterprise,ai 配置后可使用向量检索问答；工单关键词推荐仍可正常使用。"
      type="info"
      :closable="false"
    />
    <el-form v-else @submit.prevent="ask"
      ><el-form-item
        ><el-input
          v-model="question"
          type="textarea"
          :rows="4"
          maxlength="2000"
          show-word-limit
          placeholder="例如：VPN提示认证失败，应如何排查？"
          aria-label="知识问题" /></el-form-item
      ><el-button type="primary" native-type="submit" :loading="busy"
        >检索并回答</el-button
      >
      <p v-if="busy" class="muted">
        本地模型正在生成，首次加载可能需要较长时间。
      </p></el-form
    >
    <template v-if="answer"
      ><h3>回答</h3>
      <p class="pre-wrap">{{ answer.answer }}</p>
      <h3 v-if="answer.sources.length">参考知识</h3>
      <p v-for="source in answer.sources" :key="source.id">
        <router-link :to="`/knowledge/${source.id}`"
          >[{{ source.id }}] {{ source.title }}</router-link
        >
      </p></template
    >
  </section>
</template>
