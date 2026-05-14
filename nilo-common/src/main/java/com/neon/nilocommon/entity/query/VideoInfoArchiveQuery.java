package com.neon.nilocommon.entity.query;

import java.util.Date;


/**
 * 删除视频存档参数
 */
public class VideoInfoArchiveQuery extends BaseQuery {


	/**
	 * 视频ID
	 */
	private Long videoId;

	/**
	 * 视频封面（路径要展示给前端，所以相对路径不带顶层目录）
	 */
	private String videoCover;

	private String videoCoverFuzzy;

	/**
	 * 视频名称
	 */
	private String videoName;

	private String videoNameFuzzy;

	/**
	 * 用户ID
	 */
	private Long userId;

	/**
	 * 创建时间
	 */
	private String createTime;

	private String createTimeStart;

	private String createTimeEnd;

	/**
	 * 最后更新时间
	 */
	private String lastUpdateTime;

	private String lastUpdateTimeStart;

	private String lastUpdateTimeEnd;

	/**
	 * 删除时间
	 */
	private String deleteTime;

	private String deleteTimeStart;

	private String deleteTimeEnd;

	/**
	 * 删除者用户类型，0:用户，1:管理员
	 */
	private Integer deleterType;

	/**
	 * 删除详情
	 */
	private String deleteDetail;

	private String deleteDetailFuzzy;

	/**
	 * 父级分类ID
	 */
	private Integer pCategoryId;

	/**
	 * 分类ID
	 */
	private Integer categoryId;

	/**
	 * 0:自制作 1:转载
	 */
	private Short postType;

	/**
	 * 原资源说明
	 */
	private String originInfo;

	private String originInfoFuzzy;

	/**
	 * 标签，用","分隔不同的标签
	 */
	private String tags;

	private String tagsFuzzy;

	/**
	 * 简介
	 */
	private String introduction;

	private String introductionFuzzy;

	/**
	 * 互动设置（
如果可以发弹幕和发评论，就是NULL；
如果不能发弹幕，但是能发评论，就是0;
如果能发弹幕，不能发评论，就是1;
如果既不能发弹幕，也不能发评论，就是0,1
）
	 */
	private String interaction;

	private String interactionFuzzy;

	/**
	 * 持续时间（秒）
	 */
	private Integer duration;

	/**
	 * 播放数量
	 */
	private Integer playCount;

	/**
	 * 点赞数量
	 */
	private Integer likeCount;

	/**
	 * 弹幕数量
	 */
	private Integer danmakuCount;

	/**
	 * 评论数量
	 */
	private Integer commentCount;

	/**
	 * 投币数量
	 */
	private Integer coinCount;

	/**
	 * 收藏数量
	 */
	private Integer collectCount;


	public void setVideoId(Long videoId){
		this.videoId = videoId;
	}

	public Long getVideoId(){
		return this.videoId;
	}

	public void setVideoCover(String videoCover){
		this.videoCover = videoCover;
	}

	public String getVideoCover(){
		return this.videoCover;
	}

	public void setVideoCoverFuzzy(String videoCoverFuzzy){
		this.videoCoverFuzzy = videoCoverFuzzy;
	}

	public String getVideoCoverFuzzy(){
		return this.videoCoverFuzzy;
	}

	public void setVideoName(String videoName){
		this.videoName = videoName;
	}

	public String getVideoName(){
		return this.videoName;
	}

	public void setVideoNameFuzzy(String videoNameFuzzy){
		this.videoNameFuzzy = videoNameFuzzy;
	}

	public String getVideoNameFuzzy(){
		return this.videoNameFuzzy;
	}

	public void setUserId(Long userId){
		this.userId = userId;
	}

	public Long getUserId(){
		return this.userId;
	}

	public void setCreateTime(String createTime){
		this.createTime = createTime;
	}

	public String getCreateTime(){
		return this.createTime;
	}

	public void setCreateTimeStart(String createTimeStart){
		this.createTimeStart = createTimeStart;
	}

	public String getCreateTimeStart(){
		return this.createTimeStart;
	}
	public void setCreateTimeEnd(String createTimeEnd){
		this.createTimeEnd = createTimeEnd;
	}

	public String getCreateTimeEnd(){
		return this.createTimeEnd;
	}

	public void setLastUpdateTime(String lastUpdateTime){
		this.lastUpdateTime = lastUpdateTime;
	}

	public String getLastUpdateTime(){
		return this.lastUpdateTime;
	}

	public void setLastUpdateTimeStart(String lastUpdateTimeStart){
		this.lastUpdateTimeStart = lastUpdateTimeStart;
	}

	public String getLastUpdateTimeStart(){
		return this.lastUpdateTimeStart;
	}
	public void setLastUpdateTimeEnd(String lastUpdateTimeEnd){
		this.lastUpdateTimeEnd = lastUpdateTimeEnd;
	}

	public String getLastUpdateTimeEnd(){
		return this.lastUpdateTimeEnd;
	}

	public void setDeleteTime(String deleteTime){
		this.deleteTime = deleteTime;
	}

	public String getDeleteTime(){
		return this.deleteTime;
	}

	public void setDeleteTimeStart(String deleteTimeStart){
		this.deleteTimeStart = deleteTimeStart;
	}

	public String getDeleteTimeStart(){
		return this.deleteTimeStart;
	}
	public void setDeleteTimeEnd(String deleteTimeEnd){
		this.deleteTimeEnd = deleteTimeEnd;
	}

	public String getDeleteTimeEnd(){
		return this.deleteTimeEnd;
	}

	public void setDeleterType(Integer deleterType){
		this.deleterType = deleterType;
	}

	public Integer getDeleterType(){
		return this.deleterType;
	}

	public void setDeleteDetail(String deleteDetail){
		this.deleteDetail = deleteDetail;
	}

	public String getDeleteDetail(){
		return this.deleteDetail;
	}

	public void setDeleteDetailFuzzy(String deleteDetailFuzzy){
		this.deleteDetailFuzzy = deleteDetailFuzzy;
	}

	public String getDeleteDetailFuzzy(){
		return this.deleteDetailFuzzy;
	}

	public void setpCategoryId(Integer pCategoryId){
		this.pCategoryId = pCategoryId;
	}

	public Integer getpCategoryId(){
		return this.pCategoryId;
	}

	public void setCategoryId(Integer categoryId){
		this.categoryId = categoryId;
	}

	public Integer getCategoryId(){
		return this.categoryId;
	}

	public void setPostType(Short postType){
		this.postType = postType;
	}

	public Short getPostType(){
		return this.postType;
	}

	public void setOriginInfo(String originInfo){
		this.originInfo = originInfo;
	}

	public String getOriginInfo(){
		return this.originInfo;
	}

	public void setOriginInfoFuzzy(String originInfoFuzzy){
		this.originInfoFuzzy = originInfoFuzzy;
	}

	public String getOriginInfoFuzzy(){
		return this.originInfoFuzzy;
	}

	public void setTags(String tags){
		this.tags = tags;
	}

	public String getTags(){
		return this.tags;
	}

	public void setTagsFuzzy(String tagsFuzzy){
		this.tagsFuzzy = tagsFuzzy;
	}

	public String getTagsFuzzy(){
		return this.tagsFuzzy;
	}

	public void setIntroduction(String introduction){
		this.introduction = introduction;
	}

	public String getIntroduction(){
		return this.introduction;
	}

	public void setIntroductionFuzzy(String introductionFuzzy){
		this.introductionFuzzy = introductionFuzzy;
	}

	public String getIntroductionFuzzy(){
		return this.introductionFuzzy;
	}

	public void setInteraction(String interaction){
		this.interaction = interaction;
	}

	public String getInteraction(){
		return this.interaction;
	}

	public void setInteractionFuzzy(String interactionFuzzy){
		this.interactionFuzzy = interactionFuzzy;
	}

	public String getInteractionFuzzy(){
		return this.interactionFuzzy;
	}

	public void setDuration(Integer duration){
		this.duration = duration;
	}

	public Integer getDuration(){
		return this.duration;
	}

	public void setPlayCount(Integer playCount){
		this.playCount = playCount;
	}

	public Integer getPlayCount(){
		return this.playCount;
	}

	public void setLikeCount(Integer likeCount){
		this.likeCount = likeCount;
	}

	public Integer getLikeCount(){
		return this.likeCount;
	}

	public void setDanmakuCount(Integer danmakuCount){
		this.danmakuCount = danmakuCount;
	}

	public Integer getDanmakuCount(){
		return this.danmakuCount;
	}

	public void setCommentCount(Integer commentCount){
		this.commentCount = commentCount;
	}

	public Integer getCommentCount(){
		return this.commentCount;
	}

	public void setCoinCount(Integer coinCount){
		this.coinCount = coinCount;
	}

	public Integer getCoinCount(){
		return this.coinCount;
	}

	public void setCollectCount(Integer collectCount){
		this.collectCount = collectCount;
	}

	public Integer getCollectCount(){
		return this.collectCount;
	}

}
