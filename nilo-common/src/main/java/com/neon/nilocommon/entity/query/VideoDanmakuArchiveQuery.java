package com.neon.nilocommon.entity.query;

import java.util.Date;


/**
 * 视频弹幕存档参数
 */
public class VideoDanmakuArchiveQuery extends BaseQuery {


	/**
	 * 弹幕ID【对外展示】
	 */
	private Long danmakuId;

	/**
	 * 视频ID
	 */
	private Long videoId;

	/**
	 * 视频文件ID
	 */
	private Long fileId;

	/**
	 * 用户ID
	 */
	private Long userId;

	/**
	 * 发布时间
	 */
	private String postTime;

	private String postTimeStart;

	private String postTimeEnd;

	/**
	 * 内容
	 */
	private String content;

	private String contentFuzzy;

	/**
	 * 展示位置
	 */
	private Integer position;

	/**
	 * 颜色(HEX+不透明度)
	 */
	private String color;

	private String colorFuzzy;

	/**
	 * 展示时刻（单位：毫秒）
	 */
	private Integer displayMoment;


	public void setDanmakuId(Long danmakuId){
		this.danmakuId = danmakuId;
	}

	public Long getDanmakuId(){
		return this.danmakuId;
	}

	public void setVideoId(Long videoId){
		this.videoId = videoId;
	}

	public Long getVideoId(){
		return this.videoId;
	}

	public void setFileId(Long fileId){
		this.fileId = fileId;
	}

	public Long getFileId(){
		return this.fileId;
	}

	public void setUserId(Long userId){
		this.userId = userId;
	}

	public Long getUserId(){
		return this.userId;
	}

	public void setPostTime(String postTime){
		this.postTime = postTime;
	}

	public String getPostTime(){
		return this.postTime;
	}

	public void setPostTimeStart(String postTimeStart){
		this.postTimeStart = postTimeStart;
	}

	public String getPostTimeStart(){
		return this.postTimeStart;
	}
	public void setPostTimeEnd(String postTimeEnd){
		this.postTimeEnd = postTimeEnd;
	}

	public String getPostTimeEnd(){
		return this.postTimeEnd;
	}

	public void setContent(String content){
		this.content = content;
	}

	public String getContent(){
		return this.content;
	}

	public void setContentFuzzy(String contentFuzzy){
		this.contentFuzzy = contentFuzzy;
	}

	public String getContentFuzzy(){
		return this.contentFuzzy;
	}

	public void setPosition(Integer position){
		this.position = position;
	}

	public Integer getPosition(){
		return this.position;
	}

	public void setColor(String color){
		this.color = color;
	}

	public String getColor(){
		return this.color;
	}

	public void setColorFuzzy(String colorFuzzy){
		this.colorFuzzy = colorFuzzy;
	}

	public String getColorFuzzy(){
		return this.colorFuzzy;
	}

	public void setDisplayMoment(Integer displayMoment){
		this.displayMoment = displayMoment;
	}

	public Integer getDisplayMoment(){
		return this.displayMoment;
	}

}
