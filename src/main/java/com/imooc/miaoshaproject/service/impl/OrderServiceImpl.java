package com.imooc.miaoshaproject.service.impl;

import com.alibaba.fastjson.JSON;
import com.imooc.miaoshaproject.dao.OrderDOMapper;
import com.imooc.miaoshaproject.dataobject.SequenceDO;
import com.imooc.miaoshaproject.error.BusinessException;
import com.imooc.miaoshaproject.error.EmBusinessError;
import com.imooc.miaoshaproject.mq.MQConfiguration;
import com.imooc.miaoshaproject.mq.strategy.MQCheckLocalTransactionStrategy;
import com.imooc.miaoshaproject.mq.strategy.MQLocalTransactionStrategy;
import com.imooc.miaoshaproject.mq.strategy.MQTransactionStrategy;
import com.imooc.miaoshaproject.mq.strategy.MQTransactionStrategyHolder;
import com.imooc.miaoshaproject.service.ItemService;
import com.imooc.miaoshaproject.service.OrderService;
import com.imooc.miaoshaproject.service.UserService;
import com.imooc.miaoshaproject.service.model.ItemModel;
import com.imooc.miaoshaproject.service.model.OrderModel;
import com.imooc.miaoshaproject.service.model.UserModel;
import com.imooc.miaoshaproject.dao.SequenceDOMapper;
import com.imooc.miaoshaproject.dataobject.OrderDO;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.LocalTransactionState;
import org.apache.rocketmq.client.producer.TransactionMQProducer;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.common.message.Message;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by hzllb on 2018/11/18.
 */
@Service
public class OrderServiceImpl implements OrderService {


    @Autowired
    private SequenceDOMapper sequenceDOMapper;

    @Autowired
    private ItemService itemService;

    @Autowired
    private UserService userService;

    @Autowired
    private OrderDOMapper orderDOMapper;

    @Autowired
    private TransactionMQProducer transactionMQProducer;

    /**
     * 库存-订单事务策略类
     * @return
     */
    @Bean("stock_order_transaction")
    MQTransactionStrategy stockOrderTransactionStrategy() {
        return new MQTransactionStrategy() {
            @Override
            public LocalTransactionState doTransaction(Object o) {
                Map<String, Object> map = (Map<String, Object>) o;
                Integer userId = (Integer) map.get("userId");
                ItemModel itemModel = (ItemModel) map.get("item");
                Integer promoId = (Integer) map.get("promoId");
                Integer amount = (Integer) map.get("amount");
                try {
                    doOrder(userId, itemModel, promoId, amount);
                } catch (BusinessException e) {
                    e.printStackTrace();
                    return LocalTransactionState.ROLLBACK_MESSAGE;
                }
                return LocalTransactionState.COMMIT_MESSAGE;
            }

            @Override
            public LocalTransactionState checkTransaction(String transactionId) {
                return LocalTransactionState.COMMIT_MESSAGE;
            }
        };
    }

    /**
     * 创建订单的事务性操作
     * 利用MQ提供的事务保证MQ的发送和创建订单同时成功或失败
     * @param userId
     * @param itemModel
     * @param promoId
     * @param amount
     * @return
     */
    @Override
    @Transactional
    public OrderModel doOrder(Integer userId, ItemModel itemModel, Integer promoId, Integer amount) throws BusinessException {

        //3.订单入库
        OrderModel orderModel = new OrderModel();
        orderModel.setUserId(userId);
        orderModel.setItemId(itemModel.getId());
        orderModel.setAmount(amount);
        if(promoId != null){
            orderModel.setItemPrice(itemModel.getPromoModel().getPromoItemPrice());
        }else{
            orderModel.setItemPrice(itemModel.getPrice());
        }
        orderModel.setPromoId(promoId);
        orderModel.setOrderPrice(orderModel.getItemPrice().multiply(new BigDecimal(amount)));

        //生成交易流水号,订单号
        orderModel.setId(generateOrderNo());
        OrderDO orderDO = convertFromOrderModel(orderModel);
        orderDOMapper.insertSelective(orderDO);

        //加上商品的销量
        itemService.increaseSales(itemModel.getId(),amount);
        //4.返回前端
        return orderModel;
    }

    @Override
    @Transactional
    public boolean createOrder(Integer userId, Integer itemId, Integer promoId, Integer amount) throws BusinessException {
        //1.校验下单状态,下单的商品是否存在，用户是否合法，购买数量是否正确
        ItemModel itemModel = itemService.getItemInCacheById(itemId);
        if(itemModel == null){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"商品信息不存在");
        }

        UserModel userModel = userService.getUserInCacheById(userId);
        if(userModel == null){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"用户信息不存在");
        }
        if(amount <= 0 || amount > 99){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"数量信息不正确");
        }

        //校验活动信息
        if(promoId != null){
            //（1）校验对应活动是否存在这个适用商品
            if(promoId.intValue() != itemModel.getPromoModel().getId()){
                throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"活动信息不正确");
                //（2）校验活动是否正在进行中
            }else if(itemModel.getPromoModel().getStatus().intValue() != 2) {
                throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"活动信息还未开始");
            }
        }

        //2.减少缓存库存
        long stock = itemService.decreaseStock(itemModel.getId(),amount);
        if(stock < 0){
            throw new BusinessException(EmBusinessError.STOCK_NOT_ENOUGH);
        }
        // 3.发送MQ事务消息
        Map<String, Object> body = new HashMap<>(); // 消息参数
        body.put("itemId", itemModel.getId());
        body.put("amount", amount);
        body.put(MQTransactionStrategyHolder.STRATEGY_TYPE,"stock_order_transaction"); // 事务类型，用于选择事务策略
        Map<String, Object> args = new HashMap<>(); // 本地事务参数
        args.put("userId", userId);
        args.put("item", itemModel);
        args.put("promoId", promoId);
        args.put("amount", amount);
        Message message = new Message(MQConfiguration.topicName, "increse",
                JSON.toJSON(body).toString().getBytes(Charset.forName("UTF-8")));
        TransactionSendResult result = null;
        try {
            result = transactionMQProducer.sendMessageInTransaction(message, args);
        } catch (MQClientException e) {
            e.printStackTrace();
            return false;
        }
        switch (result.getLocalTransactionState()) {
            case COMMIT_MESSAGE:
                return true;
            case ROLLBACK_MESSAGE:
                return false;
            case UNKNOW:
                return false;
            default:
                return false;
        }
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private String generateOrderNo(){
        //订单号有16位
        StringBuilder stringBuilder = new StringBuilder();
        //前8位为时间信息，年月日
        LocalDateTime now = LocalDateTime.now();
        String nowDate = now.format(DateTimeFormatter.ISO_DATE).replace("-","");
        stringBuilder.append(nowDate);

        //中间6位为自增序列
        //获取当前sequence
        int sequence = 0;
        SequenceDO sequenceDO =  sequenceDOMapper.getSequenceByName("order_info");
        sequence = sequenceDO.getCurrentValue();
        sequenceDO.setCurrentValue(sequenceDO.getCurrentValue() + sequenceDO.getStep());
        sequenceDOMapper.updateByPrimaryKeySelective(sequenceDO);
        String sequenceStr = String.valueOf(sequence);
        for(int i = 0; i < 6-sequenceStr.length();i++){
            stringBuilder.append(0);
        }
        stringBuilder.append(sequenceStr);


        //最后2位为分库分表位,暂时写死
        stringBuilder.append("00");

        return stringBuilder.toString();
    }
    private OrderDO convertFromOrderModel(OrderModel orderModel){
        if(orderModel == null){
            return null;
        }
        OrderDO orderDO = new OrderDO();
        BeanUtils.copyProperties(orderModel,orderDO);
        orderDO.setItemPrice(orderModel.getItemPrice().doubleValue());
        orderDO.setOrderPrice(orderModel.getOrderPrice().doubleValue());
        return orderDO;
    }
}
