package com.example.core.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.core.entity.AgentFileStorage;

@Mapper
public interface AgentFileStorageMapper extends BaseMapper<AgentFileStorage> {

    @Delete("DELETE FROM agent_file_storage WHERE id = #{id}")
    int physicalDeleteById(@Param("id") Long id);

    @Select("SELECT * FROM agent_file_storage WHERE original_file_id = #{originalFileId}")
    List<AgentFileStorage> selectByOriginalFileId(@Param("originalFileId") String originalFileId);

    @Select("SELECT f.* FROM agent_file_storage f " +
            "INNER JOIN (SELECT original_name, MAX(id) as max_id " +
            "           FROM agent_file_storage " +
            "           WHERE session_id = #{sessionId} AND deleted = 0 AND is_original = 1 " +
            "           GROUP BY original_name) latest " +
            "ON f.id = latest.max_id " +
            "ORDER BY f.create_time DESC")
    List<AgentFileStorage> selectLatestByName(@Param("sessionId") String sessionId);
}
