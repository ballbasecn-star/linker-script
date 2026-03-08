package com.linkscript.core.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.linkscript.core.vector.VectorSearchService;
import com.linkscript.domain.dto.GenerateCompositionRequest;
import com.linkscript.domain.dto.GenerateCompositionResponse;
import com.linkscript.domain.dto.GenerationOptions;
import com.linkscript.domain.entity.FragmentType;
import com.linkscript.domain.entity.GenerationLogEntity;
import com.linkscript.domain.entity.LogicFragmentEntity;
import com.linkscript.domain.repository.GenerationLogRepository;
import com.linkscript.domain.repository.LogicFragmentRepository;
import com.linkscript.infra.ai.AiGateway;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class GenerationServiceTest {

    @Test
    void shouldGenerateFallbackScriptAndPersistLog() {
        VectorSearchService vectorSearchService = Mockito.mock(VectorSearchService.class);
        LogicFragmentRepository fragmentRepository = Mockito.mock(LogicFragmentRepository.class);
        GenerationLogRepository generationLogRepository = Mockito.mock(GenerationLogRepository.class);
        AiGateway aiGateway = Mockito.mock(AiGateway.class);

        LogicFragmentEntity hook = new LogicFragmentEntity();
        hook.setScriptUuid("uuid-1");
        hook.setFragmentType(FragmentType.HOOK);
        hook.setContent("别再用老套路讲 AI 工具了。");

        LogicFragmentEntity value = new LogicFragmentEntity();
        value.setScriptUuid("uuid-1");
        value.setFragmentType(FragmentType.VALUE);
        value.setContent("先抛冲突，再给动作，转化会更高。");

        when(fragmentRepository.findByScriptUuidInOrderByScriptUuidAscIdAsc(List.of("uuid-1")))
                .thenReturn(List.of(hook, value));
        when(aiGateway.chat(any(), any())).thenReturn(Optional.empty());
        when(generationLogRepository.save(any())).thenAnswer(invocation -> {
            GenerationLogEntity entity = invocation.getArgument(0);
            entity.setTopic(entity.getTopic());
            entity.setReferenceUuids(entity.getReferenceUuids());
            entity.setAiOutput(entity.getAiOutput());
            return setId(entity, 99L);
        });

        GenerationService service = new GenerationService(
                vectorSearchService,
                fragmentRepository,
                generationLogRepository,
                aiGateway
        );

        GenerateCompositionRequest request = new GenerateCompositionRequest(
                "AI 工具推广",
                List.of("uuid-1"),
                new GenerationOptions("幽默", 300)
        );

        GenerateCompositionResponse response = service.generate(request);

        assertThat(response.logId()).isEqualTo(99L);
        assertThat(response.referenceUuids()).containsExactly("uuid-1");
        assertThat(response.content()).contains("AI 工具推广");
        assertThat(response.content()).contains("开场钩子");
    }

    private GenerationLogEntity setId(GenerationLogEntity entity, long id) {
        try {
            var field = GenerationLogEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
            return entity;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
