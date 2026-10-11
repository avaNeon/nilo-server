// Package api 调用 nilo-web 的公开接口，都不需要登录
package api

import (
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"

	"nilo-bot/model"
)

// WebClient 保存 nilo-web 的地址和发请求用的 HTTP 客户端
type WebClient struct {
	baseURL    string
	httpClient *http.Client
}

func NewWebClient(baseURL string) *WebClient {
	return &WebClient{
		baseURL:    strings.TrimSuffix(baseURL, "/"),
		httpClient: &http.Client{Timeout: 10 * time.Second},
	}
}

// LoadVideo 分页查询已发布的视频（VideoController.loadVideo）。页码超过总页数时后端返回最后一页
func (c *WebClient) LoadVideo(pageNo int) (model.VideoPage, error) {
	return call[model.VideoPage](c, http.MethodGet, "/video/video", url.Values{"pageNo": {strconv.Itoa(pageNo)}})
}

// LoadVideoInfo 查视频详情，只取标签（VideoController.loadVideoInfo），去掉空白的标签
func (c *WebClient) LoadVideoInfo(videoID string) ([]string, error) {
	detail, err := call[model.VideoDetail](c, http.MethodGet, "/video/video/"+videoID, nil)
	if err != nil {
		return nil, err
	}
	tags := make([]string, 0, len(detail.Tags))
	for _, tag := range detail.Tags {
		if tag = strings.TrimSpace(tag); tag != "" {
			tags = append(tags, tag)
		}
	}
	return tags, nil
}

// Play 给视频加一次播放（VideoController 的播放统计接口）。后端按 sessionId-视频、IP-视频两个维度限流
func (c *WebClient) Play(videoID, sessionID string) error {
	_, err := call[json.RawMessage](c, http.MethodPost, "/video/"+videoID, url.Values{"sessionId": {sessionID}})
	return err
}

// SearchVideo 搜索一次关键字（VideoSearchController.searchVideo），会计入热搜。
// 和用户在搜索框里搜一次一样：综合排序、第 1 页、每页 20 条
func (c *WebClient) SearchVideo(keyword string) error {
	_, err := call[json.RawMessage](c, http.MethodGet, "/video-search/1/1/20", url.Values{"keyword": {keyword}})
	return err
}

// call 发一次请求并解析统一的返回格式。
// 失败的情况：网络不通；后端返回 status 不是 success（比如被限流，错误原因取 info）；HTTP 状态码不是 200；返回内容不是 JSON
func call[T any](c *WebClient, method, path string, query url.Values) (T, error) {
	var zero T
	// 构造路径
	target := c.baseURL + path
	// 构造 param
	if len(query) > 0 {
		target += "?" + query.Encode()
	}
	// 构造请求
	req, err := http.NewRequest(method, target, nil)
	if err != nil {
		return zero, err
	}
	// 发送请求
	resp, err := c.httpClient.Do(req)
	if err != nil {
		return zero, err
	}
	// 函数退出时断开连接
	defer resp.Body.Close()

	// 先尝试按统一格式解析：后端自己返回的错误带着原因，比只看状态码更有用
	var body model.Response[T]
	decodeErr := json.NewDecoder(resp.Body).Decode(&body)
	if decodeErr == nil && body.Status != "success" {
		return zero, errors.New(body.Info)
	}
	if resp.StatusCode != http.StatusOK {
		return zero, fmt.Errorf("HTTP %d", resp.StatusCode)
	}
	if decodeErr != nil {
		return zero, fmt.Errorf("返回内容解析失败: %w", decodeErr)
	}
	return body.Data, nil
}
