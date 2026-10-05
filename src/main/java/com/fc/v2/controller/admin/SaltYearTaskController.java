package com.fc.v2.controller.admin;

import java.util.Date;

import com.fc.v2.common.base.BaseController;
import com.fc.v2.common.domain.AjaxResult;
import com.fc.v2.common.domain.ResultTable;
import com.fc.v2.common.log.Log;
import com.fc.v2.service.ITSaltYearTaskService;
import com.fc.v2.service.salt.SaltYearRoundResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.format.annotation.DateTimeFormat;
import com.fc.v2.model.auto.TSaltYearTask;

/**
 * 年检到期催办单 Controller（scheduling-job 形状：现行屏 + 跑一轮入口）。
 *
 * 屏上三个数与逐条两处痕迹，只取自跑一轮那一趟出口的回话，不许一处取数一处手填。
 *
 * @author fuce
 * @date 2026-10-04
 */
@Api(value = "年检到期催办单")
@Controller
@RequestMapping("/saltYearTask")
public class SaltYearTaskController extends BaseController {

    private final String prefix = "admin/saltYearTask";

    @Autowired
    private ITSaltYearTaskService saltYearTaskService;

    @ApiOperation(value = "年检催办现行屏跳转", notes = "年检催办现行屏跳转")
    @GetMapping("/view")
    @RequiresPermissions("saltYearTask:view")
    public String view(ModelMap model) {
        return prefix + "/list";
    }

    @Log(title = "年检催办现行屏", action = "list")
    @ApiOperation(value = "现行屏条目", notes = "待派/已派出/分不出三种情形照常列着，本轮不动的不遮住")
    @GetMapping("/list")
    @RequiresPermissions("saltYearTask:list")
    @ResponseBody
    public ResultTable list() {
        return dataTable(saltYearTaskService.listBoard());
    }

    @Log(title = "年检催办跑一轮", action = "run")
    @ApiOperation(value = "跑一轮", notes = "服务层唯一出口：挑单、派人、落痕、收口三数与四样回话全在这一趟")
    @PostMapping("/run")
    @RequiresPermissions("saltYearTask:run")
    @ResponseBody
    public AjaxResult run() {
        SaltYearRoundResult round = saltYearTaskService.runRound(new Date());
        // 屏上三个数、逐条痕迹、四样回话全部取自这一趟出口，不另查屏、不手填
        return AjaxResult.success(200, round.getMessage(), round);
    }

    @Log(title = "年检催办手工建条", action = "add")
    @ApiOperation(value = "开一条催办", notes = "年检岗手工建：名录联动、默认提前3工作日定版、同企业+事由+止点幂等")
    @PostMapping("/add")
    @RequiresPermissions("saltYearTask:add")
    @ResponseBody
    public AjaxResult add(
            @org.springframework.web.bind.annotation.RequestParam("siteId") Long siteId,
            @org.springframework.web.bind.annotation.RequestParam(value = "bizType", required = false) Integer bizType,
            @org.springframework.web.bind.annotation.RequestParam("dueAt")
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date dueAt,
            @org.springframework.web.bind.annotation.RequestParam(value = "leadWorkdays", required = false)
            Integer leadWorkdays,
            @org.springframework.web.bind.annotation.RequestParam("scriptNo") String scriptNo,
            @org.springframework.web.bind.annotation.RequestParam(value = "content", required = false) String content) {
        TSaltYearTask saved = saltYearTaskService.createManual(siteId, bizType, dueAt,
                leadWorkdays, scriptNo, content);
        if (saved == null) {
            // 联动/话本/止点校验不过：名录摘牌或查无、话本缺失停用、止点空着，均不开条
            return AjaxResult.error("建条未成：企业已摘牌或查无名录、话本缺失/停用/事由不对，或止点未填");
        }
        return AjaxResult.success(200, "已开催办条目", saved);
    }
}
