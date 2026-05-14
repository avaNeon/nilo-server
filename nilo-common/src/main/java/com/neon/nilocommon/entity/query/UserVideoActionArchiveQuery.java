package com.neon.nilocommon.entity.query;

import java.util.Date;


/**
 * 用户视频行为 点赞、收藏、投币参数
 */
public class UserVideoActionArchiveQuery extends BaseQuery {


	/**
	 * 自增ID
	 */
	private Long actionId;

	/**
	 * 视频ID
	 */
	private Long videoId;

	/**
	 * 视频用户ID
	 */
	private Long videoUserId;

	/**
	 * 1:视频点赞 2:视频收藏 3:视频投币
	 */
	private Integer actionType;

	/**
	 * 投币数量
	 */
	private Short coinAmount;

	/**
	 * 用户ID
	 */
	private Long userId;

	/**
	 * 操作时间
	 */
	private String actionTime;

	private String actionTimeStart;

	private String actionTimeEnd;


	public void setActionId(Long actionId){
		this.actionId = actionId;
	}

	public Long getActionId(){
		return this.actionId;
	}

	public void setVideoId(Long videoId){
		this.videoId = videoId;
	}

	public Long getVideoId(){
		return this.videoId;
	}

	public void setVideoUserId(Long videoUserId){
		this.videoUserId = videoUserId;
	}

	public Long getVideoUserId(){
		return this.videoUserId;
	}

	public void setActionType(Integer actionType){
		this.actionType = actionType;
	}

	public Integer getActionType(){
		return this.actionType;
	}

	public void setCoinAmount(Short coinAmount){
		this.coinAmount = coinAmount;
	}

	public Short getCoinAmount(){
		return this.coinAmount;
	}

	public void setUserId(Long userId){
		this.userId = userId;
	}

	public Long getUserId(){
		return this.userId;
	}

	public void setActionTime(String actionTime){
		this.actionTime = actionTime;
	}

	public String getActionTime(){
		return this.actionTime;
	}

	public void setActionTimeStart(String actionTimeStart){
		this.actionTimeStart = actionTimeStart;
	}

	public String getActionTimeStart(){
		return this.actionTimeStart;
	}
	public void setActionTimeEnd(String actionTimeEnd){
		this.actionTimeEnd = actionTimeEnd;
	}

	public String getActionTimeEnd(){
		return this.actionTimeEnd;
	}

}
