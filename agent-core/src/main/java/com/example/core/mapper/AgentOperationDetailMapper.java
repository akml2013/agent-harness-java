package com.example.core.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.core.entity.AgentOperationDetail;

@Mapper
public interface AgentOperationDetailMapper extends BaseMapper<AgentOperationDetail> {

        @Select("SELECT * FROM agent_operation_details WHERE session_id = #{sessionId} ORDER BY create_time ASC")
        List<AgentOperationDetail> selectBySessionId(@Param("sessionId") String sessionId);

        @Select("SELECT * FROM agent_operation_details WHERE session_id = #{sessionId} AND operation_type = #{operationType} ORDER BY create_time ASC")
        List<AgentOperationDetail> selectBySessionIdAndType(@Param("sessionId") String sessionId,
                        @Param("operationType") String operationType);

        @Select("SELECT * FROM agent_operation_details WHERE session_id = #{sessionId} AND tool_name = #{toolName} ORDER BY create_time ASC")
        List<AgentOperationDetail> selectBySessionIdAndToolName(@Param("sessionId") String sessionId,
                        @Param("toolName") String toolName);

        @Select("SELECT * FROM agent_operation_details WHERE session_id = #{sessionId} AND round_index = #{roundIndex} ORDER BY create_time ASC")
        List<AgentOperationDetail> selectBySessionIdAndRound(@Param("sessionId") String sessionId,
                        @Param("roundIndex") Integer roundIndex);

        @Select("SELECT * FROM agent_operation_details WHERE session_id = #{sessionId} AND file_key = #{fileKey} ORDER BY create_time ASC")
        List<AgentOperationDetail> selectBySessionIdAndFileKey(@Param("sessionId") String sessionId,
                        @Param("fileKey") String fileKey);

        @Select("SELECT DISTINCT file_key FROM agent_operation_details WHERE session_id = #{sessionId} AND file_key IS NOT NULL ORDER BY create_time ASC")
        List<String> selectDistinctFileKeysBySessionId(@Param("sessionId") String sessionId);
}
