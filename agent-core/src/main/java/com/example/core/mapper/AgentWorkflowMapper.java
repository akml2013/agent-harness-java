package com.example.core.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.core.entity.AgentWorkflow;

@Mapper
public interface AgentWorkflowMapper extends BaseMapper<AgentWorkflow> {

    @Delete("DELETE FROM agent_workflows WHERE id = #{id}")
    int physicalDeleteById(Long id);

    @Delete("DELETE FROM agent_workflows WHERE workflow_id = #{workflowId}")
    int physicalDeleteByWorkflowId(String workflowId);
}
