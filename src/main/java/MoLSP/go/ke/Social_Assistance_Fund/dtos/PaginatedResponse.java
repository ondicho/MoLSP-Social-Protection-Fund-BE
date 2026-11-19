package MoLSP.go.ke.Social_Assistance_Fund.dtos;

import org.springframework.data.domain.Page;

import java.util.List;

public record PaginatedResponse<T>(
        List<T> content,
        int pageNo,
        int pageSize,
        long totalElements,
        int totalPages,
        boolean last) {

    public static <T> PaginatedResponse<T> from(Page<T> page) {
        return new PaginatedResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isLast());
    }
}
