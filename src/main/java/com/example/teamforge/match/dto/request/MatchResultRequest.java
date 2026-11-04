package com.example.teamforge.match.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

public record MatchResultRequest(
        @JsonDeserialize(using = ScoreDeserializer.class)
        @NotNull @Min(0) @Schema(description = "A팀 경기 스코어", minimum = "0") Integer scoreA,
        @JsonDeserialize(using = ScoreDeserializer.class)
        @NotNull @Min(0) @Schema(description = "B팀 경기 스코어", minimum = "0") Integer scoreB,
        @Size(max = 2000) @Schema(description = "경기 메모. 생략·null은 빈 문자열로 저장", maxLength = 2000) String memo
) {
    public static class ScoreDeserializer extends ValueDeserializer<Integer> {
        @Override
        public Integer deserialize(JsonParser parser, DeserializationContext context) {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
                return context.reportInputMismatch(Integer.class, "경기 스코어는 JSON 정수여야 합니다.");
            }
            return parser.getIntValue();
        }
    }
}
