package com.example.core.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.core.entity.Agent;

@Mapper
public interface AgentMapper extends BaseMapper<Agent> {
}
