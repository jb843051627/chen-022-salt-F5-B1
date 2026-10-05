package com.fc.v2.service;

import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TSaltYearTask;
import com.fc.v2.service.salt.SaltYearRoundResult;

/**
 * 年检到期催办单 Service接口（scheduling-job 形状：周期执行，无增删改查入口）
 *
 * @author fuce
 * @date 2026-09-14
 */
public interface ITSaltYearTaskService {

    /** 按主键回查条目 */
    TSaltYearTask selectTSaltYearTaskById(Long id);

    /** 现行屏：三种情形的条目照常列着（待派/已派出/分不出），按止点时刻排序 */
    List<TSaltYearTask> listBoard();

    /** 该时刻档口已到、仍是待派的条目（用全省同一把工作日尺量；窗口日的压条在跑一轮处另压） */
    List<TSaltYearTask> listDue(Date at);

    /**
     * 原来的跑一轮方法，照旧用，不加参数：返回本轮派出条数。
     * 完整收口回话见 {@link #runRound(Date)}，同一趟出口，不另挑单、不另把尺。
     */
    int runOnce(Date at);

    /**
     * 跑一轮的唯一出口：挑单、派人、两处落痕、分不出原委、收口三数与四样回话全出自此处。
     * 定时入口与现行屏都只取这一趟的回话。
     */
    SaltYearRoundResult runRound(Date at);

    /**
     * 年检岗手工新建一条催办条目（现行屏"开一条催办"入口）。
     *
     * <ul>
     *   <li>与企业总名录联动：名录查无或已摘牌的，建不出来（回 null）；</li>
     *   <li>话本口径与跑一轮同一把：缺失/停用/事由不对建不出来；</li>
     *   <li>最可提前几日开口按常规填 3 个工作日（leadWorkdays 传 null 时），
     *       一旦建条即行内定版（写 amount），跑轮只认行内这一版，日后常规改了不重算；</li>
     *   <li>同一批并着建两回幂等：同企业+事由+止点只落一条，重复建回的是已在册那条。</li>
     * </ul>
     *
     * @return 建出的（或已在册的同批）条目；校验不过回 null
     */
    TSaltYearTask createManual(Long siteId, Integer bizType, Date dueAt,
                               Integer leadWorkdays, String scriptNo, String content);
}
