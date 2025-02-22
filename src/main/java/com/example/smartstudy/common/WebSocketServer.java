package com.example.smartstudy.common;

import com.example.smartstudy.config.CustomSpringConfigurator;
import com.example.smartstudy.model.MessageEntity;
import com.example.smartstudy.utils.RedisUtil;
import com.google.gson.Gson;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

@Component
// 配置 websocket 的路径
@ServerEndpoint(
        value = "/websocket/{id}",
        decoders = { MessageEntityDecode.class },
        encoders = { MessageEntityEncode.class },
        configurator = CustomSpringConfigurator.class
)
public class WebSocketServer {

    private Session session;
    private final Gson gson;
    private final RedisUtil redis;
    // 存储所有的用户连接
    private static final Map<Long, Session> WEBSOCKET_MAP = new ConcurrentHashMap<>();

    @Autowired
    public WebSocketServer(Gson gson, RedisUtil redis) {
        this.gson = gson;
        this.redis = redis;
    }

    @OnOpen
    public void onOpen(@PathParam("id") Long id, Session session) {
        this.session = session;
        // 根据 /websocket/{id} 中传入的用户 id 作为键，存储每个用户的 session
        WEBSOCKET_MAP.put(id, session);
    }

    @OnMessage
    public void onMessage(MessageEntity message) throws IOException {
        // 根据消息实体中的消息发送者和接受者的 id 组成信箱存储的键
        // 按两人id升序并以 - 字符分隔为键
        String key = LongStream.of(message.getFrom(), message.getTo())
                .sorted()
                .mapToObj(String::valueOf)
                .collect(Collectors.joining("-"));
        // 将信息存储到 redis 中
        redis.set(key, message);
        // 如果用户在线就将信息发送给指定用户
        if (WEBSOCKET_MAP.get(message.getTo()) != null) {
            WEBSOCKET_MAP.get(message.getTo()).getBasicRemote().sendText(gson.toJson(message));
        }
    }

    @OnClose
    public void onClose() {
        // 用户退出时，从 map 中删除信息
        for (Map.Entry<Long, Session> entry : WEBSOCKET_MAP.entrySet()) {
            if (this.session.getId().equals(entry.getValue().getId())) {
                WEBSOCKET_MAP.remove(entry.getKey());
                return;
            }
        }
    }

    @OnError
    public void onError(Throwable error) {
        error.printStackTrace();
    }

}

