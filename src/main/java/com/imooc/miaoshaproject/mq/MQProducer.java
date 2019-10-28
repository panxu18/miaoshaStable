package com.imooc.miaoshaproject.mq;

import com.alibaba.fastjson.JSON;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

@Component
public class MQProducer {

    @Value("${mq.nameserver.addr}")
    private String namesrvAddr;

    @Value("${mq.topicname.staock}")
    private String topicName;

    private DefaultMQProducer producer;

    @PostConstruct
    void init() throws MQClientException {
        //初始化producer
        producer = new DefaultMQProducer("producer_group");
        producer.setNamesrvAddr(namesrvAddr);
        producer.start();
    }

    public boolean reduceStock(Integer itemId, Integer amount) {
        Map<String, Object> body = new HashMap<>();
        body.put("itemId", itemId);
        body.put("amount", amount);
        Message message = new Message(topicName, "increse",
                JSON.toJSON(body).toString().getBytes(Charset.forName("UTF-8")));
        try {
            producer.send(message);
            return true;
        } catch (MQClientException e) {
            e.printStackTrace();
            return false;
        } catch (RemotingException e) {
            e.printStackTrace();
            return false;
        } catch (MQBrokerException e) {
            e.printStackTrace();
            return false;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }


}
