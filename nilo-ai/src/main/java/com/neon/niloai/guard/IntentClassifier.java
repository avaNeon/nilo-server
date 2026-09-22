package com.neon.niloai.guard;

import com.neon.niloai.entity.enums.IntentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 前置意图分类：判断用户这句话属不属于白名单里的行为。<hr/>
 * <p>只做分类，不生成任何面向用户的内容。任何异常、空值、非法值一律按 {@link IntentType#REJECT}
 * 处理——出错时默认拒绝，不默认放行。
 */
@Slf4j
@Component
public class IntentClassifier
{
    private static final String OPEN_TAG = "<用户输入>";

    private static final String CLOSE_TAG = "</用户输入>";

    private static final String REPLY_OPEN_TAG = "<上一轮助手回复>";

    private static final String REPLY_CLOSE_TAG = "</上一轮助手回复>";

    private final ChatClient classifierChatClient;

    public IntentClassifier(@Qualifier("classifierChatClient") ChatClient classifierChatClient)
    {
        this.classifierChatClient = classifierChatClient;
    }

    /**
     * @param lastReply 上一轮助手的回复，对话第一句时为 null。单看「第二个多长」判断不出意图，得结合上文
     * @return 命中的白名单行为；无法判定或出错时返回 REJECT
     */
    public IntentType classify(String question, String lastReply)
    {
        if (!StringUtils.hasText(question))
        {
            return IntentType.REJECT;
        }

        IntentDecision decision;
        try
        {
            decision = classifierChatClient.prompt().user(wrap(question, lastReply)).call().entity(IntentDecision.class);
        }
        catch (RuntimeException e)
        {
            log.warn("意图分类调用失败，按拒绝处理, question={}", question, e);
            return IntentType.REJECT;
        }
        if (decision == null || decision.intent() == null)
        {
            log.warn("意图分类没有返回可用结果，按拒绝处理, question={}", question);
            return IntentType.REJECT;
        }

        log.info("意图分类结果, intent={}, question={}", decision.intent(), question);

        return decision.intent();
    }

    /**
     * 用标签把用户输入（以及上一轮回复）框起来当数据看待
     */
    private String wrap(String question, String lastReply)
    {
        String input = OPEN_TAG + "\n" + sanitize(question) + "\n" + CLOSE_TAG;
        if (!StringUtils.hasText(lastReply))
        {
            return input;
        }
        return REPLY_OPEN_TAG + "\n" + sanitize(lastReply) + "\n" + REPLY_CLOSE_TAG + "\n" + input;
    }

    /**
     * 剔除文本里自带的同名标签，避免它提前闭合跳出包裹
     */
    private String sanitize(String text)
    {
        return text.replace(OPEN_TAG, "").replace(CLOSE_TAG, "").replace(REPLY_OPEN_TAG, "").replace(REPLY_CLOSE_TAG, "");
    }

    /**
     * 包一层 record 是为了让结构化输出能生成 JSON Schema，模型只能填枚举里的值
     */
    public record IntentDecision(IntentType intent)
    {
    }
}
