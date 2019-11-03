package com.imooc.miaoshaproject.mq.strategy;

import org.apache.rocketmq.client.producer.LocalTransactionState;

public interface MQLocalTransactionStrategy extends  MQTransactionStrategy{
    LocalTransactionState doTransaction(Object o);
}
