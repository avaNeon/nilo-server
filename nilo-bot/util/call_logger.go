// Package util 放通用的小工具
package util

import (
	"log/slog"
	"os"
)

// CallLogger 记录接口调用，输出到标准输出（用 docker logs 查看），每次调用一行：
//
//	time=2026-10-11T07:03:00.120Z api=play session=bot-3f9a2c1d0e5b7a64 video=2107840690523209728 ok=true err=null
//
// 键=值 的格式既方便人看，也方便之后按字段统计，比如数 api=play ok=true 有多少行
type CallLogger struct {
	logger    *slog.Logger
	sessionID string
}

func NewCallLogger(sessionID string) *CallLogger {
	return &CallLogger{logger: newLogger(), sessionID: sessionID}
}

// Log 记录一次接口调用。还没拿到视频时（分页查询失败）video 写 null；调用成功时 err 写 null
func (l *CallLogger) Log(api, videoID string, err error) {
	video, errText := "null", "null"
	if videoID != "" {
		video = videoID
	}
	if err != nil {
		errText = err.Error()
	}
	l.logger.Info("", "api", api, "session", l.sessionID, "video", video, "ok", err == nil, "err", errText)
}

// newLogger 文本格式的 slog，去掉默认带的 level 和 msg，一行只留时间和调用信息
func newLogger() *slog.Logger {
	return slog.New(slog.NewTextHandler(os.Stdout, &slog.HandlerOptions{
		ReplaceAttr: func(groups []string, a slog.Attr) slog.Attr {
			if a.Key == slog.LevelKey || a.Key == slog.MessageKey {
				return slog.Attr{}
			}
			return a
		},
	}))
}
