package com.example.core.react.agent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.example.core.entity.Agent;
import com.example.core.entity.AgentScheduledTask;
import com.example.core.service.AgentService;
import com.example.core.service.ScheduledTaskService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class HeartbeatScheduler {

    private final AgentService agentService;
    private final AgentRuntimeManager runtimeManager;
    private final ScheduledTaskService scheduledTaskService;

    private ScheduledExecutorService taskScannerScheduler;
    private ScheduledFuture<?> taskScannerFuture;

    @EventListener(ApplicationReadyEvent.class)
    public void startAll() {
        taskScannerScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "scheduled-task-scanner");
            t.setDaemon(true);
            return t;
        });

        startScheduledTaskScanner();

        log.info("定时任务扫描器已启动");
    }

    private void startScheduledTaskScanner() {
        if (taskScannerFuture != null) {
            taskScannerFuture.cancel(false);
        }

        taskScannerFuture = taskScannerScheduler.scheduleAtFixedRate(() -> {
            try {
                scanScheduledTasks();
            } catch (Exception e) {
                log.error("定时任务扫描异常: {}", e.getMessage(), e);
            }
        }, 1, 1, TimeUnit.MINUTES);

        log.info("定时任务扫描线程已启动: 每1分钟扫描一次");
    }

    private void scanScheduledTasks() {
        List<AgentScheduledTask> dueTasks = scheduledTaskService.getDueTasks();
        if (dueTasks.isEmpty()) {
            return;
        }

        log.info("定时任务扫描: 发现{}个到期任务", dueTasks.size());

        Map<String, List<AgentScheduledTask>> tasksByAgent = new java.util.HashMap<>();
        for (AgentScheduledTask task : dueTasks) {
            tasksByAgent.computeIfAbsent(task.getAgentId(), k -> new java.util.ArrayList<>()).add(task);
        }

        for (Map.Entry<String, List<AgentScheduledTask>> entry : tasksByAgent.entrySet()) {
            String agentId = entry.getKey();
            List<AgentScheduledTask> agentDueTasks = entry.getValue();

            AgentRuntime runtime = runtimeManager.getRuntime(agentId);
            if (runtime == null) {
                log.warn("定时任务跳过: AgentRuntime不存在, agentId={}", agentId);
                continue;
            }

            if (Agent.RUNTIME_RUNNING.equals(runtime.getStatus())) {
                log.debug("定时任务跳过: Agent正在执行任务, agentId={}", agentId);
                continue;
            }

            for (AgentScheduledTask task : agentDueTasks) {
                String taskContent = task.getTaskInput() != null ? task.getTaskInput() : task.getTaskDescription();
                runtime.submitMessage(AgentMessage.scheduledTaskMessage(task.getTaskId(), taskContent));
                scheduledTaskService.markExecuted(task.getTaskId());
                log.info("定时任务精准触发: taskId={}, agentId={}, 只触发此Agent", task.getTaskId(), agentId);
            }
        }
    }

    public void stopAll() {
        log.info("停止定时任务扫描器...");
        if (taskScannerFuture != null) {
            taskScannerFuture.cancel(false);
        }
        if (taskScannerScheduler != null) {
            taskScannerScheduler.shutdown();
        }
        log.info("定时任务扫描器已停止");
    }

    public int getScheduledCount() {
        return 0;
    }
}
