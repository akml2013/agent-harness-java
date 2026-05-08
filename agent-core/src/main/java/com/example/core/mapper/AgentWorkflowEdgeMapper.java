package com.example.core.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.core.entity.AgentWorkflowEdge;

@Mapper
public interface AgentWorkflowEdgeMapper extends BaseMapper<AgentWorkflowEdge> {

    @Delete("DELETE FROM agent_workflow_edges WHERE workflow_id = #{workflowId}")
    int physicalDeleteByWorkflowId(String workflowId);
}
