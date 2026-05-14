package com.neon.nilocommon.entity.query;



/**
 * 删除视频文件存档参数
 */
public class VideoInfoFileArchiveQuery extends BaseQuery {


	/**
	 * 唯一ID
	 */
	private Long fileId;

	/**
	 * 用户ID
	 */
	private Long userId;

	/**
	 * 视频ID
	 */
	private Long videoId;

	/**
	 * 视频文件名
	 */
	private String fileName;

	private String fileNameFuzzy;

	/**
	 * 文件索引
	 */
	private Integer fileIndex;

	/**
	 * 文件大小
	 */
	private Long fileSize;

	/**
	 * 文件路径
	 */
	private String filePath;

	private String filePathFuzzy;

	/**
	 * 持续时间（秒）
	 */
	private Integer duration;


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

	public void setVideoId(Long videoId){
		this.videoId = videoId;
	}

	public Long getVideoId(){
		return this.videoId;
	}

	public void setFileName(String fileName){
		this.fileName = fileName;
	}

	public String getFileName(){
		return this.fileName;
	}

	public void setFileNameFuzzy(String fileNameFuzzy){
		this.fileNameFuzzy = fileNameFuzzy;
	}

	public String getFileNameFuzzy(){
		return this.fileNameFuzzy;
	}

	public void setFileIndex(Integer fileIndex){
		this.fileIndex = fileIndex;
	}

	public Integer getFileIndex(){
		return this.fileIndex;
	}

	public void setFileSize(Long fileSize){
		this.fileSize = fileSize;
	}

	public Long getFileSize(){
		return this.fileSize;
	}

	public void setFilePath(String filePath){
		this.filePath = filePath;
	}

	public String getFilePath(){
		return this.filePath;
	}

	public void setFilePathFuzzy(String filePathFuzzy){
		this.filePathFuzzy = filePathFuzzy;
	}

	public String getFilePathFuzzy(){
		return this.filePathFuzzy;
	}

	public void setDuration(Integer duration){
		this.duration = duration;
	}

	public Integer getDuration(){
		return this.duration;
	}

}
