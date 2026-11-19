package MoLSP.go.ke.Social_Assistance_Fund.utils;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class PaginationUtil {
    public static final int DEFAULT_PAGE_NO = 0;
    public static final int DEFAULT_PAGE_SIZE = 2;
    public static final int MAX_PAGE_SIZE = 100;
    public static final String DEFAULT_SORT_BY = "createdAt";
    public static final String DEFAULT_SORT_DIRECTION = "desc";

    public Pageable buildPageable(Integer pageNo, Integer pageSize, String sortBy, String sortDirection) {
        return buildPageable(pageNo, pageSize, sortBy, sortDirection, DEFAULT_SORT_BY);
    }

    public Pageable buildPageable(Integer pageNo, Integer pageSize, String sortBy, String sortDirection,
                                  String defaultSortBy) {
        int resolvedPageNo = pageNo == null ? DEFAULT_PAGE_NO : pageNo;
        int resolvedPageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        String resolvedSortBy = sortBy == null || sortBy.isBlank() ? defaultSortBy : sortBy;
        String resolvedDirection = sortDirection == null || sortDirection.isBlank()
                ? DEFAULT_SORT_DIRECTION : sortDirection;

        if (resolvedPageNo < 0) {
            throw new IllegalArgumentException("pageNo must be zero or greater");
        }
        if (resolvedPageSize < 1 || resolvedPageSize > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("pageSize must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (!resolvedSortBy.matches("[A-Za-z][A-Za-z0-9]*")) {
            throw new IllegalArgumentException("sortBy contains unsupported characters");
        }
        Sort.Direction direction;
        try {
            direction = Sort.Direction.fromString(resolvedDirection);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("sortDirection must be 'asc' or 'desc'", exception);
        }
        return PageRequest.of(resolvedPageNo, resolvedPageSize, Sort.by(direction, resolvedSortBy));
    }
}
