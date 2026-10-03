<script setup>
import { ref, computed, onMounted } from "vue";
import { useAuth } from "../stores/auth";
import api from "../api";
import { date, statuses, statusType } from "../utils";
const auth = useAuth(),
  stats = ref(null),
  recent = ref([]),
  loading = ref(true),
  failed = ref(false);
const cards = computed(() =>
  stats.value
    ? [
        ["工单总数", stats.value.total],
        ["待处理", stats.value.open],
        ["处理中", stats.value.inProgress],
        ["已关闭", stats.value.closed],
        ["高优先级", stats.value.highPriority],
        ...(auth.isAdmin
          ? [
              ["用户数量", stats.value.userCount],
              ["知识文章", stats.value.articleCount],
            ]
          : []),
      ]
    : [],
);
const distribution = computed(() =>
  stats.value
    ? [
        ["待处理", stats.value.open, "#d89a29"],
        ["处理中", stats.value.inProgress, "#3867cf"],
        ["已解决", stats.value.resolved, "#47997b"],
        ["已关闭", stats.value.closed, "#9aa8bd"],
      ]
    : [],
);
const gradient = computed(() => {
  let start = 0;
  const total = stats.value?.total || 0;
  if (!total) return "#e8edf5";
  return `conic-gradient(${distribution.value
    .map(([, n, color]) => {
      const end = start + (n / total) * 100;
      const s = `${color} ${start}% ${end}%`;
      start = end;
      return s;
    })
    .join(",")})`;
});
async function load() {
  loading.value = true;
  failed.value = false;
  try {
    const [s, p] = await Promise.all([
      api.get("/tickets/statistics"),
      api.get("/tickets/page", { params: { size: 5 } }),
    ]);
    stats.value = s;
    recent.value = p.records;
  } catch {
    failed.value = true;
  } finally {
    loading.value = false;
  }
}
onMounted(load);
</script>
<template>
  <div v-loading="loading">
    <div class="welcome">
      <div>
        <h2>{{ auth.username }}，欢迎回来</h2>
        <p class="muted">
          {{
            auth.isAdmin
              ? "查看团队服务进度，关注需要优先处理的事项。"
              : "跟进你的工单，查找解决问题所需的知识。"
          }}
        </p>
      </div>
      <el-button type="primary" @click="$router.push('/tickets/create')"
        >创建工单</el-button
      >
    </div>
    <el-alert v-if="failed" title="概览加载失败" type="error" :closable="false"
      ><el-button text @click="load">重新加载</el-button></el-alert
    >
    <div class="stats-grid">
      <article
        v-for="([label, value], index) in cards"
        :key="label"
        class="stat-card"
      >
        <span>{{ label }}</span
        ><strong>{{ value }}</strong>
        <div
          class="stat-accent"
          :style="{ background: ['#3867cf', '#d89a29', '#47997b'][index % 3] }"
        />
      </article>
    </div>
    <div class="dashboard-grid">
      <section class="panel">
        <div class="panel-title">
          <h2>工单状态分布</h2>
          <span class="muted">实时数据库统计</span>
        </div>
        <div v-if="stats" class="chart-area">
          <div
            class="donut"
            :style="{ background: gradient }"
            role="img"
            :aria-label="
              distribution.map(([label, n]) => `${label}${n}条`).join('，')
            "
          >
            <div>
              <strong>{{ stats.total }}</strong
              ><span>全部工单</span>
            </div>
          </div>
          <ul class="legend">
            <li v-for="[label, n, color] in distribution" :key="label">
              <i :style="{ background: color }" />{{ label }}<b>{{ n }}</b>
            </li>
          </ul>
        </div>
      </section>
      <section class="panel">
        <div class="panel-title">
          <h2>最近工单</h2>
          <router-link to="/tickets">查看全部 →</router-link>
        </div>
        <el-table :data="recent" empty-text="暂无工单"
          ><el-table-column label="标题" min-width="180"
            ><template #default="{ row }"
              ><router-link :to="`/tickets/${row.id}`">{{
                row.title
              }}</router-link></template
            ></el-table-column
          ><el-table-column label="状态" width="100"
            ><template #default="{ row }"
              ><el-tag :type="statusType(row.status)">{{
                statuses[row.status]
              }}</el-tag></template
            ></el-table-column
          ><el-table-column label="创建时间" width="170"
            ><template #default="{ row }">{{
              date(row.createdAt)
            }}</template></el-table-column
          ></el-table
        >
      </section>
    </div>
  </div>
</template>
