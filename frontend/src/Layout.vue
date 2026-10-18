<script setup>
import { useAuth } from "./stores/auth";
import { useRoute, useRouter } from "vue-router";
import api from "./api";
const auth = useAuth(),
  route = useRoute(),
  router = useRouter();
async function logout() {
  try {
    await api.post("/users/logout");
    auth.clear();
    router.replace("/login");
  } catch {
    /* Keep session if revocation failed; API displays the error. */
  }
}
</script>
<template>
  <div class="shell">
    <aside class="sidebar">
      <router-link class="brand" to="/dashboard"
        ><span class="brand-mark">ST</span
        ><span>Smart Ticket<small>企业服务工作台</small></span></router-link
      >
      <nav aria-label="主导航">
        <p class="nav-label">工作空间</p>
        <router-link to="/dashboard">工作概览</router-link>
        <router-link to="/tickets">{{
          auth.isAdmin ? "工单列表" : "我的工单"
        }}</router-link>
        <router-link to="/tickets/create">创建工单</router-link>
        <p class="nav-label">知识中心</p>
        <router-link to="/knowledge">知识库</router-link>
        <router-link to="/assistant">知识问答</router-link>
        <router-link to="/account">账号安全</router-link>
        <template v-if="auth.isAdmin"
          ><p class="nav-label">管理中心</p>
          <router-link to="/admin/tickets">全部工单</router-link
          ><router-link to="/admin/assigned">分配给我的工单</router-link>
          <router-link to="/admin/users">用户与权限</router-link>
          <router-link to="/admin/system">运行监控</router-link>
          <router-link to="/admin/audit">操作审计</router-link>
          <router-link to="/admin/knowledge">知识库管理</router-link></template
        >
      </nav>
      <div class="sidebar-foot">
        IT SUPPORT PLATFORM<br /><small>让问题处理有迹可循</small>
      </div>
    </aside>
    <section class="workspace">
      <header class="topbar">
        <span>企业智能工单与知识协同平台</span>
        <div class="user-menu">
          <span>{{ auth.username }}</span
          ><el-tag size="small">{{
            auth.isAdmin ? "管理员" : "普通用户"
          }}</el-tag
          ><el-button text @click="logout">退出登录</el-button>
        </div>
      </header>
      <main>
        <div class="page-heading">
          <span class="eyebrow">SERVICE WORKSPACE</span>
          <h1>{{ route.meta.title }}</h1>
        </div>
        <router-view :key="route.fullPath" />
      </main>
    </section>
  </div>
</template>
