// Package service 是 bot 每一步的具体做法：挑视频、加播放、搜标签。不保存状态，需要的状态由调用方传入
package service

import (
	"fmt"
	"math/rand/v2"
	"nilo-bot/api"
	"nilo-bot/util"
)

// BotService 具体业务过程，需要调用 nilo-web 的客户端
type BotService struct {
	client *api.WebClient
	log    *util.CallLogger
}

// NewBotService 构造器
func NewBotService(baseURL string, log *util.CallLogger) *BotService {
	return &BotService{client: api.NewWebClient(baseURL), log: log}
}

// PickVideo 在已知的总页数里随机挑一页，再从这一页里随机挑一个视频。
// 返回挑中的视频、最新的总页数；挑不到时 ok 为 false
func (s *BotService) PickVideo(pageTotal int) (videoID string, newPageTotal int, ok bool) {
	page, err := s.client.LoadVideo(rand.IntN(pageTotal) + 1)
	if err != nil {
		s.log.Log("loadVideo", "", err)
		return "", pageTotal, false
	}
	if len(page.List) == 0 {
		s.log.Log("loadVideo", "", fmt.Errorf("第 %d 页没有视频", page.PageNo))
		return "", pageTotal, false
	}
	videoID = page.List[rand.IntN(len(page.List))].VideoID
	s.log.Log("loadVideo", videoID, nil)
	return videoID, max(page.PageTotal, 1), true
}

// Play 给视频加一次播放
func (s *BotService) Play(videoID, sessionID string) {
	s.log.Log("play", videoID, s.client.Play(videoID, sessionID))
}

// SearchRandomTag 查视频详情拿到标签，随机挑一个去搜索；没有标签就不搜
func (s *BotService) SearchRandomTag(videoID string) {
	tags, err := s.client.LoadVideoInfo(videoID)
	s.log.Log("loadVideoInfo", videoID, err)
	if err != nil || len(tags) == 0 {
		return
	}
	s.log.Log("searchVideo", videoID, s.client.SearchVideo(tags[rand.IntN(len(tags))]))
}
