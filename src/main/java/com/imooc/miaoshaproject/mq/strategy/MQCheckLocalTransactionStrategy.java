package com.imooc.miaoshaproject.mq.strategy;

import org.apache.rocketmq.client.producer.LocalTransactionState;

public interface MQCheckLocalTransactionStrategy extends  MQTransactionStrategy{
    LocalTransactionState checkTransaction(String transactionId);
}
