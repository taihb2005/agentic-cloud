package vn.edu.hust.kat.common.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchOperationResponse<S, F> {

    @JsonProperty("total")
    private int total;

    @JsonProperty("success_count")
    private int successCount;

    @JsonProperty("failed_count")
    private int failedCount;

    @Builder.Default
    @JsonProperty("succeeded")
    private List<S> succeeded = new ArrayList<>();

    @Builder.Default
    @JsonProperty("failed")
    private List<OperationFailure<F>> failed = new ArrayList<>();

    public static <S, F> BatchOperationResponse<S, F> of(
            int total,
            List<S> succeeded,
            List<OperationFailure<F>> failed
    ) {
        List<S> successList = succeeded != null
                ? succeeded
                : new ArrayList<>();

        List<OperationFailure<F>> failedList = failed != null
                ? failed
                : new ArrayList<>();

        return BatchOperationResponse.<S, F>builder()
                .total(total)
                .successCount(successList.size())
                .failedCount(failedList.size())
                .succeeded(successList)
                .failed(failedList)
                .build();
    }
}
