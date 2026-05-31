package com.neon.nilocommon.util;


import com.neon.nilocommon.entity.enums.PageSize;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * <b>分页计算器</b><hr/>
 * 功能：根据页号和页大小自动计算SQL中LIMIT需要的（startIndex,pageSize）<br/>
 * 异常处理：当pageSize多于总数量时，会自动限制为总数。当startIndex不合法时，会自动设置为0
 */
@Getter
@NoArgsConstructor
public class PageCalculator
{
    // 需要手动设置
    private int pageNo;
    private int pageSize;

    // 中间计算量（用于 mapper）
    private int start;
    private int size;

    // 查询/计算结果
    private int countTotal;
    private int pageTotal;

    /**
     * 设置分页参数（会触发计算）
     *
     * @param pageNo     页号
     * @param countTotal 数据总数
     * @param pageSize   页大小
     */
    public PageCalculator(Integer pageNo, int countTotal, int pageSize)
    {
        if (null == pageNo)
        {
            pageNo = 0;
        }
        this.pageNo = pageNo;
        this.countTotal = countTotal;
        this.pageSize = pageSize;
        calculate();
    }

    /**
     * 直接设置LIMIT开始序号和有效查询长度
     *
     * @param start 开始序号
     * @param size  有效查询长度
     */
    public PageCalculator(int start, int size)
    {
        this.start = start;
        this.size = size;
    }

    /**
     * 计算分页参数<hr/>
     * 如果pageSize<=0 : pageSize=20<br/>
     * 如果countTotal<=0 : countTotal=1<br/>
     * 如果pageNo<1 : pageNo=1<br/>
     * 如果pageNo>pageTotal : pageNo=pageTotal
     */
    public void calculate()
    {
        if (this.pageSize <= 0) this.pageSize = PageSize.SIZE20.getSize();
        if (this.countTotal > 0)
            this.pageTotal = this.countTotal % this.pageSize == 0 ? this.countTotal / this.pageSize : this.countTotal / this.pageSize + 1;
        else pageTotal = 1;
        if (pageNo < 1) pageNo = 1;
        if (pageNo > pageTotal) pageNo = pageTotal;

        this.start = (pageNo - 1) * pageSize;
        this.size = this.pageSize;
    }
}
