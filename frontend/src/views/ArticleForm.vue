<script setup>
import { reactive, ref, onMounted } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import api from "../api";
const route = useRoute(),
  router = useRouter(),
  editing = !!route.params.id,
  formRef = ref(),
  busy = ref(false),
  ready = ref(!editing);
const form = reactive({
  title: "",
  summary: "",
  content: "",
  category: "",
  keywords: "",
  status: "DRAFT",
});
const rules = Object.fromEntries(
  [
    ["title", 200],
    ["summary", 500],
    ["content", 50000],
    ["category", 100],
    ["keywords", 500],
  ].map(([key, max]) => [
    key,
    [
      { required: true, message: "请填写此项", trigger: "blur" },
      { max, message: `不能超过${max}字`, trigger: "blur" },
    ],
  ]),
);
onMounted(async () => {
  if (editing)
    try {
      const a = await api.get(`/knowledge/${route.params.id}`);
      for (const k of Object.keys(form)) form[k] = a[k];
      ready.value = true;
    } catch {}
});
async function submit() {
  if (!(await formRef.value.validate().catch(() => false))) return;
  busy.value = true;
  try {
    const a = editing
      ? await api.put(`/knowledge/${route.params.id}`, form)
      : await api.post("/knowledge", form);
    ElMessage.success(
      form.status === "PUBLISHED" ? "文章已发布" : "草稿已保存",
    );
    router.push(`/knowledge/${a.id}`);
  } catch {
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <section class="panel form-panel">
    <div class="panel-title">
      <h2>{{ editing ? "编辑知识文章" : "新增知识文章" }}</h2>
      <el-button text @click="$router.push('/admin/knowledge')"
        >返回知识管理</el-button
      >
    </div>
    <el-alert
      v-if="!ready"
      title="文章加载失败"
      type="error"
      :closable="false"
    /><el-form
      v-else
      ref="formRef"
      :model="form"
      :rules="rules"
      label-position="top"
      @submit.prevent="submit"
      ><el-form-item label="标题" prop="title"
        ><el-input v-model="form.title" maxlength="200" /></el-form-item
      ><el-form-item label="摘要" prop="summary"
        ><el-input
          v-model="form.summary"
          type="textarea"
          :rows="2"
          maxlength="500" /></el-form-item
      ><el-form-item label="分类" prop="category"
        ><el-input
          v-model="form.category"
          maxlength="100"
          placeholder="如：网络支持、账号权限" /></el-form-item
      ><el-form-item label="关键词" prop="keywords"
        ><el-input
          v-model="form.keywords"
          maxlength="500"
          placeholder="用逗号分隔，如：VPN,认证,账号" /></el-form-item
      ><el-form-item label="正文（纯文本）" prop="content"
        ><el-input
          v-model="form.content"
          type="textarea"
          :rows="14"
          maxlength="50000"
          placeholder="问题现象、可能原因、排查步骤、解决方法、注意事项" /></el-form-item
      ><el-form-item label="发布状态"
        ><el-radio-group v-model="form.status"
          ><el-radio value="DRAFT">草稿</el-radio
          ><el-radio value="PUBLISHED">发布</el-radio></el-radio-group
        ></el-form-item
      >
      <div class="form-actions">
        <el-button @click="$router.push('/admin/knowledge')">取消</el-button
        ><el-button type="primary" native-type="submit" :loading="busy"
          >保存文章</el-button
        >
      </div></el-form
    >
  </section>
</template>
