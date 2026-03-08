package com.linkscript.core.vector;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.linkscript.domain.dto.FragmentSearchResponse;
import com.linkscript.domain.entity.FragmentType;
import com.linkscript.domain.entity.LogicFragmentEntity;
import com.linkscript.domain.entity.ScriptEntity;
import com.linkscript.domain.repository.LogicFragmentJdbcRepository;
import com.linkscript.domain.repository.LogicFragmentRepository;
import com.linkscript.domain.repository.ScriptRepository;
import com.linkscript.infra.ai.VectorProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.ArgumentMatchers;
import org.springframework.dao.DataAccessResourceFailureException;

class VectorSearchServiceTest {

    @Test
    void shouldFallbackToLexicalSearchWhenVectorQueryFails() {
        EmbeddingService embeddingService = Mockito.mock(EmbeddingService.class);
        LogicFragmentJdbcRepository jdbcRepository = Mockito.mock(LogicFragmentJdbcRepository.class);
        LogicFragmentRepository fragmentRepository = Mockito.mock(LogicFragmentRepository.class);
        ScriptRepository scriptRepository = Mockito.mock(ScriptRepository.class);

        when(embeddingService.embed(anyString())).thenReturn(new float[] {1.0f, 0.0f, 0.0f, 0.0f});
        when(embeddingService.toVectorLiteral(ArgumentMatchers.<float[]>any())).thenReturn("[1.0,0.0,0.0,0.0]");
        when(jdbcRepository.similaritySearch(anyString(), any(), anyInt()))
                .thenThrow(new DataAccessResourceFailureException("pgvector unavailable"));

        LogicFragmentEntity matched = new LogicFragmentEntity();
        matched.setScriptUuid("uuid-1");
        matched.setFragmentType(FragmentType.HOOK);
        matched.setContent("职场焦虑开场，第一句话就问你为什么越努力越没结果。");
        matched.setLogicDesc("通过焦虑感切入，快速建立代入。");

        LogicFragmentEntity unmatched = new LogicFragmentEntity();
        unmatched.setScriptUuid("uuid-2");
        unmatched.setFragmentType(FragmentType.HOOK);
        unmatched.setContent("美食探店开场，先上锅气和爆汁。");
        unmatched.setLogicDesc("感官刺激。");

        when(fragmentRepository.findTop200ByFragmentTypeOrderByIdDesc(FragmentType.HOOK))
                .thenReturn(List.of(unmatched, matched));

        ScriptEntity scriptOne = new ScriptEntity();
        scriptOne.setScriptUuid("uuid-1");
        scriptOne.setTitle("职场焦虑样本");
        ScriptEntity scriptTwo = new ScriptEntity();
        scriptTwo.setScriptUuid("uuid-2");
        scriptTwo.setTitle("探店样本");
        when(scriptRepository.findAllByScriptUuidIn(any())).thenReturn(List.of(scriptOne, scriptTwo));

        VectorSearchService service = new VectorSearchService(
                embeddingService,
                jdbcRepository,
                fragmentRepository,
                scriptRepository,
                new VectorProperties(4, 200)
        );

        List<FragmentSearchResponse> results = service.search("职场焦虑", FragmentType.HOOK, 2);

        assertThat(results).hasSize(2);
        assertThat(results.getFirst().scriptUuid()).isEqualTo("uuid-1");
        assertThat(results.getFirst().title()).isEqualTo("职场焦虑样本");
    }
}
