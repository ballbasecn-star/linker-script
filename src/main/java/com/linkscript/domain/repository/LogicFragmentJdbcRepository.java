package com.linkscript.domain.repository;

import com.linkscript.domain.entity.FragmentType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LogicFragmentJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public LogicFragmentJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(String scriptUuid, FragmentType type, String content, String logicDesc, String vectorLiteral,
            double confidence) {
        String pgSql = """
                INSERT INTO ls_logic_fragment (script_uuid, f_type, content, logic_desc, embedding, confidence)
                VALUES (?, ?, ?, ?, CAST(? AS vector), ?)
                """;
        try {
            jdbcTemplate.update(pgSql, scriptUuid, type.name(), content, logicDesc, vectorLiteral, confidence);
        } catch (DataAccessException exception) {
            String genericSql = """
                    INSERT INTO ls_logic_fragment (script_uuid, f_type, content, logic_desc, embedding, confidence)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """;
            jdbcTemplate.update(genericSql, scriptUuid, type.name(), content, logicDesc, vectorLiteral, confidence);
        }
    }

    public List<SearchHit> similaritySearch(String vectorLiteral, FragmentType type, int limit) {
        StringBuilder sql = new StringBuilder("""
                SELECT f.script_uuid,
                       s.title,
                       f.f_type,
                       f.content,
                       f.logic_desc,
                       1 - (f.embedding <=> CAST(? AS vector)) AS score
                FROM ls_logic_fragment f
                JOIN ls_script s ON s.script_uuid = f.script_uuid
                WHERE f.embedding IS NOT NULL
                """);
        List<Object> params = new ArrayList<>();
        params.add(vectorLiteral);
        if (type != null) {
            sql.append(" AND f.f_type = ? ");
            params.add(type.name());
        }
        sql.append(" ORDER BY f.embedding <=> CAST(? AS vector) LIMIT ? ");
        params.add(vectorLiteral);
        params.add(limit);
        return jdbcTemplate.query(sql.toString(), this::mapRow, params.toArray());
    }

    private SearchHit mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        return new SearchHit(
                resultSet.getString("script_uuid"),
                resultSet.getString("title"),
                FragmentType.valueOf(resultSet.getString("f_type")),
                resultSet.getString("content"),
                resultSet.getString("logic_desc"),
                resultSet.getDouble("score"));
    }

    public record SearchHit(
            String scriptUuid,
            String title,
            FragmentType type,
            String content,
            String logicDesc,
            double score) {
    }
}
