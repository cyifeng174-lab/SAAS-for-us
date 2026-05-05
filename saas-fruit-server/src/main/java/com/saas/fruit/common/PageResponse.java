package com.saas.fruit.common;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 分页响应格式
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PageResponse<T> extends ApiResponse<PageResponse.PageData<T>> {

    /** 无参构造函数 */
    public PageResponse() {
        super();
    }

    /** 全参构造函数 */
    public PageResponse(int code, String message, PageData<T> data) {
        super(code, message, data);
    }

    @Data
    public static class PageData<T> {
        /** 数据列表 */
        private List<T> list;
        /** 总条数 */
        private long total;
        /** 当前页码（从0开始） */
        private int page;
        /** 每页条数 */
        private int pageSize;
        /** 是否还有更多数据 */
        private boolean hasMore;

        public PageData() {
        }

        public PageData(List<T> list, long total, int page, int pageSize, boolean hasMore) {
            this.list = list;
            this.total = total;
            this.page = page;
            this.pageSize = pageSize;
            this.hasMore = hasMore;
        }
    }

    public static <T> PageResponse<T> success(List<T> list, long total, int page, int pageSize) {
        PageData<T> pageData = new PageData<>(list, total, page, pageSize, list.size() >= pageSize);
        return new PageResponse<>(0, "查询成功", pageData);
    }
}
