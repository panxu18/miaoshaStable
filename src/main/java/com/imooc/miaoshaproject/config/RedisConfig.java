package com.imooc.miaoshaproject.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.io.IOException;

@Configuration
public class RedisConfig {

    /**
     * 自定义redis模板，实现对象序列化
     */
    @Bean
    RedisTemplate redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate redisTemplate = new RedisTemplate();
        redisTemplate.setConnectionFactory(redisConnectionFactory);

        // key序列化使用字符串
        redisTemplate.setKeySerializer(new StringRedisSerializer());

        // 对象序列化使用jason
        Jackson2JsonRedisSerializer jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer<Object>(Object.class);
        ObjectMapper objectMapper = new ObjectMapper();
        SimpleModule simpleModule = new SimpleModule();
        // 定制日期序列化方法
        simpleModule.addSerializer(DateTime.class, new JsonSerializer<DateTime>() {
            @Override
            public void serialize(DateTime dateTime, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
                    throws IOException {
                jsonGenerator.writeString(dateTime.toString("yyyy-MM-dd HH:mm:ss"));
            }
        });
        simpleModule.addDeserializer(DateTime.class, new JsonDeserializer<DateTime>() {

            @Override
            public DateTime deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
                    throws IOException, JsonProcessingException {

                return DateTime.parse(jsonParser.readValueAs(String.class),
                        DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss"));
            }
        });
        // 添加类信息
        objectMapper.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL);
        objectMapper.registerModule(simpleModule);
        jackson2JsonRedisSerializer.setObjectMapper(objectMapper);
        redisTemplate.setValueSerializer(jackson2JsonRedisSerializer);
        return redisTemplate;
    }
}
