// Disposable browser account verifies creation, password/session invalidation and disabling.
const {chromium}=require(process.env.PLAYWRIGHT_MODULE||"playwright");
const {execFileSync}=require("node:child_process");
const fs=require("node:fs"),path=require("node:path"),assert=require("node:assert/strict");
const base=process.env.SMART_TICKET_UI||"http://127.0.0.1:5173",apiBase=process.env.SMART_TICKET_API||"http://127.0.0.1:8080";
const username=`ui_account_${Date.now()}`,checks=[],errors=[];
let accountId;
async function run(){
  const browser=await chromium.launch({headless:true});
  const ac=await browser.newContext({viewport:{width:1440,height:1000}}),uc=await browser.newContext();
  const admin=await ac.newPage(),user=await uc.newPage();
  for(const p of [admin,user])p.on("pageerror",e=>errors.push(e.message));
  async function login(p,name,password){await p.goto(base+"/login");await p.getByPlaceholder("请输入用户名").fill(name);await p.getByPlaceholder("请输入密码").fill(password);await p.getByRole("button",{name:"登录",exact:true}).click();await p.waitForURL("**/dashboard");await p.locator(".stat-card").first().waitFor();}
  const mark=s=>{checks.push(s);console.log("PASS:",s);};
  try{
    await login(admin,"dev_admin1","Demo@12345");
    const adminToken=await admin.evaluate(()=>JSON.parse(localStorage.getItem("smartticket-session")).token);
    await admin.goto(base+"/admin/users");await admin.getByRole("button",{name:"创建账号",exact:true}).click();
    const dialog=admin.getByRole("dialog");await dialog.getByLabel("用户名",{exact:true}).fill(username);await dialog.getByLabel("初始密码",{exact:true}).fill("Temp@123456");await dialog.getByRole("button",{name:"创建",exact:true}).click();
    await admin.locator(".el-table__row").filter({hasText:username}).waitFor();
    const response=await admin.request.get(apiBase+"/admin/users?keyword="+username,{headers:{Authorization:"Bearer "+adminToken}});
    accountId=(await response.json()).data.records.find(r=>r.username===username).id;mark("管理员通过页面创建普通账号");
    await login(user,username,"Temp@123456");
    const oldToken=await user.evaluate(()=>JSON.parse(localStorage.getItem("smartticket-session")).token);
    await user.goto(base+"/account");await user.getByLabel("原密码",{exact:true}).fill("Temp@123456");await user.getByLabel("新密码",{exact:true}).fill("Changed@123456");await user.getByLabel("确认新密码",{exact:true}).fill("Changed@123456");await user.getByRole("button",{name:"更新密码",exact:true}).click();await user.waitForURL("**/login");
    const invalid=await user.request.get(apiBase+"/tickets/page",{headers:{Authorization:"Bearer "+oldToken}});assert.equal(invalid.status(),401);mark("页面改密成功，全部旧 Token 立即返回401");
    await login(user,username,"Changed@123456");const newToken=await user.evaluate(()=>JSON.parse(localStorage.getItem("smartticket-session")).token);mark("新密码可以重新登录");
    const row=admin.locator(".el-table__row").filter({hasText:username});await row.locator(".el-switch").click();await row.getByRole("button",{name:"保存",exact:true}).click();await admin.locator(".el-message-box__btns button.el-button--primary").click();await admin.getByText("账号已更新",{exact:true}).waitFor();
    const disabled=await user.request.get(apiBase+"/tickets/page",{headers:{Authorization:"Bearer "+newToken}});assert.equal(disabled.status(),401);mark("页面停用账号后，现有会话立即失效");
    await admin.goto(base+"/assistant");await admin.getByLabel("知识问题").fill("VPN认证失败怎么排查？");await admin.getByRole("button",{name:"检索并回答",exact:true}).click();await admin.getByText("参考知识",{exact:true}).waitFor({timeout:180000});await admin.screenshot({path:path.resolve(__dirname,"../docs/screenshots/assistant-answer.png"),fullPage:true});mark("浏览器真实 AI 回答与来源链接可见");
    assert.deepEqual(errors,[]);fs.writeFileSync(path.resolve(__dirname,"../docs/account-ui-verification.json"),JSON.stringify({result:"PASS",checks,consoleErrors:errors},null,2)+"\n");
  }finally{
    await browser.close();
    if(accountId){assert.ok(Number.isSafeInteger(accountId));assert.match(username,/^ui_account_\d+$/);const sql=`BEGIN; DELETE FROM operation_log WHERE user_id=${accountId} OR (target_type='USER' AND target_id=${accountId}); DELETE FROM app_user WHERE id=${accountId} AND username='${username}'; COMMIT;`;execFileSync("docker",["exec",process.env.TEST_DB_CONTAINER||"smartticket-postgres","psql","-U","smartticket","-d","intelligent_ticket","-v","ON_ERROR_STOP=1","-c",sql],{stdio:"pipe"});}
  }
}
run().catch(e=>{console.error(e);process.exitCode=1;});
