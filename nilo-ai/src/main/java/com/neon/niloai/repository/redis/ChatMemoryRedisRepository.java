package com.neon.niloai.repository.redis;

import com.neon.nilocommon.entity.constants.RedisKey;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 对话记忆的 Redis 存储<hr/>
 * <p>Spring AI 1.0 只自带内存版存储。有了这个 Bean，自动配置出来的 ChatMemory（默认只保留最近 20 条）就会改用 Redis。</p>
 * <p>一段对话存成一个值：窗口裁剪后 Spring AI 每次都整体覆盖写回，一条命令就够。</p>
 * <p>只存用户、助手、系统三类消息的文本。工具调用的中间过程不进记忆，最终回答里已经带了 videoId，追问够用。</p>
 */
@Component
@RequiredArgsConstructor
public class ChatMemoryRedisRepository implements ChatMemoryRepository
{
    /**
     * 最后一次对话之后多久过期，每次写入重新计时
     */
    private static final Duration TTL = Duration.ofHours(2);

    private static final String TYPE = "type";

    private static final String TEXT = "text";

    private final RedisTemplate <String, Object> redisTemplate;

    @Override
    public List <String> findConversationIds()
    {
        List <String> conversationIds = new ArrayList <>();
        ScanOptions options = ScanOptions.scanOptions().match(RedisKey.AI_CHAT_MEMORY_PREFIX + "*").build();
        try (Cursor <String> cursor = redisTemplate.scan(options))
        {
            cursor.forEachRemaining(key -> conversationIds.add(key.substring(RedisKey.AI_CHAT_MEMORY_PREFIX.length())));
        }
        return conversationIds;
    }

    @Override
    public List <Message> findByConversationId(String conversationId)
    {
        if (!(redisTemplate.opsForValue().get(RedisKey.AI_CHAT_MEMORY_PREFIX + conversationId) instanceof List <?> stored))
        {
            return List.of();
        }
        List <Message> messages = new ArrayList <>(stored.size());
        for (Object item : stored)
        {
            if (item instanceof Map <?, ?> map)
            {
                Message message = toMessage(String.valueOf(map.get(TYPE)), String.valueOf(map.get(TEXT)));
                if (message != null)
                {
                    messages.add(message);
                }
            }
        }
        return messages;
    }

    @Override
    public void saveAll(String conversationId, List <Message> messages)
    {
        // 用 ArrayList / HashMap：Redis 序列化会记下具体类型，Map.of 这类不可变集合反序列化不回来
        List <Map <String, String>> stored = new ArrayList <>(messages.size());
        for (Message message : messages)
        {
            if (message.getText() == null)
            {
                continue;
            }
            Map <String, String> item = new HashMap <>();
            item.put(TYPE, message.getMessageType().name());
            item.put(TEXT, message.getText());
            stored.add(item);
        }
        redisTemplate.opsForValue().set(RedisKey.AI_CHAT_MEMORY_PREFIX + conversationId, stored, TTL);
    }

    @Override
    public void deleteByConversationId(String conversationId)
    {
        redisTemplate.delete(RedisKey.AI_CHAT_MEMORY_PREFIX + conversationId);
    }

    /**
     * 工具消息等其它类型不恢复，返回 null
     */
    private Message toMessage(String type, String text)
    {
        return switch (type)
        {
            case "USER" -> new UserMessage(text);
            case "ASSISTANT" -> new AssistantMessage(text);
            case "SYSTEM" -> new SystemMessage(text);
            default -> null;
        };
    }
}
