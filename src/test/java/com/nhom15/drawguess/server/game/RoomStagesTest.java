package com.nhom15.drawguess.server.game;

import com.nhom15.drawguess.common.protocol.*;
import com.nhom15.drawguess.server.dao.WordDAO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RoomStagesTest {
    @TempDir Path directory;

    private final Map<Integer, List<Message>> inbox = new LinkedHashMap<>();

    private List<Message> messages(int id) { return inbox.computeIfAbsent(id, key -> new ArrayList<>()); }

    private Message last(int id, MessageType type) {
        return messages(id).stream().filter(m -> m.getType() == type).reduce((a, b) -> b).orElseThrow();
    }

    private void join(RoomManager rooms, int... ids) {
        for (int id : ids) { rooms.join(new UserInfo(id, "player" + id, "USER"), 1, messages(id)::add); }
    }

    private GameManager game() {
        WordDAO words = new WordDAO() {
            @Override protected Map<String, List<String>> loadTopics() {
                Map<String, List<String>> topics = new LinkedHashMap<>();
                topics.put("Động vật", List.of("Con mèo", "Con chó", "Con voi"));
                topics.put("Đồ ăn", List.of("Phở", "Bún chả", "Cơm tấm"));
                topics.put("Nhạc cụ", List.of("Đàn"));
                return topics;
            }
        };
        return new GameManager(words, new DrawingStore(directory), System::nanoTime, false,
                new com.nhom15.drawguess.server.dao.HistoryDAO() { @Override public void save(HistoryData.Detail d) {} });
    }

    @Test void onlyTheHostSetsStagesAndEveryoneSeesThem() throws Exception {
        try (GameManager game = game()) {
            RoomManager rooms = new RoomManager(game, System::nanoTime, RoomManager.StartTimer.IMMEDIATE);
            join(rooms, 1, 2);
            assertEquals(List.of(""), rooms.getRooms().getFirst().getStages(), "Mặc định một vòng ngẫu nhiên");
            assertEquals(List.of("Động vật", "Đồ ăn"), rooms.topics(), "Chủ đề thiếu từ không được liệt kê");

            rooms.setStages(2, List.of("Đồ ăn"), messages(2)::add);
            assertEquals("NOT_HOST", last(2, MessageType.ACTION_FAILED).getData());

            rooms.setStages(1, List.of("Đồ ăn", "", " Động vật "), messages(1)::add);
            RoomInfo seen = (RoomInfo) last(2, MessageType.ROOM_INFO).getData();
            assertEquals(List.of("Đồ ăn", "", "Động vật"), seen.getStages());

            for (Object invalid : List.of(List.of(), List.of("", "", "", "", "", ""), List.of("Đồ ăn", "Đồ ăn"),
                    List.of(1), "Đồ ăn", List.of("x".repeat(101)))) {
                messages(1).clear();
                rooms.setStages(1, invalid, messages(1)::add);
                assertEquals("INVALID_STAGES", last(1, MessageType.ACTION_FAILED).getData(), String.valueOf(invalid));
            }
            assertEquals(3, rooms.getRooms().getFirst().getStages().size(), "Yêu cầu sai không đổi vòng đấu");

            rooms.leave(1, messages(1)::add);
            assertEquals(2, rooms.getRooms().getFirst().getHostId());
            assertEquals(3, rooms.getRooms().getFirst().getStages().size(), "Đổi chủ phòng vẫn giữ vòng đấu");
            rooms.leave(2, messages(2)::add);
            assertEquals(List.of(""), rooms.getRooms().getFirst().getStages(), "Phòng trống thì về mặc định");
        }
    }

    @Test void matchStartsWithTheChosenStagesAndStagesAreLockedWhilePlaying() throws Exception {
        try (GameManager game = game()) {
            RoomManager rooms = new RoomManager(game, System::nanoTime, RoomManager.StartTimer.IMMEDIATE);
            join(rooms, 1, 2, 3);
            rooms.setStages(1, List.of("Đồ ăn", "Động vật"), messages(1)::add);
            for (int id = 1; id <= 3; id++) { rooms.ready(id, true, messages(id)::add); }
            DrawingPrompt prompt = (DrawingPrompt) last(2, MessageType.DRAW_WORD).getData();
            assertEquals("Đồ ăn", prompt.getCategory());
            assertEquals(2, prompt.getStageCount());
            assertTrue(List.of("Phở", "Bún chả", "Cơm tấm").contains(prompt.getWord()));

            rooms.setStages(1, List.of("Động vật"), messages(1)::add);
            assertEquals("ROOM_PLAYING", last(1, MessageType.ACTION_FAILED).getData());
        }
    }

    @Test void unavailableTopicKeepsTheRoomWaitingWithAClearReason() throws Exception {
        try (GameManager game = game()) {
            RoomManager rooms = new RoomManager(game, System::nanoTime, RoomManager.StartTimer.IMMEDIATE);
            join(rooms, 1, 2);
            rooms.setStages(1, List.of("Nhạc cụ"), messages(1)::add);
            rooms.ready(1, true, messages(1)::add);
            rooms.ready(2, true, messages(2)::add);
            assertEquals("TOPIC_UNAVAILABLE", last(2, MessageType.GAME_ERROR).getData());
            assertEquals(RoomStatus.READY_CHECK, rooms.getRooms().getFirst().getStatus());
        }
    }
}
