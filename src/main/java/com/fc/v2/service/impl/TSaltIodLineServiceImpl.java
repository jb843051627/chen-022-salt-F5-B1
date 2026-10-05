package com.fc.v2.service.impl;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.mapper.auto.TSaltIodLineMapper;
import com.fc.v2.model.auto.TSaltIodLine;
import com.fc.v2.service.ITSaltIodLineService;

/**
 * 碘含量分档判定线 Service业务层处理（rule-eval 形状）
 *
 * <p>口径只有一把，全在 {@link #effectiveAt} 与 {@link #listAvailable}：
 * 未删、在场（status=0）、立线之日 ≤ at &lt; 让位之日（不含）才算该时刻生效。
 * 单规则求值、取最高优先级、是否可用、可用条数，四条路全都过这把尺，
 * 不允许"任意时刻都生效"或两处时段各判各的。分档一律"等于上限取高一档"。
 *
 * @author fuce
 * @date 2026-09-14
 */
@Service
public class TSaltIodLineServiceImpl implements ITSaltIodLineService {

    private static final int LINE_ON = 0;

    /** 优先级降序；让位之日晚的版本先取；再并列按 id 升序（确定性） */
    private static final Comparator<TSaltIodLine> BY_PRIORITY = new Comparator<TSaltIodLine>() {
        @Override
        public int compare(TSaltIodLine a, TSaltIodLine b) {
            int pa = a.getPriority() == null ? 0 : a.getPriority().intValue();
            int pb = b.getPriority() == null ? 0 : b.getPriority().intValue();
            if (pa != pb) {
                return pb - pa;
            }
            int end = compareEffEnd(b, a); // 让位之日晚的排前（null 视为常开，排最前）
            if (end != 0) {
                return end;
            }
            return Long.compare(a.getId(), b.getId());
        }
    };

    @javax.annotation.Resource
    private TSaltIodLineMapper saltIodLineMapper;

    /**
     * 时段比较统一走**墙钟字符串**（yyyy-MM-dd HH:mm:ss）绑定：
     * JDBC 的 serverTimezone 与本机时区不对称，直接把 java.util.Date 作参数会整体偏移，
     * 使边界判定（立线当日、让位当日）错档。
     */
    private static String ts(Date d) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("GMT+8"));
        return sdf.format(d);
    }

    @Override
    public TSaltIodLine selectTSaltIodLineById(Long id) {
        return this.saltIodLineMapper.selectById(id);
    }

    @Override
    public List<TSaltIodLine> listAvailable(Date at) {
        if (at == null) {
            return new ArrayList<TSaltIodLine>();
        }
        // 生效谓词在 SQL 处先收一道：未删 + 在场 + 立线/让位区间（让位不含）
        List<TSaltIodLine> rows = this.saltIodLineMapper.selectList(new QueryWrapper<TSaltIodLine>()
                .eq("del_flag", 0)
                .eq("status", LINE_ON)
                .and(w -> w.isNull("eff_start").or().le("eff_start", ts(at)))
                .and(w -> w.isNull("eff_end").or().gt("eff_end", ts(at))));
        rows.sort(BY_PRIORITY);
        return rows;
    }

    @Override
    public int evaluate(String ruleCode, BigDecimal input, Date at) {
        if (ruleCode == null || ruleCode.trim().isEmpty() || input == null || at == null) {
            return 0;
        }
        TSaltIodLine rule = findRule(ruleCode, at);
        return rule == null ? 0 : levelOf(rule, input);
    }

    @Override
    public int evaluateTop(BigDecimal input, Date at) {
        if (input == null || at == null) {
            return 0;
        }
        if (input.compareTo(BigDecimal.ZERO) < 0
                || input.compareTo(new BigDecimal("100")) > 0) {
            return 0;
        }
        List<TSaltIodLine> avail = listAvailable(at);
        return avail.isEmpty() ? 0 : levelOf(avail.get(0), input);
    }

    @Override
    public boolean usable(Long id, Date at) {
        if (id == null || at == null) {
            return false;
        }
        TSaltIodLine r = this.saltIodLineMapper.selectById(id);
        return effectiveAt(r, at);
    }

    @Override
    public int countAvailable(Date at) {
        // 条数与列表同一把尺，不另写一个"只看 del_flag"的口径
        return listAvailable(at).size();
    }

    /** 该规则在 at 时刻生效的版本：四条路共用的生效版本定位（优先级、让位晚、id 定序） */
    private TSaltIodLine findRule(String ruleCode, Date at) {
        List<TSaltIodLine> sameCode = new ArrayList<TSaltIodLine>();
        for (TSaltIodLine r : this.saltIodLineMapper.selectList(new QueryWrapper<TSaltIodLine>()
                .eq("del_flag", 0)
                .eq("rule_code", ruleCode))) {
            if (effectiveAt(r, at)) {
                sameCode.add(r);
            }
        }
        if (sameCode.isEmpty()) {
            return null;
        }
        sameCode.sort(BY_PRIORITY);
        return sameCode.get(0);
    }

    /**
     * 唯一生效谓词：未删 + 在场 + 立线 ≤ at &lt; 让位（两端皆可空：立线空=早已立，让位空=常开）。
     */
    private boolean effectiveAt(TSaltIodLine r, Date at) {
        if (r == null || at == null) {
            return false;
        }
        if (r.getDelFlag() != null && r.getDelFlag().intValue() == 1) {
            return false;
        }
        if (r.getStatus() == null || r.getStatus().intValue() != LINE_ON) {
            return false;
        }
        if (r.getEffStart() != null && r.getEffStart().after(at)) {
            return false;
        }
        if (r.getEffEnd() != null && !r.getEffEnd().after(at)) {
            // 让位之日不含：at >= eff_end 即失效
            return false;
        }
        return true;
    }

    /** 让位之日比较：null（常开）视为最晚；否则晚的在前 */
    private static int compareEffEnd(TSaltIodLine later, TSaltIodLine earlier) {
        Date a = later.getEffEnd();
        Date b = earlier.getEffEnd();
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return -1; // later 排前
        }
        if (b == null) {
            return 1;
        }
        return a.after(b) ? -1 : (a.before(b) ? 1 : 0);
    }

    /**
     * 分档：等于上限取高一档（严格小于本档上限才留本档）；
     * 某档上限缺失则越过该档往下比，皆无着落归四档。
     */
    private int levelOf(TSaltIodLine rule, BigDecimal input) {
        if (rule.getTh1Max() != null && input.compareTo(rule.getTh1Max()) < 0) {
            return 1;
        }
        if (rule.getTh2Max() != null && input.compareTo(rule.getTh2Max()) < 0) {
            return 2;
        }
        if (rule.getTh3Max() != null && input.compareTo(rule.getTh3Max()) < 0) {
            return 3;
        }
        return 4;
    }
}
