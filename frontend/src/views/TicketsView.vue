<script setup>
import { onMounted, reactive, ref, computed } from "vue";
import { useRoute } from "vue-router";
import { useAuth } from "../stores/auth";
import api from "../api";
import { date, statuses, statusType, priorityLabel } from "../utils";
const auth = useAuth(),
  rows = ref([]),
  total = ref(0),
  loading = ref(false),
  failed = ref(false);
const route = useRoute(),
  handlers = ref([]);
const assigned = computed(() => route.path === "/admin/assigned");
const filters = reactive({
  keyword: "",
  status: "",
  priority: null,
  username: "",
  assigneeId: null,
  current: 1,
  size: 10,
});
async function load(reset = false) {
  if (reset) filters.current = 1;
  loading.value = true;
  failed.value = false;
  try {
    const p = await api.get("/tickets/page", {
      params: Object.fromEntries(
        Object.entries({
          ...filters,
          ...(assigned.value ? { assignedToMe: true } : {}),
        }).filter(([, v]) => v !== "" && v != null),
      ),
    });
    rows.value = p.records;
    total.value = p.total;
  } catch {
    failed.value = true;
  } finally {
    loading.value = false;
  }
}
onMounted(async () => {
  await load();
  if (auth.isAdmin && !assigned.value) {
    try {
      handlers.value = await api.get("/users/handlers");
    } catch {}
  }
});
</script>
<template>
  <section class="panel">
    <div class="panel-title">
      <h2>
        {{
          assigned
            ? "分配给我的工单"
            : auth.isAdmin
              ? "全部服务工单"
              : "我的服务工单"
        }}
      </h2>
      <el-button type="primary" @click="$router.push('/tickets/create')"
        >创建工单</el-button
      >
    </div>
    <el-form class="filter-bar" inline @submit.prevent="load(true)"
      ><el-form-item
        ><el-input
          v-model="filters.keyword"
          placeholder="搜索标题或描述"
          clearable
          aria-label="标题关键词" /></el-form-item
      ><el-form-item
        ><el-select
          v-model="filters.status"
          placeholder="全部状态"
          clearable
          style="width: 150px"
          ><el-option
            v-for="(label, value) in statuses"
            :key="value"
            :label="label"
            :value="value" /></el-select></el-form-item
      ><el-form-item
        ><el-select
          v-model="filters.priority"
          placeholder="全部优先级"
          clearable
          style="width: 150px"
          ><el-option
            v-for="n in 5"
            :key="n"
            :label="priorityLabel(n)"
            :value="n" /></el-select></el-form-item
      ><el-form-item v-if="auth.isAdmin"
        ><el-input
          v-model="filters.username"
          clearable
          placeholder="创建用户名" /></el-form-item
      ><el-form-item v-if="auth.isAdmin && !assigned"
        ><el-select
          v-model="filters.assigneeId"
          placeholder="全部处理人"
          clearable
          style="width: 170px"
          ><el-option
            v-for="handler in handlers"
            :key="handler.id"
            :label="handler.username"
            :value="handler.id" /></el-select></el-form-item
      ><el-form-item
        ><el-button type="primary" native-type="submit"
          >查询</el-button
        ></el-form-item
      ></el-form
    >
    <el-alert
      v-if="failed"
      title="工单加载失败，请重新查询"
      type="error"
      :closable="false"
    />
    <el-table
      v-loading="loading"
      :data="rows"
      stripe
      empty-text="暂无符合条件的工单"
      ><el-table-column prop="id" label="ID" width="70" /><el-table-column
        prop="title"
        label="标题"
        min-width="220"
        ><template #default="{ row }"
          ><router-link :to="`/tickets/${row.id}`">{{
            row.title
          }}</router-link></template
        ></el-table-column
      ><el-table-column
        v-if="auth.isAdmin"
        prop="username"
        label="创建用户"
        width="140"
      /><el-table-column label="处理人" width="140"
        ><template #default="{ row }">{{
          row.assigneeUsername || "未分配"
        }}</template></el-table-column
      ><el-table-column label="优先级" width="105"
        ><template #default="{ row }"
          ><el-tag :type="row.priority >= 4 ? 'danger' : 'info'">{{
            priorityLabel(row.priority)
          }}</el-tag></template
        ></el-table-column
      ><el-table-column label="状态" width="110"
        ><template #default="{ row }"
          ><el-tag :type="statusType(row.status)">{{
            statuses[row.status]
          }}</el-tag></template
        ></el-table-column
      ><el-table-column label="创建时间" width="175"
        ><template #default="{ row }">{{
          date(row.createdAt)
        }}</template></el-table-column
      ><el-table-column label="更新时间" width="175"
        ><template #default="{ row }">{{
          date(row.updatedAt)
        }}</template></el-table-column
      ><el-table-column label="操作" width="85" fixed="right"
        ><template #default="{ row }"
          ><el-button
            link
            type="primary"
            @click="$router.push(`/tickets/${row.id}`)"
            >详情</el-button
          ></template
        ></el-table-column
      ></el-table
    >
    <el-pagination
      v-model:current-page="filters.current"
      v-model:page-size="filters.size"
      :page-sizes="[10, 20, 50]"
      :total="total"
      layout="total, sizes, prev, pager, next"
      @current-change="load()"
      @size-change="load(true)"
    />
  </section>
</template>
