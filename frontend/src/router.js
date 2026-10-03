import { createRouter, createWebHistory } from "vue-router";
import { useAuth } from "./stores/auth";
const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: "/login",
      component: () => import("./views/LoginView.vue"),
      meta: { public: true, title: "登录" },
    },
    {
      path: "/",
      component: () => import("./Layout.vue"),
      redirect: "/dashboard",
      children: [
        {
          path: "dashboard",
          component: () => import("./views/DashboardView.vue"),
          meta: { title: "工作概览" },
        },
        {
          path: "tickets",
          component: () => import("./views/TicketsView.vue"),
          meta: { title: "工单列表" },
        },
        {
          path: "tickets/create",
          component: () => import("./views/TicketForm.vue"),
          meta: { title: "创建工单" },
        },
        {
          path: "tickets/:id/edit",
          component: () => import("./views/TicketForm.vue"),
          meta: { title: "编辑工单" },
        },
        {
          path: "tickets/:id",
          component: () => import("./views/TicketDetail.vue"),
          meta: { title: "工单详情" },
        },
        {
          path: "knowledge",
          component: () => import("./views/KnowledgeView.vue"),
          meta: { title: "知识库" },
        },
        {
          path: "knowledge/:id",
          component: () => import("./views/ArticleDetail.vue"),
          meta: { title: "知识详情" },
        },
        {
          path: "admin/tickets",
          component: () => import("./views/TicketsView.vue"),
          meta: { admin: true, title: "全部工单管理" },
        },
        {
          path: "admin/assigned",
          component: () => import("./views/TicketsView.vue"),
          meta: { admin: true, title: "分配给我的工单" },
        },
        {
          path: "admin/knowledge",
          component: () => import("./views/KnowledgeView.vue"),
          meta: { admin: true, title: "知识库管理" },
        },
        {
          path: "admin/knowledge/create",
          component: () => import("./views/ArticleForm.vue"),
          meta: { admin: true, title: "新增知识文章" },
        },
        {
          path: "admin/knowledge/:id/edit",
          component: () => import("./views/ArticleForm.vue"),
          meta: { admin: true, title: "编辑知识文章" },
        },
      ],
    },
    { path: "/:pathMatch(.*)*", redirect: "/dashboard" },
  ],
});
router.beforeEach((to) => {
  const auth = useAuth();
  if (!to.meta.public && !auth.token) return "/login";
  if (to.path === "/login" && auth.token) return "/dashboard";
  if (to.meta.admin && !auth.isAdmin) return "/dashboard";
  document.title = `${to.meta.title || "工作台"} · Smart Ticket`;
});
export default router;
