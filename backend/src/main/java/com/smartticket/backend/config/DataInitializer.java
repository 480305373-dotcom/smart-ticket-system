package com.smartticket.backend.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartticket.backend.dto.ArticleRequest;
import com.smartticket.backend.entity.*;
import com.smartticket.backend.enums.TicketStatus;
import com.smartticket.backend.mapper.*;
import com.smartticket.backend.service.*;
import java.time.LocalDateTime;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
public class DataInitializer implements CommandLineRunner {
  private final UserMapper users;
  private final TicketMapper tickets;
  private final TicketService service;
  private final ArticleService articles;
  private final TicketActivityService activity;
  private final PasswordEncoder encoder;
  private final JdbcTemplate jdbc;

  public DataInitializer(
      UserMapper u,
      TicketMapper t,
      TicketService s,
      ArticleService a,
      TicketActivityService activity,
      PasswordEncoder e,
      JdbcTemplate j) {
    users = u;
    tickets = t;
    service = s;
    articles = a;
    this.activity = activity;
    encoder = e;
    jdbc = j;
  }

  private User user(String name, String role) {
    var old = users.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, name));
    if (old != null) return old; // Preserve existing account passwords and roles.
    var u = new User();
    u.setUsername(name);
    u.setRole(role);
    u.setPasswordHash(encoder.encode("Demo@12345"));
    users.insert(u);
    return u;
  }

  @Override
  @Transactional
  public void run(String... args) {
    jdbc.execute("SELECT pg_advisory_xact_lock(741852963)");
    // Upgrade preexisting unencoded credentials without resetting their password value.
    // Existing BCrypt/other encoded credentials are never rewritten.
    for (User existing : users.selectList(null)) {
      String stored = existing.getPasswordHash();
      if (stored != null && !stored.startsWith("$")) {
        existing.setPasswordHash(encoder.encode(stored));
        users.updateById(existing);
      }
    }
    if (jdbc.queryForObject(
            "SELECT count(*) FROM demo_seed WHERE seed_key='enterprise-demo-v1'", Long.class)
        > 0) return;
    user("zhangsan", "USER");
    user("lisi", "USER");
    user("wangwu", "USER");
    user("admin1", "ADMIN");
    User[] people = {
      user("dev_zhangsan", "USER"), user("dev_lisi", "USER"), user("dev_wangwu", "USER")
    };
    User admin = user("dev_admin1", "ADMIN");
    ArticleRequest[] knowledge = {
      new ArticleRequest(
          "VPN认证失败排查指南",
          "客户端提示认证失败时，先检查账号、密码及多因素认证状态。",
          "问题现象\n"
              + "客户端提示认证失败时，先检查账号、密码及多因素认证状态。\n\n"
              + "可能原因\n"
              + "账号或配置变化、客户端版本差异、服务故障均可能产生此类问题，需要结合发生时间与影响范围判断。\n\n"
              + "排查步骤\n"
              + "1. 核对账号是否使用公司目录中的完整用户名；确认密码未过期、键盘大小写正确。\n"
              + "2. 查看手机认证时间是否同步，重新发起一次多因素认证；连续失败后停止重试，避免锁定账号。\n\n"
              + "解决方法与验证\n"
              + "提交错误截图、客户端版本、发生时间及账号标识给 IT。管理员核对目录同步和 VPN 授权组，恢复后请用户复测。\n\n"
              + "注意事项\n"
              + "不得在工单中填写密码、一次性验证码或恢复码。",
          "远程办公",
          "VPN,认证,账号",
          "PUBLISHED"),
      new ArticleRequest(
          "公司VPN客户端安装与配置",
          "使用公司批准的客户端和网关配置接入内网。",
          "问题现象\n"
              + "使用公司批准的客户端和网关配置接入内网。\n\n"
              + "可能原因\n"
              + "账号或配置变化、客户端版本差异、服务故障均可能产生此类问题，需要结合发生时间与影响范围判断。\n\n"
              + "排查步骤\n"
              + "1. 从公司软件分发目录下载签名安装包，核对版本和系统架构。\n"
              + "2. 使用员工门户提供的网关地址，保留默认加密选项；完成多因素认证后连接。\n\n"
              + "解决方法与验证\n"
              + "连接后测试员工门户与指定内网服务，记录无法访问的地址；仅单个系统失败时检查该系统授权。\n\n"
              + "注意事项\n"
              + "不要下载来源不明的安装包，不要关闭证书校验。",
          "远程办公",
          "VPN,客户端,连接",
          "PUBLISHED"),
      new ArticleRequest(
          "企业邮箱密码重置方法",
          "适用于密码过期、忘记密码及客户端持续要求登录。",
          "问题现象\n"
              + "适用于密码过期、忘记密码及客户端持续要求登录。\n\n"
              + "可能原因\n"
              + "账号或配置变化、客户端版本差异、服务故障均可能产生此类问题，需要结合发生时间与影响范围判断。\n\n"
              + "排查步骤\n"
              + "1. 确认网页版邮箱是否可以登录；检查账号状态和门户中的密码过期提醒。\n"
              + "2. 通过员工门户自助重置功能完成身份核验；无法自助处理时提交密码重置工单。\n\n"
              + "解决方法与验证\n"
              + "重置后重新登录网页版，并更新受管邮件客户端中的凭据，发送测试邮件确认恢复。\n\n"
              + "注意事项\n"
              + "IT 不会索取旧密码；疑似账号泄露时立即报告并撤销已有会话。",
          "邮箱服务",
          "邮箱,密码,邮件",
          "PUBLISHED"),
      new ArticleRequest(
          "OA系统登录异常处理",
          "OA 登录循环跳转或提示无权限时的基础排查。",
          "问题现象\n"
              + "OA 登录循环跳转或提示无权限时的基础排查。\n\n"
              + "可能原因\n"
              + "账号或配置变化、客户端版本差异、服务故障均可能产生此类问题，需要结合发生时间与影响范围判断。\n\n"
              + "排查步骤\n"
              + "1. 记录发生时间和提示信息，确认企业网络与 VPN 正常。\n"
              + "2. 尝试受支持浏览器的隐私窗口，确认系统时间正确；不要删除其他业务系统的资料。\n\n"
              + "解决方法与验证\n"
              + "若只在某浏览器失败，清理 OA 站点缓存；持续异常由管理员检查单点登录日志与账号授权。\n\n"
              + "注意事项\n"
              + "提交截图时隐藏身份证号、客户信息和会话标识。",
          "业务系统",
          "OA,登录,认证",
          "PUBLISHED"),
      new ArticleRequest(
          "Windows网络重置操作指南",
          "无线网络断线或获取不到地址时，先检查连接再执行受控重置。",
          "问题现象\n"
              + "无线网络断线或获取不到地址时，先检查连接再执行受控重置。\n\n"
              + "可能原因\n"
              + "账号或配置变化、客户端版本差异、服务故障均可能产生此类问题，需要结合发生时间与影响范围判断。\n\n"
              + "排查步骤\n"
              + "1. 确认连接公司授权的无线网络，记录所在楼层及问题是否影响同事。\n"
              + "2. 断开并重新连接；查看网络适配器是否被禁用，检查受管设备是否仍在合规状态。\n\n"
              + "解决方法与验证\n"
              + "经 IT 确认后通过系统设置执行网络重置并重启；重启后重新配置受管 VPN，检查内网访问。\n\n"
              + "注意事项\n"
              + "不要自行修改安全代理、静态地址或安装第三方网络优化工具。",
          "网络支持",
          "Wi-Fi,无线,网络",
          "PUBLISHED"),
      new ArticleRequest(
          "打印机无法连接排查方法",
          "打印任务长时间排队、离线或无法找到共享打印机时的处理。",
          "问题现象\n"
              + "打印任务长时间排队、离线或无法找到共享打印机时的处理。\n\n"
              + "可能原因\n"
              + "账号或配置变化、客户端版本差异、服务故障均可能产生此类问题，需要结合发生时间与影响范围判断。\n\n"
              + "排查步骤\n"
              + "1. 检查打印机电源、面板告警与纸盒，确认设备连接企业网络。\n"
              + "2. 核对公司打印服务中的设备名称，尝试打印测试页，记录队列中的错误提示。\n\n"
              + "解决方法与验证\n"
              + "由 IT 清理故障任务，重新安装批准的驱动；多名用户同时异常时检查打印服务器。\n\n"
              + "注意事项\n"
              + "不要重复提交大批量任务；含敏感信息的文档必须使用安全打印。",
          "办公设备",
          "打印机,打印,驱动",
          "PUBLISHED"),
      new ArticleRequest(
          "GitLab访问权限申请流程",
          "申请项目仓库访问时，应明确项目、权限等级及有效期。",
          "问题现象\n"
              + "申请项目仓库访问时，应明确项目、权限等级及有效期。\n\n"
              + "可能原因\n"
              + "账号或配置变化、客户端版本差异、服务故障均可能产生此类问题，需要结合发生时间与影响范围判断。\n\n"
              + "排查步骤\n"
              + "1. 通过工单说明项目路径、工作用途、所需角色和截止日期。\n"
              + "2. 获得项目负责人批准，由管理员核对账号状态和最小权限范围。\n\n"
              + "解决方法与验证\n"
              + "管理员加入项目成员后，请申请人验证克隆与推送；临时权限到期及时移除。\n\n"
              + "注意事项\n"
              + "禁止共用账号或发送访问令牌，生产仓库写权限需要额外审批。",
          "研发工具",
          "GitLab,权限,仓库",
          "PUBLISHED"),
      new ArticleRequest(
          "PostgreSQL连接异常排查",
          "应用提示连接超时、连接数耗尽或认证失败时的诊断步骤。",
          "问题现象\n"
              + "应用提示连接超时、连接数耗尽或认证失败时的诊断步骤。\n\n"
              + "可能原因\n"
              + "账号或配置变化、客户端版本差异、服务故障均可能产生此类问题，需要结合发生时间与影响范围判断。\n\n"
              + "排查步骤\n"
              + "1. 记录完整错误类型与时间，核对应用数据库地址、端口和数据库名称。\n"
              + "2. 由授权管理员检查网络连通性、数据库健康状态和连接池指标，区分认证失败与连接耗尽。\n\n"
              + "解决方法与验证\n"
              + "查看活动会话及长事务，优先修复泄漏和不合理连接池；终止会话前确认业务影响并取得授权。\n\n"
              + "注意事项\n"
              + "不要把连接密码放入工单；不要通过无限增加连接数掩盖应用问题。",
          "数据库",
          "PostgreSQL,数据库,连接",
          "PUBLISHED"),
      new ArticleRequest(
          "服务器磁盘空间不足处理流程",
          "磁盘使用率告警时先定位增长来源，避免误删业务数据。",
          "问题现象\n"
              + "磁盘使用率告警时先定位增长来源，避免误删业务数据。\n\n"
              + "可能原因\n"
              + "账号或配置变化、客户端版本差异、服务故障均可能产生此类问题，需要结合发生时间与影响范围判断。\n\n"
              + "排查步骤\n"
              + "1. 检查各分区使用率及近期增长趋势，确认日志、备份或临时文件是否异常增长。\n"
              + "2. 核对保留策略、备份状态和应用写入目录，由系统负责人确认可清理范围。\n\n"
              + "解决方法与验证\n"
              + "按审批清理过期日志或临时文件，必要时扩容；监测磁盘与应用错误率确认恢复。\n\n"
              + "注意事项\n"
              + "禁止直接删除数据库文件、当前日志和未确认的备份；保留操作记录。",
          "服务器",
          "磁盘,空间,服务器",
          "PUBLISHED"),
      new ArticleRequest(
          "DNS解析故障排查",
          "内网域名访问失败但服务地址可达时，检查 DNS 和网络路径。",
          "问题现象\n"
              + "内网域名访问失败但服务地址可达时，检查 DNS 和网络路径。\n\n"
              + "可能原因\n"
              + "账号或配置变化、客户端版本差异、服务故障均可能产生此类问题，需要结合发生时间与影响范围判断。\n\n"
              + "排查步骤\n"
              + "1. 记录域名、客户端网络及故障时间，确认 VPN 正常连接。\n"
              + "2. 比较受管 DNS 对该域名的解析结果，查看缓存和最近的记录变更。\n\n"
              + "解决方法与验证\n"
              + "管理员核对内部 DNS 记录与转发配置，修正后等待 TTL 并在受影响客户端复测。\n\n"
              + "注意事项\n"
              + "不要随意切换公共 DNS，内部域名通常无法由公共解析器处理。",
          "网络支持",
          "DNS,解析,域名",
          "PUBLISHED"),
      new ArticleRequest(
          "Jenkins构建失败常见原因",
          "构建失败应优先找到最早出现的有效错误，而不是最后一行提示。",
          "问题现象\n"
              + "构建失败应优先找到最早出现的有效错误，而不是最后一行提示。\n\n"
              + "可能原因\n"
              + "账号或配置变化、客户端版本差异、服务故障均可能产生此类问题，需要结合发生时间与影响范围判断。\n\n"
              + "排查步骤\n"
              + "1. 记录任务名称、构建编号、代码提交与失败阶段，比较最近成功的构建。\n"
              + "2. 检查依赖仓库、构建节点磁盘和凭据有效期，核对工具版本与流水线配置变化。\n\n"
              + "解决方法与验证\n"
              + "修复对应配置或代码后重新执行一次；基础设施异常由平台管理员处理并记录原因。\n\n"
              + "注意事项\n"
              + "日志对外分享前移除凭据、令牌和内部地址；不要盲目反复重试部署步骤。",
          "研发工具",
          "Jenkins,构建,流水线",
          "PUBLISHED"),
      new ArticleRequest(
          "共享文件夹权限申请指南",
          "访问共享目录被拒绝时，申请明确范围的授权。",
          "问题现象\n"
              + "访问共享目录被拒绝时，申请明确范围的授权。\n\n"
              + "可能原因\n"
              + "账号或配置变化、客户端版本差异、服务故障均可能产生此类问题，需要结合发生时间与影响范围判断。\n\n"
              + "排查步骤\n"
              + "1. 记录共享路径、业务用途、需要读或写权限以及有效期。\n"
              + "2. 由资料负责人批准后，管理员核对目录 ACL 与授权组，避免直接开放全盘。\n\n"
              + "解决方法与验证\n"
              + "权限变更后重新登录或刷新会话，验证目标目录并记录结果；定期复核授权。\n\n"
              + "注意事项\n"
              + "不得申请与工作无关的资料，不要发送敏感文件作为故障附件。",
          "账号权限",
          "共享,文件夹,权限",
          "PUBLISHED")
    };
    for (var article : knowledge) articles.save(null, article, admin.getId());
    String[][] data = {
      {"VPN无法连接", "VPN 客户端连接公司网络时超时，居家网络正常。"},
      {"VPN认证失败", "VPN 提示认证失败，多因素认证已完成但无法访问内网。"},
      {"OA系统登录失败", "OA 登录后反复跳回登录页，影响报销审批。"},
      {"企业邮箱无法发送邮件", "企业邮箱可以收信，但邮件发送提示队列异常。"},
      {"邮箱密码重置申请", "邮箱密码过期，申请通过身份核验后重置。"},
      {"三楼打印机无法打印", "打印机显示离线，多个打印任务停留在队列。"},
      {"办公电脑蓝屏", "办公电脑启动业务应用时蓝屏，已记录错误截图。"},
      {"无线网络连接异常", "Wi-Fi 显示已连接但无法获取有效网络地址。"},
      {"会议室Wi-Fi信号不稳定", "会议室无线网络频繁断线，视频会议受到影响。"},
      {"GitLab项目权限申请", "申请 GitLab 研发仓库只读权限，用于本周代码评审。"},
      {"Jenkins构建失败", "Jenkins 流水线在依赖下载阶段失败，请协助排查。"},
      {"业务应用数据库连接失败", "PostgreSQL 连接超时，应用登录接口受到影响。"},
      {"PostgreSQL连接数过高", "数据库连接池告警，活动连接数持续增长。"},
      {"服务器磁盘空间不足", "应用服务器日志分区剩余不足，写入开始报错。"},
      {"文件服务器无法访问", "共享文件服务器访问超时，同组同事也受影响。"},
      {"新员工账号开通", "申请为下周入职员工开通企业账号和邮箱。"},
      {"离职员工账号禁用", "请按审批单禁用离职账号并保留交接数据。"},
      {"共享文件夹权限申请", "申请共享项目文件夹只读权限，有资料负责人审批。"},
      {"软件安装权限申请", "申请安装公司批准的数据库客户端软件。"},
      {"ERP页面加载异常", "ERP 库存页面加载超过一分钟，其他页面正常。"},
      {"DNS解析异常", "内网域名 DNS 解析失败，VPN 连接正常。"},
      {"内网服务访问超时", "工作门户下午开始访问超时，请检查服务健康状态。"},
      {"SSL证书即将过期", "业务域名证书将于一周后到期，请安排续期。"},
      {"Redis服务异常", "测试环境 Redis 连接告警，需要平台组检查服务。"},
      {"应用服务器CPU占用过高", "应用服务器 CPU 持续超过90%，订单查询响应变慢。"}
    };
    LocalDateTime now = LocalDateTime.now();
    for (int i = 0; i < data.length; i++) {
      var ticket = new Ticket();
      ticket.setTitle(data[i][0]);
      ticket.setDescription(data[i][1]);
      ticket.setPriority(i % 5 + 1);
      ticket.setUserId(people[i % 3].getId());
      ticket = service.createTicket(ticket);
      if (i % 3 != 0) service.updateStatus(ticket.getId(), TicketStatus.IN_PROGRESS, admin.getId());
      if (i % 3 == 2) service.updateStatus(ticket.getId(), TicketStatus.CLOSED, admin.getId());
      if (i % 2 == 0) {
        activity.comment(ticket.getId(), ticket.getUserId(), "已补充发生时间和影响范围，请协助排查。相关截图可按公司规范提供。");
        activity.comment(
            ticket.getId(),
            admin.getId(),
            i % 3 == 2 ? "已修复配置并与申请人复测，服务恢复正常。" : "已收到反馈，正在核对日志和账号授权，请保留错误提示。");
      }
      LocalDateTime created = now.minusDays(25 - i).minusHours(i % 4);
      jdbc.update(
          "UPDATE ticket SET created_at=?,updated_at=? WHERE id=?",
          created,
          created.plusHours(i % 3 * 2),
          ticket.getId());
      jdbc.update(
          "UPDATE ticket_history SET created_at=? + CASE action WHEN 'CREATE' THEN interval '0"
              + " hours' ELSE CASE new_status WHEN 'IN_PROGRESS' THEN interval '2 hours' ELSE"
              + " interval '4 hours' END END WHERE ticket_id=?",
          created,
          ticket.getId());
      jdbc.update(
          "UPDATE ticket_comment SET created_at=? WHERE ticket_id=?",
          created.plusHours(1),
          ticket.getId());
    }
    jdbc.update("INSERT INTO demo_seed(seed_key) VALUES ('enterprise-demo-v1')");
  }
}
