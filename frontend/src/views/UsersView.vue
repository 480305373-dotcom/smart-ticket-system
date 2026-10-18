<script setup>
import { onMounted, ref, reactive } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import api from "../api";
import { useAuth } from "../stores/auth";
const auth = useAuth(),
  rows = ref([]),
  total = ref(0),
  page = ref(1),
  keyword = ref(""),
  busy = ref(false),
  failed = ref(false);
const creating = ref(false),
  newAccount = reactive({ username: "", password: "" });
async function create() {
  if (!newAccount.username.trim() || newAccount.password.length < 8)
    return ElMessage.warning("请填写用户名，密码至少8位");
  busy.value = true;
  try {
    await api.post("/admin/users", {
      username: newAccount.username.trim(),
      password: newAccount.password,
    });
    creating.value = false;
    newAccount.username = "";
    newAccount.password = "";
    ElMessage.success("普通账号已创建，可在列表中调整角色");
    await load(true);
  } catch {
  } finally {
    busy.value = false;
  }
}
async function load(reset = false) {
  if (reset) page.value = 1;
  busy.value = true;
  failed.value = false;
  try {
    const r = await api.get("/admin/users", {
      params: { current: page.value, size: 10, keyword: keyword.value },
    });
    rows.value = r.records;
    total.value = r.total;
  } catch {
    failed.value = true;
  } finally {
    busy.value = false;
  }
}
async function save(row) {
  try {
    await ElMessageBox.confirm(
      `确认修改 ${row.username} 的账号状态？旧会话会立即失效。`,
      "修改账号",
    );
    await api.patch(`/admin/users/${row.id}`, {
      role: row.role,
      enabled: row.enabled,
    });
    ElMessage.success("账号已更新");
    await load();
  } catch {}
}
onMounted(() => load());
</script>
<template>
  <section class="panel">
    <div class="panel-title">
      <h2>用户与权限</h2>
      <el-button type="primary" @click="creating = true">创建账号</el-button>
    </div>
    <el-dialog
      v-model="creating"
      title="创建普通账号"
      width="450px"
      @closed="newAccount.password = ''"
    >
      <el-form label-position="top" @submit.prevent="create"
        ><el-form-item label="用户名"
          ><el-input
            v-model="newAccount.username"
            maxlength="50"
            autocomplete="off" /></el-form-item
        ><el-form-item label="初始密码"
          ><el-input
            v-model="newAccount.password"
            type="password"
            show-password
            maxlength="72"
            autocomplete="new-password" /></el-form-item
        ><el-button type="primary" native-type="submit" :loading="busy"
          >创建</el-button
        ></el-form
      >
    </el-dialog>
    <p class="muted">
      角色和启用状态修改会撤销旧会话，不能修改自己的账号状态。
    </p>
    <el-form inline @submit.prevent="load(true)"
      ><el-form-item
        ><el-input
          v-model="keyword"
          placeholder="搜索用户名"
          clearable /></el-form-item
      ><el-form-item
        ><el-button native-type="submit" type="primary"
          >查询</el-button
        ></el-form-item
      ></el-form
    ><el-alert
      v-if="failed"
      title="用户加载失败，请重试"
      type="error"
      :closable="false"
    />
    <el-table v-loading="busy" :data="rows" empty-text="暂无匹配用户"
      ><el-table-column prop="id" label="ID" width="80" /><el-table-column
        prop="username"
        label="用户名"
        min-width="180"
      /><el-table-column label="角色" min-width="150"
        ><template #default="{ row }"
          ><el-select v-model="row.role" :disabled="row.id === auth.userId"
            ><el-option label="普通用户" value="USER" /><el-option
              label="管理员"
              value="ADMIN" /></el-select></template></el-table-column
      ><el-table-column label="启用状态" width="140"
        ><template #default="{ row }"
          ><el-switch
            v-model="row.enabled"
            :disabled="row.id === auth.userId" /></template></el-table-column
      ><el-table-column label="操作" width="100"
        ><template #default="{ row }"
          ><el-button
            link
            type="primary"
            :disabled="row.id === auth.userId"
            @click="save(row)"
            >保存</el-button
          ></template
        ></el-table-column
      ></el-table
    >
    <el-pagination
      v-model:current-page="page"
      :page-size="10"
      :total="total"
      layout="total,prev,pager,next"
      @current-change="load()"
    />
  </section>
</template>
