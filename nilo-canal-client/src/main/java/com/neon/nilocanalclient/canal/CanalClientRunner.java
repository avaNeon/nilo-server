package com.neon.nilocanalclient.canal;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import com.alibaba.otter.canal.protocol.Message;
import com.neon.nilocanalclient.config.CanalProperties;
import com.neon.nilocanalclient.service.VideoInfoCanalService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 常驻拉取 Canal Server 变更并交给 {@link VideoInfoCanalService}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CanalClientRunner implements ApplicationRunner
{
    private final AtomicBoolean running = new AtomicBoolean(true);

    private Thread worker;

    private volatile CanalConnector connector;

    private final CanalProperties canalProperties;

    private final VideoInfoCanalService videoInfoCanalService;

    @Override
    public void run(ApplicationArguments args)
    {
        worker = new Thread(this::listenLoop, "canal-client-worker");
        worker.setDaemon(false);
        worker.start();
        log.info("Canal client 已启动, {}:{} destination={} subscribe={}",
                 canalProperties.getHost(),
                 canalProperties.getPort(),
                 canalProperties.getDestination(),
                 canalProperties.getSubscribe());
    }

    private void listenLoop()
    {
        while (running.get())
        {
            try
            {
                // 检查连接，断了就重连
                ensureConnected();

                // 从canal中获取消息
                Message message = connector.getWithoutAck(canalProperties.getBatchSize());
                long batchId = message.getId();
                int size = message.getEntries() == null ? 0 : message.getEntries().size();

                // 如果没数据，休眠一会儿
                if (batchId == -1L || size == 0)
                {
                    sleepQuietly(canalProperties.getIdleSleepMs());
                    continue;
                }

                try
                {
                    // 处理数据
                    videoInfoCanalService.handleEntries(message.getEntries());

                    // ACK
                    connector.ack(batchId);
                }
                catch (Exception e)
                {
                    log.error("处理 Canal 批次失败, batchId={}, 将 rollback", batchId, e);
                    safeRollback(batchId);
                    sleepQuietly(canalProperties.getIdleSleepMs());
                }
            }
            catch (Exception e)
            {
                log.error("Canal 拉取异常，准备重连", e);
                disconnectQuietly();
                sleepQuietly(Math.max(canalProperties.getIdleSleepMs(), 3000L));
            }
        }

        disconnectQuietly();
        log.info("Canal client 已结束监听");
    }

    private void ensureConnected()
    {
        if (connector != null)
        {
            return;
        }

        CanalConnector newConnector = CanalConnectors.newSingleConnector(new InetSocketAddress(canalProperties.getHost(),
                                                                                               canalProperties.getPort()),
                                                                         canalProperties.getDestination(),
                                                                         canalProperties.getUsername() == null ? "" : canalProperties.getUsername(),
                                                                         canalProperties.getPassword() == null ? "" : canalProperties.getPassword());
        newConnector.connect();
        newConnector.subscribe(canalProperties.getSubscribe());
        newConnector.rollback();
        connector = newConnector;
        log.info("已连接 Canal Server");
    }

    /**
     * 按批次安全回滚
     *
     * @param batchId 批次ID
     */
    private void safeRollback(long batchId)
    {
        try
        {
            if (connector != null)
            {
                connector.rollback(batchId);
            }
        }
        catch (Exception e)
        {
            log.warn("Canal rollback 失败, batchId={}", batchId, e);
            disconnectQuietly();
        }
    }

    private void disconnectQuietly()
    {
        CanalConnector current = connector;
        connector = null;
        if (current == null)
        {
            return;
        }
        try
        {
            current.disconnect();
        }
        catch (Exception e)
        {
            log.warn("断开 Canal 连接失败", e);
        }
    }

    private void sleepQuietly(long ms)
    {
        try
        {
            Thread.sleep(ms);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            running.set(false);
        }
    }

    @PreDestroy
    public void shutdown()
    {
        running.set(false);
        if (worker != null)
        {
            worker.interrupt();
            try
            {
                worker.join(5000L);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
        }
        disconnectQuietly();
    }
}
