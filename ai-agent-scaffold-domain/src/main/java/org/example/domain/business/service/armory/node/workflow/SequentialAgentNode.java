package org.example.domain.business.service.armory.node.workflow;

import cn.bugstack.wrench.design.framework.tree.StrategyHandler;
import com.google.adk.agents.BaseAgent;
import com.google.adk.agents.SequentialAgent;
import lombok.extern.slf4j.Slf4j;
import org.example.domain.agent.model.entity.ArmoryCommandEntity;
import org.example.domain.agent.model.valobj.AiAgentConfigTableVO;
import org.example.domain.agent.model.valobj.AiAgentRegisterVO;
import org.example.domain.business.service.armory.AbstractArmorySupport;
import org.example.domain.business.service.armory.factory.DefaultArmoryFactory;
import org.example.domain.business.service.armory.node.RunnerNode;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author dabuw
 * @description
 * @date 12/11/26
 */
@Slf4j
@Service("sequentialAgentNode")
public class SequentialAgentNode extends AbstractArmorySupport {

    @Resource
    private RunnerNode runnerNode;

    @Override
    protected AiAgentRegisterVO doApply(ArmoryCommandEntity requestParameter, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {
        log.info("Ai Agent 装配操作 - SequentialAgentNode");

        List<AiAgentConfigTableVO.Module.AgentWorkflow> agentWorkflows = dynamicContext.getAgentWorkflows();
        AiAgentConfigTableVO.Module.AgentWorkflow agentWorkflow = agentWorkflows.remove(0);

        List<String> subAgentNames = agentWorkflow.getSubAgents();
        List<BaseAgent> subAgents = dynamicContext.queryAgentList(subAgentNames);

        SequentialAgent sequentialAgent =
                SequentialAgent.builder()
                        .name(agentWorkflow.getName())
                        .description(agentWorkflow.getDescription())
                        .subAgents(subAgents)
                        .build();

        dynamicContext.getAgentGroup().put(agentWorkflow.getName(), sequentialAgent);

        // 设置到上下文对象
        dynamicContext.setSequentialAgent(sequentialAgent);

        // 注册到 Spring 容器
        registerBean(agentWorkflow.getName(), SequentialAgent.class, sequentialAgent);

        return router(requestParameter, dynamicContext);
    }

    @Override
    public StrategyHandler<ArmoryCommandEntity, DefaultArmoryFactory.DynamicContext, AiAgentRegisterVO> get(ArmoryCommandEntity requestParameter, DefaultArmoryFactory.DynamicContext dynamicContext) throws Exception {
        return runnerNode;
    }

}