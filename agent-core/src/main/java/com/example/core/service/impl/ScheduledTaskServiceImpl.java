package com.example.core.service.impl;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.core.entity.AgentScheduledTask;
import com.example.core.mapper.AgentScheduledTaskMapper;
import com.example.core.service.ScheduledTaskService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduledTaskServiceImpl implements ScheduledTaskService {

    private final AgentScheduledTaskMapper scheduledTaskMapper;

    @Override
    public AgentScheduledTask createTask(String agentId, String sessionId, Long userId, String taskName,
            String taskDescription, String repeatType, String repeatRule, String taskInput) {
        AgentScheduledTask task = new AgentScheduledTask();
        task.setTaskId(UUID.randomUUID().toString());
        task.setAgentId(agentId);
        task.setSessionId(sessionId);
        task.setUserId(userId);
        task.setTaskName(taskName);
        task.setTaskDescription(taskDescription);
        task.setRepeatType(repeatType);
        task.setRepeatRule(repeatRule);
        task.setTaskInput(taskInput);
        task.setStatus(AgentScheduledTask.STATUS_ACTIVE);
        task.setExecuteCount(0);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextTime = calculateNextExecuteTime(repeatType, repeatRule, now);
        task.setNextExecuteTime(nextTime);

        scheduledTaskMapper.insert(task);
        log.info("定时任务已创建: taskId={}, agentId={}, repeatType={}, nextExecuteTime={}",
                task.getTaskId(), agentId, repeatType, nextTime);
        return task;
    }

    @Override
    public AgentScheduledTask updateTask(String taskId, Long userId, String taskName, String taskDescription,
            String repeatType, String repeatRule, String taskInput, String status) {
        AgentScheduledTask task = getByTaskId(taskId);
        if (task == null) {
            log.warn("定时任务不存在: taskId={}", taskId);
            return null;
        }

        LambdaUpdateWrapper<AgentScheduledTask> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AgentScheduledTask::getTaskId, taskId);

        if (taskName != null) {
            wrapper.set(AgentScheduledTask::getTaskName, taskName);
            task.setTaskName(taskName);
        }
        if (taskDescription != null) {
            wrapper.set(AgentScheduledTask::getTaskDescription, taskDescription);
            task.setTaskDescription(taskDescription);
        }
        if (repeatType != null) {
            wrapper.set(AgentScheduledTask::getRepeatType, repeatType);
            task.setRepeatType(repeatType);
        }
        if (repeatRule != null) {
            wrapper.set(AgentScheduledTask::getRepeatRule, repeatRule);
            task.setRepeatRule(repeatRule);
        }
        if (taskInput != null) {
            wrapper.set(AgentScheduledTask::getTaskInput, taskInput);
            task.setTaskInput(taskInput);
        }
        if (status != null) {
            wrapper.set(AgentScheduledTask::getStatus, status);
            task.setStatus(status);
        }

        boolean needRecalculate = repeatType != null || repeatRule != null;
        if (needRecalculate) {
            String effectiveRepeatType = repeatType != null ? repeatType : task.getRepeatType();
            String effectiveRepeatRule = repeatRule != null ? repeatRule : task.getRepeatRule();
            LocalDateTime nextTime = calculateNextExecuteTime(effectiveRepeatType, effectiveRepeatRule,
                    LocalDateTime.now());
            wrapper.set(AgentScheduledTask::getNextExecuteTime, nextTime);
            task.setNextExecuteTime(nextTime);
        }

        scheduledTaskMapper.update(null, wrapper);
        log.info("定时任务已更新: taskId={}", taskId);
        return task;
    }

    @Override
    public boolean pauseTask(String taskId, Long userId) {
        AgentScheduledTask task = getByTaskId(taskId);
        if (task == null || !AgentScheduledTask.STATUS_ACTIVE.equals(task.getStatus())) {
            return false;
        }
        LambdaUpdateWrapper<AgentScheduledTask> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AgentScheduledTask::getTaskId, taskId)
                .set(AgentScheduledTask::getStatus, AgentScheduledTask.STATUS_PAUSED);
        scheduledTaskMapper.update(null, wrapper);
        log.info("定时任务已暂停: taskId={}", taskId);
        return true;
    }

    @Override
    public boolean resumeTask(String taskId, Long userId) {
        AgentScheduledTask task = getByTaskId(taskId);
        if (task == null || !AgentScheduledTask.STATUS_PAUSED.equals(task.getStatus())) {
            return false;
        }
        LocalDateTime nextTime = calculateNextExecuteTime(task.getRepeatType(), task.getRepeatRule(),
                LocalDateTime.now());
        LambdaUpdateWrapper<AgentScheduledTask> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AgentScheduledTask::getTaskId, taskId)
                .set(AgentScheduledTask::getStatus, AgentScheduledTask.STATUS_ACTIVE)
                .set(AgentScheduledTask::getNextExecuteTime, nextTime);
        scheduledTaskMapper.update(null, wrapper);
        log.info("定时任务已恢复: taskId={}, nextExecuteTime={}", taskId, nextTime);
        return true;
    }

    @Override
    public boolean cancelTask(String taskId, Long userId) {
        AgentScheduledTask task = getByTaskId(taskId);
        if (task == null) {
            return false;
        }
        LambdaUpdateWrapper<AgentScheduledTask> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AgentScheduledTask::getTaskId, taskId)
                .set(AgentScheduledTask::getStatus, AgentScheduledTask.STATUS_CANCELLED);
        scheduledTaskMapper.update(null, wrapper);
        log.info("定时任务已取消: taskId={}", taskId);
        return true;
    }

    @Override
    public List<AgentScheduledTask> getActiveTasksByAgentId(String agentId) {
        LambdaQueryWrapper<AgentScheduledTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentScheduledTask::getAgentId, agentId)
                .in(AgentScheduledTask::getStatus,
                        List.of(AgentScheduledTask.STATUS_ACTIVE, AgentScheduledTask.STATUS_PAUSED))
                .orderByAsc(AgentScheduledTask::getNextExecuteTime);
        return scheduledTaskMapper.selectList(wrapper);
    }

    @Override
    public List<AgentScheduledTask> getDueTasks() {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<AgentScheduledTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentScheduledTask::getStatus, AgentScheduledTask.STATUS_ACTIVE)
                .le(AgentScheduledTask::getNextExecuteTime, now);
        return scheduledTaskMapper.selectList(wrapper);
    }

    @Override
    public List<AgentScheduledTask> getDueTasksByAgentId(String agentId) {
        LocalDateTime now = LocalDateTime.now();
        LambdaQueryWrapper<AgentScheduledTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentScheduledTask::getAgentId, agentId)
                .eq(AgentScheduledTask::getStatus, AgentScheduledTask.STATUS_ACTIVE)
                .le(AgentScheduledTask::getNextExecuteTime, now);
        return scheduledTaskMapper.selectList(wrapper);
    }

    @Override
    public List<AgentScheduledTask> getTasksByUserId(Long userId) {
        LambdaQueryWrapper<AgentScheduledTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentScheduledTask::getUserId, userId)
                .in(AgentScheduledTask::getStatus,
                        List.of(AgentScheduledTask.STATUS_ACTIVE, AgentScheduledTask.STATUS_PAUSED))
                .orderByAsc(AgentScheduledTask::getNextExecuteTime);
        return scheduledTaskMapper.selectList(wrapper);
    }

    @Override
    public AgentScheduledTask getByTaskId(String taskId) {
        LambdaQueryWrapper<AgentScheduledTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentScheduledTask::getTaskId, taskId);
        return scheduledTaskMapper.selectOne(wrapper);
    }

    @Override
    public void markExecuted(String taskId) {
        AgentScheduledTask task = getByTaskId(taskId);
        if (task == null) {
            log.warn("标记执行失败，任务不存在: taskId={}", taskId);
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        int newCount = (task.getExecuteCount() != null ? task.getExecuteCount() : 0) + 1;

        LocalDateTime nextTime = calculateNextExecuteTime(task.getRepeatType(), task.getRepeatRule(), now);

        LambdaUpdateWrapper<AgentScheduledTask> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AgentScheduledTask::getTaskId, taskId)
                .set(AgentScheduledTask::getLastExecuteTime, now)
                .set(AgentScheduledTask::getExecuteCount, newCount);

        if (AgentScheduledTask.REPEAT_ONCE.equals(task.getRepeatType())) {
            wrapper.set(AgentScheduledTask::getStatus, AgentScheduledTask.STATUS_COMPLETED)
                    .set(AgentScheduledTask::getNextExecuteTime, null);
        } else {
            wrapper.set(AgentScheduledTask::getNextExecuteTime, nextTime);
        }

        scheduledTaskMapper.update(null, wrapper);
        log.info("定时任务已标记执行: taskId={}, executeCount={}, nextExecuteTime={}", taskId, newCount, nextTime);
    }

    @Override
    public LocalDateTime calculateNextExecuteTime(String repeatType, String repeatRule, LocalDateTime afterTime) {
        if (repeatRule == null || repeatRule.isBlank()) {
            return null;
        }

        JSONObject rule;
        try {
            rule = JSON.parseObject(repeatRule);
        } catch (Exception e) {
            log.warn("解析repeat_rule失败: {}", e.getMessage());
            return null;
        }

        return switch (repeatType) {
            case AgentScheduledTask.REPEAT_ONCE -> calculateOnce(rule, afterTime);
            case AgentScheduledTask.REPEAT_HOURLY -> calculateHourly(rule, afterTime);
            case AgentScheduledTask.REPEAT_DAILY -> calculateDaily(rule, afterTime);
            case AgentScheduledTask.REPEAT_WEEKLY -> calculateWeekly(rule, afterTime);
            case AgentScheduledTask.REPEAT_MONTHLY -> calculateMonthly(rule, afterTime);
            case AgentScheduledTask.REPEAT_YEARLY -> calculateYearly(rule, afterTime);
            default -> {
                log.warn("未知的重复类型: {}", repeatType);
                yield null;
            }
        };
    }

    private LocalDateTime calculateHourly(JSONObject rule, LocalDateTime afterTime) {
        Integer minute = rule.getInteger("minute");
        if (minute == null || minute < 0 || minute > 59) {
            minute = 0;
        }

        JSONArray timesArr = rule.getJSONArray("times");
        if (timesArr != null && !timesArr.isEmpty()) {
            List<LocalTime> times = parseTimes(timesArr);
            if (!times.isEmpty()) {
                return calculateDailyFromTimes(times, afterTime, rule);
            }
        }

        LocalTime startTime = null;
        LocalTime endTime = null;
        String startTimeStr = rule.getString("startTime");
        String endTimeStr = rule.getString("endTime");
        if (startTimeStr != null && !startTimeStr.isBlank()) {
            try {
                startTime = LocalTime.parse(startTimeStr);
            } catch (Exception e) {
                log.debug("解析startTime失败(忽略): {}", e.getMessage());
            }
        }
        if (endTimeStr != null && !endTimeStr.isBlank()) {
            try {
                endTime = LocalTime.parse(endTimeStr);
            } catch (Exception e) {
                log.debug("解析endTime失败(忽略): {}", e.getMessage());
            }
        }

        LocalDateTime startDate = afterTime;
        String startDateStr = rule.getString("startDate");
        if (startDateStr != null && !startDateStr.isBlank()) {
            try {
                LocalDateTime sd = LocalDateTime.parse(startDateStr + " 00:00:00",
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                if (sd.isAfter(afterTime)) {
                    startDate = sd;
                }
            } catch (Exception e) {
                log.debug("解析startDate失败(忽略): {}", e.getMessage());
            }
        }

        LocalDateTime candidate = startDate.withMinute(minute).withSecond(0).withNano(0);
        if (candidate.isAfter(afterTime)) {
            if (startTime != null && candidate.toLocalTime().isBefore(startTime)) {
                candidate = candidate.with(startTime).withMinute(minute).withSecond(0).withNano(0);
                if (candidate.toLocalTime().isBefore(startTime)) {
                    candidate = candidate.withMinute(startTime.getMinute()).withSecond(0).withNano(0);
                }
            }
            if (endTime != null && candidate.toLocalTime().isAfter(endTime)) {
                candidate = candidate.plusDays(1).with(startTime != null ? startTime : LocalTime.MIN).withMinute(minute)
                        .withSecond(0).withNano(0);
                if (startTime != null && candidate.toLocalTime().isBefore(startTime)) {
                    candidate = candidate.withMinute(startTime.getMinute()).withSecond(0).withNano(0);
                }
            }
            return candidate;
        }

        candidate = afterTime.plusHours(1).withMinute(minute).withSecond(0).withNano(0);
        if (startTime != null && candidate.toLocalTime().isBefore(startTime)) {
            candidate = candidate.with(startTime).withMinute(minute).withSecond(0).withNano(0);
            if (candidate.toLocalTime().isBefore(startTime)) {
                candidate = candidate.withMinute(startTime.getMinute()).withSecond(0).withNano(0);
            }
        }
        if (endTime != null && candidate.toLocalTime().isAfter(endTime)) {
            candidate = candidate.plusDays(1).with(startTime != null ? startTime : LocalTime.MIN).withMinute(minute)
                    .withSecond(0).withNano(0);
            if (startTime != null && candidate.toLocalTime().isBefore(startTime)) {
                candidate = candidate.withMinute(startTime.getMinute()).withSecond(0).withNano(0);
            }
        }

        return candidate;
    }

    private LocalDateTime calculateDaily(JSONObject rule, LocalDateTime afterTime) {
        JSONArray timesArr = rule.getJSONArray("times");
        if (timesArr == null || timesArr.isEmpty()) {
            return null;
        }

        List<LocalTime> times = parseTimes(timesArr);
        return calculateDailyFromTimes(times, afterTime, rule);
    }

    private LocalDateTime calculateDailyFromTimes(List<LocalTime> times, LocalDateTime afterTime, JSONObject rule) {
        LocalDateTime startDate = afterTime;
        String startDateStr = rule.getString("startDate");
        if (startDateStr != null && !startDateStr.isBlank()) {
            try {
                LocalDateTime sd = LocalDateTime.parse(startDateStr + " 00:00:00",
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                if (sd.isAfter(afterTime)) {
                    startDate = sd;
                }
            } catch (Exception e) {
                log.debug("解析startDate失败(忽略): {}", e.getMessage());
            }
        }

        for (int dayOffset = 0; dayOffset <= 366; dayOffset++) {
            LocalDateTime baseDate = startDate.plusDays(dayOffset);
            for (LocalTime time : times) {
                LocalDateTime candidateTime = baseDate.toLocalDate().atTime(time);
                if (candidateTime.isAfter(afterTime)) {
                    return candidateTime;
                }
            }
        }
        return null;
    }

    private LocalDateTime calculateOnce(JSONObject rule, LocalDateTime afterTime) {
        String executeAt = rule.getString("execute_at");
        if (executeAt == null) {
            return null;
        }
        try {
            LocalDateTime target = LocalDateTime.parse(executeAt,
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            return target.isAfter(afterTime) ? target : null;
        } catch (Exception e) {
            log.warn("解析ONCE执行时间失败: {}", e.getMessage());
            return null;
        }
    }

    private LocalDateTime calculateWeekly(JSONObject rule, LocalDateTime afterTime) {
        JSONArray weekdaysArr = rule.getJSONArray("weekdays");
        JSONArray timesArr = rule.getJSONArray("times");
        if (weekdaysArr == null || weekdaysArr.isEmpty() || timesArr == null || timesArr.isEmpty()) {
            return null;
        }

        List<Integer> weekdays = new ArrayList<>();
        for (int i = 0; i < weekdaysArr.size(); i++) {
            weekdays.add(weekdaysArr.getInteger(i));
        }
        Collections.sort(weekdays);

        List<LocalTime> times = parseTimes(timesArr);

        LocalDateTime startDate = afterTime;
        String startDateStr = rule.getString("startDate");
        if (startDateStr != null && !startDateStr.isBlank()) {
            try {
                LocalDateTime sd = LocalDateTime.parse(startDateStr + " 00:00:00",
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                if (sd.isAfter(afterTime)) {
                    startDate = sd;
                }
            } catch (Exception e) {
                log.debug("解析startDate失败(忽略): {}", e.getMessage());
            }
        }

        LocalDateTime candidate = null;
        for (int weekOffset = 0; weekOffset <= 53; weekOffset++) {
            LocalDateTime baseDate = startDate.plusWeeks(weekOffset);
            for (int day : weekdays) {
                DayOfWeek targetDay = DayOfWeek.of(day);
                LocalDateTime targetDate;
                if (baseDate.getDayOfWeek().getValue() <= day) {
                    targetDate = baseDate.with(TemporalAdjusters.nextOrSame(targetDay));
                } else {
                    targetDate = baseDate.with(TemporalAdjusters.next(targetDay));
                }
                if (weekOffset > 0) {
                    targetDate = startDate.plusWeeks(weekOffset).with(TemporalAdjusters.nextOrSame(targetDay));
                    if (targetDate.isBefore(startDate.plusWeeks(weekOffset))) {
                        targetDate = startDate.plusWeeks(weekOffset + 1).with(TemporalAdjusters.nextOrSame(targetDay));
                    }
                }

                for (LocalTime time : times) {
                    LocalDateTime candidateTime = targetDate.toLocalDate().atTime(time);
                    if (candidateTime.isAfter(afterTime)) {
                        if (candidate == null || candidateTime.isBefore(candidate)) {
                            candidate = candidateTime;
                        }
                    }
                }
            }
            if (candidate != null) {
                break;
            }
        }
        return candidate;
    }

    private LocalDateTime calculateMonthly(JSONObject rule, LocalDateTime afterTime) {
        JSONArray daysArr = rule.getJSONArray("daysOfMonth");
        JSONArray timesArr = rule.getJSONArray("times");
        if (daysArr == null || daysArr.isEmpty() || timesArr == null || timesArr.isEmpty()) {
            return null;
        }

        List<Integer> days = new ArrayList<>();
        for (int i = 0; i < daysArr.size(); i++) {
            days.add(daysArr.getInteger(i));
        }
        Collections.sort(days);

        List<LocalTime> times = parseTimes(timesArr);

        LocalDateTime startDate = afterTime;
        String startDateStr = rule.getString("startDate");
        if (startDateStr != null && !startDateStr.isBlank()) {
            try {
                LocalDateTime sd = LocalDateTime.parse(startDateStr + " 00:00:00",
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                if (sd.isAfter(afterTime)) {
                    startDate = sd;
                }
            } catch (Exception e) {
                log.debug("解析startDate失败(忽略): {}", e.getMessage());
            }
        }

        LocalDateTime candidate = null;
        for (int monthOffset = 0; monthOffset <= 12; monthOffset++) {
            LocalDateTime baseMonth = startDate.plusMonths(monthOffset).withDayOfMonth(1);
            for (int day : days) {
                int maxDay = baseMonth.toLocalDate().lengthOfMonth();
                int effectiveDay = Math.min(day, maxDay);
                LocalDateTime targetDate = baseMonth.withDayOfMonth(effectiveDay);

                for (LocalTime time : times) {
                    LocalDateTime candidateTime = targetDate.toLocalDate().atTime(time);
                    if (candidateTime.isAfter(afterTime)) {
                        if (candidate == null || candidateTime.isBefore(candidate)) {
                            candidate = candidateTime;
                        }
                    }
                }
            }
            if (candidate != null) {
                break;
            }
        }
        return candidate;
    }

    private LocalDateTime calculateYearly(JSONObject rule, LocalDateTime afterTime) {
        JSONArray monthsArr = rule.getJSONArray("monthsOfYear");
        JSONArray daysArr = rule.getJSONArray("daysOfMonth");
        JSONArray timesArr = rule.getJSONArray("times");
        if (monthsArr == null || monthsArr.isEmpty() || daysArr == null || daysArr.isEmpty()
                || timesArr == null || timesArr.isEmpty()) {
            return null;
        }

        List<Integer> months = new ArrayList<>();
        for (int i = 0; i < monthsArr.size(); i++) {
            months.add(monthsArr.getInteger(i));
        }
        Collections.sort(months);

        List<Integer> days = new ArrayList<>();
        for (int i = 0; i < daysArr.size(); i++) {
            days.add(daysArr.getInteger(i));
        }
        Collections.sort(days);

        List<LocalTime> times = parseTimes(timesArr);

        LocalDateTime candidate = null;
        for (int yearOffset = 0; yearOffset <= 2; yearOffset++) {
            int year = afterTime.getYear() + yearOffset;
            for (int month : months) {
                for (int day : days) {
                    try {
                        LocalDateTime targetDate = LocalDateTime.of(year, month, 1, 0, 0);
                        int maxDay = targetDate.toLocalDate().lengthOfMonth();
                        int effectiveDay = Math.min(day, maxDay);
                        targetDate = LocalDateTime.of(year, month, effectiveDay, 0, 0);

                        for (LocalTime time : times) {
                            LocalDateTime candidateTime = targetDate.toLocalDate().atTime(time);
                            if (candidateTime.isAfter(afterTime)) {
                                if (candidate == null || candidateTime.isBefore(candidate)) {
                                    candidate = candidateTime;
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.debug("无效日期: year={}, month={}, day={}", year, month, day);
                    }
                }
            }
            if (candidate != null) {
                break;
            }
        }
        return candidate;
    }

    private List<LocalTime> parseTimes(JSONArray timesArr) {
        List<LocalTime> times = new ArrayList<>();
        for (int i = 0; i < timesArr.size(); i++) {
            try {
                times.add(LocalTime.parse(timesArr.getString(i)));
            } catch (Exception e) {
                log.warn("解析时间失败: {}", timesArr.getString(i));
            }
        }
        Collections.sort(times);
        return times;
    }
}
