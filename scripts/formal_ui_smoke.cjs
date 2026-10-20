// Browser workflow checks. Certificate chain/hostname validation is performed by formal_smoke.py.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const root = path.resolve(__dirname, '..');
const privateConfig = Object.fromEntries(fs.readFileSync(path.join(root, '.runtime/formal.env'), 'utf8')
  .replace(/^\uFEFF/, '').split(/\r?\n/).filter(line => line.includes('=')).map(line => {
    const split = line.indexOf('='); return [line.slice(0, split), line.slice(split + 1)];
  }));
const base = 'https://localhost:8443';
const checks = [], pageErrors = [];
let userId, ticketId, token;

(async () => {
  const browser = await chromium.launch({ headless: true });
  // Local CA is deliberately not installed in the system/browser trust store.
  const adminContext = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1440, height: 1000 } });
  const userContext = await browser.newContext({ ignoreHTTPSErrors: true, viewport: { width: 1440, height: 1000 } });
  const admin = await adminContext.newPage(), user = await userContext.newPage();
  for (const page of [admin, user]) page.on('pageerror', error => pageErrors.push(error.message));
  const mark = name => { checks.push(name); console.log('PASS: ' + name); };
  const login = async (page, username, password) => {
    await page.goto(base + '/login');
    await page.getByPlaceholder('请输入用户名').fill(username);
    await page.getByPlaceholder('请输入密码').fill(password);
    await page.getByRole('button', { name: '登录', exact: true }).click();
    await page.waitForURL('**/dashboard');
    await page.locator('.stat-card').first().waitFor();
  };
  const api = async (method, url, data) => {
    const response = await admin.request.fetch(base + '/api' + url, {
      method, data, headers: { Authorization: 'Bearer ' + token }
    });
    assert.equal(response.status(), 200, `Unexpected response: ${method} ${url}`);
    return (await response.json()).data;
  };
  try {
    await user.goto(base + '/tickets');
    await user.waitForURL('**/login');
    assert.equal(await user.getByRole('link', { name: /注册/ }).count(), 0);
    mark('Unauthenticated route redirects to login; formal self-registration is hidden');
    await login(admin, privateConfig.ADMIN_USERNAME, privateConfig.ADMIN_PASSWORD);
    token = await admin.evaluate(() => JSON.parse(localStorage.getItem('smartticket-session')).token);
    mark('Private administrator logs in and sees the real dashboard');
    for (const [url, text] of [['/admin/system', '服务与消息监控'], ['/admin/users', '用户与权限']]) {
      await admin.goto(base + url);
      await admin.getByText(text, { exact: true }).first().waitFor();
    }
    mark('Administrator opens service monitoring and account management');
    const username = 'browsercheck_' + crypto.randomBytes(5).toString('hex');
    const password = crypto.randomBytes(18).toString('base64url');
    await api('POST', '/admin/users', { username, password });
    userId = (await api('GET', '/admin/users?keyword=' + username)).records[0].id;
    await login(user, username, password);
    await user.reload();
    await user.locator('.stat-card').first().waitFor();
    mark('Normal user logs in and retains the session after a reload');
    await user.goto(base + '/admin/users');
    await user.waitForURL('**/dashboard');
    mark('Normal user is blocked by the administrator route guard');
    await user.goto(base + '/tickets/create');
    await user.getByPlaceholder('例如：VPN提示认证失败，无法连接公司网络').fill('HTTPS浏览器验收工单');
    await user.getByPlaceholder('请填写具体故障现象与已尝试的操作，勿填写密码和验证码').fill('由正式环境浏览器验收生成，将在验收后清理。');
    await user.getByRole('button', { name: '提交工单', exact: true }).click();
    await user.waitForURL(/\/tickets\/\d+$/);
    ticketId = Number(user.url().split('/').pop());
    mark('Normal user creates a ticket through the HTTPS page');
    await user.getByPlaceholder('补充信息或记录处理结果').fill('浏览器提交的补充说明');
    await user.getByRole('button', { name: '发送留言', exact: true }).click();
    await user.getByText('浏览器提交的补充说明', { exact: true }).first().waitFor();
    await admin.goto(base + '/tickets/' + ticketId);
    await admin.getByRole('button', { name: '开始处理', exact: true }).click();
    await admin.getByRole('button', { name: '关闭工单', exact: true }).click();
    await user.reload();
    await user.getByText('已关闭', { exact: true }).first().waitFor();
    mark('Browser comments and administrator ticket lifecycle are reflected to the user');
    await user.setViewportSize({ width: 768, height: 1024 });
    await user.goto(base + '/dashboard');
    await user.locator('.stat-card').first().waitFor();
    assert.equal(await user.evaluate(() => document.documentElement.scrollWidth > innerWidth + 2), false);
    mark('768px dashboard has no horizontal overflow');
    await user.getByRole('button', { name: '退出登录', exact: true }).click();
    await user.waitForURL('**/login');
    mark('Logout clears the browser session');
    assert.deepEqual(pageErrors, []);
    mark('No unhandled browser page errors');
  } finally {
    if (token) {
      if (ticketId) await api('DELETE', '/tickets/' + ticketId);
      if (userId) await api('PATCH', '/admin/users/' + userId, { role: 'USER', enabled: false });
    }
    await browser.close();
  }
  fs.writeFileSync(path.join(root, 'docs/formal-ui-verification.json'), JSON.stringify({
    result: 'PASS', checks, pageErrors, localCertificateBypassForUiOnly: true,
    tlsVerification: 'Separate formal_smoke.py requires CA-chain and hostname validation'
  }, null, 2) + '\n');
})().catch(error => { console.error(error); process.exitCode = 1; });
