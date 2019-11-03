package com.imooc.miaoshaproject.mq.strategy;

import org.apache.rocketmq.client.producer.LocalTransactionState;

public interface MQTransactionStrategy {
    LocalTransactionState doTransaction(Object o);

    LocalTransactionState checkTransaction(String transactionId);
}
