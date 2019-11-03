package com.imooc.miaoshaproject.mq;

import com.alibaba.fastjson.JSON;
import com.imooc.miaoshaproject.mq.strategy.MQCheckLocalTransactionStrategy;
import com.imooc.miaoshaproject.mq.strategy.MQLocalTransactionStrategy;
import com.imooc.miaoshaproject.mq.strategy.MQTransactionStrategy;
import com.imooc.miaoshaproject.mq.strategy.MQTransactionStrategyHolder;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionListener;
import org.apache.rocketmq.client.producer.TransactionMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.Map;

@Configuration
public class MQConfiguration {

    public static String namesrvAddr;

    public static String topicName;

    public static String getNamesrvAddr() {
        return namesrvAddr;
    }

    /**
     * Spring 不能为静态属性自动注入，通过非静态set方法注入
     * @param namesrvAddr
     */
    @Value("${mq.nameserver.addr}")
    void setNamesrvAddr(String namesrvAddr) {
        MQConfiguration.namesrvAddr = namesrvAddr;
    }

    public static String getTopicName() {
        return topicName;
    }

    @Value("${mq.topicname.staock}")
    void setTopicName(String topicName) {
        MQConfiguration.topicName = topicName;
    }

    @Bean
    TransactionMQProducer transactionMQProducer() throws MQClientException {
        //初始化producer
        TransactionMQProducer producer = new TransactionMQProducer("transaction_producer_group");
        producer.setNamesrvAddr(namesrvAddr);
        producer.setTransactionListener(new TransactionListener() {
            @Override
            public LocalTransactionState executeLocalTransaction(Message message, Object o) {
                // 选择策略
                String jsonString = new String(message.getBody());
                Map<String, Object> map = JSON.parseObject(jsonString, Map.class);
                String type = (String) map.get(MQTransactionStrategyHolder.STRATEGY_TYPE);
                MQTransactionStrategy strategy = MQTransactionStrategyHolder.select(type);
                return strategy.doTransaction(o);
            }

            @Override
            public LocalTransactionState checkLocalTransaction(MessageExt messageExt) {
                // 选择策略
                String jsonString = new String(messageExt.getBody());
                Map<String, Object> map = JSON.parseObject(jsonString, Map.class);
                String type = (String) map.get(MQTransactionStrategyHolder.STRATEGY_TYPE);
                MQTransactionStrategy strategy = MQTransactionStrategyHolder.select(type);
                String transactionId = (String) map.get(MQTransactionStrategyHolder.TRANSACTION_ID);
                return strategy.checkTransaction(transactionId);
            }
        });
        producer.start();
        return producer;
    }
}
