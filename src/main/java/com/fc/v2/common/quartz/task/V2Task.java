package com.fc.v2.common.quartz.task;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fc.v2.model.auto.TSysUser;
import com.fc.v2.service.ITSaltYearTaskService;
import com.fc.v2.service.ITSysUserService;
import com.fc.v2.service.salt.SaltYearRoundResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 *测试类
 * @CLASSNAME   V2Task
 * @Description 定时调度具体工作类
 * @Auther Jan  橙寂
 * @DATE 2019/9/2 0002 15:33
 */
@Component("v2Task")
public class V2Task {

	@Autowired
    private ITSysUserService userService;

	@Autowired
	private ITSaltYearTaskService saltYearTaskService;

	/**
	 * 年检催办跑一轮（无参，供 t_sys_quartz_job 以 v2Task.saltYearRun() 挂周期）。
	 * 挑单、派人、落痕、收口三数全出自服务层那一处出口，定时处只点一下，不加参数。
	 */
	public void saltYearRun() {
		SaltYearRoundResult round = saltYearTaskService.runRound(new Date());
		System.out.println("年检催办本轮收口：" + round.getMessage()
				+ "（还欠开口" + round.getOwingCount()
				+ "，派出" + round.getDispatchedCount()
				+ "，分不出" + round.getFailedCount() + "）时间：" + DateUtil.now());
	}

	/**
     * 无参的任务
     */
    public void runTask1()
    {
        System.out.println("正在执行定时任务，无参方法");
    }

    /**
     * 有参任务
     * 目前仅执行常见的数据类型  Integer Long  带L  string  带 ''  bool Double 带 d
     * @param a
     * @param b
     */
    public void runTask2(Integer a,Long b,String c,Boolean d,Double e)
    {
    	List<TSysUser> list=  userService.selectTSysUserList(new QueryWrapper<TSysUser>());
    	System.err.println("用户查询num："+list.size());
        System.out.println("正在执行定时任务，带多个参数的方法"+a+"   "+b+" "+c+"  "+d+" "+e+"执行时间:"+DateUtil.now());
    }
}
