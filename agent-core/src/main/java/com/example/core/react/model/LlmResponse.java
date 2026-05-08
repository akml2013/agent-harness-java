package com.example.core.react.model;

import java.util.List;

import com.alibaba.fastjson2.annotation.JSONField;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmResponse {

    private String thought;

    @JSONField(name = "brief_thought")
    private String briefThought;

    private String action;

    @JSONField(name = "action_input")
    private Object actionInput;

    private String decision;

    @Builder.Default
    private boolean finished = false;

    @JSONField(name = "final_answer")
    private String finalAnswer;

    @JSONField(name = "task_list")
    private List<TaskItem> taskList;

    @com.alibaba.fastjson2.annotation.JSONField(serialize = false, deserialize = false)
    private String parseError;
}
