package com.example.core.react.model;

import com.alibaba.fastjson2.annotation.JSONField;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskItem {

    @JSONField(name = "item_index")
    private Integer itemIndex;

    private String description;

    @JSONField(name = "action_type")
    private String actionType;

    @Builder.Default
    private String status = "PENDING";

    @JSONField(name = "input_params")
    private String inputParams;

    @JSONField(name = "output_result")
    private String outputResult;

    @JSONField(name = "error_message")
    private String errorMessage;
}
