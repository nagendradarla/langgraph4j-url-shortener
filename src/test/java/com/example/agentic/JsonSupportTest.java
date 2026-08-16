package com.example.agentic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonSupportTest {

    @Test
    void roundTripObjectAndArray() {
        String json = "{\"title\":\"T\",\"frs\":{\"FR-1\":\"a\"},\"ambiguities\":[],\"ok\":true}";
        Map<String, Object> parsed = JsonSupport.parseObject(json);
        assertEquals("T", parsed.get("title"));
        assertTrue(parsed.get("frs") instanceof Map<?, ?>);
        assertEquals("a", ((Map<?, ?>) parsed.get("frs")).get("FR-1"));
        assertEquals(true, parsed.get("ok"));
    }

    @Test
    void readSpecAndTasks(@TempDir Path tmp) throws Exception {
        Path spec = tmp.resolve("spec.json");
        Files.writeString(spec, "{\"title\":\"Aliases\",\"frs\":{\"FR-1\":\"custom alias\"},\"ambiguities\":[\"TTL?\"]}");
        Map<String, Object> loaded = LiveAgents.readSpec(spec);
        assertEquals("Aliases", loaded.get("title"));
        assertEquals(List.of("TTL?"), loaded.get("ambiguities"));

        Path tasks = tmp.resolve("tasks.json");
        Files.writeString(tasks, "{\"tasks\":[{\"id\":\"T1\",\"title\":\"alias\",\"fr\":\"FR-1\",\"parallel\":true}]}");
        List<Map<String, Object>> list = LiveAgents.readTasks(tasks);
        assertEquals(1, list.size());
        assertEquals("T1", list.get(0).get("id"));
        assertEquals(List.of(), list.get(0).get("dependsOn"));
    }
}
