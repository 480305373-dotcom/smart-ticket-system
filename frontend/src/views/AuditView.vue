<script setup>
import { onMounted, ref } from "vue";
import api from "../api";
import { date } from "../utils";
const rows = ref([]),
  total = ref(0),
  page = ref(1),
  busy = ref(false),
  failed = ref(false);
async function load() {
  busy.value = true;
  failed.value = false;
  try {
    const r = await api.get("/operation-logs", {
      params: { current: page.value, size: 20 },
    });
    rows.value = r.records;
    total.value = r.total;
  } catch {
    failed.value = true;
  } finally {
    busy.value = false;
  }
}
onMounted(load);
</script>
<template>
  <section class="panel">
    <div class="panel-title">
      <h2>操作审计</h2>
      <el-button @click="load">刷新</el-button>
    </div>
    <el-alert
      v-if="failed"
      title="审计读取失败，请重试"
      type="error"
      :closable="false"
    />
    <el-table v-loading="busy" :data="rows" empty-text="暂无审计记录"
      ><el-table-column prop="id" label="ID" width="80" /><el-table-column
        prop="userId"
        label="操作人 ID"
        width="110"
      /><el-table-column
        prop="action"
        label="操作"
        min-width="190"
      /><el-table-column
        prop="targetType"
        label="资源"
        width="90"
      /><el-table-column
        prop="targetId"
        label="资源 ID"
        width="100"
      /><el-table-column
        prop="detail"
        label="说明"
        min-width="280"
      /><el-table-column label="时间" width="180"
        ><template #default="{ row }">{{
          date(row.createdAt)
        }}</template></el-table-column
      ></el-table
    >
    <el-pagination
      v-model:current-page="page"
      :page-size="20"
      :total="total"
      layout="total,prev,pager,next"
      @current-change="load"
    />
  </section>
</template>
