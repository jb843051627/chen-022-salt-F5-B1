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
}
