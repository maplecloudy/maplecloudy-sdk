package com.maplecloudy.distribute.engine.appserver;

import java.util.List;

import com.google.common.collect.Lists;
import com.maplecloudy.distribute.engine.app.elasticsearch.ElasticSearchTask;
import com.maplecloudy.distribute.engine.app.elasticsearch.ElatisticSearchPara;
import com.maplecloudy.distribute.engine.app.kibana.KibanaPara;
import com.maplecloudy.distribute.engine.app.kibana.StartKibanaTask;
import com.maplecloudy.distribute.engine.apptask.AppTask;
import com.maplecloudy.distribute.engine.apptask.TaskPool;

public class AppImpl implements IApp {

	public AppImpl() {

	}
	
	public int startElasticSearch(ElatisticSearchPara para)
	{
	  ElasticSearchTask task = new ElasticSearchTask(para);
    TaskPool.addTask(task);
    return 0;
	}
	public int startKibana(KibanaPara para) {
		System.out.println("startKibana:" + para.getName());
		StartKibanaTask task = new StartKibanaTask(para);
		TaskPool.addTask(task);
		return 0;
	}

	@Override
	public List<AppStatus> getAppStatus(KibanaPara para) {
		System.out.println("getAppStatus:" + para.getName());
		
		try {
			AppTask task = TaskPool.taskMap.get(para.getName());
			if (task == null) {
				new StartKibanaTask(para).checkTaskApp();
			}
			return task.getAppStatus();
		} catch (Exception e) {
			e.printStackTrace();
			AppStatus as = new AppStatus();
			as.error = "get app info error with:" + e.getMessage();
			return Lists.newArrayList();
		}
	}

	public List<String> getAppTaskInfo(KibanaPara para) {
		System.out.println("getAppTaskInfo:" + para.getName());
		AppTask task = TaskPool.taskMap.get(para.getName());
		if (task != null) {
			return task.runInfo;
		} else {
			return Lists.newArrayList();
		}
	}

	@Override
	public int stopAppTask(KibanaPara para) {
		System.out.println("stopAppTaskInfo:" + para.getName());
		try {
			AppTask task = TaskPool.taskMap.get(para.getName());
			if (task == null) {
				task = new StartKibanaTask(para);
				TaskPool.taskMap.put(para.getName(), task);
			}
			return task.stopApp();
		} catch (Exception e) {
			return -1;
		}
	}

}
