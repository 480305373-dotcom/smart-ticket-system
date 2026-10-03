<script setup>
import { ref, reactive, onMounted } from "vue";
import { useAuth } from "../stores/auth";
import { useRoute } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import api from "../api";
import { date } from "../utils";
const auth = useAuth(),
  route = useRoute(),
  management = route.path.startsWith("/admin"),
  rows = ref([]),
  total = ref(0),
  categories = ref([]),
  loading = ref(false),
  failed = ref(false);
const filters = reactive({
  title: "",
  keyword: "",
  category: "",
  current: 1,
  size: 9,
});
async function load(reset = false) {
  if (reset) filters.current = 1;
  loading.value = true;
  failed.value = false;
  try {
    const p = await api.get("/knowledge", {
      params: Object.fromEntries(
        Object.entries(filters).filter(([, v]) => v !== ""),
      ),
    });
    rows.value = p.records;
    total.value = p.total;
    categories.value = await api.get("/knowledge/categories");
  } catch {
    failed.value = true;
  } finally {
    loading.value = false;
  }
}
async function remove(id) {
  try {
    await ElMessageBox.confirm("确认删除此知识文章？", "删除知识", {
      confirmButtonText: "删除",
      cancelButtonText: "取消",
      type: "warning",
    });
    await api.delete(`/knowledge/${id}`);
    ElMessage.success("文章已删除");
    await load();
  } catch {}
}
onMounted(() => load());
</script>
<template>
  <section class="panel">
    <div class="panel-title">
      <div>
        <h2>企业知识中心</h2>
        <p class="muted">查找常见问题的排查步骤与服务申请流程</p>
      </div>
      <el-button
        v-if="auth.isAdmin"
        type="primary"
        @click="$router.push('/admin/knowledge/create')"
        >新增文章</el-button
      >
    </div>
    <el-form class="filter-bar" inline @submit.prevent="load(true)"
      ><el-form-item
        ><el-input
          v-model="filters.title"
          placeholder="搜索文章标题"
          clearable /></el-form-item
      ><el-form-item
        ><el-input
          v-model="filters.keyword"
          placeholder="关键词，如 VPN、邮箱"
          clearable /></el-form-item
      ><el-form-item
        ><el-select
          v-model="filters.category"
          clearable
          placeholder="全部分类"
          style="width: 170px"
          ><el-option
            v-for="c in categories"
            :key="c"
            :value="c"
            :label="c" /></el-select></el-form-item
      ><el-form-item
        ><el-button type="primary" native-type="submit"
          >查询</el-button
        ></el-form-item
      ></el-form
    ><el-alert
      v-if="failed"
      title="知识库加载失败，请重新查询"
      type="error"
      :closable="false"
    />
    <div v-loading="loading" class="knowledge-grid">
      <article v-for="a in rows" :key="a.id" class="knowledge-card">
        <div class="panel-title">
          <el-tag size="small" type="info">{{ a.category }}</el-tag
          ><el-tag v-if="a.status === 'DRAFT'" size="small" type="warning"
            >草稿</el-tag
          >
        </div>
        <router-link :to="`/knowledge/${a.id}`"
          ><h3>{{ a.title }}</h3></router-link
        >
        <p>{{ a.summary }}</p>
        <small class="muted">更新于 {{ date(a.updatedAt) }}</small>
        <div v-if="management" class="form-actions">
          <el-button
            link
            type="primary"
            @click="$router.push(`/admin/knowledge/${a.id}/edit`)"
            >编辑 / 发布</el-button
          ><el-button link type="danger" @click="remove(a.id)">删除</el-button>
        </div>
      </article>
    </div>
    <el-empty
      v-if="!loading && !rows.length && !failed"
      description="暂无匹配的知识文章"
    /><el-pagination
      v-model:current-page="filters.current"
      :page-size="filters.size"
      :total="total"
      layout="total, prev, pager, next"
      @current-change="load()"
    />
  </section>
</template>
