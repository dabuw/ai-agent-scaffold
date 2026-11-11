package org.example.domain.business.service;

import org.example.domain.agent.model.valobj.AiAgentConfigTableVO;

import java.util.List;

/**
 * @author dabuw
 * @description
 * @date 11/11/26
 */
public interface IArmoryService {

    void acceptArmoryAgents(List<AiAgentConfigTableVO> tables) throws Exception;


}
