package com.example.core.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.core.entity.AgentWorkflowNode;

@Mapper
public interface AgentWorkflowNodeMapper extends BaseMapper<AgentWorkflowNode> {

    @Delete("DELETE FROM agent_workflow_nodes WHERE workflow_id = #{workflowId}")
    int physicalDeleteByWorkflowId(String workflowId);
}
