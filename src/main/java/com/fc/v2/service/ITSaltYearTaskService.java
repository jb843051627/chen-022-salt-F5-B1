package com.fc.v2.service;

import java.util.Date;
import java.util.List;

import com.fc.v2.model.auto.TSaltYearTask;
import com.fc.v2.service.salt.SaltYearCreateResult;
import com.fc.v2.service.salt.SaltYearRoundResult;

/**
 * 年检到期催办单 Service接口（scheduling-job 形状：周期执行；另开手动新建一条的手工入口）
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
     * 手动新建一条年检催办条目（年检岗手工入口）：与催办册同一本，落册即是待派，
     * 现行屏与下一轮跑一轮照此条走，不另立册。
     *
     * 定死的取舍：
     * 1）同一批（企业 + 事由 + 止点时刻，未删）并着建两回只落一回——第二回原样回话
     *    册内既有条目，不覆盖、不另建；最可提前几日等一切以册内既有版本为准，
     *    要改走改单，不靠再建一回；
     * 2）最可提前几日缺省按 0 填（开口点即止点本身，全省一把尺的缺省口径），
     *    负数、零头、出格大数一律拒收；
     * 3）联动两本册在进门处就核：企业须在册（摘牌/查无直接拒收），话本填了须在场
     *    且对得上事由；话本没填则取该事由唯一在场的一本，取不到唯一就留空，
     *    跑一轮时按"无本可依"挂分不出（既有设计路径）。
     */
    SaltYearCreateResult createManual(TSaltYearTask record);
}
