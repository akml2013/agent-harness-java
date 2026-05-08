package com.example.core.system.tool;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import com.example.core.system.SystemTool;
import com.example.core.system.SystemToolParamParser;
import com.example.core.system.SystemToolResult;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GetCurrentTimeTool implements SystemTool {

    private static final DateTimeFormatter DATE_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Override
    public String getName() {
        return "get_current_time";
    }

    @Override
    public String getDescription() {
        return "获取当前系统时间，返回完整时间、日期、时间、星期几、Unix时间戳等信息。"
                + "当需要知道当前时间来创建定时任务、判断时间条件或计算时间差时使用此工具。";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("timezone", Map.of("type", "string", "description",
                "时区ID（可选），如Asia/Shanghai、UTC等，默认系统时区"));
        return schema;
    }

    @Override
    public SystemToolResult execute(Map<String, Object> params) {
        String timezone = SystemToolParamParser.getString(params, "timezone");

        ZoneId zoneId;
        if (timezone != null && !timezone.isBlank()) {
            try {
                zoneId = ZoneId.of(timezone);
            } catch (Exception e) {
                return SystemToolResult.error("无效的时区ID: " + timezone);
            }
        } else {
            zoneId = ZoneId.systemDefault();
        }

        LocalDateTime now = LocalDateTime.now(zoneId);
        DayOfWeek dayOfWeek = now.getDayOfWeek();

        Map<String, Object> data = new HashMap<>();
        data.put("datetime", now.format(DATE_TIME_FMT));
        data.put("date", now.format(DATE_FMT));
        data.put("time", now.format(TIME_FMT));
        data.put("year", now.getYear());
        data.put("month", now.getMonthValue());
        data.put("day", now.getDayOfMonth());
        data.put("hour", now.getHour());
        data.put("minute", now.getMinute());
        data.put("second", now.getSecond());
        data.put("day_of_week", dayOfWeek.getValue());
        data.put("day_of_week_name", getDayOfWeekName(dayOfWeek));
        data.put("timezone", zoneId.toString());
        data.put("unix_timestamp", System.currentTimeMillis() / 1000);

        return SystemToolResult.success("当前时间: " + now.format(DATE_TIME_FMT), data);
    }

    private String getDayOfWeekName(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> "周一";
            case TUESDAY -> "周二";
            case WEDNESDAY -> "周三";
            case THURSDAY -> "周四";
            case FRIDAY -> "周五";
            case SATURDAY -> "周六";
            case SUNDAY -> "周日";
        };
    }
}
