// Real browser + Vite proxy + Spring Boot + PostgreSQL smoke test.
// Run: node scripts/ui_smoke.cjs (npm install --no-save playwright, then npx playwright install chromium).
const path = require("node:path");
const fs = require("node:fs");
const assert = require("node:assert/strict");
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || "playwright");
const root = path.resolve(__dirname, "..");
const base = process.env.SMART_TICKET_UI || "http://127.0.0.1:5173";
const apiBase = process.env.SMART_TICKET_API || "http://localhost:8080";
const errors = [],
  checks = [];
const unique = `浏览器联调_${Date.now()}`;
let ticketId, articleId, adminToken;

async function run() {
  const browser = await chromium.launch({ headless: true });
  const userContext = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
  });
  const adminContext = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
  });
  const user = await userContext.newPage(),
    admin = await adminContext.newPage();
  for (const page of [user, admin])
    page.on("pageerror", (e) => errors.push(e.message));
  const mark = (message) => {
    checks.push(message);
    console.log("PASS:", message);
  };
  const visible = async (page, text) =>
    page.getByText(text, { exact: true }).first().waitFor({ state: "visible" });
  async function screenshot(page, name) {
    await page.waitForFunction(
      () => !document.querySelector('[class*="enter-active"]'),
    );
    await page.screenshot({
      path: path.join(root, "docs/screenshots", name),
      fullPage: true,
    });
  }
  async function login(page, name) {
    await page.goto(base + "/login");
    await page.getByPlaceholder("请输入用户名").fill(name);
    await page.getByPlaceholder("请输入密码").fill("Demo@12345");
    await page.getByRole("button", { name: "登录", exact: true }).click();
    await page.waitForURL("**/dashboard");
    await page.locator(".stat-card").first().waitFor();
  }
  async function api(method, url, data) {
    const response = await admin.request.fetch(apiBase + url, {
      method,
      headers: { Authorization: "Bearer " + adminToken },
      data,
    });
    assert.equal(response.status(), 200, await response.text());
    return (await response.json()).data;
  }
  try {
    await user.goto(base + "/tickets");
    await user.waitForURL("**/login");
    mark("未登录访问业务页跳转登录");
    fs.mkdirSync(path.join(root, "docs/screenshots"), { recursive: true });
    await screenshot(user, "login.png");
    await login(user, "dev_zhangsan");
    mark("USER登录并显示数据库Dashboard");
    await user.reload();
    await user.locator(".stat-card").first().waitFor();
    mark("刷新后恢复登录状态");
    await user.goto(base + "/admin/tickets");
    await user.waitForURL("**/dashboard");
    mark("USER管理员路由被拦截");
    await user.goto(base + "/tickets/create");
    await user
      .getByPlaceholder("例如：VPN提示认证失败，无法连接公司网络")
      .fill("VPN认证失败 " + unique);
    await user
      .getByPlaceholder("请填写具体故障现象与已尝试的操作，勿填写密码和验证码")
      .fill("VPN认证失败，无法连接公司网络，请核对账号授权。");
    await user.getByRole("button", { name: "提交工单", exact: true }).click();
    await user.waitForURL(/\/tickets\/\d+$/);
    ticketId = Number(user.url().split("/").pop());
    await visible(user, "可能对你有帮助");
    await user.locator(".recommendation").first().waitFor();
    assert.equal(
      await user.getByRole("button", { name: "开始处理", exact: true }).count(),
      0,
    );
    mark("浏览器创建工单、显示历史和知识推荐，USER无状态按钮");
    await user
      .getByPlaceholder("补充信息或记录处理结果")
      .fill("用户已提供VPN故障发生时间。");
    await user.getByRole("button", { name: "发送留言", exact: true }).click();
    await visible(user, "用户已提供VPN故障发生时间。");
    mark("USER留言真实提交");
    await screenshot(user, "ticket-detail.png");
    await login(admin, "dev_admin1");
    adminToken = await admin.evaluate(
      () => JSON.parse(localStorage.getItem("smartticket-session")).token,
    );
    await screenshot(admin, "dashboard.png");
    await admin.goto(base + "/admin/system");
    await visible(admin,"服务与消息监控");
    await screenshot(admin,"system.png");
    mark("ADMIN运维页面读取真实依赖及事件状态");
    await admin.goto(base + "/admin/users");
    await admin.locator(".el-table__row").first().waitFor();
    await screenshot(admin,"users.png");
    mark("ADMIN用户与权限页面展示真实账号");
    await admin.goto(base + "/admin/audit");
    await admin.locator(".el-table__row").first().waitFor();
    mark("ADMIN审计页面展示数据库记录");
    await user.goto(base + "/assistant");
    await visible(user,"知识问答助手");
    await screenshot(user,"assistant.png");
    mark("知识问答页面根据真实服务能力展示入口");
    await user.goto(base + "/account");
    await visible(user,"账号安全");
    mark("USER账号安全页面可访问");
    await user.goto(base + `/tickets/${ticketId}`);
    await admin.goto(base + `/tickets/${ticketId}`);
    await admin.locator(".el-form-item").filter({ hasText: "指派处理人" }).locator(".el-select__wrapper").click();
    await admin.getByRole("option", { name: "dev_admin1", exact: true }).click();
    await admin.getByRole("button", { name: "保存指派", exact: true }).click();
    await visible(admin, "指派：未分配 → dev_admin1");
    await admin.goto(base + "/admin/assigned");
    await admin.locator(".el-table__row").filter({ hasText: unique }).waitFor();
    mark("ADMIN指派给自己，分配给我的工单队列显示真实记录");
    await user.reload();
    await visible(user, "指派：未分配 → dev_admin1");
    assert.equal(await user.getByRole("button", { name: "保存指派", exact: true }).count(),0);
    mark("USER可查看处理人和指派历史，不能修改指派");
    await admin.goto(base + `/tickets/${ticketId}`);
    await admin.getByRole("button", { name: "开始处理", exact: true }).click();
    await visible(admin, "待处理 → 处理中");
    await admin
      .getByPlaceholder("补充信息或记录处理结果")
      .fill("管理员已同步权限，请复测。");
    await admin.getByRole("button", { name: "发送留言", exact: true }).click();
    await visible(admin, "管理员已同步权限，请复测。");
    await admin.getByRole("button", { name: "关闭工单", exact: true }).click();
    await visible(admin, "处理中 → 已关闭");
    mark("ADMIN页面处理OPEN→IN_PROGRESS→CLOSED，历史和评论同步");
    await user.reload();
    await visible(user, "处理中 → 已关闭");
    mark("USER刷新看到管理员处理结果");
    await admin.goto(base + "/admin/tickets");
    await admin.getByPlaceholder("搜索标题或描述").fill(unique);
    await admin.getByRole("button", { name: "查询", exact: true }).click();
    await admin.locator(".el-table__row").filter({ hasText: unique }).waitFor();
    mark("工单分页与标题查询");
    await admin.goto(base + "/admin/knowledge/create");
    await admin.getByLabel("标题", { exact: true }).fill(unique + "知识文章");
    await admin
      .getByLabel("摘要", { exact: true })
      .fill("浏览器验证的VPN排查文章摘要");
    await admin.getByPlaceholder("如：网络支持、账号权限").fill("网络支持");
    await admin
      .getByPlaceholder("用逗号分隔，如：VPN,认证,账号")
      .fill("VPN,认证");
    await admin
      .getByPlaceholder("问题现象、可能原因、排查步骤、解决方法、注意事项")
      .fill(
        "问题现象\nVPN认证失败\n排查步骤\n核对账号和客户端版本，管理员核对授权。",
      );
    await admin.getByRole("button", { name: "保存文章", exact: true }).click();
    await admin.waitForURL(/\/knowledge\/\d+$/);
    articleId = Number(admin.url().split("/").pop());
    await visible(admin, unique + "知识文章");
    mark("ADMIN创建真实数据库知识草稿");
    await user.goto(base + `/knowledge/${articleId}`);
    await visible(user, "文章不存在或尚未发布");
    mark("USER无法读取未发布草稿");
    await admin.getByRole("button", { name: "编辑文章", exact: true }).click();
    await admin.locator("label.el-radio").filter({ hasText: "发布" }).click();
    await admin.getByRole("button", { name: "保存文章", exact: true }).click();
    await admin.waitForURL(/\/knowledge\/\d+$/);
    await user.reload();
    await visible(user, unique + "知识文章");
    mark("ADMIN发布后USER可阅读知识正文");
    await user.goto(base + "/knowledge");
    await user.locator(".knowledge-card").first().waitFor();
    await screenshot(user, "knowledge.png");
    await user.setViewportSize({ width: 768, height: 1024 });
    await screenshot(user, "tablet.png");
    assert.ok(
      await user.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
      "页面出现横向溢出",
    );
    mark("基础响应式768px页面无横向溢出");
    const another = await api("POST", "/tickets", {
      title: unique + "其他用户工单",
      priority: 2,
    });
    try {
      await user.goto(base + `/tickets/${another.id}`);
      await visible(user, "无权访问该工单。");
      mark("浏览器呈现后端403提示");
    } finally {
      await api("DELETE", `/tickets/${another.id}`);
    }
    await user.setViewportSize({ width: 1440, height: 1000 });
    await user.getByRole("button", { name: "退出登录", exact: true }).click();
    await user.waitForURL("**/login");
    mark("退出清除状态并回到登录页");
    assert.deepEqual(errors, []);
    fs.writeFileSync(
      path.join(root, "docs/ui-verification.json"),
      JSON.stringify(
        { result: "PASS", checks, consoleErrors: errors },
        null,
        2,
      ) + "\n",
    );
    console.log(`PASS: ${checks.length} browser flows, no page errors.`);
  } finally {
    if (adminToken) {
      if (ticketId) await api("DELETE", `/tickets/${ticketId}`);
      if (articleId) await api("DELETE", `/knowledge/${articleId}`);
    }
    await browser.close();
  }
}
run().catch((e) => {
  console.error(e);
  process.exitCode = 1;
});
