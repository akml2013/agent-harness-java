package com.example.core.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.core.entity.AgentWorkflow;
import com.example.core.entity.AgentWorkflowEdge;
import com.example.core.entity.AgentWorkflowNode;
import com.example.core.mapper.AgentWorkflowEdgeMapper;
import com.example.core.mapper.AgentWorkflowMapper;
import com.example.core.mapper.AgentWorkflowNodeMapper;
import com.example.core.service.DeepseekService;
import com.example.core.service.WorkflowService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowServiceImpl implements WorkflowService {

    private final AgentWorkflowMapper workflowMapper;
    private final AgentWorkflowNodeMapper nodeMapper;
    private final AgentWorkflowEdgeMapper edgeMapper;
    private final DeepseekService deepseekService;

    private static final String NAME_SYSTEM_PROMPT = "你是一个工作流命名器。请根据工作流描述，生成一个简短的工作流名称。要求：1.不超过10个字；2.不要使用引号；3.直接输出名称，不要额外说明；4.用中文生成。";

    @Override
    @Transactional
    public AgentWorkflow createWorkflowFromTaskNodes(Long userId, String name, String description,
            List<Map<String, Object>> taskNodeMaps, String createSource) {
        if (taskNodeMaps == null || taskNodeMaps.isEmpty()) {
            throw new IllegalArgumentException("任务节点列表不能为空");
        }

        String workflowId = UUID.randomUUID().toString().replace("-", "");

        if (name == null || name.isBlank()) {
            name = generateWorkflowName(description != null ? description : "新工作流");
        }

        AgentWorkflow workflow = new AgentWorkflow();
        workflow.setWorkflowId(workflowId);
        workflow.setUserId(userId);
        workflow.setName(name);
        workflow.setDescription(description);
        workflow.setStatus(AgentWorkflow.STATUS_ACTIVE);
        workflow.setNodeCount(taskNodeMaps.size());
        workflow.setVersion(1);
        workflow.setCreateSource(createSource != null ? createSource : AgentWorkflow.SOURCE_AI);
        workflow.setDeleted(0);
        workflowMapper.insert(workflow);

        String startNodeId = UUID.randomUUID().toString().replace("-", "");
        String endNodeId = UUID.randomUUID().toString().replace("-", "");

        AgentWorkflowNode startNode = new AgentWorkflowNode();
        startNode.setNodeId(startNodeId);
        startNode.setWorkflowId(workflowId);
        startNode.setUserId(userId);
        startNode.setNodeType(AgentWorkflowNode.TYPE_START);
        startNode.setNodeName("开始");
        startNode.setPositionX(0);
        startNode.setPositionY(100);
        startNode.setSortOrder(0);
        nodeMapper.insert(startNode);

        List<String> taskNodeIds = new ArrayList<>();
        for (int i = 0; i < taskNodeMaps.size(); i++) {
            Map<String, Object> nodeMap = taskNodeMaps.get(i);
            String taskId = UUID.randomUUID().toString().replace("-", "");
            taskNodeIds.add(taskId);

            AgentWorkflowNode taskNode = new AgentWorkflowNode();
            taskNode.setNodeId(taskId);
            taskNode.setWorkflowId(workflowId);
            taskNode.setUserId(userId);
            taskNode.setNodeType(AgentWorkflowNode.TYPE_TASK);
            taskNode.setNodeName(getStr(nodeMap, "node_name", "任务" + (i + 1)));
            taskNode.setPositionX((i + 1) * 200);
            taskNode.setPositionY(100);
            taskNode.setToolName(getStr(nodeMap, "tool_name", null));
            taskNode.setSortOrder(i + 1);

            Object toolParamsObj = nodeMap.get("tool_params");
            if (toolParamsObj != null) {
                if (toolParamsObj instanceof Map || toolParamsObj instanceof List) {
                    taskNode.setToolParams(JSON.toJSONString(toolParamsObj));
                } else {
                    taskNode.setToolParams(toolParamsObj.toString());
                }
            }

            taskNode.setDescription(getStr(nodeMap, "description", null));
            nodeMapper.insert(taskNode);
        }

        AgentWorkflowNode endNode = new AgentWorkflowNode();
        endNode.setNodeId(endNodeId);
        endNode.setWorkflowId(workflowId);
        endNode.setUserId(userId);
        endNode.setNodeType(AgentWorkflowNode.TYPE_END);
        endNode.setNodeName("结束");
        endNode.setPositionX((taskNodeMaps.size() + 1) * 200);
        endNode.setPositionY(100);
        endNode.setSortOrder(0);
        nodeMapper.insert(endNode);

        List<String> allNodeIds = new ArrayList<>();
        allNodeIds.add(startNodeId);
        allNodeIds.addAll(taskNodeIds);
        allNodeIds.add(endNodeId);

        for (int i = 0; i < allNodeIds.size() - 1; i++) {
            AgentWorkflowEdge edge = new AgentWorkflowEdge();
            edge.setEdgeId(UUID.randomUUID().toString().replace("-", ""));
            edge.setWorkflowId(workflowId);
            edge.setUserId(userId);
            edge.setSourceNodeId(allNodeIds.get(i));
            edge.setTargetNodeId(allNodeIds.get(i + 1));
            edgeMapper.insert(edge);
        }

        workflow.setNodeCount(taskNodeMaps.size());
        workflowMapper.updateById(workflow);

        log.info("工作流创建成功: workflowId={}, name={}, taskNodes={}", workflowId, name, taskNodeMaps.size());
        return workflow;
    }

    private String getStr(Map<String, Object> map, String key, String defaultVal) {
        Object val = map.get(key);
        if (val == null || val.toString().isBlank())
            return defaultVal;
        return val.toString();
    }

    @Override
    @Transactional
    public AgentWorkflow createWorkflow(Long userId, String name, String description,
            List<AgentWorkflowNode> nodes, List<AgentWorkflowEdge> edges,
            String createSource) {
        ValidationResult validation = validateWorkflow(nodes, edges);
        if (!validation.valid()) {
            throw new IllegalArgumentException("工作流校验失败: " + validation.message());
        }

        String workflowId = UUID.randomUUID().toString().replace("-", "");

        if (name == null || name.isBlank()) {
            name = generateWorkflowName(description != null ? description : "新工作流");
        }

        int taskNodeCount = (int) nodes.stream()
                .filter(n -> AgentWorkflowNode.TYPE_TASK.equals(n.getNodeType()))
                .count();

        AgentWorkflow workflow = new AgentWorkflow();
        workflow.setWorkflowId(workflowId);
        workflow.setUserId(userId);
        workflow.setName(name);
        workflow.setDescription(description);
        workflow.setStatus(AgentWorkflow.STATUS_ACTIVE);
        workflow.setNodeCount(taskNodeCount);
        workflow.setVersion(1);
        workflow.setCreateSource(createSource != null ? createSource : AgentWorkflow.SOURCE_MANUAL);
        workflow.setDeleted(0);
        workflowMapper.insert(workflow);

        saveNodesAndEdges(workflowId, userId, nodes, edges);

        workflow.setNodeCount(taskNodeCount);
        workflowMapper.updateById(workflow);

        log.info("工作流创建成功: workflowId={}, name={}, nodes={}, edges={}",
                workflowId, name, nodes.size(), edges.size());
        return workflow;
    }

    @Override
    @Transactional
    public AgentWorkflow updateWorkflow(Long userId, String workflowId,
            String name, String description,
            List<AgentWorkflowNode> nodes, List<AgentWorkflowEdge> edges) {
        AgentWorkflow workflow = getWorkflowEntity(userId, workflowId);
        if (workflow == null) {
            throw new IllegalArgumentException("工作流不存在: " + workflowId);
        }

        if (nodes != null && edges != null) {
            ValidationResult validation = validateWorkflow(nodes, edges);
            if (!validation.valid()) {
                throw new IllegalArgumentException("工作流校验失败: " + validation.message());
            }
        }

        if (name != null && !name.isBlank()) {
            workflow.setName(name);
        }
        if (description != null) {
            workflow.setDescription(description);
        }

        if (nodes != null && edges != null) {
            nodeMapper.physicalDeleteByWorkflowId(workflowId);
            edgeMapper.physicalDeleteByWorkflowId(workflowId);

            saveNodesAndEdges(workflowId, userId, nodes, edges);

            int taskNodeCount = (int) nodes.stream()
                    .filter(n -> AgentWorkflowNode.TYPE_TASK.equals(n.getNodeType()))
                    .count();
            workflow.setNodeCount(taskNodeCount);
            workflow.setVersion(workflow.getVersion() + 1);
        }

        workflowMapper.updateById(workflow);
        log.info("工作流更新成功: workflowId={}", workflowId);
        return workflow;
    }

    @Override
    @Transactional
    public boolean deleteWorkflow(Long userId, String workflowId) {
        AgentWorkflow workflow = getWorkflowEntity(userId, workflowId);
        if (workflow == null) {
            return false;
        }

        workflowMapper.update(null, new LambdaUpdateWrapper<AgentWorkflow>()
                .eq(AgentWorkflow::getId, workflow.getId())
                .set(AgentWorkflow::getDeleted, 1));

        log.info("工作流已软删除: workflowId={}", workflowId);
        return true;
    }

    @Override
    public List<AgentWorkflow> listWorkflows(Long userId) {
        return workflowMapper.selectList(new LambdaQueryWrapper<AgentWorkflow>()
                .eq(AgentWorkflow::getUserId, userId)
                .eq(AgentWorkflow::getDeleted, 0)
                .orderByDesc(AgentWorkflow::getCreateTime));
    }

    @Override
    public AgentWorkflow getWorkflow(Long userId, String workflowId) {
        return getWorkflowEntity(userId, workflowId);
    }

    @Override
    public List<AgentWorkflowNode> getWorkflowNodes(String workflowId) {
        return nodeMapper.selectList(new LambdaQueryWrapper<AgentWorkflowNode>()
                .eq(AgentWorkflowNode::getWorkflowId, workflowId)
                .orderByAsc(AgentWorkflowNode::getSortOrder));
    }

    @Override
    public List<AgentWorkflowEdge> getWorkflowEdges(String workflowId) {
        return edgeMapper.selectList(new LambdaQueryWrapper<AgentWorkflowEdge>()
                .eq(AgentWorkflowEdge::getWorkflowId, workflowId));
    }

    @Override
    public AgentWorkflow renameWorkflow(Long userId, String workflowId, String newName) {
        AgentWorkflow workflow = getWorkflowEntity(userId, workflowId);
        if (workflow == null) {
            throw new IllegalArgumentException("工作流不存在: " + workflowId);
        }
        workflow.setName(newName);
        workflowMapper.updateById(workflow);
        log.info("工作流重命名: workflowId={}, newName={}", workflowId, newName);
        return workflow;
    }

    @Override
    public String generateWorkflowName(String description) {
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                String name = deepseekService.chatWithSystem(NAME_SYSTEM_PROMPT, description);
                if (name != null && !name.isBlank()) {
                    name = name.trim().replaceAll("^[\"'\u201c\u201d]+|[\"'\u201c\u201d]+$", "");
                    if (name.length() > 15) {
                        name = name.substring(0, 15);
                    }
                    return name;
                }
            } catch (Exception e) {
                log.warn("LLM生成工作流名称失败(第{}次): {}", attempt, e.getMessage());
            }
        }
        return "新工作流" + getNextWorkflowSequence();
    }

    @Override
    public ValidationResult validateWorkflow(List<AgentWorkflowNode> nodes, List<AgentWorkflowEdge> edges) {
        if (nodes == null || nodes.isEmpty()) {
            return ValidationResult.fail("节点列表不能为空");
        }
        if (edges == null) {
            edges = new ArrayList<>();
        }

        long startCount = nodes.stream().filter(n -> AgentWorkflowNode.TYPE_START.equals(n.getNodeType())).count();
        if (startCount == 0) {
            return ValidationResult.fail("缺少开始节点");
        }
        if (startCount > 1) {
            return ValidationResult.fail("只能有一个开始节点");
        }

        long endCount = nodes.stream().filter(n -> AgentWorkflowNode.TYPE_END.equals(n.getNodeType())).count();
        if (endCount == 0) {
            return ValidationResult.fail("缺少结束节点");
        }
        if (endCount > 1) {
            return ValidationResult.fail("只能有一个结束节点");
        }

        Map<String, List<String>> adjacency = new HashMap<>();
        Map<String, String> nodeIdMap = new HashMap<>();
        for (AgentWorkflowNode node : nodes) {
            String nid = node.getNodeId() != null ? node.getNodeId() : UUID.randomUUID().toString().replace("-", "");
            nodeIdMap.put(nid, nid);
            adjacency.put(nid, new ArrayList<>());
        }

        Set<String> nodeIds = nodeIdMap.keySet();
        for (AgentWorkflowEdge edge : edges) {
            String source = edge.getSourceNodeId();
            String target = edge.getTargetNodeId();
            if (!nodeIds.contains(source)) {
                return ValidationResult.fail("边的源节点不存在: " + source);
            }
            if (!nodeIds.contains(target)) {
                return ValidationResult.fail("边的目标节点不存在: " + target);
            }
            adjacency.get(source).add(target);
        }

        String startNodeId = nodes.stream()
                .filter(n -> AgentWorkflowNode.TYPE_START.equals(n.getNodeType()))
                .map(AgentWorkflowNode::getNodeId)
                .findFirst().orElse(null);
        String endNodeId = nodes.stream()
                .filter(n -> AgentWorkflowNode.TYPE_END.equals(n.getNodeType()))
                .map(AgentWorkflowNode::getNodeId)
                .findFirst().orElse(null);

        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();
        queue.add(startNodeId);
        visited.add(startNodeId);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            for (String next : adjacency.getOrDefault(current, new ArrayList<>())) {
                if (!visited.contains(next)) {
                    visited.add(next);
                    queue.add(next);
                }
            }
        }
        if (!visited.contains(endNodeId)) {
            return ValidationResult.fail("开始节点到结束节点不存在通路");
        }

        if (hasCycle(nodes, edges, adjacency)) {
            return ValidationResult.fail("工作流中存在环路");
        }

        for (AgentWorkflowNode node : nodes) {
            if (AgentWorkflowNode.TYPE_TASK.equals(node.getNodeType())) {
                String nid = node.getNodeId();
                boolean hasIncoming = edges.stream().anyMatch(e -> nid.equals(e.getTargetNodeId()));
                boolean hasOutgoing = edges.stream().anyMatch(e -> nid.equals(e.getSourceNodeId()));
                if (!hasIncoming) {
                    return ValidationResult.fail("任务节点\"" + node.getNodeName() + "\"没有入边");
                }
                if (!hasOutgoing) {
                    return ValidationResult.fail("任务节点\"" + node.getNodeName() + "\"没有出边");
                }
            }
        }

        if (visited.size() < nodeIds.size()) {
            return ValidationResult.fail("存在孤立节点，无法从开始节点到达");
        }

        return ValidationResult.ok();
    }

    @Override
    public Map<String, Object> getWorkflowForExecution(Long userId, String workflowId) {
        AgentWorkflow workflow = getWorkflowEntity(userId, workflowId);
        if (workflow == null) {
            throw new IllegalArgumentException("工作流不存在: " + workflowId);
        }
        if (AgentWorkflow.STATUS_DISABLED.equals(workflow.getStatus())) {
            throw new IllegalArgumentException("工作流已禁用: " + workflowId);
        }

        List<AgentWorkflowNode> nodes = nodeMapper.selectList(
                new LambdaQueryWrapper<AgentWorkflowNode>()
                        .eq(AgentWorkflowNode::getWorkflowId, workflowId)
                        .orderByAsc(AgentWorkflowNode::getSortOrder));

        List<Map<String, Object>> nodeList = new ArrayList<>();
        for (AgentWorkflowNode node : nodes) {
            if (AgentWorkflowNode.TYPE_TASK.equals(node.getNodeType())) {
                Map<String, Object> nodeMap = new LinkedHashMap<>();
                nodeMap.put("node_id", node.getNodeId());
                nodeMap.put("node_name", node.getNodeName());
                nodeMap.put("tool_name", node.getToolName());
                nodeMap.put("tool_params", node.getToolParams());
                nodeMap.put("sort_order", node.getSortOrder());
                nodeList.add(nodeMap);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("workflow_id", workflowId);
        result.put("workflow_name", workflow.getName());
        result.put("description", workflow.getDescription());
        result.put("nodes", nodeList);
        result.put("total_nodes", nodeList.size());
        result.put("instruction", "请按sort_order顺序依次执行每个节点的工具。前一节点的结果可通过prev_result引用。");
        return result;
    }

    @Override
    public Map<String, String> getWorkflowMap(Long userId) {
        List<AgentWorkflow> workflows = listWorkflows(userId);
        Map<String, String> map = new LinkedHashMap<>();
        for (AgentWorkflow wf : workflows) {
            if (!AgentWorkflow.STATUS_DISABLED.equals(wf.getStatus())) {
                String desc = wf.getDescription() != null ? wf.getDescription() : wf.getName();
                map.put(wf.getWorkflowId(), desc);
            }
        }
        return map;
    }

    private void saveNodesAndEdges(String workflowId, Long userId,
            List<AgentWorkflowNode> nodes, List<AgentWorkflowEdge> edges) {
        Map<String, String> nodeIdMapping = new HashMap<>();

        List<AgentWorkflowNode> sortedTaskNodes = new ArrayList<>();
        int taskIndex = 0;
        for (AgentWorkflowNode node : nodes) {
            String newNodeId = UUID.randomUUID().toString().replace("-", "");
            nodeIdMapping.put(node.getNodeId(), newNodeId);

            AgentWorkflowNode dbNode = new AgentWorkflowNode();
            dbNode.setNodeId(newNodeId);
            dbNode.setWorkflowId(workflowId);
            dbNode.setUserId(userId);
            dbNode.setNodeType(node.getNodeType());
            dbNode.setNodeName(node.getNodeName());
            dbNode.setPositionX(node.getPositionX() != null ? node.getPositionX() : 0);
            dbNode.setPositionY(node.getPositionY() != null ? node.getPositionY() : 0);
            dbNode.setToolName(node.getToolName());
            dbNode.setToolParams(node.getToolParams());
            dbNode.setDescription(node.getDescription());

            if (AgentWorkflowNode.TYPE_TASK.equals(node.getNodeType())) {
                taskIndex++;
                dbNode.setSortOrder(taskIndex);
                sortedTaskNodes.add(dbNode);
            } else {
                dbNode.setSortOrder(0);
            }

            nodeMapper.insert(dbNode);
        }

        if (edges != null) {
            for (AgentWorkflowEdge edge : edges) {
                String newEdgeId = UUID.randomUUID().toString().replace("-", "");
                String newSourceId = nodeIdMapping.get(edge.getSourceNodeId());
                String newTargetId = nodeIdMapping.get(edge.getTargetNodeId());

                if (newSourceId == null || newTargetId == null) {
                    log.warn("边的节点ID映射失败，跳过: source={}, target={}",
                            edge.getSourceNodeId(), edge.getTargetNodeId());
                    continue;
                }

                AgentWorkflowEdge dbEdge = new AgentWorkflowEdge();
                dbEdge.setEdgeId(newEdgeId);
                dbEdge.setWorkflowId(workflowId);
                dbEdge.setUserId(userId);
                dbEdge.setSourceNodeId(newSourceId);
                dbEdge.setTargetNodeId(newTargetId);
                dbEdge.setEdgeCondition(edge.getEdgeCondition());
                edgeMapper.insert(dbEdge);
            }
        }
    }

    private boolean hasCycle(List<AgentWorkflowNode> nodes, List<AgentWorkflowEdge> edges,
            Map<String, List<String>> adjacency) {
        Map<String, Integer> color = new HashMap<>();
        for (AgentWorkflowNode node : nodes) {
            color.put(node.getNodeId(), 0);
        }

        for (AgentWorkflowNode node : nodes) {
            if (color.get(node.getNodeId()) == 0) {
                if (dfsDetectCycle(node.getNodeId(), adjacency, color)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean dfsDetectCycle(String nodeId, Map<String, List<String>> adjacency,
            Map<String, Integer> color) {
        color.put(nodeId, 1);
        for (String next : adjacency.getOrDefault(nodeId, new ArrayList<>())) {
            if (color.get(next) == 1) {
                return true;
            }
            if (color.get(next) == 0 && dfsDetectCycle(next, adjacency, color)) {
                return true;
            }
        }
        color.put(nodeId, 2);
        return false;
    }

    private AgentWorkflow getWorkflowEntity(Long userId, String workflowId) {
        return workflowMapper.selectOne(new LambdaQueryWrapper<AgentWorkflow>()
                .eq(AgentWorkflow::getWorkflowId, workflowId)
                .eq(AgentWorkflow::getUserId, userId)
                .eq(AgentWorkflow::getDeleted, 0));
    }

    private int getNextWorkflowSequence() {
        Long maxId = workflowMapper.selectCount(new LambdaQueryWrapper<>());
        return (maxId != null ? maxId.intValue() : 0) + 1;
    }
}
