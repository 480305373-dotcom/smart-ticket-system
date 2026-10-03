<script setup>
import { reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { useAuth } from "../stores/auth";
import api from "../api";
const form = reactive({ username: "", password: "" }),
  busy = ref(false),
  error = ref(""),
  formRef = ref();
const router = useRouter(),
  auth = useAuth();
const rules = {
  username: [{ required: true, message: "请输入用户名", trigger: "blur" }],
  password: [{ required: true, message: "请输入密码", trigger: "blur" }],
};
async function submit() {
  if (!(await formRef.value.validate().catch(() => false))) return;
  busy.value = true;
  error.value = "";
  try {
    auth.set(await api.post("/users/login", form));
    router.replace("/dashboard");
  } catch (e) {
    error.value = e.userMessage || e.message;
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <div class="login-page">
    <section class="login-intro">
      <div class="brand"><span class="brand-mark">ST</span>Smart Ticket</div>
      <span class="eyebrow">ENTERPRISE IT SUPPORT</span>
      <h1>连接问题与解决方案。</h1>
      <p>从需求提交到处理完成，统一管理企业 IT 工单、沟通记录与团队知识。</p>
      <div class="login-flow">
        <span>提交工单</span><span>协同处理</span><span>沉淀知识</span>
      </div>
    </section>
    <section class="login-card">
      <span class="eyebrow">WELCOME BACK</span>
      <h2>登录工作台</h2>
      <p class="muted">使用企业账号进入服务空间</p>
      <el-alert
        v-if="error"
        :title="error"
        type="error"
        :closable="false"
        show-icon
      />
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="submit"
      >
        <el-form-item label="用户名" prop="username"
          ><el-input
            v-model="form.username"
            autocomplete="username"
            placeholder="请输入用户名"
            size="large"
        /></el-form-item>
        <el-form-item label="密码" prop="password"
          ><el-input
            v-model="form.password"
            type="password"
            show-password
            autocomplete="current-password"
            placeholder="请输入密码"
            size="large"
        /></el-form-item>
        <el-button
          type="primary"
          native-type="submit"
          size="large"
          :loading="busy"
          class="full-width"
          >登录</el-button
        >
      </el-form>
      <p class="login-note">开发环境演示账号见项目 README</p>
    </section>
  </div>
</template>
