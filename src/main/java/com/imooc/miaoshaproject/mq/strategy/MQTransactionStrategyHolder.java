package com.imooc.miaoshaproject.mq.strategy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MQTransactionStrategyHolder {

    public static final String STRATEGY_TYPE = "transactionType";

    public static final String TRANSACTION_ID = "transactionId";

    private static Map<String, MQTransactionStrategy> strategyMap;

    /**
     * Spring 不能直接注入静态属性，通过非静态set方法注入 strategyMap
     * @param strategyMap
     */
    @Autowired
    void setStrategyMap (Map<String, MQTransactionStrategy> strategyMap){
        MQTransactionStrategyHolder.strategyMap = strategyMap;
    }

    public static MQTransactionStrategy select(String transactionType) {
        return strategyMap.get(transactionType);
    }
}
