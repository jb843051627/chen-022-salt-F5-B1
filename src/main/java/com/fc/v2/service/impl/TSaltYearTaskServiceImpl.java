package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fc.v2.mapper.auto.TSaltCalendarMapper;
import com.fc.v2.mapper.auto.TSaltEntMapper;
import com.fc.v2.mapper.auto.TSaltNotifyScriptMapper;
import com.fc.v2.mapper.auto.TSaltOfficerMapper;
import com.fc.v2.mapper.auto.TSaltOfficerOffMapper;
import com.fc.v2.mapper.auto.TSaltOfficerScopeMapper;
import com.fc.v2.mapper.auto.TSaltYearTaskMapper;
import com.fc.v2.model.auto.TSaltCalendar;
import com.fc.v2.model.auto.TSaltEnt;
import com.fc.v2.model.auto.TSaltNotifyScript;
import com.fc.v2.model.auto.TSaltOfficer;
import com.fc.v2.model.auto.TSaltOfficerOff;
import com.fc.v2.model.auto.TSaltOfficerScope;
import com.fc.v2.model.auto.TSaltYearTask;
import com.fc.v2.service.ITSaltYearTaskService;
import com.fc.v2.service.salt.SaltWorkdayRuler;
import com.fc.v2.service.salt.SaltYearRoundResult;

/**
 * 年检到期催办单 Service业务层处理（scheduling-job 形状：周期执行）。
 *
 * 跑一轮的唯一出口在 {@link #runRound(Date)}：本轮挑到哪几条、够不上日子的动不动、
 * 窗口日走不走、派出那一刻落不落，四样只出自这一趟；到期档口的工作日尺也只在这里定。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TSaltYearTaskServiceImpl implements ITSaltYearTaskService {

    private static final int STATUS_WAIT = 0;
    private static final int STATUS_DONE = 1;
    private static final int STATUS_FAIL = 2;

    private static final int ENT_LISTED = 0;
    private static final int OFFICER_ON = 0;
    private static final int SCRIPT_ON = 0;

    /** 手动建条目时"最可提前几日开口"的常规取值：3 个工作日（建条当刻定版写进 amount） */
    static final int DEFAULT_LEAD_WORKDAYS = 3;

    /** 分不出原委码：日后按原委挑得出一批（public 便于外部按原委筛单/测试核对） */
    public static final String REASON_DELISTED = "企业已从名录摘走";
    public static final String REASON_CONTACT_EMPTY = "联络人栏空缺多季未补";
    public static final String REASON_SCRIPT = "话本缺失或已停用，无本可依";
    public static final String REASON_QUOTA_FULL = "执法员当日接活定额已满";
    public static final String REASON_NO_OFFICER = "无在岗且接得动该事由的执法员";

    @javax.annotation.Resource
    private TSaltYearTaskMapper saltYearTaskMapper;
    @javax.annotation.Resource
    private TSaltEntMapper saltEntMapper;
    @javax.annotation.Resource
    private TSaltNotifyScriptMapper saltNotifyScriptMapper;
    @javax.annotation.Resource
    private TSaltOfficerMapper saltOfficerMapper;
    @javax.annotation.Resource
    private TSaltOfficerScopeMapper saltOfficerScopeMapper;
    @javax.annotation.Resource
    private TSaltOfficerOffMapper saltOfficerOffMapper;
    @javax.annotation.Resource
    private TSaltCalendarMapper saltCalendarMapper;

    /**
     * 时段比较统一按 GMT+8 墙钟串走：JDBC 的 serverTimezone 与本机时区不对称，直接传
     * java.util.Date 会整体偏移，使"恰好到期"这类边界用例错判（与同包其他盐政服务同法）。
     */
    private static String wall(Date d) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT+8"));
        return sdf.format(d);
    }

    private static LocalDateTime wallLdt(Date d) {
        return LocalDateTime.parse(wall(d).replace(' ', 'T'));
    }

    private static LocalDate wallDay(Date d) {
        return wallLdt(d).toLocalDate();
    }

    /** 本轮的尺只在这一处定：省历一次取齐，挑单与回写共用同一把 */
    private SaltWorkdayRuler buildRuler() {
        Set<LocalDate> off = new HashSet<LocalDate>();
        Set<LocalDate> win = new HashSet<LocalDate>();
        List<TSaltCalendar> rows = this.saltCalendarMapper.selectList(
                new QueryWrapper<TSaltCalendar>().eq("del_flag", 0));
        for (TSaltCalendar c : rows) {
            if (c.getCalDate() == null || c.getDayKind() == null) {
                continue;
            }
            LocalDate day = wallDay(c.getCalDate());
            if (c.getDayKind() == TSaltCalendar.KIND_CASE_WINDOW) {
                win.add(day);
            } else {
                off.add(day);
            }
        }
        return new SaltWorkdayRuler(off, win);
    }

    @Override
    public TSaltYearTask selectTSaltYearTaskById(Long id) {
        return this.saltYearTaskMapper.selectById(id);
    }

    @Override
    public List<TSaltYearTask> listBoard() {
        return this.saltYearTaskMapper.selectList(new QueryWrapper<TSaltYearTask>()
                .eq("del_flag", 0)
                .orderByAsc("due_at")
                .orderByAsc("id"));
    }

    @Override
    public List<TSaltYearTask> listDue(Date at) {
        Date now = at == null ? new Date() : at;
        SaltWorkdayRuler ruler = buildRuler();
        LocalDateTime nowLdt = wallLdt(now);
        List<TSaltYearTask> due = new ArrayList<TSaltYearTask>();
        for (TSaltYearTask r : listWaitTasks()) {
            Integer lead = r.getAmount() == null ? 0 : r.getAmount().intValue();
            if (r.getDueAt() != null && ruler.openable(wallLdt(r.getDueAt()), lead, nowLdt)) {
                due.add(r);
            }
        }
        return due;
    }

    @Override
    public int runOnce(Date at) {
        // 原方法照旧，不加参数：一趟出口，派出条数取自同一趟回话，不另挑单。
        return runRound(at).getDispatchedCount();
    }

    @Override
    public TSaltYearTask createManual(Long siteId, Integer bizType, Date dueAt,
                                      Integer leadWorkdays, String scriptNo, String content) {
        if (siteId == null || dueAt == null) {
            return null;
        }
        int biz = bizType == null ? 0 : bizType.intValue();

        // 催办册与年检岗那本（企业总名录）联动：名录里没有或已摘牌的，开不进催办册
        TSaltEnt ent = this.saltEntMapper.selectOne(new QueryWrapper<TSaltEnt>()
                .eq("id", siteId).eq("del_flag", 0));
        if (ent == null || (ent.getStatus() != null && ent.getStatus() != ENT_LISTED)) {
            return null;
        }
        // 话本口径与跑一轮同一把：缺失/停用/事由不对，不予建条
        if (findUsableScript(scriptNo, biz) == null) {
            return null;
        }

        // 同批并着建两回要幂等：同企业+事由+止点视为同一批，只落一条，回的是已在册那条。
        // 止点按墙钟串绑定（与本类时段比较同一口径，直传 Date 会因 serverTimezone 偏移漏判）。
        String dueWall = wall(dueAt);
        TSaltYearTask existing = this.saltYearTaskMapper.selectOne(new QueryWrapper<TSaltYearTask>()
                .eq("del_flag", 0)
                .eq("site_id", siteId)
                .eq("biz_type", biz)
                .eq("due_at", dueWall)
                .orderByAsc("id")
                .last("limit 1"));
        if (existing != null) {
            return existing;
        }

        // "最可提前几日开口"按常规填 3 个工作日；一旦建条即行内定版，
        // 日后跑轮只认这一行的 amount，不按任何新版常规重算（取舍定死，见 runRound）。
        int lead = leadWorkdays == null ? DEFAULT_LEAD_WORKDAYS : Math.max(0, leadWorkdays.intValue());

        TSaltYearTask r = new TSaltYearTask();
        r.setItemNo(generateItemNo(siteId));
        r.setSiteId(siteId);
        r.setBizType(biz);
        r.setDueAt(dueAt);
        r.setAmount(new BigDecimal(lead));
        r.setScriptNo(scriptNo);
        r.setContent(content);
        r.setStatus(STATUS_WAIT);
        r.setDelFlag(0);
        try {
            this.saltYearTaskMapper.insert(r);
        } catch (org.springframework.dao.DuplicateKeyException dup) {
            // 并着两回抢插，库上唯一键 (site_id,biz_type,due_at) 兜底：转取已落的同批那条
            TSaltYearTask raced = this.saltYearTaskMapper.selectOne(new QueryWrapper<TSaltYearTask>()
                    .eq("del_flag", 0)
                    .eq("site_id", siteId)
                    .eq("biz_type", biz)
                    .eq("due_at", dueWall)
                    .orderByAsc("id")
                    .last("limit 1"));
            if (raced != null) {
                return raced;
            }
            throw dup;
        }
        return r;
    }

    /** 条目代号：NJ + 建条墙钟（紧凑）+ 企业号，同批幂等由 (企业,事由,止点) 唯一键兜住 */
    private static String generateItemNo(Long siteId) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT+8"));
        return "NJ" + sdf.format(new Date()) + "-" + siteId;
    }

    @Override
    public SaltYearRoundResult runRound(Date at) {
        Date now = at == null ? new Date() : at;
        LocalDateTime nowLdt = wallLdt(now);
        LocalDate today = nowLdt.toLocalDate();
        String nowWall = wall(now);

        SaltWorkdayRuler ruler = buildRuler();
        boolean windowDay = ruler.caseWindow(today);

        SaltYearRoundResult result = new SaltYearRoundResult();
        result.setRanAt(now);
        result.setWindowDay(windowDay);

        // 一、捞单：待派 + 档口踩过（止点正压算已到，含等号）；踩没到的先压着本轮不点名。
        List<TSaltYearTask> picked = new ArrayList<TSaltYearTask>();
        for (TSaltYearTask r : listWaitTasks()) {
            if (r.getDueAt() == null) {
                result.getHeldNotDueItemIds().add(r.getId());
                continue;
            }
            int lead = r.getAmount() == null ? 0 : r.getAmount().intValue();
            if (!ruler.openable(wallLdt(r.getDueAt()), lead, nowLdt)) {
                result.getHeldNotDueItemIds().add(r.getId());
                continue;
            }
            picked.add(r);
            result.getPickedItemIds().add(r.getId());
        }

        // 二、窗口层 + 派人：集中办案那几日，手上真有已到档的也不许动，整批压下等下一轮。
        // 非窗口日每一条都走一遍；一桩走岔不撂下其余，try/catch 兜底逐条办完。
        // 挑人口径整轮只此一份计划：按执法员的负载与按企业（机构）的归属在这里同源算出，
        // 同一家企业整轮（乃至往轮已绑过）只黏一位执法员，不会一条路上一个人。
        if (windowDay) {
            for (TSaltYearTask r : picked) {
                result.getPressedItemIds().add(r.getId());
            }
        } else {
            RoundPlan plan = new RoundPlan(nowWall.substring(0, 10));
            for (TSaltYearTask r : picked) {
                try {
                    dispatchOne(r, nowWall, plan);
                } catch (Exception ex) {
                    markFail(r, "本轮派单异常：" + ex.getMessage(), nowWall);
                }
            }
        }

        // 四、收口：三个数不照屏幕誊，回库里逐条点到位（按本轮捞到的 id 回查，只认本轮落下的痕）。
        recountFromDb(picked, nowWall, result);
        if (picked.isEmpty()) {
            result.setMessage("本轮无到期");
        } else if (windowDay) {
            result.setMessage("全省集中办案窗口日，已到档" + picked.size() + "条压至下一轮");
        } else {
            result.setMessage("本轮派出" + result.getDispatchedCount()
                    + "条，分不出" + result.getFailedCount()
                    + "条，还欠开口" + result.getOwingCount() + "条");
        }
        return result;
    }

    /** 待派条目：上轮已派出/已判分不出的，本轮不许再当待派捞回来；按止点、id 定序（同秒两桩分两桩） */
    private List<TSaltYearTask> listWaitTasks() {
        return this.saltYearTaskMapper.selectList(new QueryWrapper<TSaltYearTask>()
                .eq("del_flag", 0)
                .eq("status", STATUS_WAIT)
                .orderByAsc("due_at")
                .orderByAsc("id"));
    }

    /**
     * 一条走一遍：开不成单的当场转挂分不出（摘牌 / 联络人空），
     * 堪派则一处 UPDATE 齐落"情形=已派出、派出那一刻、接活执法员"两个半痕迹。
     * 挑哪个人只问本轮那一份 {@link RoundPlan}：按执法员分派与按企业（机构）分派
     * 不再各算各的，同一家企业整轮只黏一位执法员。
     */
    private void dispatchOne(TSaltYearTask r, String nowWall, RoundPlan plan) {
        int bizType = r.getBizType() == null ? 0 : r.getBizType().intValue();

        TSaltEnt ent = r.getSiteId() == null ? null : this.saltEntMapper.selectOne(
                new QueryWrapper<TSaltEnt>().eq("id", r.getSiteId()).eq("del_flag", 0));
        if (ent == null || (ent.getStatus() != null && ent.getStatus() != ENT_LISTED)) {
            markFail(r, REASON_DELISTED, nowWall);
            return;
        }
        if (ent.getContactPerson() == null || ent.getContactPerson().trim().isEmpty()) {
            markFail(r, REASON_CONTACT_EMPTY, nowWall);
            return;
        }

        TSaltNotifyScript script = findUsableScript(r.getScriptNo(), bizType);
        if (script == null) {
            markFail(r, REASON_SCRIPT, nowWall);
            return;
        }

        PickResult pick = plan.choose(bizType, r.getSiteId());
        if (pick.officer == null) {
            // 挑不出分两种原委：堪派的全满额 / 压根没有在岗又接得动的（休办、接不动已逐位跳过）
            markFail(r, pick.noneEligible ? REASON_NO_OFFICER : REASON_QUOTA_FULL, nowWall);
            return;
        }
        TSaltOfficer chosen = pick.officer;

        // 派出一处落齐：情形转已派出 + 派出那一刻 + 执法员，少一头都算没走完
        UpdateWrapper<TSaltYearTask> uw = new UpdateWrapper<TSaltYearTask>()
                .eq("id", r.getId())
                .eq("del_flag", 0)
                .eq("status", STATUS_WAIT)
                .set("status", STATUS_DONE)
                .set("officer_id", chosen.getId())
                .set("dispatch_at", nowWall)
                .set("fail_reason", null)
                .set("update_time", nowWall);
        int rows = this.saltYearTaskMapper.update(null, uw);
        if (rows != 1) {
            markFail(r, "派出落痕未齐（更新行数" + rows + "）", nowWall);
        } else {
            // 落痕齐了才回记：同轮下一条立刻见得到这份负载与企业归属
            plan.note(chosen, r.getSiteId());
        }
    }

    /** 话本口径一处定：在场、未删、事由对得上（话本事由空着=两类皆可）；建条目与跑一轮共用 */
    private TSaltNotifyScript findUsableScript(String scriptNo, int bizType) {
        if (scriptNo == null || scriptNo.trim().isEmpty()) {
            return null;
        }
        TSaltNotifyScript script = this.saltNotifyScriptMapper.selectOne(
                new QueryWrapper<TSaltNotifyScript>()
                        .eq("script_no", scriptNo)
                        .eq("del_flag", 0)
                        .eq("status", SCRIPT_ON));
        if (script == null || (script.getBizType() != null && script.getBizType() != bizType)) {
            return null;
        }
        return script;
    }

    /** 派人挑选回话：选中的执法员；一位堪派的都没有时 noneEligible=true */
    private static final class PickResult {
        private TSaltOfficer officer;
        private boolean noneEligible;
    }

    /**
     * 本轮分派计划：两条分派路（按执法员负载、按企业归属）只在这一处同源算。
     *
     * <ul>
     *   <li>负载 = 库里今日已接 + 本趟已预留：第一条派出后同轮马上记账，
     *       不会再把同一家企业当无人接活派给第二个人；</li>
     *   <li>企业亲和：同一家企业今日已绑过哪位，本趟后续条目仍只给他；
     *       他满额就按定额挂"分不出"，绝不拆派。</li>
     * </ul>
     */
    private final class RoundPlan {
        private final String today;
        /** 每位执法员已预留负载：库里今日已接（底账）+ 本趟已派出 */
        private final Map<Long, Integer> reserved = new HashMap<Long, Integer>();
        /** 企业（机构）→ 今日已绑执法员 */
        private final Map<Long, Long> siteOfficer = new HashMap<Long, Long>();
        /** 堪派名册按事由缓存一趟 */
        private final Map<Integer, List<TSaltOfficer>> eligibleCache =
                new HashMap<Integer, List<TSaltOfficer>>();

        private RoundPlan(String today) {
            this.today = today;
            // 底账只点今日已落下的痕：负载与企业归属同源出自这一处
            for (TSaltYearTask t : todayDispatched(today)) {
                if (t.getOfficerId() == null) {
                    continue;
                }
                Integer n = this.reserved.get(t.getOfficerId());
                this.reserved.put(t.getOfficerId(), n == null ? 1 : n + 1);
                if (t.getSiteId() != null && !this.siteOfficer.containsKey(t.getSiteId())) {
                    this.siteOfficer.put(t.getSiteId(), t.getOfficerId());
                }
            }
        }

        private PickResult choose(int bizType, Long siteId) {
            PickResult res = new PickResult();
            List<TSaltOfficer> eligible = eligible(bizType);
            if (eligible.isEmpty()) {
                res.noneEligible = true;
                return res;
            }

            // 一、企业亲和优先：这家今日已绑过的人，仍是他接（满额也不拆给第二个人）
            if (siteId != null && this.siteOfficer.containsKey(siteId)) {
                TSaltOfficer bound = officerById(this.siteOfficer.get(siteId), eligible);
                if (bound != null) {
                    if (hasRoom(bound)) {
                        res.officer = bound;
                    }
                    return res;
                }
                // 既定的人今日休办/接不动了：才落到下面当无人兜底另挑，属顶班不是同人撞派
            }

            // 二、按负载挑：当日接得最少的先点；满额跳过；并列按执法员 id 定序
            TSaltOfficer best = null;
            int bestLoad = Integer.MAX_VALUE;
            for (TSaltOfficer o : eligible) {
                int n = loadOf(o.getId());
                if (!hasRoom(o)) {
                    continue;
                }
                if (n < bestLoad || (n == bestLoad && best != null && o.getId() < best.getId())) {
                    best = o;
                    bestLoad = n;
                }
            }
            res.officer = best;
            return res;
        }

        /** 派出落痕后回记一笔：负载预留 + 企业归属，同轮下一条立刻看得见 */
        private void note(TSaltOfficer officer, Long siteId) {
            Integer n = this.reserved.get(officer.getId());
            this.reserved.put(officer.getId(), n == null ? 1 : n + 1);
            if (siteId != null) {
                this.siteOfficer.put(siteId, officer.getId());
            }
        }

        private List<TSaltOfficer> eligible(int bizType) {
            if (!this.eligibleCache.containsKey(bizType)) {
                this.eligibleCache.put(bizType, eligibleOfficers(bizType, this.today));
            }
            return this.eligibleCache.get(bizType);
        }

        private int loadOf(Long officerId) {
            Integer n = this.reserved.get(officerId);
            return n == null ? 0 : n.intValue();
        }

        private boolean hasRoom(TSaltOfficer o) {
            int limit = o.getDailyLimit() == null ? Integer.MAX_VALUE : o.getDailyLimit().intValue();
            return loadOf(o.getId()) < limit;
        }

        private TSaltOfficer officerById(Long id, List<TSaltOfficer> eligible) {
            for (TSaltOfficer o : eligible) {
                if (o.getId().equals(id)) {
                    return o;
                }
            }
            return null;
        }
    }

    private List<TSaltOfficer> eligibleOfficers(int bizType, String today) {
        List<TSaltOfficer> all = this.saltOfficerMapper.selectList(new QueryWrapper<TSaltOfficer>()
                .eq("del_flag", 0)
                .eq("status", OFFICER_ON)
                .orderByAsc("id"));
        Set<Long> capable = new HashSet<Long>();
        for (TSaltOfficerScope s : this.saltOfficerScopeMapper.selectList(
                new QueryWrapper<TSaltOfficerScope>().eq("del_flag", 0).eq("biz_type", bizType))) {
            capable.add(s.getOfficerId());
        }
        Set<Long> off = new HashSet<Long>();
        for (TSaltOfficerOff o : this.saltOfficerOffMapper.selectList(
                new QueryWrapper<TSaltOfficerOff>().eq("del_flag", 0).eq("off_date", today))) {
            off.add(o.getOfficerId());
        }
        List<TSaltOfficer> eligible = new ArrayList<TSaltOfficer>();
        for (TSaltOfficer o : all) {
            if (capable.contains(o.getId()) && !off.contains(o.getId())) {
                eligible.add(o);
            }
        }
        return eligible;
    }

    /** 当日已派出条目（派出那一刻落在今日 00:00:00–23:59:59）：负载底账与企业归属同源 */
    private List<TSaltYearTask> todayDispatched(String today) {
        return this.saltYearTaskMapper.selectList(new QueryWrapper<TSaltYearTask>()
                .eq("del_flag", 0)
                .eq("status", STATUS_DONE)
                .ge("dispatch_at", today + " 00:00:00")
                .le("dispatch_at", today + " 23:59:59"));
    }

    /** 当场转挂分不出：卡住的环与原委写进同一条，日后按原委挑得出一批 */
    private void markFail(TSaltYearTask r, String reason, String nowWall) {
        this.saltYearTaskMapper.update(null, new UpdateWrapper<TSaltYearTask>()
                .eq("id", r.getId())
                .eq("del_flag", 0)
                .eq("status", STATUS_WAIT)
                .set("status", STATUS_FAIL)
                .set("fail_reason", reason)
                .set("officer_id", null)
                .set("dispatch_at", null)
                .set("update_time", nowWall));
    }

    /**
     * 回库逐条点：只认库里此刻的状态与两处痕迹，不看内存、不照屏誊；
     * 且痕迹时刻须就是本轮这一刻——只报本轮这一回，不把往轮的派出/分不出捎进来。
     */
    private void recountFromDb(List<TSaltYearTask> picked, String nowWall, SaltYearRoundResult result) {
        int dispatched = 0;
        int failed = 0;
        int owing = 0;
        for (TSaltYearTask r : picked) {
            TSaltYearTask db = this.saltYearTaskMapper.selectById(r.getId());
            if (db == null || (db.getDelFlag() != null && db.getDelFlag() == 1)) {
                continue;
            }
            Integer st = db.getStatus();
            if (st != null && st == STATUS_DONE
                    && db.getDispatchAt() != null && db.getOfficerId() != null
                    && nowWall.equals(wall(db.getDispatchAt()))) {
                dispatched++;
                result.getDispatched().add(new SaltYearRoundResult.DispatchTrace(
                        db.getId(), db.getItemNo(), db.getOfficerId(), officerName(db.getOfficerId()),
                        db.getDispatchAt()));
            } else if (st != null && st == STATUS_FAIL
                    && db.getUpdateTime() != null && nowWall.equals(wall(db.getUpdateTime()))) {
                failed++;
                result.getFailed().add(new SaltYearRoundResult.FailTrace(
                        db.getId(), db.getItemNo(), db.getFailReason()));
            } else {
                // 窗口压下、或落痕不全/不是本轮落的仍挂待派的，都算还欠开口
                owing++;
            }
        }
        result.setDispatchedCount(dispatched);
        result.setFailedCount(failed);
        result.setOwingCount(owing);
    }

    private String officerName(Long officerId) {
        TSaltOfficer o = this.saltOfficerMapper.selectById(officerId);
        return o == null ? null : o.getOfficerName();
    }
}
